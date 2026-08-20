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

data class MeasuredText(val lines: List<String>, val lineHeightMm: Int) {
    init {
        require(lines.isNotEmpty() && lines.none { it.isEmpty() }) { "measured text requires non-empty lines" }
        require(lineHeightMm > 0) { "measured text requires a positive line height" }
    }
}

fun interface TextMeasurer {
    fun measure(text: String, style: TextStyle, widthMm: Int): MeasuredText
}

enum class TextStyle { TITLE, BODY, CAPTION }

val REPORT_DISCLAIMER = BilingualText(
    en = "This report records visible conditions at the inspection time. It is not legal, building, engineering, " +
        "property, health or safety advice. Requirements and standards may change. Consult appropriately licensed " +
        "professionals before acting.",
    zh = "本报告仅记录检查时可见的状况，不构成法律、建筑、工程、物业、健康或安全建议。要求和标准可能变化，" +
        "采取行动前请咨询具备相应执照的专业人士。",
)
