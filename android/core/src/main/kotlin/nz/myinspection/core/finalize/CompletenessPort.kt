package nz.myinspection.core.finalize

import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.template.TemplateDomains

/**
 * 指向房间实例内一个具体检查项（未必已在 `inspection_item` 落库——「缺状态」的项恰恰还没有那一行）。
 */
data class MissingItem(val roomInstanceId: String, val stableId: String)

/**
 * 完备性校验结果：finalize 前置闸的判定输出，逐项清单直接喂给 UI（"哪几项还没做完"）。
 *
 * [roomsMissingInstance]：模板要求（至少一项未被抑制）却连 `room_instance` 都没有的房间键——整间房
 * 从未被走查过，是"缺项"里最强的一种，理应与逐项清单一起报给 UI（"哪个房间没做"，不只是"哪个检查项没做"）。
 * [itemsWithInvalidStatus]：`ADVERSE_ONLY` 判定遇到一个不在冻结域内的评级字符串——不是"这个状态是否
 * 合法"这层校验的第二道闸（那层的权威在 `core/capture` 的写入口，见 [DbCompletenessChecker] 顶部说明），
 * 而是"不利发现分类器本身对它能不能给出确定答案"——分类不出来就不能悄悄当成"非不利"放行。
 * 两者默认空列表，方便只关心逐项判定的调用方（如测试里的假 `CompletenessPort`）不必每次都显式传。
 */
data class CompletenessResult(
    val itemsMissingStatus: List<MissingItem>,
    val itemsMissingMandatoryPhoto: List<MissingItem>,
    val roomsMissingInstance: List<String> = emptyList(),
    val itemsWithInvalidStatus: List<MissingItem> = emptyList(),
) {
    val isComplete: Boolean
        get() = itemsMissingStatus.isEmpty() && itemsMissingMandatoryPhoto.isEmpty() &&
            roomsMissingInstance.isEmpty() && itemsWithInvalidStatus.isEmpty()
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
 * [CompletenessPort] 的 DB 直查默认实现。判定三件事：
 *
 * 0. **缺房间实例**：模板里存在至少一个未被抑制项的房间键，却在 `room_instance` 里一行都找不到——
 *    这整间房从未被建过、更谈不上"走查完了"。房间实例化是 `core/capture`（`InspectionRepository`）
 *    在建巡检时一次性做完的事件，此后没有任何写路径会再补建/校验它——不像"缺状态"判定天然覆盖"这项
 *    压根没答"（逐项判定按模板定义驱动，答题表里没有对应行就是缺），"这间房压根没建"如果只按
 *    `room_instance` 现有的行去推它自己"该有哪些房间"，就是拿现状去验证现状（同一份数据既当输入
 *    又当基准，circular），任何缺失的房间都会被这份自证悄悄放过。finalize 是"没有遗漏"这个承诺的
 *    最后一道闸，故在此独立对照模板重新推导"应该有哪些房间"，不信任上游已经建好。
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
 * **"至少一个 room_instance" 是当前语义的完整形态，不是简化**：`repeatable`（同一房间键多实例，如两间
 * 卧室）标记已被人裁排除在模板 JSON 之外（`T1-TEMPLATE-ENGINE` non_goals，见 `T2-ROOM-REPEATABLE`），
 * 故当前每个模板房间键至多对应一个 `room_instance`——"≥1"与"恰好 1"在今天等价。`T2-ROOM-REPEATABLE`
 * 落地、房间键可对应多实例后，这条检查须重新评估：那时"缺房间"可能要变成"数量不足"（对照物业房型数），
 * 而不再是单纯的"存在性"判定。
 *
 * **`ADVERSE_ONLY` 分类器本身必须完备（fail-closed），这不是"重新校验状态合法性"的第二道闸**：
 * 状态字符串合不合法（是否落在 `TemplateDomains.allowedStatusesFor(type)` 域内）由 `core/capture` 的
 * 写入口（`setItemStatus`，落实 `T1-TEMPLATE-ENGINE` 的评级域契约）在唯一铸造点把关，本类不重复这道闸
 * （同 `L220`：不变量活在铸造点，不在下游层层复查）。但"不利发现"分类是本类**自己**运行的判定——分类器
 * 若对一个不在冻结域内的状态字符串默默判"非不利"，那就是分类器本身漏判成 complete（fail-open），
 * 与"漏建的房间被现状自证悄悄放过"是同一类缺陷，不是要不要重开合法性闸的问题。故域外状态归入
 * [CompletenessResult.itemsWithInvalidStatus]，域直接取自 [TemplateDomains]（与
 * `DbCompletenessCheckerTest` 的分类表同一个真相源，不会各说各话）。
 *
 * 四类问题分别用 [MissingItem]/[MissingItem]/房间键字符串/[MissingItem] 报告，UI 侧可以统一渲染成
 * "哪几项 + 哪几间房还没做完"。
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

        // 模板要求（至少一项未被抑制）却一个 room_instance 都没有的房间键——独立对照模板算出"应有哪些
        // 房间"，不从 room_instance 现有的行反推，否则漏建的房间永远不会被这份判定看见。
        val instantiatedRoomKeys = roomInstances.mapTo(mutableSetOf()) { it.room_key }
        val roomsMissingInstance = checkItemDefs
            .filter { it.stable_id !in suppressedStableIds }
            .map { it.room }
            .toSortedSet()
            .filterNot { it in instantiatedRoomKeys }

        val existingItems = database.inspectionItemQueries.selectByInspection(inspectionId).executeAsList()
        val existingByKey = existingItems.associateBy { it.room_instance_id to it.stable_id }

        // 每个房间实例只查一次自己的照片，供房间级/项目级两条规则复用。
        val photosByRoom = roomInstances.associate { room ->
            room.id to database.photoQueries.selectByRoomInstance(room.id).executeAsList()
        }

        val missingStatus = mutableListOf<MissingItem>()
        val missingPhoto = mutableListOf<MissingItem>()
        val invalidStatus = mutableListOf<MissingItem>()

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
                if (def.photo_rule == "ADVERSE_ONLY") {
                    when (classifyAdverseness(inspection.type, existing.status)) {
                        Adverseness.UNCLASSIFIABLE -> invalidStatus += MissingItem(room.id, def.stable_id)
                        Adverseness.ADVERSE -> {
                            val hasItemPhoto = roomPhotos.any { it.inspection_item_id == existing.id }
                            if (!hasItemPhoto) missingPhoto += MissingItem(room.id, def.stable_id)
                        }
                        Adverseness.NOT_ADVERSE -> Unit
                    }
                }
            }
        }

        return CompletenessResult(
            itemsMissingStatus = missingStatus,
            itemsMissingMandatoryPhoto = missingPhoto,
            roomsMissingInstance = roomsMissingInstance,
            itemsWithInvalidStatus = invalidStatus,
        )
    }

    private enum class Adverseness { ADVERSE, NOT_ADVERSE, UNCLASSIFIABLE }

    private companion object {
        // 租赁四档（nz.myinspection.core.template.TemplateDomains.RENTAL_STATUSES）的不利发现 =
        // FAIR/POOR；年检五态（ANNUAL_STATUSES）的不利发现 = MONITOR/MAINTENANCE_ITEM/
        // SIGNIFICANT_DEFECT。NOT_APPLICABLE 与 GOOD/NO_ISSUE 不逼拍，不在这两个集合里——两个域的
        // 完整划分（这两个集合 ∪ 各自的非不利子集 == 冻结域）由 DbCompletenessCheckerTest 断言钉死。
        val RENTAL_ADVERSE = setOf("FAIR", "POOR")
        val ANNUAL_ADVERSE = setOf("MONITOR", "MAINTENANCE_ITEM", "SIGNIFICANT_DEFECT")

        /**
         * 域直接取自冻结的 [TemplateDomains]（与分类表测试同一个真相源，不手抄一份副本）：状态不在该
         * 巡检类型的合法域内——不管是 core/capture 的铸造闸出于某种原因没拦住，还是数据被绕过谓词直连
         * SQL 腐坏——分类器都不得默默判"非不利"，必须显式报告分类不出来。
         */
        fun classifyAdverseness(inspectionType: String, status: String): Adverseness {
            val domain = if (inspectionType == "ANNUAL") TemplateDomains.ANNUAL_STATUSES else TemplateDomains.RENTAL_STATUSES
            if (status !in domain) return Adverseness.UNCLASSIFIABLE
            val adverseSet = if (inspectionType == "ANNUAL") ANNUAL_ADVERSE else RENTAL_ADVERSE
            return if (status in adverseSet) Adverseness.ADVERSE else Adverseness.NOT_ADVERSE
        }
    }
}
