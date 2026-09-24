package nz.myinspection.core.report.importing.docx.image

import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Pure inspection of a narrow RGB8/RGBA8, non-interlaced PNG subset. Only complete
 * tiny payloads within fixed qualification budgets yield validation candidates; human review remains mandatory.
 * Other inputs remain reviewable; a proven header above 40 MP throws a closed error.
 * No bytes are mutated or retained, and JPEG headers never prove payload validity.
 */
class DocxImageQualifier {
    fun qualify(bytes: ByteArray): DocxImageQualification {
        val png = PNG_SIGNATURE.indices.all { it < bytes.size && bytes[it] == PNG_SIGNATURE[it] }
        val dimensions = if (png) pngDimensions(bytes) else jpegDimensions(bytes)
        val candidate = png && dimensions != null && dimensions.width <= 24 && dimensions.height <= 24 &&
            bytes.size <= 65536 && u8(bytes, 24) == 8 && u8(bytes, 25) in setOf(2, 6) &&
            u8(bytes, 28) == 0 && hasPayload(bytes, dimensions)
        return DocxImageQualification(dimensions,
            if (candidate) DocxImageDisposition.VALIDATED_SMALL_CANDIDATE else DocxImageDisposition.REVIEW_REQUIRED)
    }

    // W3C PNG 3 sections 5.6, 10 and 11.2. Only IHDR/IDAT/IEND are supported;
    // every other chunk leaves the image review-required.
    private fun hasPayload(bytes: ByteArray, dimensions: DocxImageDimensions): Boolean {
        var at = 33
        var chunks = 1
        val compressed = ByteArray(bytes.size)
        var compressedSize = 0
        while (at.toLong() + 12 <= bytes.size) {
            if (++chunks > 64) return false
            val length = u32(bytes, at)
            val end = at.toLong() + 12 + length
            if (end > bytes.size) return false
            val count = length.toInt()
            if (CRC32().apply { update(bytes, at + 4, count + 4) }.value != u32(bytes, at + 8 + count)) return false
            val type = String(bytes, at + 4, 4, Charsets.US_ASCII)
            when (type) {
                "IDAT" -> {
                    bytes.copyInto(compressed, compressedSize, at + 8, at + 8 + count)
                    compressedSize += count
                }
                "IEND" -> return length == 0L && end == bytes.size.toLong() &&
                    completeScanlines(compressed.copyOf(compressedSize), dimensions, if (u8(bytes, 25) == 2) 3 else 4)
                else -> return false
            }
            at = end.toInt()
        }
        return false
    }

    private fun completeScanlines(compressed: ByteArray, dimensions: DocxImageDimensions, channels: Int): Boolean {
        val stride = 1 + dimensions.width * channels
        val expected = stride * dimensions.height
        // One extra byte detects over-expansion without allocating the claimed image size.
        val output = ByteArray(expected + 1)
        val inflater = Inflater(false)
        try {
            inflater.setInput(compressed)
            var size = 0
            while (!inflater.finished() && size < output.size) {
                val before = inflater.bytesRead
                val written = inflater.inflate(output, size, output.size - size)
                size += written
                if (written == 0 && inflater.bytesRead == before) return false
            }
            // Exact size rules out the buffer-full exit, leaving only end-of-stream.
            // Dictionary requests and truncated streams instead stop without progress.
            return inflater.remaining == 0 && size == expected &&
                (0 until dimensions.height).all { u8(output, it * stride) in 0..4 }
        } catch (_: DataFormatException) {
            return false
        } finally {
            inflater.end()
        }
    }

    private fun pngDimensions(bytes: ByteArray): DocxImageDimensions? {
        if (bytes.size < 33 || u32(bytes, 8) != 13L || String(bytes, 12, 4, Charsets.US_ASCII) != "IHDR") return null
        if (CRC32().apply { update(bytes, 12, 17) }.value != u32(bytes, 29)) return null
        val depths = when (u8(bytes, 25)) {
            0 -> setOf(1, 2, 4, 8, 16)
            2, 4, 6 -> setOf(8, 16)
            3 -> setOf(1, 2, 4, 8)
            else -> emptySet()
        }
        if (u8(bytes, 24) !in depths || u8(bytes, 26) != 0 || u8(bytes, 27) != 0 || u8(bytes, 28) !in 0..1) return null
        val width = u32(bytes, 16)
        val height = u32(bytes, 20)
        if (width !in 1L..2147483647L || height !in 1L..2147483647L) return null
        return bounded(width, height)
    }

    private fun jpegDimensions(bytes: ByteArray): DocxImageDimensions? {
        // ITU-T T.81 B.2.2/B.2.4: conservative SOF0/1/2 dimensions, never JPEG qualification.
        if (bytes.size < 2 || u8(bytes, 0) != 255 || u8(bytes, 1) != 216) return null
        var at = 2
        while (at.toLong() + 4 <= bytes.size && u8(bytes, at) == 255) {
            while (at < bytes.size && u8(bytes, at) == 255) at++
            if (at.toLong() + 3 > bytes.size) return null
            val marker = u8(bytes, at++)
            if (marker !in 0xc0..0xcf && marker !in 0xe0..0xef && marker !in setOf(0xdb, 0xdd, 0xfe)) return null
            val length = u8(bytes, at) * 256 + u8(bytes, at + 1)
            if (length < 2 || at.toLong() + length > bytes.size) return null
            if (marker in 0xc0..0xcf && marker !in setOf(0xc4, 0xcc)) {
                if (marker !in 0xc0..0xc2 || length < 11) return null
                val components = u8(bytes, at + 7)
                if (components == 0 || length != 8 + 3 * components || (marker == 0xc2 && components > 4)) return null
                if (u8(bytes, at + 2) !in (if (marker == 0xc0) setOf(8) else setOf(8, 12))) return null
                val identifiers = HashSet<Int>()
                for (i in 0 until components) {
                    val component = at + 8 + i * 3
                    val sampling = u8(bytes, component + 1)
                    if (!identifiers.add(u8(bytes, component)) || (sampling ushr 4) !in 1..4 ||
                        (sampling and 15) !in 1..4 || u8(bytes, component + 2) > 3) return null
                }
                val width = u8(bytes, at + 5) * 256 + u8(bytes, at + 6)
                val height = u8(bytes, at + 3) * 256 + u8(bytes, at + 4)
                return if (width == 0 || height == 0) null else bounded(width.toLong(), height.toLong())
            }
            at += length
        }
        return null
    }

    private fun bounded(width: Long, height: Long): DocxImageDimensions {
        if (width > 40000000 || height > 40000000 || width * height > 40000000) throw DocxImagePixelLimitException()
        return DocxImageDimensions(width.toInt(), height.toInt())
    }

    private fun u8(bytes: ByteArray, at: Int) = bytes[at].toInt() and 255
    private fun u32(bytes: ByteArray, at: Int) = (0..3).fold(0L) { value, i -> value * 256 + u8(bytes, at + i) }

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    }
}
