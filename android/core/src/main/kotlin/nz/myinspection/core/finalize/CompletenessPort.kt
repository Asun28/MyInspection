package nz.myinspection.core.finalize

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import nz.myinspection.core.capture.ItemDef
import nz.myinspection.core.capture.RecordedItem
import nz.myinspection.core.capture.RoomSnapshot
import nz.myinspection.core.capture.computeMissingNotes
import nz.myinspection.core.capture.computeMissingPhotos
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
 * 从未被走查过，是"缺项"里最强的一种。[itemsMissingNote]：不利发现却备注空白（`core/capture` 的
 * `computeMissingNotes`，需求 §5「不利发现强制备注」）。[itemsWithInvalidStatus]：`ADVERSE_ONLY`/
 * 不利发现分类遇到一个不在冻结域内的评级字符串——分类不出来就不能悄悄当成"非不利"放行（见
 * [DbCompletenessChecker] 顶部说明）。三者默认空列表，方便只关心逐项判定的调用方（如测试里的假
 * `CompletenessPort`）不必每次都显式传。
 */
data class CompletenessResult(
    val itemsMissingStatus: List<MissingItem>,
    val itemsMissingMandatoryPhoto: List<MissingItem>,
    val roomsMissingInstance: List<String> = emptyList(),
    val itemsWithInvalidStatus: List<MissingItem> = emptyList(),
    val itemsMissingNote: List<MissingItem> = emptyList(),
) {
    val isComplete: Boolean
        get() = itemsMissingStatus.isEmpty() && itemsMissingMandatoryPhoto.isEmpty() &&
            roomsMissingInstance.isEmpty() && itemsWithInvalidStatus.isEmpty() && itemsMissingNote.isEmpty()
}

/**
 * finalize 前置的完备性校验端口。`FinalizeInspectionUseCase` 只依赖这个接口——调用方以后要换实现
 * （比如 `:app` 装配层想接一个带缓存/不同数据源的版本）不用改 finalize 内部任何一行。
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
 * [CompletenessPort] 的默认实现——`core/capture`（`T2-CAPTURE-CORE`）权威完备性函数的**适配器**，
 * 组合 finalize 自己才拥有的两项检查。判定五件事：
 *
 * 0. **缺房间实例**（finalize 自有，capture 不管）：模板里存在至少一个未被抑制项的房间键，却在
 *    `room_instance` 里一行都找不到——`core/capture`（`InspectionRepository`）在建巡检时按模板房间键
 *    实例化 `room_instance`，且在恢复一个此前被抑制、DRAFT 期间又被取消抑制的项时会补建缺失的房间
 *    （`ensureRoomInstancesForRestoredItem`）；但这条补建路径只覆盖"抑制→取消抑制"这一种场景，不覆盖
 *    "房间实例化环节本身有 bug 或被绕过"这类更一般的缺失——finalize 不能假设自己面对的巡检一定走过这条
 *    补建路径。若只按 `room_instance` 现有的行反推"该有哪些房间"，就是拿现状验证现状（circular），
 *    漏建的房间会被悄悄放过。finalize 是"没有遗漏"这个承诺的最后一道闸，故独立对照模板重新推导。
 * 1. **缺状态**（finalize 自有，结构性判定，无需委派）：模板里该房间类型下所有未被抑制的项，若
 *    `inspection_item` 里还没有对应行——该项还没有人作答。这不是一条"规则"，只是"答题表里有没有
 *    这一行"，`core/capture` 也没有为它单独暴露一个清单函数（[nz.myinspection.core.capture.RoomProgress]
 *    只给计数不给逐项清单）——故直接对着喂给 capture 函数的**同一份** [RoomSnapshot] 判定，词汇仍是
 *    capture 的（`recordedItems` 有没有这个 key），不是自造一套。
 * 2. **缺强制照片**——委派给 [nz.myinspection.core.capture.computeMissingPhotos]（房间级 ROOM_PANORAMA
 *    + 项目级 ADVERSE_ONLY 两级规则，权威定义与"不利发现"分类域都在 capture）。
 * 3. **不利发现缺备注**——委派给 [nz.myinspection.core.capture.computeMissingNotes]（需求 §5「不利发现
 *    强制备注」，与两级拍照规则各自独立生效，权威同样在 capture）。
 * 4. **不利发现分类器本身必须完备（fail-closed），这不是"重新校验状态合法性"的第二道闸**：状态字符串
 *    合不合法（是否落在 `TemplateDomains.allowedStatusesFor(type)` 域内）由 `core/capture` 的写入口
 *    （`setItemStatus`，落实 `T1-TEMPLATE-ENGINE` 的评级域契约）在唯一铸造点把关，本类不重复这道闸
 *    （同 `L220`：不变量活在铸造点，不在下游层层复查）。但 `computeMissingPhotos`/`computeMissingNotes`
 *    内部用的 `AdverseStatuses.isAdverse` 对域外状态同样静默返回"非不利"（那是 capture 自己的既定行为、
 *    不是本类能改的），若数据经直连 SQL 绕过铸造点腐坏成域外状态，委派给 capture 的那两项判定会对**那
 *    一项**误判"完成"——但 finalize 作为最后一道闸，独立再判一次"这个状态到底在不在域里"，域外即报
 *    [CompletenessResult.itemsWithInvalidStatus]、`isComplete` 整体仍为 false，不依赖 capture 那两个
 *    函数对同一项的（对它们自己而言正确、但对 finalize 不够）判断。域直接取自 [TemplateDomains]（与
 *    `DbCompletenessCheckerTest` 的分类表同一个真相源，不会各说各话）。
 *
 * 五类问题分别用 [MissingItem]/[MissingItem]/房间键字符串/[MissingItem]/[MissingItem] 报告，UI 侧可以
 * 统一渲染。
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
        val itemIdToStableId = existingItems.associate { it.id to it.stable_id }

        // 每个房间实例只查一次自己的照片，供 capture 的房间级/项目级两条规则复用。
        val photosByRoom = roomInstances.associate { room ->
            room.id to database.photoQueries.selectByRoomInstance(room.id).executeAsList()
        }

        val missingStatus = mutableListOf<MissingItem>()
        val invalidStatus = mutableListOf<MissingItem>()
        val roomSnapshots = mutableListOf<RoomSnapshot>()

        for (room in roomInstances) {
            val applicableDefs = checkItemDefs.filter { it.room == room.room_key && it.stable_id !in suppressedStableIds }
            val roomPhotos = photosByRoom[room.id].orEmpty()

            val itemDefs = applicableDefs.map { def ->
                ItemDef(stableId = def.stable_id, photoRule = def.photo_rule, allowedStatuses = decodeAllowedStatuses(def.allowed_statuses))
            }
            val recordedItems = mutableMapOf<String, RecordedItem>()
            for (def in applicableDefs) {
                val existing = existingByKey[room.id to def.stable_id]
                if (existing == null) {
                    missingStatus += MissingItem(room.id, def.stable_id)
                    continue
                }
                recordedItems[def.stable_id] = RecordedItem(status = existing.status, note = existing.note)
                // 不利发现分类器的完备性是 finalize 自己的闸，独立于委派给 capture 的判定跑一遍（见类
                // 顶部说明第 4 条）——不管 capture 那两个函数对这一项会判成什么，域外状态照样单独报出。
                if (classifyAdverseness(inspection.type, existing.status) == Adverseness.UNCLASSIFIABLE) {
                    invalidStatus += MissingItem(room.id, def.stable_id)
                }
            }
            val roomPhotoCount = roomPhotos.count { it.inspection_item_id == null }
            val itemPhotoCounts = roomPhotos.mapNotNull { it.inspection_item_id }
                .mapNotNull { itemIdToStableId[it] }
                .groupingBy { it }
                .eachCount()

            roomSnapshots += RoomSnapshot(
                roomInstanceId = room.id,
                roomKey = room.room_key,
                displayLabel = room.display_label,
                items = itemDefs,
                recordedItems = recordedItems,
                roomPhotoCount = roomPhotoCount,
                itemPhotoCounts = itemPhotoCounts,
            )
        }

        // 委派：房间级/项目级拍照完备性权威在 capture。ROOM_PANORAMA 缺口 capture 按房间**实例**粒度报
        // （MissingRoomPhoto 带 roomInstanceId，不只是 roomKey），本类沿用既有的逐项报告粒度——把"这个
        // 房间实例缺全景"展开成该实例下每一条 ROOM_PANORAMA 项定义各一条 MissingItem，判定本身（缺不缺）
        // 仍是 capture 算出来的，这里只做输出粒度的映射，不是重新判定。**必须按 roomInstanceId 匹配、
        // 不能退化成按 room_key 匹配**：同一 room_key 今天已可对应多个 room_instance（唯一索引是
        // `(inspection_id, room_key, instance_no)`，不是单纯 `room_key`——T2-ROOM-REPEATABLE 落地前，
        // 现有 mint 点固定写 `instance_no = 1` 故实际不会出现，但索引本身已允许），按 room_key 匹配会把
        // "只有其中一个实例缺全景"误报成"两个实例都缺"。
        val photoCompleteness = computeMissingPhotos(inspection.type, roomSnapshots)
        val missingPanoramaRoomInstanceIds = photoCompleteness.missingRoomPanoramas.mapTo(mutableSetOf()) { it.roomInstanceId }
        val missingPhoto = mutableListOf<MissingItem>()
        for (room in roomInstances) {
            if (room.id !in missingPanoramaRoomInstanceIds) continue
            checkItemDefs.filter { it.room == room.room_key && it.stable_id !in suppressedStableIds && it.photo_rule == "ROOM_PANORAMA" }
                .forEach { missingPhoto += MissingItem(room.id, it.stable_id) }
        }
        photoCompleteness.missingItemPhotos.forEach { missingPhoto += MissingItem(it.roomInstanceId, it.stableId) }

        // 委派：不利发现强制备注权威也在 capture（需求 §5）。
        val missingNote = computeMissingNotes(inspection.type, roomSnapshots).map { MissingItem(it.roomInstanceId, it.stableId) }

        return CompletenessResult(
            itemsMissingStatus = missingStatus,
            itemsMissingMandatoryPhoto = missingPhoto,
            roomsMissingInstance = roomsMissingInstance,
            itemsWithInvalidStatus = invalidStatus,
            itemsMissingNote = missingNote,
        )
    }

    private enum class Adverseness { ADVERSE, NOT_ADVERSE, UNCLASSIFIABLE }

    private companion object {
        private val ALLOWED_STATUSES_SERIALIZER = ListSerializer(String.serializer())

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

        /** `check_item_def.allowed_statuses` 的编码同 `TemplateStore`：JSON 字符串数组。 */
        fun decodeAllowedStatuses(raw: String): List<String> = Json.decodeFromString(ALLOWED_STATUSES_SERIALIZER, raw)
    }
}
