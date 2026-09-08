package nz.myinspection.core.report.importing.plan

import nz.myinspection.core.report.importing.docx.extract.ExtractionWarningCode
import nz.myinspection.core.report.importing.docx.extract.FragmentRole
import nz.myinspection.core.report.importing.plan.ImportSourceCategory as Category

/** Complete in-memory preview capability. Only capture can bind one immutable review revision. */
class ImportReviewPreview private constructor(val review: ImportReview) {
    private val choices = exactChoices()
    val bulkSources: List<ImportSourceId> = immutable(choices.keys.toList())

    /** Missing native ratings remain unrated: READY permits planning, never finalization or writes. */
    fun isReady(current: ImportReview): Boolean = current === review && current.blockers.isEmpty()

    fun confirmExact(current: ImportReview, requested: List<ImportSourceId>): ImportReview {
        require(current === review) { "STALE_PREVIEW" }
        val selection = requested.toList()
        require(selection.size == selection.toSet().size && selection.toSet() == choices.keys) { "COMPLETE_BULK_REQUIRED" }
        // Revisions stay local until every existing decision guard succeeds; failure publishes nothing.
        return choices.entries.fold(current) { revision, (source, choice) -> revision.decide(source, choice) }
    }

    private fun exactChoices(): Map<ImportSourceId, ImportDecision.Item> {
        val rows = review.plan.rows
        val identities = rows.flatMap { it.identity }.mapTo(mutableSetOf()) { it.text }
        val captionLocations = (rows.flatMap { it.captions }.map { it.text.source } +
            rows.flatMap { it.fragments }.filter { it.role == FragmentRole.CAPTION }.map { it.text.source } +
            rows.flatMap { it.warnings }.filter { it.code == ExtractionWarningCode.AMBIGUOUS_CAPTIONS }.mapNotNull { it.source })
            .mapTo(mutableSetOf()) { it.part to it.ordinal }
        val undecided = review.sources.filter { it.id == it.owner && it.decision == null }.mapTo(mutableSetOf()) { it.id }
        return rows.mapNotNull { row ->
            val source = row.sourceIds.singleOrNull { it.category == Category.ITEM } ?: return@mapNotNull null
            val candidate = row.candidate ?: return@mapNotNull null
            val target = candidate.target ?: return@mapNotNull null
            val status = candidate.suggestedStatus ?: return@mapNotNull null
            val evidence = row.items.flatMap { listOfNotNull(it.name, it.status, it.comment) }
            if (source !in undecided || row.identity.isNotEmpty() || row.summaries.isNotEmpty() ||
                row.captions.isNotEmpty() || row.images.isNotEmpty() ||
                evidence.any { it in identities || (it.source.part to it.source.ordinal) in captionLocations }) return@mapNotNull null
            source to ImportDecision.Item(target, status)
        }.toMap()
    }

    companion object {
        fun capture(review: ImportReview): ImportReviewPreview = ImportReviewPreview(review)
    }
}
