package nz.myinspection.core.report

import nz.myinspection.core.model.InspectionItemSnapshot
import nz.myinspection.core.model.InspectionSnapshot
import nz.myinspection.core.model.PhotoSnapshot

enum class Audience { LANDLORD, TENANT }

enum class Urgency { LOW, MEDIUM, HIGH }

data class BilingualText(val en: String, val zh: String) {
    init {
        require(en.isNotBlank() && zh.isNotBlank()) { "bilingual text requires both en and zh" }
    }
}

data class ReportPhoto(
    val id: String,
    val snapshot: PhotoSnapshot,
    val privacy: Boolean,
    val reference: String,
    val capturedAt: Long,
)

data class ReportItem(
    val id: String,
    val snapshot: InspectionItemSnapshot,
    val label: BilingualText,
    val photos: List<ReportPhoto> = emptyList(),
)

data class ReportRoom(
    val id: String,
    val label: BilingualText,
    val items: List<ReportItem>,
    val photos: List<ReportPhoto> = emptyList(),
)

data class StatusDefinition(
    val status: String,
    val label: BilingualText,
    val description: BilingualText,
    val adverse: Boolean,
)

data class ReportSupplement(val reference: String, val text: String)

data class ReportRemediation(
    val itemId: String,
    val urgency: Urgency,
    val text: BilingualText,
)

/**
 * Read-only report projection layered over the frozen canonical snapshot. Presentation identifiers,
 * bilingual labels and privacy flags are deliberately outside the canonical hash domain.
 */
data class ReportSnapshot(
    val canonical: InspectionSnapshot,
    val tenancyReference: String?,
    val rooms: List<ReportRoom>,
    val statusDefinitions: List<StatusDefinition>,
    val supplements: List<ReportSupplement> = emptyList(),
    val remediations: List<ReportRemediation> = emptyList(),
)

data class ReportOptions(val includePrivacyPhotos: Boolean = false)

fun interface TextMeasurer {
    fun heightMm(text: String, style: TextStyle, widthMm: Int): Int
}

enum class TextStyle { TITLE, BODY, CAPTION }
