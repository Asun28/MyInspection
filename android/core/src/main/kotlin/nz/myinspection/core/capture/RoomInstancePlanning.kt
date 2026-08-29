package nz.myinspection.core.capture

import nz.myinspection.core.db.MyInspectionDatabase

/** Stable room identity used by capture creation and finalize completeness. */
internal data class PlannedRoomInstance(
    val roomKey: String,
    val instanceNo: Long,
    val displayLabel: String,
)

/**
 * Derive the complete ordered room-instance set from template authority plus property configuration.
 * Item rooms missing a declaration (legacy or partially migrated templates) remain singleton instead of disappearing.
 */
internal fun MyInspectionDatabase.planRoomInstances(
    propertyId: String,
    templateVersionId: String,
    suppressedStableIds: Set<String>,
): List<PlannedRoomInstance> {
    val activeItemRoomKeys = LinkedHashSet(
        checkItemDefQueries.selectByTemplateVersion(templateVersionId).executeAsList()
            .filter { it.stable_id !in suppressedStableIds }
            .map { it.room },
    )
    val declaredRooms = templateRoomDefQueries.selectByTemplateVersionIncludingDeleted(templateVersionId)
        .executeAsList()
        .filter { it.deleted_at == null && it.room_key in activeItemRoomKeys }
    val declaredByKey = declaredRooms.associateBy { it.room_key }
    val orderedRoomKeys = buildList {
        declaredRooms.forEach { add(it.room_key) }
        activeItemRoomKeys.filterNot { it in declaredByKey }.forEach(::add)
    }
    val configuredCounts = propertyRoomConfigQueries.selectActiveByProperty(propertyId).executeAsList()
        .associate { it.room_key to it.instance_count }

    return buildList {
        for (roomKey in orderedRoomKeys) {
            val count = if (declaredByKey[roomKey]?.repeatable == 1L) {
                configuredCounts[roomKey] ?: 1L
            } else {
                1L
            }
            check(count in 1L..99L) { "invalid persisted instance count $count for room '$roomKey'" }
            for (instanceNo in 1L..count) {
                add(
                    PlannedRoomInstance(
                        roomKey = roomKey,
                        instanceNo = instanceNo,
                        displayLabel = if (count == 1L) roomKey else "$roomKey $instanceNo",
                    ),
                )
            }
        }
    }
}

internal fun MyInspectionDatabase.hasActiveRepeatableRoom(roomKey: String): Boolean =
    templateVersionQueries.selectActive().executeAsList().any { templateVersion ->
        templateRoomDefQueries.selectByTemplateVersionIncludingDeleted(templateVersion.id).executeAsList().any {
            it.room_key == roomKey && it.repeatable == 1L && it.deleted_at == null
        }
    }
