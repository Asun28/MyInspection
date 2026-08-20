package nz.myinspection.core.report

const val A4_WIDTH_MM = 210
const val A4_HEIGHT_MM = 297
const val PAGE_MARGIN_MM = 15
const val BODY_BOTTOM_MM = 272

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

data class CoverBlock(
    val address: String,
    val inspectionType: String,
    val scheduledAt: Long,
    val tenancyReference: String?,
    val adverseItemCount: Int,
    val remediationCount: Int,
) : DocumentBlock

data class SectionTitleBlock(val key: String, val title: BilingualText) : DocumentBlock

data class StatusDefinitionBlock(
    val status: String,
    val label: BilingualText,
    val description: BilingualText,
    val adverse: Boolean,
) : DocumentBlock

data class SummaryItemBlock(
    val itemId: String,
    val roomId: String,
    val status: String,
) : DocumentBlock

data class RoomTitleBlock(val roomId: String, val label: BilingualText) : DocumentBlock

data class ItemRowBlock(
    val itemId: String,
    val label: BilingualText,
    val status: String,
    val note: String?,
    val wearOrDamage: String?,
) : DocumentBlock

enum class ImagePurpose { INLINE, APPENDIX }

data class ImageSlotBlock(
    val photoId: String,
    val purpose: ImagePurpose,
    val reference: String,
    val source: String,
    val capturedAt: Long,
) : DocumentBlock

data class RemediationBlock(
    val itemId: String,
    val urgency: Urgency,
    val text: BilingualText,
) : DocumentBlock

data class SupplementBlock(val reference: String, val text: String) : DocumentBlock

data class DisclaimerBlock(val text: BilingualText) : DocumentBlock

data class TenantAgreementBlock(val label: BilingualText) : DocumentBlock

data class FooterBlock(
    val dataHash: String,
    val shortHash: String,
    val pageNumber: Int,
    val totalPages: Int,
) : DocumentBlock
