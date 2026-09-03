package nz.myinspection.core.report.html

import nz.myinspection.core.report.content.ReportContentPhoto

/**
 * One piece of evidence the document will not carry. It is a *refusal*, not a failure of the report: a
 * renderer turns it into the same missing-photograph figure a null or an oversized picture produces, so
 * the reader still gets the number, the caption and the capture time.
 *
 * It has its own type precisely so a renderer can catch this and nothing else. A blanket catch around a
 * port call would also swallow a genuine defect in that port and render a silently incomplete report -
 * photographs quietly absent from an evidence document is the worst available outcome. It remains an
 * [IllegalArgumentException] so a caller that has not heard of it still sees an argument error rather
 * than something unrecognisable.
 */
class RejectedEvidenceException(message: String) : IllegalArgumentException(message)

/**
 * Bytes for one reviewed photo, as they will be embedded. `:core` has no filesystem, so reading, EXIF
 * rotation and any downscaling happen behind [ReportImageSource] in `:app`; a renderer only base64-encodes
 * what it is handed. Not a data class: [bytes] is an array, whose generated `equals` compares identity and
 * would make two different pictures look interchangeable.
 */
class EmbeddedImage(val mediaType: String, val bytes: ByteArray) {
    init {
        if (mediaType !in ALLOWED_MEDIA_TYPES) throw RejectedEvidenceException("unsupported type: $mediaType")
        if (bytes.isEmpty()) throw RejectedEvidenceException("an embedded image needs bytes")
    }

    companion object {
        /**
         * The raster formats every current browser decodes without a plugin. SVG is absent by decision,
         * not by omission: it is a document that can carry script, not a picture.
         */
        val ALLOWED_MEDIA_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}

/**
 * The only way evidence bytes enter the document. [maxBytes] is the ceiling the caller will accept, so an
 * implementation can decline an oversized file before reading it rather than materialise a corrupt 200 MB
 * one and be turned away afterwards - a bound checked only after allocation cannot prevent the allocation.
 * Returning null is ordinary, not an error: a missing, oversized or unreadable file must still produce a
 * numbered figure rather than a failed report.
 */
fun interface ReportImageSource {
    fun read(photo: ReportContentPhoto, maxBytes: Int): EmbeddedImage?
}

/**
 * Hard ceilings on what one document may embed. They are refusals, not hints: past either bound the figure
 * keeps its caption and reference and loses only its picture, so a report of a property with hundreds of
 * photographs stays openable on a phone instead of exhausting its memory.
 *
 * A document bound below the per-image bound is a caller wiring the renderer wrongly, so it is an ordinary
 * argument error and deliberately NOT a [RejectedEvidenceException] - a renderer catches the refusal, and
 * catching this too would hide a misconfiguration behind silently missing photographs.
 */
data class HtmlImageBounds(
    val maxImageBytes: Int = 2 * 1024 * 1024,
    val maxTotalImageBytes: Int = 24 * 1024 * 1024,
) {
    init {
        require(maxImageBytes > 0) { "maxImageBytes must be positive" }
        require(maxTotalImageBytes >= maxImageBytes) { "the document bound cannot be below the per-image bound" }
    }
}
