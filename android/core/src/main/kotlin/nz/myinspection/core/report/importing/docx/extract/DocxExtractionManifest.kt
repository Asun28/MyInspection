package nz.myinspection.core.report.importing.docx.extract

import java.text.Normalizer
import java.util.Collections
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

data class SourceLocation(val part: String, val ordinal: Int, val occurrence: Int = 0)
data class ExtractedText(val source: SourceLocation, val raw: String) {
    val normalized: String = Normalizer.normalize(raw, Normalizer.Form.NFC).trim().replace(Regex("[ \\t\\n\\x0B\\f\\r]+"), " ")
}
data class ExtractedItem(val room: String?, val name: ExtractedText, val status: ExtractedText?, val comment: ExtractedText?)
enum class FragmentRole { UNKNOWN, ROOM, ITEM, STATUS, COMMENT, LABEL, IDENTITY, CAPTION, NARRATIVE }
data class ExtractedFragment(val role: FragmentRole, val text: ExtractedText)
enum class ExtractionWarningCode {
    UNRESOLVED_TEXT, PAGINATION_EXCLUDED, METADATA_EXCLUDED, URL_EXCLUDED,
    AMBIGUOUS_COLUMNS, UNRESOLVED_NARRATIVE, AMBIGUOUS_CAPTIONS, LAYOUT_IMAGE_EXCLUDED, MISSING_IMAGE, IMAGE_REVIEW_REQUIRED,
}
data class ExtractionWarning(val code: ExtractionWarningCode, val source: SourceLocation?)
data class IdentityCandidate(val field: String, val text: ExtractedText)
data class CaptionCandidate(val number: String, val text: ExtractedText)
data class ExtractedImage(val part: String, val sha256: String, val width: Int?, val height: Int?)
enum class DrawingKind { INLINE, ANCHORED }
data class DrawingPlacement(val source: SourceLocation, val kind: DrawingKind, val imagePart: String?)

/** Extraction is evidence for review; none of these suggestions authorizes a native write. */
class DocxExtractionManifest internal constructor(
    items: List<ExtractedItem>, fragments: List<ExtractedFragment>, warnings: List<ExtractionWarning>,
    identity: List<IdentityCandidate>, summaryCandidates: List<ExtractedText>, captions: List<CaptionCandidate>,
    images: List<ExtractedImage>, placements: List<DrawingPlacement>,
) {
    val items: List<ExtractedItem> = immutable(items)
    val fragments: List<ExtractedFragment> = immutable(fragments)
    val warnings: List<ExtractionWarning> = immutable(warnings)
    val identity: List<IdentityCandidate> = immutable(identity)
    val summaryCandidates: List<ExtractedText> = immutable(summaryCandidates)
    val captions: List<CaptionCandidate> = immutable(captions)
    val images: List<ExtractedImage> = immutable(images)
    val placements: List<DrawingPlacement> = immutable(placements)
    val extractorVersion: String = "DOCX-EXTRACT-1"
    val normalizedDigest: String = fingerprint()

    // Versioned UTF-8 fields: signed big-endian byte length (-1 = null), then bytes.
    // Each named list has an explicit decimal count. Raw spelling is part of the commitment.
    private fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun field(value: String?) {
            val encoded = value?.let { Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(it)) }
            digest.update(ByteBuffer.allocate(4).putInt(encoded?.remaining() ?: -1).array())
            if (encoded != null) digest.update(encoded)
        }
        fun location(value: SourceLocation?) { field(value?.part); field(value?.ordinal?.toString()); field(value?.occurrence?.toString()) }
        fun text(value: ExtractedText?) { location(value?.source); field(value?.raw); field(value?.normalized) }
        fun <T> group(name: String, values: List<T>, emit: (T) -> Unit) { field(name); field(values.size.toString()); values.forEach(emit) }
        field(extractorVersion)
        group("fragments", fragments) { field(it.role.name); text(it.text) }
        group("items", items) { field(it.room); text(it.name); text(it.status); text(it.comment) }
        group("identity", identity) { field(it.field); text(it.text) }
        group("summary", summaryCandidates, ::text)
        group("captions", captions) { field(it.number); text(it.text) }
        group("images", images) { field(it.part); field(it.sha256); field(it.width?.toString()); field(it.height?.toString()) }
        group("placements", placements) { location(it.source); field(it.kind.name); field(it.imagePart) }
        group("warnings", warnings) { field(it.code.name); location(it.source) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
internal fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
