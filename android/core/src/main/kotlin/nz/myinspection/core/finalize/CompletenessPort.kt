package nz.myinspection.core.finalize

import nz.myinspection.core.db.MyInspectionDatabase

/**
 * 指向房间实例内一个具体检查项（未必已在 `inspection_item` 落库——「缺状态」的项恰恰还没有那一行）。
 */
data class MissingItem(val roomInstanceId: String, val stableId: String)

/**
 * 完备性校验结果：finalize 前置闸的判定输出，逐项清单直接喂给 UI（"哪几项还没做完"）。
 */
data class CompletenessResult(
    val itemsMissingStatus: List<MissingItem>,
    val itemsMissingMandatoryPhoto: List<MissingItem>,
) {
    val isComplete: Boolean get() = itemsMissingStatus.isEmpty() && itemsMissingMandatoryPhoto.isEmpty()
}

/**
 * finalize 前置的完备性校验端口——**本卡与 `T2-CAPTURE-CORE` 的集成缝**（该卡在写这段代码时尚未合并
 * 到 master，见卡文「上下文包」）。`FinalizeInspectionUseCase` 只依赖这个接口，不依赖任何具体实现，
 * 所以调用方（:app 装配层）以后可以直接换成 T2-CAPTURE-CORE 的权威 `missingPhotos()`/项完成度查询，
 * 不用改 finalize 内部任何一行。
 *
 * [DbCompletenessChecker] 是**本卡自带的默认实现**——只用当前已冻结的 schema 做出一个自洽、可测的判定，
 * 让本卡在没有 capture-core 的情况下也能独立成立（见任务说明"define the completeness check against
 * your own interface/port"）。它对"不利发现"的分类是从 `T2-CAPTURE-CORE` 卡文已定的规则搬来的一份
 * **临时复制**，两边后续独立演进时可能出现分歧——集成缝与该风险已登记 [specs/tech-debt-tracker.md] TD9，
 * 不在此处重复展开。
 */
fun interface CompletenessPort {
    fun check(inspectionId: String): CompletenessResult
}

/**
 * [CompletenessPort] 的 DB 直查默认实现。判定两件事：
 *
 * 1. **缺状态**：对每个活跃房间实例，模板里该房间类型（`check_item_def.room == room_instance.room_key`）
 *    下所有未被 `property_item_override` 永久抑制的项，若在 `inspection_item` 里还没有对应行——该项
 *    "还没有人给它定状态"，因为 `inspection_item` 是**答题表**，行存在即代表已作答（DB 层 `status`
 *    虽是 `NOT NULL`，但那约束的是"已作答的行必须有值"，不是"每个应答项都已作答"——后者才是这里要判的）。
 * 2. **缺强制照片**（两级规则，语义来自 `check_item_def.photo_rule`）：
 *    - `ROOM_PANORAMA`：该房间下存在一条此规则的已答项时，要求该房间实例至少有一张房间级照片
 *      （`photo.inspection_item_id IS NULL`）。
 *    - `ADVERSE_ONLY`：该项已答且评级落在"不利发现"集合时，要求该项至少有一张项目级照片
 *      （`photo.inspection_item_id = 该项 id`）。"不利发现"集合按巡检类型区分（ANNUAL 用三档缺陷分级，
 *      其余用租赁四档），取值搬自 `T2-CAPTURE-CORE` 卡文已定的规则表。
 *
 * 两类问题都用 [MissingItem] 报告，`stableId` 取自模板项定义、`roomInstanceId` 取自房间实例，
 * 与"缺状态"清单同形状，UI 侧可以统一渲染。
 */
class DbCompletenessChecker(private val database: MyInspectionDatabase) : CompletenessPort {

    override fun check(inspectionId: String): CompletenessResult {
        val inspection = database.inspectionQueries.selectById(inspectionId).executeAsOne()
        val roomInstances = database.roomInstanceQueries.selectByInspection(inspectionId).executeAsList()
        val checkItemDefs = database.checkItemDefQueries
            .selectByTemplateVersion(inspection.template_version_id)
            .executeAsList()
        val suppressedStableIds = database.propertyItemOverrideQueries
            .selectByProperty(inspection.property_id)
            .executeAsList()
            .filter { it.suppressed == 1L }
            .mapTo(mutableSetOf()) { it.stable_id }

        val existingItems = database.inspectionItemQueries.selectByInspection(inspectionId).executeAsList()
        val existingByKey = existingItems.associateBy { it.room_instance_id to it.stable_id }

        // 每个房间实例只查一次自己的照片，供房间级/项目级两条规则复用（同一批行，两种问法）。
        val photosByRoom = roomInstances.associate { room ->
            room.id to database.photoQueries.selectByRoomInstance(room.id).executeAsList()
        }

        val missingStatus = mutableListOf<MissingItem>()
        val missingPhoto = mutableListOf<MissingItem>()

        for (room in roomInstances) {
            val applicableDefs = checkItemDefs.filter { it.room == room.room_key && it.stable_id !in suppressedStableIds }
            for (def in applicableDefs) {
                val existing = existingByKey[room.id to def.stable_id]
                if (existing == null) {
                    missingStatus += MissingItem(room.id, def.stable_id)
                    continue
                }
                val roomPhotos = photosByRoom[room.id].orEmpty()
                when (def.photo_rule) {
                    "ROOM_PANORAMA" -> {
                        val hasPanorama = roomPhotos.any { it.inspection_item_id == null }
                        if (!hasPanorama) missingPhoto += MissingItem(room.id, def.stable_id)
                    }
                    "ADVERSE_ONLY" -> {
                        if (isAdverseStatus(inspection.type, existing.status)) {
                            val hasItemPhoto = roomPhotos.any { it.inspection_item_id == existing.id }
                            if (!hasItemPhoto) missingPhoto += MissingItem(room.id, def.stable_id)
                        }
                    }
                    else -> Unit // NULL = 无强制拍照要求
                }
            }
        }

        return CompletenessResult(itemsMissingStatus = missingStatus, itemsMissingMandatoryPhoto = missingPhoto)
    }

    private companion object {
        // 搬自 T2-CAPTURE-CORE 卡文「两级拍照规则」：租赁四档的不利发现 = FAIR/POOR；
        // 年检三档缺陷分级的不利发现 = MONITOR/MAINTENANCE_ITEM/SIGNIFICANT_DEFECT。
        // N_A 与 GOOD/NO_ISSUE 不逼拍，故不在这两个集合里。
        val RENTAL_ADVERSE = setOf("FAIR", "POOR")
        val ANNUAL_ADVERSE = setOf("MONITOR", "MAINTENANCE_ITEM", "SIGNIFICANT_DEFECT")

        fun isAdverseStatus(inspectionType: String, status: String): Boolean =
            status in if (inspectionType == "ANNUAL") ANNUAL_ADVERSE else RENTAL_ADVERSE
    }
}
