package nz.myinspection.core.report.importing.docx.extract

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import nz.myinspection.core.report.importing.docx.`package`.DocxPackageReader
import nz.myinspection.core.report.importing.docx.`package`.DocxPackage
import nz.myinspection.core.report.importing.docx.`package`.DocxPart
import nz.myinspection.core.report.importing.docx.`package`.DocxPartKind

/** Original text and generated pixels; only the fragmented package shape mirrors the audit. */
internal const val DOCUMENT_PART = "word/document.xml"
private const val FIXTURE_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val FIXTURE_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val FIXTURE_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
private const val FIXTURE_WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
private const val FIXTURE_PR = "http://schemas.openxmlformats.org/package/2006/relationships"
internal fun run(text: String) = "<w:r><w:t>$text</w:t></w:r>"
internal fun p(text: String) = "<w:p><w:r><w:t xml:space='preserve'>$text</w:t></w:r></w:p>"
internal fun story(root: String, body: String) =
    "<w:$root xmlns:w='$FIXTURE_W' xmlns:r='$FIXTURE_R' xmlns:a='$FIXTURE_A' xmlns:wp='$FIXTURE_WP'>$body</w:$root>"
internal fun drawing(id: String, kind: String = "inline") =
    "<w:p><w:r><w:drawing><wp:$kind><a:graphic><a:graphicData><a:blip r:embed='$id'/>" +
        "</a:graphicData></a:graphic></wp:$kind></w:drawing></w:r></w:p>"
internal fun relationship(id: String, target: String, type: String) =
    "<Relationship Id='$id' Target='$target' Type='$FIXTURE_R/$type'/>"
internal fun field(instruction: String, cache: String, simple: Boolean) = if (simple)
    "<w:fldSimple w:instr='$instruction'>$cache</w:fldSimple>" else
    "<w:r><w:fldChar w:fldCharType='begin'/></w:r><w:r><w:instrText>$instruction</w:instrText></w:r>" +
        "<w:r><w:fldChar w:fldCharType='separate'/></w:r>$cache<w:r><w:fldChar w:fldCharType='end'/></w:r>"
internal fun relationships(body: String) = "<Relationships xmlns='$FIXTURE_PR'>$body</Relationships>"
internal fun row(name: String, status: String, comment: String) =
    "<w:tr><w:tc>${p(name)}</w:tc><w:tc>${p(status)}</w:tc><w:tc>${p(comment)}</w:tc></w:tr>"
internal fun image(size: Int, seed: Int, format: String = "png"): ByteArray {
    val bitmap = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until size) for (x in 0 until size) bitmap.setRGB(x, y, (seed * 10007 + x * 503 + y * 59) and 0xffffff)
    return ByteArrayOutputStream().also { check(ImageIO.write(bitmap, format, it)) }.toByteArray()
}
internal fun repairPngCrc(bytes: ByteArray): ByteArray = bytes.apply {
    val crc = CRC32().apply { update(bytes, 12, 17) }.value
    for (i in 0..3) this[29 + i] = (crc ushr (24 - i * 8)).toByte()
}
internal fun forgedImage(bytes: ByteArray, format: String) = DocxPackage(read(parts()).parts +
    DocxPart("word/media/forged.$format", DocxPartKind.IMAGE, bytes))
internal fun imageParts(bytes: ByteArray, format: String) = parts(drawing("image") + drawing("image", "anchor")).apply {
    this["word/media/bad.$format"] = bytes
    this["word/_rels/document.xml.rels"] = relationships(relationship("image", "media/bad.$format", "image")).toByteArray()
}
internal fun incompleteImages(format: String): List<ByteArray> {
    val valid = image(1, 1, format)
    val png = format == "png"
    val sof = if (png) 0 else (0 until valid.size - 1).first { valid[it] == 255.toByte() && valid[it + 1] == 192.toByte() }
    val headerEnd = if (png) 33 else sof + 2 + (valid[sof + 2].toInt() and 255) * 256 + (valid[sof + 3].toInt() and 255)
    val truncated = valid.copyOf(if (png) 45 else valid.size - 3)
    val damaged = valid.copyOf().apply {
        val offset = if (png) 41 else (0 until size - 1).first { this[it] == 255.toByte() && this[it + 1] == 218.toByte() } + 1
        this[offset] = 0
    }
    return listOf(valid.copyOf(headerEnd), truncated, damaged,
        truncated + valid.copyOfRange(valid.size - (if (png) 12 else 2), valid.size))
}
internal fun parts(body: String = p("Unknown original observation")): LinkedHashMap<String, ByteArray> = linkedMapOf(
    "[Content_Types].xml" to ("<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>" +
        "<Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/>" +
        "<Override PartName='/word/document.xml' ContentType='application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml'/>" +
        (1..12).joinToString("") { index -> listOf("header", "footer").joinToString("") { kind ->
            "<Override PartName='/word/$kind$index.xml' ContentType='application/vnd.openxmlformats-officedocument.wordprocessingml.$kind+xml'/>"
        } } + "</Types>").toByteArray(),
    "_rels/.rels" to relationships(relationship("office", DOCUMENT_PART, "officeDocument")).toByteArray(),
    DOCUMENT_PART to story("document", "<w:body>$body</w:body>").toByteArray(),
).apply {
    for (index in 1..12) for ((kind, root) in listOf("header" to "hdr", "footer" to "ftr")) {
        this["word/$kind$index.xml"] = story(root, p("Unique $kind observation $index")).toByteArray()
    }
}
internal fun sample(): LinkedHashMap<String, ByteArray> {
    val body = buildString {
        append(p("PROPERTY ADDRESS") + p("42 Synthetic Lane") + p("INSPECTION DATE") + p("3 September 2026"))
        append(p("Inspection (03/09/2026)") + p("Feature") + p("Exterior"))
        for (i in 1..10) append(p("Outer feature $i"))
        append(p("Hallway"))
        for (i in 1..6) append(p("Passage feature $i"))
        append(p("Status"))
        repeat(12) { append(p("Good")) }
        append(p("Good Comments"))
        repeat(16) { append(p("Unassigned outer observation $it")) }
        append(p("Bathroom"))
        for (i in 1..14) append(p("Washroom feature $i"))
        append(p("Bedroom 1"))
        for (i in 1..7) append(p("Sleeping feature $i"))
        append(p("Bedroom 2"))
        for (i in 1..2) append(p("Second sleeping feature $i"))
        repeat(21) { append(p(if (it == 20) "Undecided spelling" else "Good")) }
        repeat(24) { append(p("Unassigned inner observation $it")) }
        append("<w:tbl>")
        repeat(24) { i ->
            val room = when (i) { 5 -> p("Lounge"); 14 -> p("Kitchen"); else -> "" }
            append("<w:tr><w:tc>$room${p("  Tabular feature $i  ")}</w:tc>" +
                "<w:tc>${p(if (i >= 22) "" else "  FaIr  ")}</w:tc><w:tc>${p("Table observation $i")}</w:tc></w:tr>")
        }
        append("</w:tbl>" + p("General") + p("Overall impression") + p("Comments / Summary") + p("Original synthetic summary.") + p("Images"))
        for (pair in (1..89).chunked(2)) append(p(pair.joinToString(" ") { i -> "${i.toString().padStart(3, '0')}-Area Alpha $i" }))
        for (i in 1..67) append(drawing("image$i", if (i % 2 == 0) "anchor" else "inline"))
        for (i in 1..15) append(drawing("small$i"))
    }
    return parts(body).apply {
        val links = buildString {
            for (i in 1..67) append(relationship("image$i", "media/photo$i.png", "image"))
            for (i in 1..15) append(relationship("small$i", "media/small$i.png", "image"))
        }
        this["word/_rels/document.xml.rels"] = relationships(links).toByteArray()
        for (i in 1..67) this["word/media/photo$i.png"] = image(32, i)
        for (i in 1..15) this["word/media/small$i.png"] = image(i, i)
        this["word/header1.xml"] = story("hdr", p("Header observation") + drawing("again", "anchor")).toByteArray()
        this["word/_rels/header1.xml.rels"] = relationships(relationship("again", "MEDIA/Photo67.PNG", "image")).toByteArray()
        for (i in 1..3) this["word/footer$i.xml"] = story("ftr", p("Good") + p("Fair") + p("Unique footer observation $i")).toByteArray()
    }
}
internal fun zip(parts: Map<String, ByteArray>, stored: Boolean = true): ByteArray = ByteArrayOutputStream().also { bytes ->
    ZipOutputStream(bytes).use { out -> parts.forEach { (name, original) ->
        val data = if (name == "[Content_Types].xml") {
            val images = listOf("png" to "image/png", "jpg" to "image/jpeg").filter { (ext, _) -> parts.keys.any { it.endsWith(".$ext") } }
            original.toString(Charsets.UTF_8).replace("</Types>", images.joinToString("") { (ext, type) ->
                "<Default Extension='$ext' ContentType='$type'/>"
            } + "</Types>").toByteArray()
        } else original
        val entry = ZipEntry(name).apply {
            time = 0
            if (stored) { method = ZipEntry.STORED; size = data.size.toLong(); crc = CRC32().apply { update(data) }.value }
        }
        out.putNextEntry(entry)
        out.write(data)
        out.closeEntry()
    } }
}.toByteArray()
internal fun read(parts: Map<String, ByteArray>, stored: Boolean = true) =
    DocxPackageReader().read(ByteArrayInputStream(zip(parts, stored)))
