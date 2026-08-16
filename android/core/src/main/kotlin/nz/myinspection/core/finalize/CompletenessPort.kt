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
 * finalize 前置的完备性校验端口——`T2-CAPTURE-CORE` 尚未合并到 master 时本卡与它的集成缝
 * （见卡文「上下文包」）。`FinalizeInspectionUseCase` 只依赖这个接口，调用方以后可以直接换成
 * T2-CAPTURE-CORE 的权威 `missingPhotos()`/项完成度查询，不用改 finalize 内部任何一行。
 *
 * [DbCompletenessChecker] 是本卡自带的默认实现——只用当前已冻结的 schema 做出一个自洽、可测的判定，
 * 让本卡在没有 capture-core 时也能独立成立。它对"不利发现"的分类是从 `T2-CAPTURE-CORE` 卡文已定的
 * 规则表搬来的临时复制，两边独立演进时可能出现分歧——待 R5 登记为技术债。
 *
 * **`check()` 逻辑上只读**：完备性判定不应产生任何写副作用；写是契约违反，不是受支持的用法。类型系统
 * 拦不住实现方在 `check()` 里写库，故 [FinalizeInspectionUseCase] 把每一条完备性判定后的拒绝路径都用
 * `rollback` 而非普通返回收尾——一个违反契约、真的写了东西又报"不完整"的实现，它的写会随这条拒绝路径
 * 一起被撤销，不会留下痕迹（由 `FinalizeInspectionUseCaseTest`「if a seam inside the transaction throws
 * after writing...」及「a write performed during a rejected completeness check is rolled back...」两条
 * 用例证实，后者专测"正常返回 RejectedIncomplete"这条路径，前者测异常路径）。
 *
 * **实现约束**：若实现确需读写（合规实现不应该），必须只通过 [FinalizeInspectionUseCase] 传入的同一个
 * [MyInspectionDatabase]（不得持有/打开自己的连接）——只有这样才能加入调用方已开启的事务，`rollback`
 * 才管得到它。默认实现 [DbCompletenessChecker] 满足这一点（构造器收的就是调用方传入的同一个
 * `database`），且本身不写。
 *
 * **单连接是 v1 契约**：上面的回滚判别测试证明的是单连接事务语义（本用例、`SupplementChainService`
 * 皆同一连接、同一线程顺序执行）。跨连接的入列/锁竞争语义——比如一个 `CompletenessPort` 实现绑定了
 * 另一个连接——不在这份契约内，已登记 **TD10**（`specs/tech-debt-tracker.md`，多连接 DB 契约）：待
 * :app 装配层真的需要多连接时，跟着一个可行的测试驱动方式一起定义；在那之前，评审不得以多连接场景
 * block 单连接卡。
 */
fun interface CompletenessPort {
    fun check(inspectionId: String): CompletenessResult
}

/**
 * [CompletenessPort] 的 DB 直查默认实现。判定两件事：
 *
 * 1. **缺状态**：对每个活跃房间实例，模板里该房间类型下所有未被 `property_item_override` 永久抑制的项，
 *    若在 `inspection_item` 里还没有对应行——该项还没有人作答（`inspection_item` 是答题表，行存在
 *    即已作答）。
 * 2. **缺强制照片**（两级规则，语义来自 `check_item_def.photo_rule`）：
 *    - `ROOM_PANORAMA`：房间级要求，与该房间下具体哪一项是否已作答无关——要求该房间至少一张房间级照片
 *      （`photo.inspection_item_id IS NULL`），即使适用项本身还没作答也一并报出（避免用户补完状态、
 *      重跑 finalize 才发现还缺照片）。
 *    - `ADVERSE_ONLY`：依赖该项的评级（只有已作答才有），故只在已作答且评级属"不利发现"时才判。
 *      "不利发现"集合按巡检类型区分（ANNUAL 用三档缺陷分级，其余用租赁四档）。
 *
 * 两类问题都用 [MissingItem] 报告，UI 侧可以统一渲染。
 */
class DbCompletenessChecker(private val database: MyInspectionDatabase) : CompletenessPort {

    override fun check(inspectionId: String): CompletenessResult {
        val inspection = database.inspectionQueries.selectById(inspectionId).executeAsOne()
        // room_instance.selectByInspection（冻结物）没有 ORDER BY；显式重排使输出清单顺序确定。
        val roomInstances = database.roomInstanceQueries.selectByInspection(inspectionId).executeAsList()
            .sortedBy { it.id }
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

        // 每个房间实例只查一次自己的照片，供房间级/项目级两条规则复用。
        val photosByRoom = roomInstances.associate { room ->
            room.id to database.photoQueries.selectByRoomInstance(room.id).executeAsList()
        }

        val missingStatus = mutableListOf<MissingItem>()
        val missingPhoto = mutableListOf<MissingItem>()

        for (room in roomInstances) {
            val applicableDefs = checkItemDefs.filter { it.room == room.room_key && it.stable_id !in suppressedStableIds }
            val roomPhotos = photosByRoom[room.id].orEmpty()

            val roomPanoramaDefs = applicableDefs.filter { it.photo_rule == "ROOM_PANORAMA" }
            if (roomPanoramaDefs.isNotEmpty() && roomPhotos.none { it.inspection_item_id == null }) {
                roomPanoramaDefs.forEach { missingPhoto += MissingItem(room.id, it.stable_id) }
            }

            for (def in applicableDefs) {
                val existing = existingByKey[room.id to def.stable_id]
                if (existing == null) {
                    missingStatus += MissingItem(room.id, def.stable_id)
                    continue
                }
                if (def.photo_rule == "ADVERSE_ONLY" && isAdverseStatus(inspection.type, existing.status)) {
                    val hasItemPhoto = roomPhotos.any { it.inspection_item_id == existing.id }
                    if (!hasItemPhoto) missingPhoto += MissingItem(room.id, def.stable_id)
                }
            }
        }

        return CompletenessResult(itemsMissingStatus = missingStatus, itemsMissingMandatoryPhoto = missingPhoto)
    }

    private companion object {
        // 租赁四档（nz.myinspection.core.template.TemplateDomains.RENTAL_STATUSES）的不利发现 =
        // FAIR/POOR；年检五态（ANNUAL_STATUSES）的不利发现 = MONITOR/MAINTENANCE_ITEM/
        // SIGNIFICANT_DEFECT。NOT_APPLICABLE 与 GOOD/NO_ISSUE 不逼拍，不在这两个集合里——两个域的
        // 完整划分（这两个集合 ∪ 各自的非不利子集 == 冻结域）由 DbCompletenessCheckerTest 断言钉死。
        val RENTAL_ADVERSE = setOf("FAIR", "POOR")
        val ANNUAL_ADVERSE = setOf("MONITOR", "MAINTENANCE_ITEM", "SIGNIFICANT_DEFECT")

        fun isAdverseStatus(inspectionType: String, status: String): Boolean =
            status in if (inspectionType == "ANNUAL") ANNUAL_ADVERSE else RENTAL_ADVERSE
    }
}
