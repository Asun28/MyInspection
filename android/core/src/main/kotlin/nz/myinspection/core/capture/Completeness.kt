package nz.myinspection.core.capture

/** `check_item_def.photo_rule` 的两个非空取值（域同 CheckItemDef.sq 的 CHECK，第三态是 null=无要求）。 */
internal const val PHOTO_RULE_ROOM_PANORAMA = "ROOM_PANORAMA"
internal const val PHOTO_RULE_ADVERSE_ONLY = "ADVERSE_ONLY"

/**
 * 各模板类型的「不利发现」评级域（卡片上下文包「两级拍照规则」/「不利发现强制备注」共用同一份域）。
 * 出租三类共享同一集合；年检另有五态里的三个不利档；未知类型永不触发任何强制。
 *
 * **只暴露谓词，不暴露集合**：早前版本有一个返回 `Set<String>` 的 `forType`，调用方能把它强转成
 * `MutableSet` 改写这份进程级共享判据（同 `core/template/Template.kt` 的 `TemplateDomains` 已修过的
 * 那一类缺陷——T1-TEMPLATE-ENGINE 早前也踩过"只读集合非真不可变，强转回去仍可改"）。这里换一种做法：
 * 底下两个集合本就 `private`，唯一的公开出口 [isAdverse] 只返回 `Boolean`，没有集合引用可拿去强转，
 * 这条口子从结构上就不存在，不必再靠 `Collections.unmodifiableSet` 兜底。
 */
object AdverseStatuses {
    private val RENTAL_ADVERSE: Set<String> = setOf("FAIR", "POOR")
    private val ANNUAL_ADVERSE: Set<String> = setOf("MONITOR", "MAINTENANCE_ITEM", "SIGNIFICANT_DEFECT")

    /** [status] 对 [type] 而言是否落在「不利发现」域内。 */
    fun isAdverse(type: String, status: String): Boolean = when (type) {
        "ROUTINE", "INGOING", "EXIT" -> status in RENTAL_ADVERSE
        "ANNUAL" -> status in ANNUAL_ADVERSE
        else -> false
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

/**
 * 单房间进度：房间级全景要求满足当且仅当该房间不要求全景，或已有 >=1 张房间级照片。
 *
 * **`completedItems` 把「不利发现强制备注」计入完成判定**（卡片正文「status 为不利发现时 note 非空
 * 才算该项完成」）：一项已记录状态但落在不利发现域内、备注仍空白，不算完成——不止是 [computeMissingNotes]
 * 单独报出来这一份缺口，房间/整体进度也必须如实显示"没做完"，两处不能有一处判完成一处判未完成。
 */
fun computeRoomProgress(type: String, room: RoomSnapshot): RoomProgress {
    val requiresPanorama = room.items.any { it.photoRule == PHOTO_RULE_ROOM_PANORAMA }
    val completedItems = room.items.count { def ->
        val recorded = room.recordedItems[def.stableId] ?: return@count false
        !AdverseStatuses.isAdverse(type, recorded.status) || !recorded.note.isNullOrBlank()
    }
    return RoomProgress(
        roomInstanceId = room.roomInstanceId,
        roomKey = room.roomKey,
        displayLabel = room.displayLabel,
        totalItems = room.items.size,
        completedItems = completedItems,
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

    val missingItems = rooms.flatMap { room ->
        room.items.filter { it.photoRule == PHOTO_RULE_ADVERSE_ONLY }.mapNotNull { def ->
            val recorded = room.recordedItems[def.stableId] ?: return@mapNotNull null
            val hasPhoto = (room.itemPhotoCounts[def.stableId] ?: 0) >= 1
            if (AdverseStatuses.isAdverse(type, recorded.status) && !hasPhoto) MissingItemPhoto(room.roomInstanceId, def.stableId) else null
        }
    }
    return PhotoCompleteness(missingRooms, missingItems)
}

/**
 * 不利发现强制备注：与 photoRule 无关（NULL/ADVERSE_ONLY/ROOM_PANORAMA 任一项，只要判到不利发现都要备注），
 * 与两级拍照规则各自独立生效。空白字符串同样视为未备注（`isNullOrBlank`）。
 */
fun computeMissingNotes(type: String, rooms: List<RoomSnapshot>): List<MissingNote> {
    return rooms.flatMap { room ->
        room.items.mapNotNull { def ->
            val recorded = room.recordedItems[def.stableId] ?: return@mapNotNull null
            if (AdverseStatuses.isAdverse(type, recorded.status) && recorded.note.isNullOrBlank()) {
                MissingNote(room.roomInstanceId, def.stableId)
            } else {
                null
            }
        }
    }
}
