package nz.myinspection.core.report.importing.plan

import nz.myinspection.core.report.importing.docx.extract.ExtractionWarningCode
import nz.myinspection.core.report.importing.docx.extract.FragmentRole
import nz.myinspection.core.report.importing.plan.ImportSourceCategory as Category

enum class ImportPrivacy { NO_TENANT_BELONGINGS, TENANT_BELONGINGS }
enum class ImportExclusionReason { NOT_RELEVANT, DUPLICATE, PRIVACY, MISSING_MEDIA, PROVENANCE, PAGINATION, METADATA, URL, LAYOUT_IMAGE }
enum class ImportDecisionState { ACTION_REQUIRED, CONFIRMED, EXCLUDED }
sealed interface ImportDecision {
    data class Item(val target: ImportTarget, val status: String) : ImportDecision
    data class Note(val target: ImportTarget) : ImportDecision
    data class Photo(val target: ImportTarget, val privacy: ImportPrivacy) : ImportDecision
    data object Summary : ImportDecision
    data class Exclude(val reason: ImportExclusionReason, val privacy: ImportPrivacy? = null) : ImportDecision
}
data class ImportReviewedSource(val id: ImportSourceId, val owner: ImportSourceId, val state: ImportDecisionState,
    val decision: ImportDecision?, val reason: ImportExclusionReason?)
data class ImportReviewedItem(val target: ImportTarget, val status: String?, val note: String?)
data class ImportReviewedNote(val source: ImportSourceId, val target: ImportTarget, val text: String)
data class ImportReviewedPhoto(val source: ImportSourceId, val target: ImportTarget, val privacy: ImportPrivacy, val sourceSha256: String)

/** Immutable individual decisions. A revision is not a preview or permission to write. */
class ImportReview private constructor(
    private val inventory: Inventory,
    private val decisions: Map<ImportSourceId, ImportDecision>,
    val summaryStatus: String?,
) {
    val plan: ImportPlan get() = inventory.plan
    val sources: List<ImportReviewedSource> = resolveSources()
    /** Individual source notes for review; items is the sole aggregated native item projection. */
    val notes: List<ImportReviewedNote> = immutable(inventory.ids.mapNotNull { source ->
        (decisions[source] as? ImportDecision.Note)?.let { ImportReviewedNote(source, it.target, inventory.text(source)) }
    })
    val items: List<ImportReviewedItem> = nativeItems()
    val photos: List<ImportReviewedPhoto> = immutable(inventory.ids.mapNotNull { source ->
        (decisions[source] as? ImportDecision.Photo)?.let { choice ->
            ImportReviewedPhoto(source, choice.target, choice.privacy, inventory.manifest.images[source.index].sha256)
        }
    })
    val unratedTargets: List<ImportTarget> = immutable(plan.targets.filterNot { target -> items.any { it.target == target && it.status != null } })
    val blockers: List<ImportPlanningBlocker> = immutable(plan.blockers.filter { it.sourceIds.isEmpty() } +
        sources.filter { it.state == ImportDecisionState.ACTION_REQUIRED }.map { source ->
            val code = when (source.id.category) {
                Category.IMAGE -> ImportBlockerCode.PHOTO_REVIEW_REQUIRED
                Category.CAPTION -> ImportBlockerCode.AMBIGUOUS_CAPTION
                Category.PLACEMENT -> ImportBlockerCode.MISSING_IMAGE
                Category.WARNING -> when (inventory.manifest.warnings[source.id.index].code) {
                    ExtractionWarningCode.MISSING_IMAGE -> ImportBlockerCode.MISSING_IMAGE
                    ExtractionWarningCode.AMBIGUOUS_CAPTIONS -> ImportBlockerCode.AMBIGUOUS_CAPTION
                    ExtractionWarningCode.IMAGE_REVIEW_REQUIRED -> ImportBlockerCode.PHOTO_REVIEW_REQUIRED
                    else -> ImportBlockerCode.UNRESOLVED_CONTENT
                }
                else -> ImportBlockerCode.UNRESOLVED_CONTENT
            }
            ImportPlanningBlocker(code, listOf(source.id))
        })

    fun decide(source: ImportSourceId, decision: ImportDecision): ImportReview {
        require(source !in decisions) { "SOURCE_ALREADY_DECIDED" }
        return change(source, decision)
    }

    fun replace(source: ImportSourceId, decision: ImportDecision): ImportReview {
        require(source in decisions) { "SOURCE_NOT_DECIDED" }
        return change(source, decision)
    }

    fun selectSummaryStatus(status: String): ImportReview {
        require(includedSummary().isNotEmpty() && inventory.summaryIds.all { it in decisions }) { "SUMMARY_INCOMPLETE" }
        validateStatus(summaryTarget(), status)
        return ImportReview(inventory, decisions, status)
    }

    private fun change(source: ImportSourceId, decision: ImportDecision): ImportReview {
        require(source in inventory.idSet && source !in inventory.aliases && source.category != Category.WARNING) { "SOURCE_NOT_EDITABLE" }
        validate(source, decision)
        val updated = decisions + (source to decision)
        val writers = updated.values.filterIsInstance<ImportDecision.Item>().map { it.target }
        require(writers.distinct().size == writers.size) { "DUPLICATE_TARGET" }
        if (ImportDecision.Summary in updated.values) require(summaryTarget() !in writers) { "SUMMARY_TARGET_CONFLICT" }
        return ImportReview(inventory, updated, if (source.category == Category.SUMMARY) null else summaryStatus)
    }

    private fun validate(source: ImportSourceId, decision: ImportDecision) {
        require(source !in inventory.identitySources || decision == ImportDecision.Exclude(ImportExclusionReason.PROVENANCE)) { "IDENTITY_REQUIRES_EXCLUSION" }
        when (decision) {
            is ImportDecision.Item -> {
                require(source.category == Category.ITEM) { "ITEM_SOURCE_REQUIRED" }
                validateStatus(decision.target, decision.status)
            }
            is ImportDecision.Note -> {
                require(source.category == Category.CAPTION || (source.category == Category.FRAGMENT &&
                    inventory.manifest.fragments[source.index].role != FragmentRole.IDENTITY)) { "NOTE_SOURCE_REQUIRED" }
                validateTarget(decision.target)
                require(decision.target.stableId != "GEN-SUMMARY-01") { "SUMMARY_REQUIRES_AGGREGATE" }
            }
            is ImportDecision.Photo -> {
                require(source.category == Category.IMAGE) { "IMAGE_SOURCE_REQUIRED" }
                validateTarget(decision.target)
                require(inventory.manifest.images[source.index].sha256.matches(Regex("[0-9a-f]{64}"))) { "MISSING_IMAGE_DIGEST" }
            }
            ImportDecision.Summary -> {
                require(source.category == Category.SUMMARY) { "SUMMARY_SOURCE_REQUIRED" }
                summaryTarget()
            }
            is ImportDecision.Exclude -> {
                require(source.category != Category.IMAGE || decision.privacy != null) { "PHOTO_PRIVACY_REQUIRED" }
                require(source.category == Category.IMAGE || decision.privacy == null) { "PRIVACY_REQUIRES_IMAGE" }
            }
        }
    }

    private fun validateTarget(target: ImportTarget) {
        require(target in plan.targets) { "UNKNOWN_TARGET" }
    }

    private fun validateStatus(target: ImportTarget, status: String) {
        validateTarget(target)
        require(status in plan.context.template!!.template.items.single { it.stableId == target.stableId }.allowedStatuses) { "STATUS_NOT_ALLOWED" }
    }

    private fun summaryTarget(): ImportTarget = requireNotNull(plan.targets.singleOrNull { it.stableId == "GEN-SUMMARY-01" }) { "SUMMARY_TARGET_REQUIRED" }
    private fun includedSummary() = inventory.summaryIds.filter { decisions[it] == ImportDecision.Summary }

    private fun nativeItems(): List<ImportReviewedItem> {
        val ratings = inventory.ids.mapNotNull { source -> (decisions[source] as? ImportDecision.Item)?.let { choice ->
            choice.target to ImportReviewedItem(choice.target, choice.status, inventory.manifest.items[source.index].comment?.raw)
        } }.toMap()
        val noteGroups = notes.groupBy { it.target }
        val summary = if (summaryStatus == null) null else summaryTarget()
        return immutable(plan.targets.mapNotNull { target ->
            val rating = ratings[target]
            val parts = listOfNotNull(rating?.note) + noteGroups[target].orEmpty().map { it.text }
            when {
                target == summary -> ImportReviewedItem(target, summaryStatus, includedSummary().joinToString("\n") { inventory.text(it) })
                rating != null || parts.isNotEmpty() -> ImportReviewedItem(target, rating?.status, parts.takeIf { it.isNotEmpty() }?.joinToString("\n"))
                else -> null
            }
        })
    }

    private fun resolveSources(): List<ImportReviewedSource> {
        val resolved = inventory.ids.filter { it.category != Category.WARNING }.associateWith { source ->
            val owner = inventory.aliases[source] ?: source
            val decision = decisions[owner]
            val state = when {
                decision == null || (decision == ImportDecision.Summary && summaryStatus == null) -> ImportDecisionState.ACTION_REQUIRED
                decision is ImportDecision.Exclude -> ImportDecisionState.EXCLUDED
                else -> ImportDecisionState.CONFIRMED
            }
            ImportReviewedSource(source, owner, state, decision, (decision as? ImportDecision.Exclude)?.reason)
        }
        return immutable(inventory.ids.map { source -> resolved[source] ?: resolveWarning(source, resolved) })
    }

    private fun resolveWarning(source: ImportSourceId, resolved: Map<ImportSourceId, ImportReviewedSource>): ImportReviewedSource {
        val warning = inventory.manifest.warnings[source.index]
        val reason = when (warning.code) {
            ExtractionWarningCode.PAGINATION_EXCLUDED -> ImportExclusionReason.PAGINATION
            ExtractionWarningCode.METADATA_EXCLUDED -> ImportExclusionReason.METADATA
            ExtractionWarningCode.URL_EXCLUDED -> ImportExclusionReason.URL
            ExtractionWarningCode.LAYOUT_IMAGE_EXCLUDED -> ImportExclusionReason.LAYOUT_IMAGE
            else -> null
        }
        val owners = inventory.warningOwners[source].orEmpty().map { resolved.getValue(it) }
        val terminal = owners.isNotEmpty() && owners.none { it.state == ImportDecisionState.ACTION_REQUIRED }
        val excluded = terminal && owners.all { it.state == ImportDecisionState.EXCLUDED }
        val state = when {
            reason != null || excluded -> ImportDecisionState.EXCLUDED
            terminal && warning.code != ExtractionWarningCode.MISSING_IMAGE -> ImportDecisionState.CONFIRMED
            else -> ImportDecisionState.ACTION_REQUIRED
        }
        return ImportReviewedSource(source, source, state, null, reason ?: if (excluded) owners.first().reason else null)
    }

    companion object {
        fun start(input: ImportPlanningInput): ImportReview = ImportReview(Inventory(input), emptyMap(), null)
    }

    private class Inventory(input: ImportPlanningInput) {
        val plan = ImportPlanner().project(input)
        val manifest = input.manifest
        val ids = plan.rows.flatMap { it.sourceIds }.sortedWith(compareBy({ it.category.ordinal }, { it.index }))
        val idSet = ids.toSet()
        val summaryIds = ids.filter { it.category == Category.SUMMARY }
        private val identityTexts = manifest.identity.map { it.text }.toSet()
        val identitySources = ids.filter { source ->
            val evidence = when (source.category) {
                Category.ITEM -> manifest.items[source.index].let { listOfNotNull(it.name, it.status, it.comment) }
                Category.FRAGMENT -> listOf(manifest.fragments[source.index].text)
                Category.CAPTION -> listOf(manifest.captions[source.index].text)
                Category.SUMMARY -> listOf(manifest.summaryCandidates[source.index])
                Category.IDENTITY -> listOf(manifest.identity[source.index].text)
                else -> emptyList()
            }
            evidence.any { it in identityTexts }
        }.toSet()
        val aliases = mutableMapOf<ImportSourceId, ImportSourceId>()
        val warningOwners = mutableMapOf<ImportSourceId, List<ImportSourceId>>()
        private val captionOwners = ids.mapNotNull { source ->
            val text = when (source.category) {
                Category.CAPTION -> manifest.captions[source.index].text
                Category.FRAGMENT -> manifest.fragments[source.index].takeIf { it.role == FragmentRole.CAPTION }?.text
                else -> null
            }
            text?.let { (it.source.part to it.source.ordinal) to source }
        }.groupBy({ it.first }, { it.second })

        init {
            plan.rows.forEach { row ->
                val textOwners = row.sourceIds.flatMap { source ->
                    val texts = when (source.category) {
                        Category.ITEM -> manifest.items[source.index].let { listOfNotNull(it.name, it.status, it.comment).distinct() }
                        Category.SUMMARY -> listOf(manifest.summaryCandidates[source.index])
                        Category.IDENTITY -> listOf(manifest.identity[source.index].text)
                        else -> emptyList()
                    }
                    texts.map { it to source }
                }.groupBy({ it.first }, { it.second })
                row.sourceIds.filter { it.category == Category.FRAGMENT }.forEach { fragmentId ->
                    val evidence = manifest.fragments[fragmentId.index].text
                    val owners = textOwners[evidence].orEmpty()
                    if (owners.size == 1) aliases[fragmentId] = owners.single()
                }
                val image = row.sourceIds.singleOrNull { it.category == Category.IMAGE }
                if (image != null) row.sourceIds.filter { it.category == Category.PLACEMENT }.forEach { aliases[it] = image }
            }
            plan.rows.forEach { row ->
                val contentOwners = row.sourceIds.filter { it.category != Category.WARNING }.map { aliases[it] ?: it }.distinct()
                val photoOwners = contentOwners.filter { it.category == Category.IMAGE }
                row.sourceIds.filter { it.category == Category.WARNING }.forEach { warningId ->
                    val warning = manifest.warnings[warningId.index]
                    val owners = when (warning.code) {
                        ExtractionWarningCode.AMBIGUOUS_CAPTIONS -> warning.source?.let { captionOwners[it.part to it.ordinal] }.orEmpty()
                        ExtractionWarningCode.IMAGE_REVIEW_REQUIRED -> photoOwners
                        else -> contentOwners
                    }
                    warningOwners[warningId] = owners
                }
            }
        }

        fun text(source: ImportSourceId): String = when (source.category) {
            Category.FRAGMENT -> manifest.fragments[source.index].text.raw
            Category.CAPTION -> manifest.captions[source.index].text.raw
            Category.SUMMARY -> manifest.summaryCandidates[source.index].raw
            else -> error("TEXT_SOURCE_REQUIRED")
        }
    }
}
