package nz.myinspection.core.report.importing.plan

import java.time.LocalDate
import nz.myinspection.core.template.TemplateItem
import nz.myinspection.core.template.TemplateLoader

class ImportPlanningSnapshot private constructor(
    val context: ImportPlanContext,
    targets: List<ImportTarget>,
    blockers: List<ImportPlanningBlocker>,
) {
    val targets: List<ImportTarget> = immutable(targets)
    /** Candidates are not part of this predecessor, so every target starts unrated. */
    val unratedTargets: List<ImportTarget> = immutable(targets)
    val blockers: List<ImportPlanningBlocker> = immutable(blockers)

    companion object {
        fun create(input: ImportPlanningInput): ImportPlanningSnapshot {
            val context = ImportPlanContext(
                propertyId = input.propertyId,
                tenancyId = input.tenancyId,
                reportDate = input.reportDate,
                sourceSha256 = input.sourceSha256,
                manifestDigest = input.manifest.normalizedDigest,
                template = input.template,
                roomInstances = input.roomInstances,
                suppressedStableIds = input.suppressedStableIds,
                hasActiveDraft = input.hasActiveDraft,
            )
            val blockers = mutableListOf<ImportPlanningBlocker>()
            if (input.propertyId.isNullOrBlank()) blockers += blocker(ImportBlockerCode.MISSING_PROPERTY)
            if (input.tenancyId.isNullOrBlank()) blockers += blocker(ImportBlockerCode.MISSING_TENANCY)
            when {
                input.reportDate == null -> blockers += blocker(ImportBlockerCode.MISSING_REPORT_DATE)
                !isIsoDate(input.reportDate) -> blockers += blocker(ImportBlockerCode.INVALID_REPORT_DATE)
            }
            if (input.sourceSha256?.matches(SHA256) != true) blockers += blocker(ImportBlockerCode.INVALID_SOURCE_SHA256)
            if (input.hasActiveDraft) blockers += blocker(ImportBlockerCode.ACTIVE_DRAFT)

            val binding = input.template
            if (binding == null) blockers += blocker(ImportBlockerCode.MISSING_TEMPLATE)
            val template = binding?.template
            val validTemplate = binding?.id?.isNotBlank() == true && binding.contentHash.matches(SHA256) &&
                template != null && template.type == "ROUTINE" && template.version == 2 && TemplateLoader.validate(template).isEmpty()
            if (binding != null && !validTemplate) blockers += blocker(ImportBlockerCode.TEMPLATE_NOT_CURRENT_ROUTINE_V2)
            if (!validTemplate) return ImportPlanningSnapshot(context, emptyList(), blockers)

            val knownIds = template.items.mapTo(mutableSetOf()) { it.stableId }
            if (!knownIds.containsAll(input.suppressedStableIds)) {
                blockers += blocker(ImportBlockerCode.UNKNOWN_SUPPRESSED_STABLE_ID)
                return ImportPlanningSnapshot(context, emptyList(), blockers)
            }
            val activeItems = template.items.filterNot { it.stableId in input.suppressedStableIds }
            if (!validInventory(activeItems, template.rooms.associate { it.key to it.repeatable }, input.roomInstances)) {
                blockers += blocker(ImportBlockerCode.INVALID_ROOM_INVENTORY)
                return ImportPlanningSnapshot(context, emptyList(), blockers)
            }
            val targets = input.roomInstances.flatMap { room ->
                activeItems.filter { it.room == room.roomKey }.map { item ->
                    ImportTarget(room.roomKey, room.instanceNo, item.stableId, room.displayLabel)
                }
            }
            return ImportPlanningSnapshot(context, targets, blockers)
        }

        private fun validInventory(
            items: List<TemplateItem>,
            repeatable: Map<String, Boolean>,
            rooms: List<ImportRoomInstance>,
        ): Boolean {
            val expected = items.map { it.room }.toSet()
            val grouped = rooms.groupBy { it.roomKey }
            return rooms.all { it.roomKey in expected && it.instanceNo in 1L..99L } && grouped.keys == expected &&
                grouped.all { (key, values) ->
                    val count = values.size.toLong()
                    val numbers = values.map { it.instanceNo }.toSet()
                    numbers.size == values.size && numbers == (1L..count).toSet() &&
                        (repeatable[key] == true || count == 1L) &&
                        values.all { it.displayLabel == if (count == 1L) key else "$key ${it.instanceNo}" }
                }
        }

        private fun isIsoDate(value: String): Boolean =
            value.length == 10 && runCatching { LocalDate.parse(value) }.isSuccess

        private fun blocker(code: ImportBlockerCode) = ImportPlanningBlocker(code)
        private val SHA256 = Regex("[0-9a-f]{64}")
    }
}
