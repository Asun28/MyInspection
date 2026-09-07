package nz.myinspection.core.report.importing.plan

import nz.myinspection.core.report.importing.docx.extract.CaptionCandidate
import nz.myinspection.core.report.importing.docx.extract.DocxExtractionManifest
import nz.myinspection.core.report.importing.docx.extract.ExtractedFragment
import nz.myinspection.core.report.importing.docx.extract.ExtractedItem
import nz.myinspection.core.report.importing.docx.extract.ExtractedText
import nz.myinspection.core.report.importing.docx.extract.ExtractionWarning
import nz.myinspection.core.report.importing.docx.extract.ExtractionWarningCode
import nz.myinspection.core.report.importing.docx.extract.FragmentRole
import nz.myinspection.core.report.importing.docx.extract.SourceLocation
import nz.myinspection.core.template.TemplateItem

/** Pure projection only. Review, preview, receipt and all writes belong to later cards. */
class ImportPlanner {
    fun project(input: ImportPlanningInput): ImportPlan {
        val snapshot = ImportPlanningSnapshot.create(input)
        val blockers = snapshot.blockers.toMutableList()
        val targetIds = snapshot.targets.mapTo(mutableSetOf()) { it.stableId }
        val activeItems = snapshot.context.template?.template?.items.orEmpty().filter { it.stableId in targetIds }
        val rows = sourceRows(input.manifest, blockers, snapshot.targets, activeItems)
        return ImportPlan(snapshot.context, snapshot.targets, rows, blockers, photoReviews(rows), snapshot.unratedTargets)
    }

    private fun sourceRows(
        manifest: DocxExtractionManifest,
        blockers: MutableList<ImportPlanningBlocker>,
        targets: List<ImportTarget> = emptyList(),
        templateItems: List<TemplateItem> = emptyList(),
    ): List<ImportReviewRow> {
        val drafts = mutableListOf<RowDraft>()
        val claimedFragments = mutableSetOf<Int>()
        manifest.items.forEachIndexed { itemIndex, item ->
            val aliases = manifest.fragments.withIndex().filter { (_, fragment) ->
                listOfNotNull(item.name, item.status, item.comment).any { it == fragment.text } &&
                    manifest.items.count { other -> listOfNotNull(other.name, other.status, other.comment).any { it == fragment.text } } == 1
            }
            claimedFragments += aliases.map { it.index }
            val candidate = candidate(item, targets, templateItems, blockers, source(ImportSourceCategory.ITEM, itemIndex))
            drafts += RowDraft(
                ids = mutableListOf(source(ImportSourceCategory.ITEM, itemIndex)).apply { aliases.forEach { add(source(ImportSourceCategory.FRAGMENT, it.index)) } },
                items = mutableListOf(item),
                fragments = aliases.mapTo(mutableListOf()) { it.value },
                candidate = candidate,
            )
        }

        val claimedCaptions = mutableSetOf<Int>()
        manifest.fragments.forEachIndexed { fragmentIndex, fragment ->
            if (fragmentIndex in claimedFragments) return@forEachIndexed
            if (fragment.role == FragmentRole.CAPTION) {
                val captions = manifest.captions.withIndex().filter { (_, caption) ->
                    caption.text.source.part == fragment.text.source.part && caption.text.source.ordinal == fragment.text.source.ordinal
                }
                val parents = manifest.fragments.count { other ->
                    other.role == FragmentRole.CAPTION && other.text.source.part == fragment.text.source.part &&
                        other.text.source.ordinal == fragment.text.source.ordinal
                }
                if (captions.isNotEmpty() && parents == 1) {
                    claimedCaptions += captions.map { it.index }
                    val ids = mutableListOf(source(ImportSourceCategory.FRAGMENT, fragmentIndex))
                    ids += captions.map { source(ImportSourceCategory.CAPTION, it.index) }
                    drafts += RowDraft(ids, fragments = mutableListOf(fragment), captions = captions.mapTo(mutableListOf()) { it.value })
                    blockers += blocker(ImportBlockerCode.AMBIGUOUS_CAPTION, ids)
                    return@forEachIndexed
                }
            }
            val id = source(ImportSourceCategory.FRAGMENT, fragmentIndex)
            drafts += RowDraft(mutableListOf(id), fragments = mutableListOf(fragment))
            blockers += blocker(ImportBlockerCode.UNRESOLVED_CONTENT, listOf(id))
        }
        manifest.captions.forEachIndexed { index, caption ->
            if (index !in claimedCaptions) {
                val id = source(ImportSourceCategory.CAPTION, index)
                drafts += RowDraft(mutableListOf(id), captions = mutableListOf(caption))
                blockers += blocker(ImportBlockerCode.AMBIGUOUS_CAPTION, listOf(id))
            }
        }
        manifest.identity.forEachIndexed { index, identity ->
            val id = source(ImportSourceCategory.IDENTITY, index)
            val owner = drafts.singleOrNull { draft -> draft.fragments.any { it.text == identity.text } }
            if (owner == null) {
                drafts += RowDraft(mutableListOf(id), identity = mutableListOf(identity))
                blockers += blocker(ImportBlockerCode.UNRESOLVED_CONTENT, listOf(id))
            } else { owner.ids += id; owner.identity += identity }
        }
        manifest.summaryCandidates.forEachIndexed { index, summary ->
            val id = source(ImportSourceCategory.SUMMARY, index)
            val owner = drafts.singleOrNull { draft -> draft.fragments.any { it.text == summary } }
            if (owner == null) {
                drafts += RowDraft(mutableListOf(id), summaries = mutableListOf(summary))
                blockers += blocker(ImportBlockerCode.UNRESOLVED_CONTENT, listOf(id))
            } else { owner.ids += id; owner.summaries += summary }
        }

        val claimedPlacements = mutableSetOf<Int>()
        val imagesByPart = manifest.images.groupBy { it.part }
        manifest.images.forEachIndexed { imageIndex, image ->
            val placements = manifest.placements.withIndex().filter { it.value.imagePart == image.part && imagesByPart[image.part]?.size == 1 }
            claimedPlacements += placements.map { it.index }
            val ids = mutableListOf(source(ImportSourceCategory.IMAGE, imageIndex))
            ids += placements.map { source(ImportSourceCategory.PLACEMENT, it.index) }
            drafts += RowDraft(ids, images = mutableListOf(image), placements = placements.mapTo(mutableListOf()) { it.value })
            blockers += blocker(ImportBlockerCode.PHOTO_REVIEW_REQUIRED, listOf(source(ImportSourceCategory.IMAGE, imageIndex)))
        }
        manifest.placements.forEachIndexed { index, placement ->
            if (index !in claimedPlacements) {
                val id = source(ImportSourceCategory.PLACEMENT, index)
                drafts += RowDraft(mutableListOf(id), placements = mutableListOf(placement))
                val code = if (imagesByPart[placement.imagePart].orEmpty().size > 1) ImportBlockerCode.UNRESOLVED_CONTENT else ImportBlockerCode.MISSING_IMAGE
                blockers += blocker(code, listOf(id))
            }
        }

        manifest.warnings.forEachIndexed { index, warning -> attachWarning(drafts, warning, source(ImportSourceCategory.WARNING, index), blockers) }
        return drafts.map { it.freeze() }
    }

    private fun candidate(
        item: ExtractedItem,
        targets: List<ImportTarget>,
        templateItems: List<TemplateItem>,
        blockers: MutableList<ImportPlanningBlocker>,
        source: ImportSourceId,
    ): ImportCandidate {
        val name = item.name.normalized
        val matchingDefinitions = templateItems.filter { normalize(it.textEn) == name || normalize(it.textZh) == name }
        val matches = targets.filter { target ->
            target.stableId in matchingDefinitions.map { it.stableId } &&
                (item.room == null || normalize(target.displayLabel) == normalize(item.room))
        }
        val target = matches.singleOrNull()
        when (matches.size) {
            0 -> blockers += blocker(ImportBlockerCode.UNKNOWN_TARGET, listOf(source))
            1 -> Unit
            else -> blockers += blocker(ImportBlockerCode.AMBIGUOUS_TARGET, listOf(source))
        }
        val status = item.status
        val allowed = target?.let { selected -> templateItems.single { it.stableId == selected.stableId }.allowedStatuses }
            ?: matchingDefinitions.singleOrNull()?.allowedStatuses.orEmpty()
        val suggestedStatus = when {
            status == null || status.normalized.isEmpty() -> {
                blockers += blocker(ImportBlockerCode.BLANK_STATUS, listOf(source)); null
            }
            status.normalized in allowed -> status.normalized
            else -> {
                blockers += blocker(ImportBlockerCode.UNSUPPORTED_STATUS, listOf(source)); null
            }
        }
        if (target != null && suggestedStatus != null) blockers += blocker(ImportBlockerCode.SUGGESTION_REQUIRES_REVIEW, listOf(source))
        return ImportCandidate(target, suggestedStatus, status)
    }

    private fun attachWarning(
        drafts: MutableList<RowDraft>,
        warning: ExtractionWarning,
        warningId: ImportSourceId,
        blockers: MutableList<ImportPlanningBlocker>,
    ) {
        val owner = when (warning.code) {
            ExtractionWarningCode.UNRESOLVED_TEXT, ExtractionWarningCode.AMBIGUOUS_COLUMNS,
            ExtractionWarningCode.UNRESOLVED_NARRATIVE, ExtractionWarningCode.IMAGE_REVIEW_REQUIRED,
            ExtractionWarningCode.MISSING_IMAGE -> drafts.singleOrNull { it.hasSource(warning.source) }
            else -> null
        }
        val disposition = when (warning.code) {
            ExtractionWarningCode.PAGINATION_EXCLUDED, ExtractionWarningCode.METADATA_EXCLUDED,
            ExtractionWarningCode.URL_EXCLUDED, ExtractionWarningCode.LAYOUT_IMAGE_EXCLUDED -> WarningDisposition.EXTRACTOR_PROVENANCE_EXCLUDED
            ExtractionWarningCode.IMAGE_REVIEW_REQUIRED -> WarningDisposition.PHOTO_PRIVACY_REVIEW
            ExtractionWarningCode.AMBIGUOUS_CAPTIONS -> WarningDisposition.CAPTION_ASSOCIATION_BLOCKER
            ExtractionWarningCode.UNRESOLVED_TEXT, ExtractionWarningCode.AMBIGUOUS_COLUMNS,
            ExtractionWarningCode.UNRESOLVED_NARRATIVE, ExtractionWarningCode.MISSING_IMAGE -> WarningDisposition.SOURCE_OWNER_REQUIRED
        }
        val target = owner ?: RowDraft(mutableListOf()).also { drafts += it }
        target.ids += warningId
        target.warnings += warning
        target.warningReviews += ImportWarningReview(warning, if (owner == null && disposition == WarningDisposition.SOURCE_OWNER_REQUIRED) WarningDisposition.GLOBAL_BLOCKER else disposition)
        when (warning.code) {
            ExtractionWarningCode.UNRESOLVED_TEXT, ExtractionWarningCode.AMBIGUOUS_COLUMNS, ExtractionWarningCode.UNRESOLVED_NARRATIVE -> blockers += blocker(ImportBlockerCode.UNRESOLVED_CONTENT, target.ids)
            ExtractionWarningCode.MISSING_IMAGE -> blockers += blocker(ImportBlockerCode.MISSING_IMAGE, target.ids)
            ExtractionWarningCode.IMAGE_REVIEW_REQUIRED -> blockers += blocker(ImportBlockerCode.PHOTO_REVIEW_REQUIRED, target.ids)
            ExtractionWarningCode.AMBIGUOUS_CAPTIONS -> blockers += blocker(ImportBlockerCode.AMBIGUOUS_CAPTION, target.ids)
            else -> Unit
        }
    }

    private fun normalize(value: String): String = ExtractedText(SourceLocation("", 0), value).normalized
    private fun source(category: ImportSourceCategory, index: Int) = ImportSourceId(category, index)
    private fun blocker(code: ImportBlockerCode, ids: List<ImportSourceId> = emptyList()) = ImportPlanningBlocker(code, ids)
    private fun photoReviews(rows: List<ImportReviewRow>) = rows.filter { it.images.isNotEmpty() }.map { row ->
        ImportPhotoReview(row.sourceIds.single { it.category == ImportSourceCategory.IMAGE }, row.sourceIds.filter { it.category == ImportSourceCategory.PLACEMENT }, ImportPhotoReviewState.UNREVIEWED_EXCLUDED, ImportPhotoAction.ACTION_REQUIRED)
    }

    private class RowDraft(
        val ids: MutableList<ImportSourceId>,
        val items: MutableList<ExtractedItem> = mutableListOf(),
        val fragments: MutableList<ExtractedFragment> = mutableListOf(),
        val identity: MutableList<nz.myinspection.core.report.importing.docx.extract.IdentityCandidate> = mutableListOf(),
        val summaries: MutableList<ExtractedText> = mutableListOf(),
        val captions: MutableList<CaptionCandidate> = mutableListOf(),
        val images: MutableList<nz.myinspection.core.report.importing.docx.extract.ExtractedImage> = mutableListOf(),
        val placements: MutableList<nz.myinspection.core.report.importing.docx.extract.DrawingPlacement> = mutableListOf(),
        val warnings: MutableList<ExtractionWarning> = mutableListOf(),
        val warningReviews: MutableList<ImportWarningReview> = mutableListOf(),
        val candidate: ImportCandidate? = null,
    ) {
        fun hasSource(source: SourceLocation?) = source != null && (items.any { it.name.source == source || it.status?.source == source || it.comment?.source == source } ||
            fragments.any { it.text.source == source } || identity.any { it.text.source == source } || summaries.any { it.source == source } ||
            captions.any { it.text.source == source } || images.any { SourceLocation(it.part, 0) == source } || placements.any { it.source == source })
        fun freeze() = ImportReviewRow(ids, items, fragments, identity, summaries, captions, images, placements, warnings, warningReviews, candidate)
    }
}
