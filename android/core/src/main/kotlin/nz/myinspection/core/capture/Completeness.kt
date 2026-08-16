package nz.myinspection.core.capture

/** `check_item_def.photo_rule` 的两个非空取值（域同 CheckItemDef.sq 的 CHECK，第三态是 null=无要求）。 */
internal const val PHOTO_RULE_ROOM_PANORAMA = "ROOM_PANORAMA"
internal const val PHOTO_RULE_ADVERSE_ONLY = "ADVERSE_ONLY"

/**
 * 各模板类型的「不利发现」评级域（卡片上下文包「两级拍照规则」/「不利发现强制备注」共用同一份域）。
 * 出租三类共享同一集合；年检另有五态里的三个不利档；未知类型返回空集——调用方据此天然不触发任何强制。
 */
object AdverseStatuses {
    private val RENTAL_ADVERSE: Set<String> = setOf("FAIR", "POOR")
    private val ANNUAL_ADVERSE: Set<String> = setOf("MONITOR", "MAINTENANCE_ITEM", "SIGNIFICANT_DEFECT")

    fun forType(type: String): Set<String> = when (type) {
        "ROUTINE", "INGOING", "EXIT" -> RENTAL_ADVERSE
        "ANNUAL" -> ANNUAL_ADVERSE
        else -> emptySet()
    }
}

/** 一条项定义在完备性计算里需要的最小切面（已排除该物业当前 suppressed 的项）。 */
data class ItemDef(
    val stableId: String,
    val photoRule: String?,
    val allowedStatuses: List<String>,
)

/** 某项在本次巡检里已记录的走查结果。不在此 map 里 = 尚未走查到，不参与任何"完成/缺项"判定。 */
data class RecordedItem(val status: String, val note: String?)

/**
 * 一个房间实例在完备性计算时需要的全部输入：项定义 + 已记录结果 + 房间级/项目级照片计数。
 * 计数而非明细——完备性判定只关心"有没有"，不需要照片本身的身份。
 */
data class RoomSnapshot(
    val roomInstanceId: String,
    val roomKey: String,
    val displayLabel: String,
    val items: List<ItemDef>,
    val recordedItems: Map<String, RecordedItem>,
    val roomPhotoCount: Int,
    val itemPhotoCounts: Map<String, Int>,
)

/** 一个房间的走查进度（[isComplete] = 全部项有状态 + 若房间要求全景照则已拍，卡片正文原话）。 */
data class RoomProgress(
    val roomInstanceId: String,
    val roomKey: String,
    val displayLabel: String,
    val totalItems: Int,
    val completedItems: Int,
    val requiresRoomPanorama: Boolean,
    val hasRoomPanorama: Boolean,
) {
    val isComplete: Boolean get() = completedItems == totalItems && hasRoomPanorama
}

/** 整次巡检的走查进度（各房间进度的集合）。 */
data class WalkProgress(val inspectionId: String, val rooms: List<RoomProgress>) {
    val isComplete: Boolean get() = rooms.all { it.isComplete }
}

data class MissingRoomPhoto(val roomInstanceId: String, val roomKey: String)
data class MissingItemPhoto(val roomInstanceId: String, val stableId: String)
data class MissingNote(val roomInstanceId: String, val stableId: String)

/** `missingPhotos(inspection)` 的产出：房间级全景缺口 + 项目级不利发现缺口，UI 与 finalize 校验共用。 */
data class PhotoCompleteness(
    val missingRoomPanoramas: List<MissingRoomPhoto>,
    val missingItemPhotos: List<MissingItemPhoto>,
) {
    val isComplete: Boolean get() = missingRoomPanoramas.isEmpty() && missingItemPhotos.isEmpty()
}

/** 单房间进度：房间级全景要求满足当且仅当该房间不要求全景，或已有 >=1 张房间级照片。 */
fun computeRoomProgress(room: RoomSnapshot): RoomProgress {
    val requiresPanorama = room.items.any { it.photoRule == PHOTO_RULE_ROOM_PANORAMA }
    return RoomProgress(
        roomInstanceId = room.roomInstanceId,
        roomKey = room.roomKey,
        displayLabel = room.displayLabel,
        totalItems = room.items.size,
        completedItems = room.items.count { room.recordedItems.containsKey(it.stableId) },
        requiresRoomPanorama = requiresPanorama,
        hasRoomPanorama = !requiresPanorama || room.roomPhotoCount >= 1,
    )
}

/**
 * 两级拍照完备性：房间级——任一房间内存在 photoRule=ROOM_PANORAMA 的项定义，且该房间零张房间级照片；
 * 项目级——仅 photoRule=ADVERSE_ONLY 的项，且已记录状态落在该类型的不利发现域内，且该项零张关联照片。
 * 尚未走查到的项（不在 recordedItems 里）不参与判定——不能逼一张还没被评估的项先拍照。
 */
fun computeMissingPhotos(type: String, rooms: List<RoomSnapshot>): PhotoCompleteness {
    val missingRooms = rooms.filter { room ->
        room.items.any { it.photoRule == PHOTO_RULE_ROOM_PANORAMA } && room.roomPhotoCount < 1
    }.map { MissingRoomPhoto(it.roomInstanceId, it.roomKey) }

    val adverse = AdverseStatuses.forType(type)
    val missingItems = rooms.flatMap { room ->
        room.items.filter { it.photoRule == PHOTO_RULE_ADVERSE_ONLY }.mapNotNull { def ->
            val recorded = room.recordedItems[def.stableId] ?: return@mapNotNull null
            val hasPhoto = (room.itemPhotoCounts[def.stableId] ?: 0) >= 1
            if (recorded.status in adverse && !hasPhoto) MissingItemPhoto(room.roomInstanceId, def.stableId) else null
        }
    }
    return PhotoCompleteness(missingRooms, missingItems)
}

/**
 * 不利发现强制备注：与 photoRule 无关（NULL/ADVERSE_ONLY/ROOM_PANORAMA 任一项，只要判到不利发现都要备注），
 * 与两级拍照规则各自独立生效。空白字符串同样视为未备注（`isNullOrBlank`）。
 */
fun computeMissingNotes(type: String, rooms: List<RoomSnapshot>): List<MissingNote> {
    val adverse = AdverseStatuses.forType(type)
    return rooms.flatMap { room ->
        room.items.mapNotNull { def ->
            val recorded = room.recordedItems[def.stableId] ?: return@mapNotNull null
            if (recorded.status in adverse && recorded.note.isNullOrBlank()) {
                MissingNote(room.roomInstanceId, def.stableId)
            } else {
                null
            }
        }
    }
}
