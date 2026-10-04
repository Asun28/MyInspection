package nz.myinspection.core.report.importing.plan

import java.util.Collections
import nz.myinspection.core.report.importing.docx.extract.CaptionCandidate
import nz.myinspection.core.report.importing.docx.extract.DocxExtractionManifest
import nz.myinspection.core.report.importing.docx.extract.DrawingPlacement
import nz.myinspection.core.report.importing.docx.extract.ExtractedFragment
import nz.myinspection.core.report.importing.docx.extract.ExtractedImage
import nz.myinspection.core.report.importing.docx.extract.ExtractedItem
import nz.myinspection.core.report.importing.docx.extract.ExtractedText
import nz.myinspection.core.report.importing.docx.extract.ExtractionWarning
import nz.myinspection.core.report.importing.docx.extract.IdentityCandidate
import nz.myinspection.core.template.Template

/** Caller snapshot; room, suppression and template collections are frozen on construction. */
class ImportPlanningInput(
    val propertyId: String?, val tenancyId: String?, val reportDate: String?, val sourceSha256: String?,
    val hasActiveDraft: Boolean, val template: RoutineTemplateBinding?, roomInstances: List<ImportRoomInstance>,
    suppressedStableIds: Set<String>, val manifest: DocxExtractionManifest,
) {
    val roomInstances: List<ImportRoomInstance> = immutable(roomInstances)
    val suppressedStableIds: Set<String> = immutableSet(suppressedStableIds)
}

class RoutineTemplateBinding(val id: String, val contentHash: String, template: Template) {
    val template: Template = freezeTemplate(template)
}

data class ImportRoomInstance(val roomKey: String, val instanceNo: Long, val displayLabel: String)

class ImportPlanContext(
    val propertyId: String?, val tenancyId: String?, val reportDate: String?, val sourceSha256: String?,
    val manifestDigest: String, val template: RoutineTemplateBinding?, roomInstances: List<ImportRoomInstance>,
    suppressedStableIds: Set<String>, val hasActiveDraft: Boolean,
) {
    val roomInstances: List<ImportRoomInstance> = immutable(roomInstances)
    val suppressedStableIds: Set<String> = immutableSet(suppressedStableIds)
}

data class ImportTarget(val roomKey: String, val instanceNo: Long, val stableId: String, val displayLabel: String)
enum class ImportSourceCategory { ITEM, FRAGMENT, IDENTITY, SUMMARY, CAPTION, IMAGE, PLACEMENT, WARNING }
data class ImportSourceId(val category: ImportSourceCategory, val index: Int)
enum class ImportBlockerCode {
    MISSING_PROPERTY, MISSING_TENANCY, MISSING_REPORT_DATE, INVALID_REPORT_DATE, INVALID_SOURCE_SHA256,
    ACTIVE_DRAFT, MISSING_TEMPLATE, TEMPLATE_NOT_CURRENT_ROUTINE_V2, UNKNOWN_SUPPRESSED_STABLE_ID,
    INVALID_ROOM_INVENTORY, UNKNOWN_TARGET, AMBIGUOUS_TARGET, UNSUPPORTED_STATUS, BLANK_STATUS,
    UNRESOLVED_CONTENT, MISSING_IMAGE, AMBIGUOUS_CAPTION, PHOTO_REVIEW_REQUIRED, SUGGESTION_REQUIRES_REVIEW,
}
class ImportPlanningBlocker(val code: ImportBlockerCode, sourceIds: List<ImportSourceId> = emptyList()) {
    val sourceIds: List<ImportSourceId> = immutable(sourceIds)
}
enum class WarningDisposition { EXTRACTOR_PROVENANCE_EXCLUDED, SOURCE_OWNER_REQUIRED, GLOBAL_BLOCKER, PHOTO_PRIVACY_REVIEW, CAPTION_ASSOCIATION_BLOCKER }
data class ImportWarningReview(val warning: ExtractionWarning, val disposition: WarningDisposition)
data class ImportCandidate(val target: ImportTarget?, val suggestedStatus: String?, val sourceStatus: ExtractedText?)
class ImportReviewRow(
    sourceIds: List<ImportSourceId>, items: List<ExtractedItem> = emptyList(), fragments: List<ExtractedFragment> = emptyList(),
    identity: List<IdentityCandidate> = emptyList(), summaries: List<ExtractedText> = emptyList(), captions: List<CaptionCandidate> = emptyList(),
    images: List<ExtractedImage> = emptyList(), placements: List<DrawingPlacement> = emptyList(), warnings: List<ExtractionWarning> = emptyList(),
    warningReviews: List<ImportWarningReview> = emptyList(), val candidate: ImportCandidate? = null,
) {
    val sourceIds = immutable(sourceIds); val items = immutable(items); val fragments = immutable(fragments)
    val identity = immutable(identity); val summaries = immutable(summaries); val captions = immutable(captions)
    val images = immutable(images); val placements = immutable(placements); val warnings = immutable(warnings)
    val warningReviews = immutable(warningReviews); val warningDisposition = warningReviews.singleOrNull()?.disposition
}
enum class ImportPhotoReviewState { UNREVIEWED_EXCLUDED }
enum class ImportPhotoAction { ACTION_REQUIRED }
class ImportPhotoReview(val imageSource: ImportSourceId, placementSources: List<ImportSourceId>, val state: ImportPhotoReviewState, val action: ImportPhotoAction) {
    val placementSources = immutable(placementSources)
}

internal fun <T> immutable(values: List<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

internal fun <T> immutableSet(values: Set<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values))

private fun freezeTemplate(template: Template): Template = template.copy(
    rooms = immutable(template.rooms.map { it.copy() }),
    items = immutable(template.items.map { item ->
        item.copy(allowedStatuses = immutable(item.allowedStatuses))
    }),
)

class ImportPlan(
    val context: ImportPlanContext,
    targets: List<ImportTarget>,
    rows: List<ImportReviewRow>,
    blockers: List<ImportPlanningBlocker>,
    photoReviews: List<ImportPhotoReview>,
    unratedTargets: List<ImportTarget>,
) {
    val targets: List<ImportTarget> = immutable(targets)
    val rows: List<ImportReviewRow> = immutable(rows)
    val blockers: List<ImportPlanningBlocker> = immutable(blockers)
    val photoReviews: List<ImportPhotoReview> = immutable(photoReviews)
    /** Every configured unsuppressed target stays unrated; suggestions never confirm a rating. */
    val unratedTargets: List<ImportTarget> = immutable(unratedTargets)
}
