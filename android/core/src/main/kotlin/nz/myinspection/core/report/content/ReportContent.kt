package nz.myinspection.core.report.content

import java.util.Collections
import nz.myinspection.core.report.Audience
import nz.myinspection.core.report.BilingualText
import nz.myinspection.core.report.ReportRemediation
import nz.myinspection.core.report.ReportOptions
import nz.myinspection.core.report.ReportSnapshot
import nz.myinspection.core.report.ReportSupplement
import nz.myinspection.core.report.StatusDefinition

enum class PrivatePhotoScope { EXCLUDED, EXPLICITLY_INCLUDED }

/** Format-neutral origin label. It does not alter the native data-hash claim. */
enum class ReportOrigin { NATIVE, LEGACY_DOCX_IMPORT }

data class ReportIdentity(
    val inspectionId: String,
    val propertyId: String,
    val propertyAddress: String,
    val propertyKind: String,
    val isBoardingHouse: Boolean,
    val inspectionType: String,
    val scheduledAt: Long,
    val finalizedAt: Long,
    val tenancyReference: String?,
    val templateId: String,
    val templateVersion: Long,
    val templateContentHash: String,
)

/** Hash of the complete finalized native snapshot, including evidence hidden by report privacy policy. */
data class NativeIntegrity(val dataHash: String)

/** Immutable labels for the legacy source claim. Filenames, paths, URLs and document authors are excluded. */
data class LegacyImportProvenance(
    val sourceSha256: String,
    val normalizedManifestSha256: String,
    val mappingReceiptSha256: String,
    val extractorVersion: String,
    val sourceReportDate: String?,
)

data class ReportContentPhoto(
    val id: String,
    val contentHash: String,
    val source: String,
    val reference: String,
    val capturedAt: Long,
    val privacy: Boolean,
)

data class ReportContentItem(
    val id: String,
    val stableId: String,
    val label: BilingualText,
    val status: String,
    val note: String?,
    val wearOrDamage: String?,
    val photos: List<ReportContentPhoto>,
)

data class ReportContentRoom(
    val id: String,
    val label: BilingualText,
    val items: List<ReportContentItem>,
    val photos: List<ReportContentPhoto>,
)

data class ReportContentRoomStatusCount(val roomId: String, val status: String, val count: Int)

data class ReportContentSummaryItem(
    val roomId: String,
    val itemId: String,
    val status: String,
    val label: BilingualText,
    val note: String?,
)

data class ReportContentSummary(
    val roomStatusCounts: List<ReportContentRoomStatusCount>,
    val adverseItems: List<ReportContentSummaryItem>,
    val pendingRemediationCount: Int?,
)

/**
 * The only semantic input accepted by report renderers. Audience and privacy decisions have already happened;
 * the graph retains no canonical snapshot, renderer geometry, file location or Android object.
 */
class ReportContent private constructor(
    val contractVersion: Int,
    val identity: ReportIdentity,
    val audience: Audience,
    val privatePhotoScope: PrivatePhotoScope,
    val origin: ReportOrigin,
    val nativeIntegrity: NativeIntegrity,
    val importProvenance: LegacyImportProvenance?,
    statusDefinitions: List<StatusDefinition>,
    summary: ReportContentSummary,
    rooms: List<ReportContentRoom>,
    supplements: List<ReportSupplement>,
    remediations: List<ReportRemediation>,
    val disclaimer: BilingualText,
    val tenantAgreement: BilingualText?,
) {
    val statusDefinitions: List<StatusDefinition> = immutable(statusDefinitions)
    val summary: ReportContentSummary = summary.copy(
        roomStatusCounts = immutable(summary.roomStatusCounts),
        adverseItems = immutable(summary.adverseItems),
    )
    val rooms: List<ReportContentRoom> = immutable(rooms.map { room ->
        room.copy(
            items = immutable(room.items.map { item -> item.copy(photos = immutable(item.photos)) }),
            photos = immutable(room.photos),
        )
    })
    val supplements: List<ReportSupplement> = immutable(supplements)
    val remediations: List<ReportRemediation> = immutable(remediations)
    val semanticFingerprint: String = reportContentFingerprint(this)

    companion object {
        internal fun project(
            report: ReportSnapshot,
            audience: Audience,
            options: ReportOptions,
            provenance: LegacyImportProvenance?,
        ): ReportContent {
            val value = ReportContentProjectionBuilder.build(report, audience, options, provenance)
            return ReportContent(
                value.contractVersion,
                value.identity,
                value.audience,
                value.privatePhotoScope,
                value.origin,
                value.nativeIntegrity,
                value.importProvenance,
                value.statusDefinitions,
                value.summary,
                value.rooms,
                value.supplements,
                value.remediations,
                value.disclaimer,
                value.tenantAgreement,
            )
        }
    }
}

internal data class ReportContentProjectionValues(
    val contractVersion: Int,
    val identity: ReportIdentity,
    val audience: Audience,
    val privatePhotoScope: PrivatePhotoScope,
    val origin: ReportOrigin,
    val nativeIntegrity: NativeIntegrity,
    val importProvenance: LegacyImportProvenance?,
    val statusDefinitions: List<StatusDefinition>,
    val summary: ReportContentSummary,
    val rooms: List<ReportContentRoom>,
    val supplements: List<ReportSupplement>,
    val remediations: List<ReportRemediation>,
    val disclaimer: BilingualText,
    val tenantAgreement: BilingualText?,
)

private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
