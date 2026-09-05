package nz.myinspection.core.report.importing.docx.`package`

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

data class DocxPackageLimits(
    val maxArchiveBytes: Int = 16 * 1024 * 1024,
    val maxEntries: Int = 512,
    val maxEntryBytes: Int = 8 * 1024 * 1024,
    val maxTotalBytes: Long = 32 * 1024 * 1024,
    val maxXmlBytes: Int = 4 * 1024 * 1024,
    val maxImageBytes: Int = 8 * 1024 * 1024,
    val maxCompressionRatio: Int = 200,
    val maxXmlDepth: Int = 64,
    val maxTextNodeChars: Int = 65536,
    /** XML elements across the whole package; excludes attributes, comments and text events. */
    val maxXmlNodes: Int = 200000,
) {
    init {
        require(listOf(maxArchiveBytes, maxEntries, maxEntryBytes, maxXmlBytes, maxImageBytes,
            maxCompressionRatio, maxXmlDepth, maxTextNodeChars, maxXmlNodes).all { it > 0 } && maxTotalBytes > 0)
    }
}

enum class DocxPackageReason {
    SOURCE_READ, ARCHIVE_BYTES, ENTRY_COUNT, ENTRY_BYTES, TOTAL_BYTES, COMPRESSION_RATIO,
    XML_BYTES, IMAGE_BYTES, XML_DEPTH, XML_TEXT, XML_NODES, MALFORMED_ZIP, ENCRYPTED, UNSAFE_PATH,
    DUPLICATE_PART, UNSUPPORTED_PART, UNSUPPORTED_CONTENT, UNSAFE_RELATIONSHIP,
    DTD_OR_ENTITY, MALFORMED_XML, XML_UNAVAILABLE, INVALID_PACKAGE,
}
class DocxPackageException internal constructor(val reason: DocxPackageReason, val entriesSeen: Int, val expandedBytes: Long) :
    RuntimeException("$reason: entries=$entriesSeen, bytes=$expandedBytes")
enum class DocxPartKind { DOCUMENT, HEADER, FOOTER, RELATIONSHIPS, IMAGE }
class DocxPart internal constructor(val name: String, val kind: DocxPartKind, private val bytes: ByteArray) {
    fun copyBytes(): ByteArray = bytes.copyOf()
}
class DocxPackage internal constructor(parts: List<DocxPart>) {
    val parts: List<DocxPart> = Collections.unmodifiableList(ArrayList(parts))
}

/**
 * Read-only, all-or-nothing package boundary. The caller owns the input stream.
 * Supports classic single-volume STORED/DEFLATED ZIPs (including data descriptors),
 * transitional OOXML and inert PNG/JPEG bytes. ZIP64, ambiguous names and unsupported
 * parts are rejected. Image byte limits do not attest pixel dimensions or decodability.
 * Limits bound actual reads/expansion; callers may inject positive resource budgets.
 */
class DocxPackageReader(private val limits: DocxPackageLimits = DocxPackageLimits()) {
    fun read(source: InputStream): DocxPackage = Read(limits).read(source)

    private class Read(private val limits: DocxPackageLimits) {
        private var entriesSeen = 0
        private var expandedBytes = 0L
        private fun fail(reason: DocxPackageReason): Nothing = throw DocxPackageException(reason, entriesSeen, expandedBytes)
        private fun check(ok: Boolean) { if (!ok) fail(DocxPackageReason.MALFORMED_ZIP) }
        private lateinit var archive: ByteArray
        private fun u16(at: Int): Int { range(at, 2); return (archive[at].toInt() and 255) or ((archive[at + 1].toInt() and 255) shl 8) }
        private fun u32(at: Int): Long { range(at, 4); return (0..3).fold(0L) { n, i -> n or ((archive[at + i].toLong() and 255) shl (8 * i)) } }
        private fun range(at: Int, size: Int) { check(at >= 0 && size >= 0 && at.toLong() + size <= archive.size) }
        private fun int32(at: Int): Int = u32(at).also { check(it <= Int.MAX_VALUE) }.toInt()
        private data class Entry(val name: String, val rawName: ByteArray, val offset: Int, val flags: Int,
            val method: Int, val crc: Long, val compressed: Int, val expanded: Long)

        fun read(source: InputStream): DocxPackage {
            val input = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val requested = minOf(buffer.size.toLong(), limits.maxArchiveBytes.toLong() - input.size() + 1).toInt()
                val count = try { source.read(buffer, 0, requested) }
                catch (_: IOException) { fail(DocxPackageReason.SOURCE_READ) }
                catch (_: RuntimeException) { fail(DocxPackageReason.SOURCE_READ) }
                if (count < 0) break
                if (count == 0) fail(DocxPackageReason.SOURCE_READ)
                if (input.size().toLong() + count > limits.maxArchiveBytes) fail(DocxPackageReason.ARCHIVE_BYTES)
                input.write(buffer, 0, count)
            }
            archive = input.toByteArray()
            val entries = directory()
            val parts = linkedMapOf<String, ByteArray>()
            entries.forEachIndexed { index, entry ->
                val end = if (index + 1 < entries.size) entries[index + 1].offset else directoryStart()
                parts[entry.name] = expand(entry, end)
            }
            DocxXmlBoundary(limits, ::fail).validate(parts)
            return DocxPackage(parts.toSortedMap().mapNotNull { (name, bytes) ->
                partKind(name)?.let { DocxPart(name, it, bytes) }
            })
        }
        private fun endRecord(): Int {
            for (at in (archive.size - 22) downTo maxOf(0, archive.size - 65557)) {
                if (u32(at) == 0x06054b50L && at.toLong() + 22 + u16(at + 20) == archive.size.toLong()) return at
            }
            fail(DocxPackageReason.MALFORMED_ZIP)
        }
        private fun directoryStart() = int32(endRecord() + 16)
        private fun directory(): List<Entry> {
            val end = endRecord()
            check(u16(end + 4) == 0 && u16(end + 6) == 0 && u16(end + 8) == u16(end + 10))
            var at = int32(end + 16)
            check(at.toLong() + u32(end + 12) == end.toLong())
            val entries = ArrayList<Entry>()
            val names = HashSet<String>()
            while (at < end) {
                check(u32(at) == 0x02014b50L)
                if (++entriesSeen > limits.maxEntries) fail(DocxPackageReason.ENTRY_COUNT)
                val flags = u16(at + 8)
                if (flags and 0x2041 != 0) fail(DocxPackageReason.ENCRYPTED)
                val method = u16(at + 10)
                check(flags and 0x080e.inv() == 0 && method in listOf(0, 8) && u16(at + 34) == 0)
                val nameLength = u16(at + 28)
                val length = 46 + nameLength + u16(at + 30) + u16(at + 32)
                check(at.toLong() + length <= end)
                val rawName = archive.copyOfRange(at + 46, at + 46 + nameLength)
                val name = safeName(rawName.toString(Charsets.UTF_8), ::fail)
                if (!names.add(name)) fail(DocxPackageReason.DUPLICATE_PART)
                if (contentType(name) == null) fail(DocxPackageReason.UNSUPPORTED_PART)
                entries.add(Entry(name, rawName, int32(at + 42), flags, method, u32(at + 16), int32(at + 20), u32(at + 24)))
                at += length
            }
            check(entries.size == u16(end + 10) && entries.isNotEmpty())
            return entries.sortedBy { it.offset }.also { check(it.first().offset == 0) }
        }
        private fun expand(entry: Entry, end: Int): ByteArray {
            val at = entry.offset
            check(u32(at) == 0x04034b50L && u16(at + 6) == entry.flags && u16(at + 8) == entry.method)
            val nameLength = u16(at + 26)
            val data = at.toLong() + 30 + nameLength + u16(at + 28)
            check(data + entry.compressed <= end && end <= archive.size)
            check(archive.copyOfRange(at + 30, at + 30 + nameLength).contentEquals(entry.rawName))
            if (entry.flags and 8 == 0) check(u32(at + 14) == entry.crc && u32(at + 18) == entry.compressed.toLong() && u32(at + 22) == entry.expanded)
            var tail = data.toInt() + entry.compressed
            if (entry.flags and 8 != 0) {
                if (u32(tail) == 0x08074b50L) tail += 4
                check(u32(tail) == entry.crc && u32(tail + 4) == entry.compressed.toLong() && u32(tail + 8) == entry.expanded)
                tail += 12
            }
            check(tail == end)
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            fun append(count: Int) {
                val size = result.size().toLong() + count
                expandedBytes += count
                if (size > limits.maxEntryBytes) fail(DocxPackageReason.ENTRY_BYTES)
                if (expandedBytes > limits.maxTotalBytes) fail(DocxPackageReason.TOTAL_BYTES)
                if (size > entry.compressed.toLong() * limits.maxCompressionRatio) fail(DocxPackageReason.COMPRESSION_RATIO)
                if (partKind(entry.name) == DocxPartKind.IMAGE) {
                    if (size > limits.maxImageBytes) fail(DocxPackageReason.IMAGE_BYTES)
                } else if (size > limits.maxXmlBytes) fail(DocxPackageReason.XML_BYTES)
                result.write(buffer, 0, count)
            }
            if (entry.method == 0) {
                var position = data.toInt()
                while (position < data + entry.compressed) {
                    val count = minOf(buffer.size, data.toInt() + entry.compressed - position)
                    archive.copyInto(buffer, 0, position, position + count)
                    append(count)
                    position += count
                }
            } else {
                val inflater = Inflater(true)
                try {
                    inflater.setInput(archive, data.toInt(), entry.compressed)
                    while (!inflater.finished()) {
                        val count = inflater.inflate(buffer)
                        check(count > 0 || inflater.finished())
                        append(count)
                    }
                    check(inflater.bytesRead == entry.compressed.toLong())
                } catch (_: DataFormatException) { fail(DocxPackageReason.MALFORMED_ZIP) }
                finally { inflater.end() }
            }
            return result.toByteArray().also {
                check(it.size.toLong() == entry.expanded && CRC32().apply { update(it) }.value == entry.crc)
            }
        }
    }
}

internal fun safeName(raw: String, fail: (DocxPackageReason) -> Nothing): String {
    if (!raw.matches(Regex("[A-Za-z0-9_./\\[\\]-]+")) || raw.split('/').any { it.isEmpty() || it == "." || it == ".." || it.endsWith('.') }) {
        fail(DocxPackageReason.UNSAFE_PATH)
    }
    return raw.lowercase(Locale.ROOT)
}
internal fun partKind(name: String): DocxPartKind? = when {
    name == "word/document.xml" -> DocxPartKind.DOCUMENT
    name.matches(Regex("word/header[0-9]+\\.xml")) -> DocxPartKind.HEADER
    name.matches(Regex("word/footer[0-9]+\\.xml")) -> DocxPartKind.FOOTER
    name == "_rels/.rels" || name.matches(Regex("word/_rels/(document|header[0-9]+|footer[0-9]+)\\.xml\\.rels")) -> DocxPartKind.RELATIONSHIPS
    name.matches(Regex("word/media/[a-z0-9_-]+\\.(png|jpe?g)")) -> DocxPartKind.IMAGE
    else -> null
}
internal fun contentType(name: String): String? = when (partKind(name)) {
    DocxPartKind.DOCUMENT -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
    DocxPartKind.HEADER -> "application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"
    DocxPartKind.FOOTER -> "application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"
    DocxPartKind.RELATIONSHIPS -> "application/vnd.openxmlformats-package.relationships+xml"
    DocxPartKind.IMAGE -> if (name.endsWith(".png")) "image/png" else "image/jpeg"
    null -> when {
        name == "[content_types].xml" -> "application/xml"
        name == "docprops/core.xml" -> "application/vnd.openxmlformats-package.core-properties+xml"
        name == "docprops/app.xml" -> "application/vnd.openxmlformats-officedocument.extended-properties+xml"
        name.matches(Regex("word/(styles|settings|websettings|fonttable|numbering)\\.xml")) ->
            "application/vnd.openxmlformats-officedocument.wordprocessingml.${if (name == "word/fonttable.xml") "fontTable" else if (name == "word/websettings.xml") "webSettings" else name.substringAfter('/').substringBefore('.')}+xml"
        name.matches(Regex("word/theme/theme[0-9]+\\.xml")) -> "application/vnd.openxmlformats-officedocument.theme+xml"
        else -> null
    }
}
