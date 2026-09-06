package nz.myinspection.core.report.importing.docx.image

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Adler32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import nz.myinspection.core.report.importing.docx.`package`.DocxPackageReader
import nz.myinspection.core.report.importing.docx.`package`.DocxPartKind

/** Original pixels and text. ImageIO is a test-only independent JPEG encoder. */
internal object DocxImageFixture {
    val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    fun integer(value: Long) = ByteArray(4) { (value ushr (24 - 8 * it)).toByte() }
    fun header(width: Int = 1, height: Int = 1, color: Int = 2, depth: Int = 8) =
        integer(width.toLong()) + integer(height.toLong()) + byteArrayOf(depth.toByte(), color.toByte(), 0, 0, 0)

    fun chunk(type: String, data: ByteArray): ByteArray {
        val body = type.toByteArray(Charsets.US_ASCII) + data
        return integer(data.size.toLong()) + body + integer(CRC32().apply { update(body) }.value)
    }
    fun pngChunks(vararg chunks: Pair<String, ByteArray>) =
        signature + chunks.fold(byteArrayOf()) { bytes, (type, data) -> bytes + chunk(type, data) }

    fun scanlines(width: Int, height: Int, channels: Int, filters: IntArray = IntArray(height)): ByteArray {
        val stride = 1 + width * channels
        return ByteArray(stride * height) { at ->
            if (at % stride == 0) filters[at / stride].toByte() else (at * 37).toByte()
        }
    }
    fun zlib(raw: ByteArray, dictionary: ByteArray? = null): ByteArray {
        val deflater = Deflater()
        try {
            if (dictionary != null) deflater.setDictionary(dictionary)
            deflater.setInput(raw)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }
    fun storedZlib(raw: ByteArray, emptyBlocks: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x78, 0x01))
        repeat(emptyBlocks) { out.write(byteArrayOf(0, 0, 0, 255.toByte(), 255.toByte())) }
        out.write(1)
        for (value in listOf(raw.size, raw.size xor 65535)) {
            out.write(value and 255)
            out.write(value ushr 8)
        }
        out.write(raw)
        out.write(integer(Adler32().apply { update(raw) }.value))
        return out.toByteArray()
    }
    fun png(width: Int = 1, height: Int = 1, channels: Int = 3, filters: IntArray = IntArray(height)) =
        pngChunks("IHDR" to header(width, height, if (channels == 3) 2 else 6),
            "IDAT" to zlib(scanlines(width, height, channels, filters)), "IEND" to byteArrayOf())

    fun jpeg(): ByteArray = ByteArrayOutputStream().also { out ->
        check(ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "jpeg", out))
    }.toByteArray()

    fun jpegFrame(width: Int, height: Int) = byteArrayOf(255.toByte(), 216.toByte(), 255.toByte(), 192.toByte(),
        0, 11, 8, (height ushr 8).toByte(), height.toByte(), (width ushr 8).toByte(), width.toByte(), 1, 1, 0x11, 0)

    fun jpegHeader(bytes: ByteArray): ByteArray {
        val sof = (0 until bytes.size - 1).first { bytes[it] == 255.toByte() && bytes[it + 1] == 192.toByte() }
        val length = (bytes[sof + 2].toInt() and 255) * 256 + (bytes[sof + 3].toInt() and 255)
        return bytes.copyOf(sof + 2 + length)
    }

    fun throughReader(image: ByteArray, extension: String = "png"): ByteArray {
        val word = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val relationships = "http://schemas.openxmlformats.org/package/2006/relationships"
        val office = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val type = if (extension == "png") "png" else "jpeg"
        val parts = linkedMapOf(
            "[Content_Types].xml" to ("<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>" +
                "<Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/>" +
                "<Default Extension='$extension' ContentType='image/$type'/>" +
                "<Override PartName='/word/document.xml' ContentType='application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml'/></Types>").toByteArray(),
            "_rels/.rels" to ("<Relationships xmlns='$relationships'><Relationship Id='office' " +
                "Type='$office/officeDocument' Target='word/document.xml'/></Relationships>").toByteArray(),
            "word/document.xml" to "<w:document xmlns:w='$word'><w:body><w:p/></w:body></w:document>".toByteArray(),
            "word/_rels/document.xml.rels" to ("<Relationships xmlns='$relationships'><Relationship Id='image' " +
                "Type='$office/image' Target='media/image.$extension'/></Relationships>").toByteArray(),
            "word/media/image.$extension" to image,
        )
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            parts.forEach { (name, content) ->
                val entry = ZipEntry(name)
                entry.method = ZipEntry.STORED
                entry.size = content.size.toLong()
                entry.crc = CRC32().apply { update(content) }.value
                zip.putNextEntry(entry)
                zip.write(content)
                zip.closeEntry()
            }
        }
        return DocxPackageReader().read(bytes.toByteArray().inputStream()).parts.single { it.kind == DocxPartKind.IMAGE }.copyBytes()
    }
}
