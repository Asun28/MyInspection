package nz.myinspection.core.report

const val A4_WIDTH_MM = 210
const val A4_HEIGHT_MM = 297
const val PAGE_MARGIN_MM = 15
const val FOOTER_HEIGHT_MM = 10

/** The last y a body block may reach: the footer strip sits between here and the bottom margin. */
const val BODY_BOTTOM_MM = A4_HEIGHT_MM - PAGE_MARGIN_MM - FOOTER_HEIGHT_MM

/**
 * What a renderer draws, and only that: every block's [TextBearingBlock.textRuns], the picture of an
 * [ImageSlotBlock], and the [ItemRowBlock.thumbnails] nested in an item row. Every other field - an address,
 * a status, a note, a full digest, a photo reference - is identifying metadata for the renderer's own
 * bookkeeping and for tests. Drawing metadata is a defect: a block too tall for one page is cut into chunks
 * that each carry the whole payload, so a drawn payload field would appear once per chunk.
 */
data class DocumentPlan(
    val audience: Audience,
    val dataHash: String,
    val pages: List<PagePlan>,
)

data class PagePlan(val number: Int, val blocks: List<PlacedBlock>)

data class PlacedBlock(
    val xMm: Int,
    val yMm: Int,
    val widthMm: Int,
    val heightMm: Int,
    val content: DocumentBlock,
)

sealed interface DocumentBlock

enum class TextLanguage { EN, ZH, ORIGINAL, NEUTRAL }

data class TextRun(
    val text: String,
    val language: TextLanguage,
    val style: TextStyle,
    val xMm: Int,
    val yMm: Int,
    val widthMm: Int,
    val heightMm: Int,
)

sealed interface TextBearingBlock : DocumentBlock {
    val textRuns: List<TextRun>
}

data class RoomStatusCount(val roomId: String, val status: String, val count: Int)

data class CoverBlock(
    val address: String,
    val inspectionType: String,
    val scheduledAt: Long,
    val tenancyReference: String?,
    val adverseItemCount: Int,
    /** Items awaiting remediation. Landlord-only: null on a tenant cover, which never sees remediation data. */
    val pendingItemCount: Int?,
    val roomStatusCounts: List<RoomStatusCount>,
    override val textRuns: List<TextRun>,
) : TextBearingBlock

data class SectionTitleBlock(
    val key: String,
    val title: BilingualText,
    override val textRuns: List<TextRun>,
) : TextBearingBlock

data class StatusDefinitionBlock(
    val status: String,
    val label: BilingualText,
    val description: BilingualText,
    override val textRuns: List<TextRun>,
) : TextBearingBlock

data class SummaryItemBlock(
    val itemId: String,
    val roomId: String,
    val status: String,
    override val textRuns: List<TextRun>,
) : TextBearingBlock

data class RoomTitleBlock(
    val roomId: String,
    val label: BilingualText,
    override val textRuns: List<TextRun>,
) : TextBearingBlock

data class ItemRowBlock(
    val itemId: String,
    val label: BilingualText,
    val status: String,
    val note: String?,
    val wearOrDamage: String?,
    override val textRuns: List<TextRun>,
    /**
     * Evidence photos for this item, as thumbnails placed inside the row's own coordinate space, so the
     * renderer draws an item table with a picture column instead of a paragraph followed by loose
     * full-width images. Thumbnails are drawn, so a row split across pages partitions them: each chunk
     * owns a disjoint subset and every photo is drawn exactly once.
     */
    val thumbnails: List<ImageSlotBlock> = emptyList(),
) : TextBearingBlock

enum class ImagePurpose { INLINE, APPENDIX }

/**
 * A picture the renderer draws as-is. Geometry is part of the plan, not something the renderer derives:
 * [xMm]/[yMm] are relative to the containing block's origin and [imageHeightMm] is the picture box itself,
 * so an inline thumbnail nested in an [ItemRowBlock] and a full-page appendix image are described the same
 * way and differ only in numbers. [heightMm] covers the picture plus its caption lines plus the trailing gap.
 *
 * Image slots are indivisible. [textRuns] carry a caption capped at a fixed line count so that no caption
 * length can make a slot taller than one page and tempt the paginator to split it - splitting would produce
 * two slots with the same [photoId], i.e. one photo apparently printed twice.
 * [reference], [source] and [capturedAt] always carry the complete values even when the caption is elided.
 */
data class ImageSlotBlock(
    val photoId: String,
    val purpose: ImagePurpose,
    val reference: String,
    val source: String,
    val capturedAt: Long,
    override val textRuns: List<TextRun>,
    val xMm: Int,
    val yMm: Int,
    val widthMm: Int,
    val imageHeightMm: Int,
    val heightMm: Int,
) : TextBearingBlock

data class RemediationBlock(
    val itemId: String,
    val urgency: Urgency,
    val text: BilingualText,
    override val textRuns: List<TextRun>,
) : TextBearingBlock

data class SupplementBlock(
    val reference: String,
    val text: String,
    override val textRuns: List<TextRun>,
) : TextBearingBlock

data class DisclaimerBlock(val text: BilingualText, override val textRuns: List<TextRun>) : TextBearingBlock

data class TenantAgreementBlock(
    val label: BilingualText,
    override val textRuns: List<TextRun>,
) : TextBearingBlock

data class FooterBlock(
    val dataHash: String,
    val shortHash: String,
    val pageNumber: Int,
    val totalPages: Int,
    override val textRuns: List<TextRun>,
) : TextBearingBlock
