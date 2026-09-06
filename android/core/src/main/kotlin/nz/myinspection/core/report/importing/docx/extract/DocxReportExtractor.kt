package nz.myinspection.core.report.importing.docx.extract

import java.io.ByteArrayInputStream
import java.util.Locale
import java.security.MessageDigest
import java.util.zip.CRC32
import javax.xml.parsers.SAXParserFactory
import nz.myinspection.core.report.importing.docx.`package`.DocxPackage
import nz.myinspection.core.report.importing.docx.`package`.DocxPart
import nz.myinspection.core.report.importing.docx.`package`.DocxPartKind
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.ext.DefaultHandler2

private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
private class FieldFrame(val instruction: StringBuilder = StringBuilder(), var excluded: Boolean = false, var separated: Boolean = false)
private class ParagraphFrame(val element: Element, val source: SourceLocation, val text: StringBuilder = StringBuilder())
private class Element(val uri: String, val name: String, val attrs: Map<String, String>, val parent: Element?) {
    val children = ArrayList<Element>()
    val value = StringBuilder()
    fun isWord(local: String) = uri == W && name == local
    fun descendants(): Sequence<Element> = sequence { yield(this@Element); children.forEach { yieldAll(it.descendants()) } }
    fun ancestor(local: String): Element? = generateSequence(parent) { it.parent }.firstOrNull { it.isWord(local) }
    fun attr(local: String) = attrs["$W|$local"] ?: attrs["|$local"]
}

class DocxReportExtractor {
    fun extract(source: DocxPackage): DocxExtractionManifest = Extraction().read(source)

    private class Extraction {
        val items = ArrayList<ExtractedItem>()
        val fragments = ArrayList<ExtractedFragment>()
        val warnings = ArrayList<ExtractionWarning>()
        val identity = ArrayList<IdentityCandidate>()
        val summary = ArrayList<ExtractedText>()
        val captions = ArrayList<CaptionCandidate>()
        val images = ArrayList<ExtractedImage>()
        val placements = ArrayList<DrawingPlacement>()
        val shims = HashSet<String>()
        lateinit var parts: Map<String, DocxPart>
        var room: String? = null
        var column = FragmentRole.UNKNOWN
        var narrative = false
        var pendingIdentity: String? = null
        val occurrences = HashMap<Int, Int>()
        fun nextOccurrence(ordinal: Int): Int = (occurrences[ordinal] ?: 0).also { occurrences[ordinal] = it + 1 }
        val roomPattern = Regex("(?i)(Exterior|Hallway|Bathroom|Bedroom(?:\\s+[0-9]+(?:\\s.*)?)?|Lounge|Kitchen|General)")
        val url = Regex("(?i)(?:https?://|www\\.)[^\\s<>]+")
        fun warn(code: ExtractionWarningCode, source: SourceLocation?) { warnings.add(ExtractionWarning(code, source)) }

        fun read(source: DocxPackage): DocxExtractionManifest {
            parts = source.parts.associateBy { it.name }
            source.parts.filter { it.kind == DocxPartKind.IMAGE }.sortedBy { it.name }.forEach { part ->
                val bytes = part.copyBytes()
                val size = dimensions(bytes, part.name.endsWith(".png"))
                val at = SourceLocation(part.name, 0)
                if (size != null && size.first <= 24 && size.second <= 24) {
                    shims.add(part.name)
                    warn(ExtractionWarningCode.LAYOUT_IMAGE_EXCLUDED, at)
                } else {
                    images.add(ExtractedImage(part.name, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, size?.first, size?.second))
                    warn(ExtractionWarningCode.IMAGE_REVIEW_REQUIRED, at)
                }
            }
            val document = parts.getValue("word/document.xml")
            val documentRelations = relationships(document)
            val referenced = parse(document).descendants().filter { it.isWord("headerReference") || it.isWord("footerReference") }
                .mapNotNull { parts[documentRelations[it.attrs["$R|id"]]] }.toList()
            val fallback = source.parts.sortedWith(compareBy<DocxPart>({ it.kind.ordinal }, { it.name }))
            (listOf(document) + referenced + fallback).distinctBy { it.name }.forEach { part ->
                when (part.kind) {
                    DocxPartKind.DOCUMENT -> story(part)
                    DocxPartKind.HEADER -> story(part)
                    DocxPartKind.FOOTER -> story(part)
                    else -> Unit
                }
            }
            if (captions.isNotEmpty() || images.isNotEmpty()) warn(ExtractionWarningCode.AMBIGUOUS_CAPTIONS, null)
            return DocxExtractionManifest(items, fragments, warnings, identity, summary, captions, images, placements)
        }
        fun relationships(part: DocxPart): Map<String?, String> = parts["word/_rels/${part.name.substringAfterLast('/')}.rels"]
            ?.let { parse(it).children.associate { node -> node.attr("Id") to "word/${node.attr("Target").orEmpty().lowercase(Locale.ROOT)}" } }.orEmpty()

        fun story(part: DocxPart) {
            room = null
            column = FragmentRole.UNKNOWN
            narrative = false
            pendingIdentity = null
            occurrences.clear()
            val root = parse(part)
            val records = ArrayList<Pair<Element, ExtractedText>>()
            val positions = HashMap<Element, SourceLocation>()
            val fieldStack = ArrayList<FieldFrame>()
            var ordinal = 0
            fun flush(frame: ParagraphFrame) {
                val raw = frame.text.toString()
                frame.text.setLength(0)
                if (raw.isBlank()) return
                val location = frame.source.copy(occurrence = nextOccurrence(frame.source.ordinal))
                if (url.containsMatchIn(raw)) warn(ExtractionWarningCode.URL_EXCLUDED, location)
                val cleaned = raw.replace(url, "")
                if (cleaned.isNotBlank()) records.add(frame.element to ExtractedText(location, cleaned))
            }
            fun scan(node: Element, active: ParagraphFrame? = null) {
                val position = active?.source ?: SourceLocation(part.name, ordinal)
                require(!(node.uri == W && node.name in setOf("ins", "del", "delText", "moveFrom", "moveTo"))) { "DOCX_TRACKED_CONTENT" }
                require(!(node.name == "t" && node.value.isNotBlank() && (node.uri != W || active == null))) { "DOCX_UNSUPPORTED_TEXT" }
                if (node.isWord("sdt") && node.children.any { it.isWord("sdtPr") && it.children.any { tag ->
                        tag.isWord("tag") && tag.attr("val").orEmpty().startsWith("MSIP_", true) } }) {
                    warn(ExtractionWarningCode.METADATA_EXCLUDED, position)
                    return
                }
                if (node.isWord("fldSimple")) {
                    val code = fieldWarning(node.attr("instr").orEmpty())
                    warn(code, position)
                    if (code != ExtractionWarningCode.UNRESOLVED_TEXT) return
                }
                if (node.isWord("fldChar")) {
                    when (node.attr("fldCharType")) {
                        "begin" -> fieldStack.add(FieldFrame())
                        "separate" -> {
                            val frame = fieldStack.lastOrNull() ?: throw IllegalArgumentException("DOCX_FIELD_STRUCTURE")
                            require(!frame.separated) { "DOCX_FIELD_STRUCTURE" }
                            val code = fieldWarning(frame.instruction.toString())
                            frame.excluded = code != ExtractionWarningCode.UNRESOLVED_TEXT
                            frame.separated = true
                            warn(code, position)
                        }
                        "end" -> {
                            require(fieldStack.isNotEmpty()) { "DOCX_FIELD_STRUCTURE" }
                            require(fieldStack.last().separated) { "DOCX_FIELD_STRUCTURE" }
                            fieldStack.removeAt(fieldStack.lastIndex)
                        }
                    }
                }
                if (node.isWord("instrText")) {
                    val frame = fieldStack.lastOrNull() ?: throw IllegalArgumentException("DOCX_FIELD_STRUCTURE")
                    require(!frame.separated) { "DOCX_FIELD_STRUCTURE" }
                    frame.instruction.append(node.value)
                }
                if (node.uri == W && node.name in setOf("t", "tab", "br", "cr")) {
                    require(fieldStack.all { it.separated }) { "DOCX_FIELD_STRUCTURE" }
                }
                if (node.isWord("p")) {
                    active?.let(::flush)
                    val location = SourceLocation(part.name, ordinal++)
                    positions[node] = location
                    val frame = ParagraphFrame(node, location)
                    node.children.forEach { scan(it, frame) }
                    flush(frame)
                } else {
                    if (node.isWord("t") && fieldStack.none { it.excluded }) active?.text?.append(node.value)
                    if (node.isWord("tab") && fieldStack.none { it.excluded }) active?.text?.append('\t')
                    if ((node.isWord("br") || node.isWord("cr")) && fieldStack.none { it.excluded }) active?.text?.append('\n')
                    node.children.forEach { scan(it, active) }
                }
            }
            scan(root)
            require(fieldStack.isEmpty()) { "DOCX_FIELD_STRUCTURE" }
            val cellRecords = records.groupBy { it.first.ancestor("tc") }.mapValues { (_, values) -> values.map { it.second } }
            val headerRows = records.mapNotNull { it.first.ancestor("tr") }.distinct().filter { row ->
                row.children.filter { it.isWord("tc") }.map { cellRecords[it]?.singleOrNull()?.normalized?.lowercase(Locale.ROOT) } ==
                    listOf("feature", "status", "comments")
            }.toSet()
            val rows = HashSet<Element>()
            records.forEach { (node, text) ->
                val row = node.ancestor("tr")
                val role = if (row == null) paragraph(text) else if (row in headerRows) FragmentRole.LABEL else FragmentRole.UNKNOWN
                fragments.add(ExtractedFragment(role, text))
                if (row == null || row in headerRows || !rows.add(row)) return@forEach
                val cells = row.children.filter { it.isWord("tc") }
                if (cells.size != 3) {
                    warn(ExtractionWarningCode.UNRESOLVED_TEXT, text.source)
                    return@forEach
                }
                fun cell(index: Int) = cellRecords[cells[index]].orEmpty()
                var itemRoom = room
                val names = cell(0).filter { text ->
                    if (roomPattern.matches(text.normalized)) { room = text.raw; false }
                    else { itemRoom = room; true }
                }
                if (names.size == 1 && cell(1).size <= 1 && cell(2).size <= 1) {
                    items.add(ExtractedItem(itemRoom, names.single(), cell(1).singleOrNull(), cell(2).singleOrNull()))
                } else warn(ExtractionWarningCode.UNRESOLVED_TEXT, names.firstOrNull()?.source)
            }
            val relations = relationships(part)
            root.descendants().filter { it.uri == WP && it.name in setOf("inline", "anchor") }.forEach { drawing ->
                val position = positions[drawing.ancestor("p")] ?: SourceLocation(part.name, ordinal)
                drawing.descendants().filter { it.uri == A && it.name == "blip" }.forEach { blip ->
                    val target = relations[blip.attrs["$R|embed"]]
                    if (target in shims) return@forEach
                    val location = position.copy(occurrence = nextOccurrence(position.ordinal))
                    val imagePart = target?.takeIf { parts[it]?.kind == DocxPartKind.IMAGE }
                    if (imagePart == null) warn(ExtractionWarningCode.MISSING_IMAGE, location)
                    placements.add(DrawingPlacement(location, if (drawing.name == "inline") DrawingKind.INLINE else DrawingKind.ANCHORED, imagePart))
                }
            }
        }
        fun paragraph(text: ExtractedText): FragmentRole {
            val value = text.normalized
            if (pendingIdentity != null) {
                identity.add(IdentityCandidate(pendingIdentity!!, text)); pendingIdentity = null; return FragmentRole.IDENTITY
            }
            if (value.equals("Images", true)) { narrative = false; column = FragmentRole.UNKNOWN; return FragmentRole.LABEL }
            if (Regex("(?i)comments\\s*.?\\s*summary").matches(value)) { narrative = true; return FragmentRole.LABEL }
            if (narrative) { summary.add(text); warn(ExtractionWarningCode.UNRESOLVED_NARRATIVE, text.source); return FragmentRole.NARRATIVE }
            if (value.uppercase(Locale.ROOT) in setOf("PROPERTY ADDRESS", "INSPECTION DATE")) {
                pendingIdentity = value.uppercase(Locale.ROOT); return FragmentRole.LABEL
            }
            if (Regex("(?i)(Routine )?Inspection(?: Report|\\s*\\(.*\\))?").matches(value)) {
                identity.add(IdentityCandidate("TITLE", text)); return FragmentRole.IDENTITY
            }
            if (roomPattern.matches(value)) { room = text.raw; column = FragmentRole.ITEM; return FragmentRole.ROOM }
            if (value.equals("Feature", true)) { column = FragmentRole.ITEM; return FragmentRole.LABEL }
            if (value.equals("Status", true)) { column = FragmentRole.STATUS; return FragmentRole.LABEL }
            if (value.endsWith("Comments", true)) { column = FragmentRole.COMMENT; return FragmentRole.LABEL }
            if (value.lowercase(Locale.ROOT) in setOf("good", "fair", "poor", "clean", "average", "excellent")) column = FragmentRole.STATUS
            if (column == FragmentRole.ITEM) {
                items.add(ExtractedItem(room, text, null, null))
                warn(ExtractionWarningCode.AMBIGUOUS_COLUMNS, text.source)
                return FragmentRole.ITEM
            }
            val captionStarts = if (column == FragmentRole.UNKNOWN) Regex("(?<![0-9])[0-9]{3,}\\s*[-.]\\s*").findAll(text.raw).toList() else emptyList()
            if (captionStarts.isNotEmpty()) {
                captionStarts.forEachIndexed { index, match ->
                    val raw = text.raw.substring(match.range.first, captionStarts.getOrNull(index + 1)?.range?.first ?: text.raw.length)
                    captions.add(CaptionCandidate(match.value.takeWhile { it.isDigit() }, ExtractedText(text.source.copy(occurrence = nextOccurrence(text.source.ordinal)), raw)))
                }
                return FragmentRole.CAPTION
            }
            warn(ExtractionWarningCode.UNRESOLVED_TEXT, text.source)
            return if (column == FragmentRole.COMMENT) FragmentRole.COMMENT else FragmentRole.UNKNOWN
        }
        fun fieldWarning(instruction: String): ExtractionWarningCode {
            val words = instruction.trim().uppercase(Locale.ROOT).split(Regex("\\s+"))
            return when (if (words.first() == "INFO") words.getOrNull(1) else words.first()) {
                "PAGE", "NUMPAGES", "SECTIONPAGES", "PAGEREF" -> ExtractionWarningCode.PAGINATION_EXCLUDED
                "AUTHOR", "LASTSAVEDBY", "CREATEDATE", "SAVEDATE", "PRINTDATE", "FILENAME", "DOCPROPERTY",
                "USERNAME", "USERINITIALS", "USERADDRESS" -> ExtractionWarningCode.METADATA_EXCLUDED
                else -> ExtractionWarningCode.UNRESOLVED_TEXT
            }
        }
    }
}

private fun dimensions(bytes: ByteArray, png: Boolean): Pair<Int, Int>? {
    fun u8(at: Int) = bytes[at].toInt() and 255
    fun u16(at: Int) = (bytes[at].toInt() and 255) * 256 + (bytes[at + 1].toInt() and 255)
    fun bounded(width: Long, height: Long): Pair<Int, Int>? {
        if (width <= 0 || height <= 0) return null
        require(width <= 40000000 && height <= 40000000 && width * height <= 40000000) { "DOCX_IMAGE_PIXELS" }
        return width.toInt() to height.toInt()
    }
    val signature = if (png) listOf(137, 80, 78, 71, 13, 10, 26, 10) else listOf(255, 216)
    if (bytes.size < signature.size || signature.indices.any { u8(it) != signature[it] }) return null
    if (png) {
        if (bytes.size < 33 || !bytes.copyOfRange(12, 16).contentEquals("IHDR".toByteArray())) return null
        fun u32(at: Int) = (0..3).fold(0L) { n, i -> n * 256 + (bytes[at + i].toInt() and 255) }
        if (u32(8) != 13L) return null
        if (CRC32().apply { update(bytes, 12, 17) }.value != u32(29)) return null
        // W3C PNG 3, section 11.2.1 / Table 12: IHDR only, not pixel decoding.
        val depths = when (u8(25)) {
            0 -> setOf(1, 2, 4, 8, 16)
            2, 4, 6 -> setOf(8, 16)
            3 -> setOf(1, 2, 4, 8)
            else -> emptySet()
        }
        if (u8(24) !in depths || u8(26) != 0 || u8(27) != 0 || u8(28) !in 0..1) return null
        if (u32(16) !in 1L..2147483647L || u32(20) !in 1L..2147483647L) return null
        return bounded(u32(16), u32(20))
    }
    var at = 2
    while (at + 4 <= bytes.size && bytes[at].toInt() and 255 == 255) {
        while (at < bytes.size && bytes[at].toInt() and 255 == 255) at++
        if (at + 3 > bytes.size) return null
        val marker = bytes[at++].toInt() and 255
        // T.81 B.2.4: only frame, table, application and comment segments precede SOF.
        if (marker !in 0xc0..0xcf && marker !in 0xe0..0xef && marker !in setOf(0xdb, 0xdd, 0xfe)) return null
        val length = u16(at)
        if (length < 2 || at + length > bytes.size) return null
        if (marker in 0xc0..0xcf && marker !in setOf(0xc4, 0xcc)) {
            // ITU-T T.81, B.2.2 / Table B.2; other frame processes stay reviewable.
            if (marker !in 0xc0..0xc2 || length < 11) return null
            val components = u8(at + 7)
            if (components == 0 || length != 8 + 3 * components || (marker == 0xc2 && components > 4)) return null
            if (u8(at + 2) !in (if (marker == 0xc0) setOf(8) else setOf(8, 12))) return null
            val identifiers = HashSet<Int>()
            for (i in 0 until components) {
                val component = at + 8 + i * 3
                val sampling = u8(component + 1)
                if (!identifiers.add(u8(component)) || (sampling ushr 4) !in 1..4 ||
                    (sampling and 15) !in 1..4 || u8(component + 2) > 3) return null
            }
            return bounded(u16(at + 5).toLong(), u16(at + 3).toLong())
        }
        at += length
    }
    return null
}

private fun parse(part: DocxPart): Element {
    var root: Element? = null
    var current: Element? = null
    val handler = object : DefaultHandler2() {
        override fun startElement(uri: String, local: String, qName: String, attrs: Attributes) {
            val node = Element(uri, local, (0 until attrs.length).associate { "${attrs.getURI(it)}|${attrs.getLocalName(it)}" to attrs.getValue(it) }, current)
            current?.children?.add(node)
            if (root == null) root = node
            current = node
        }
        override fun endElement(uri: String?, local: String?, qName: String?) { current = current?.parent }
        override fun characters(chars: CharArray, start: Int, length: Int) { current?.value?.append(chars, start, length) }
        override fun startDTD(name: String?, publicId: String?, systemId: String?) { throw IllegalArgumentException("DOCX_XML") }
        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = throw IllegalArgumentException("DOCX_XML")
        override fun error(e: SAXParseException?): Unit = throw IllegalArgumentException("DOCX_XML")
        override fun fatalError(e: SAXParseException?): Unit = throw IllegalArgumentException("DOCX_XML")
    }
    try {
        val reader = SAXParserFactory.newInstance().apply { isNamespaceAware = true; isValidating = false }.newSAXParser().xmlReader
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false)
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
        reader.contentHandler = handler
        reader.entityResolver = handler
        reader.errorHandler = handler
        reader.parse(InputSource(ByteArrayInputStream(part.copyBytes())))
        return requireNotNull(root)
    } catch (_: Exception) { throw IllegalArgumentException("DOCX_XML") }
}
