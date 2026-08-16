package nz.myinspection.core.capture

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.Inspection
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.SystemClockMs
import nz.myinspection.core.db.Tenancy
import nz.myinspection.core.db.Uuid7Generator

/** `wear_or_damage` 的封闭域，与 `inspection_item.wear_or_damage` 的 CHECK 同集。 */
private val WEAR_OR_DAMAGE_VALUES = setOf("FAIR_WEAR", "DAMAGE", "UNDETERMINED")

/** 建巡检时基线未能解析的原因（UI 据此渲染「无基线」标记，需求 §6）。 */
enum class NoBaselineReason {
    /** 自住物业（无 tenancy）——基线概念不适用。 */
    NO_TENANCY,
    /** tenancy 存在，但其 `baseline_inspection_id` 尚未指定（还没有 Ingoing，或 Ingoing 尚未被指定为基线）。 */
    NO_INGOING,
}

/** `createInspection` 的产出：新巡检 id + 双轨引用解析结果 + 本次实例化出的房间列表。 */
data class CreatedInspection(
    val inspectionId: String,
    val previousInspectionId: String?,
    val baselineInspectionId: String?,
    val noBaselineReason: NoBaselineReason?,
    val roomInstanceIds: List<String>,
)

/** `setWearOrDamage` 的写入结果——除 [Written] 外的三态都是合法的"拒写"，不是异常。 */
sealed class WearOrDamageOutcome {
    /** 已写入。 */
    object Written : WearOrDamageOutcome()

    /** 该巡检不是 EXIT 类型——`wear_or_damage` 仅 EXIT 可写（需求 §6 / 卡片正文）。 */
    object NotExitType : WearOrDamageOutcome()

    /** 该巡检没有可用基线（tenancy 无、或 tenancy 尚未指定 baseline_inspection_id）。 */
    object NoBaseline : WearOrDamageOutcome()

    /** 基线巡检里没有同 stable_id 的已记录项——差异无从计算。 */
    object NoBaselineItem : WearOrDamageOutcome()

    /** 当前状态与基线状态相同——没有差异，`wear_or_damage` 不适用（卡片正文「有差异时可写」）。 */
    object NoDifference : WearOrDamageOutcome()
}

/**
 * 采集领域核：巡检生命周期（建 → 逐项置状态/备注 → 房间粒度落库）+ 走查进度 + 两级拍照/备注完备性 +
 * 双轨基线解析 + 物业级条目抑制。**无状态**——不缓存任何巡检态，每次调用现查 DB；这既是
 * "进程死亡恢复"这条不变量的实现手段，也是它成立的理由（新建一个仓储实例即等价于恢复：本就没有
 * 进程内状态要恢复）。
 *
 * **房间实例化的当前范围**：模板契约里的 `repeatable` 房间标记 + 物业房型数尚未落地——那是
 * `specs/tasks/T2-ROOM-REPEATABLE.md` 的产出，其 **own** `non_goals` 明文写着「采集期真正实例化
 * room_instance 的状态机（T2-CAPTURE-CORE 消费本卡产出）」，即由**那张卡**先把 `repeatable` 标记
 * 落进模板 JSON + schema（须先过版本评审，动的是本卡 `allow_paths` 之外、已冻结的
 * `android/core/src/main/sqldelight/` 与 `template/Template.kt`），本卡再消费。在其落地前，
 * 本仓储按模板 `check_item_def.room` 出现的**每个不同房间键实例化一个 room_instance**
 * （`instance_no = 1`，`display_label` = 房间键本身），即"单例房间"这一子集——多卧室/多卫生间的
 * 多实例支持留给 T2-ROOM-REPEATABLE 落地后的后续演进，不在**本卡** `dod_assert` 范围内。
 */
class InspectionRepository(
    private val db: MyInspectionDatabase,
    private val uuid: Uuid7Generator = Uuid7Generator(),
    private val clock: ClockMs = SystemClockMs,
) {
    /**
     * 建一次新巡检：校验入参的逻辑父行确实存在且互相一致 → 解析 previous/baseline 双轨引用 → 落
     * `inspection` 行 → 按模板房间键（排除本物业当前 suppressed 的项后）实例化 `room_instance`。
     * 单事务——半份巡检（有巡检行、房间缺几间）比整体失败更坏。
     *
     * **入参校验先于任何写入**（Codex R3 round 1 抓到的真缺陷）：property/template_version/tenancy
     * 都是逻辑外键（schema 不强制），一个拿错的 id 若不拦，要么静默造出零房间的空壳巡检
     * （模板 id 不存在——`activeRoomKeysInOrder` 查不到任何项定义，`WalkProgress.isComplete` 会因
     * "没有房间等于全部房间都完成"而**空洞为真**），要么把一条悬空引用写进 `inspection.tenancy_id`
     * （tenancy id 不存在——后续按这条巡检反查 tenancy 会失败，且 [resolveBaseline] 会把"调用方传错 id"
     * 误判成"这处物业没有基线"这一正常业务态）。`template_version.type` 与入参 `type` 不一致同样必须
     * 拦——否则用 EXIT 的评级域校验 ROUTINE 巡检的项，或反之，状态合法性检查会判错。
     *
     * 不预先创建 `inspection_item` 行：项目行在 [setItemStatus] 首次写入时才产生（`inspection_item.status`
     * 是 NOT NULL 列，创建时没有值可填；走查进度改用"该房间应有项数 vs 已记录项数"的计数比较，见
     * [computeRoomProgress]），这样也天然满足"进程死亡恢复"——没有中间态需要另外持久化。
     */
    fun createInspection(
        type: String,
        propertyId: String,
        tenancyId: String?,
        templateVersionId: String,
        scheduledAt: Long,
    ): CreatedInspection {
        checkNotNull(db.propertyQueries.selectById(propertyId).executeAsOneOrNull()) {
            "no such property: $propertyId"
        }
        val templateVersion = checkNotNull(db.templateVersionQueries.selectById(templateVersionId).executeAsOneOrNull()) {
            "no such template version: $templateVersionId"
        }
        require(templateVersion.type == type) {
            "template version $templateVersionId is type '${templateVersion.type}', not '$type'"
        }
        val tenancy = tenancyId?.let {
            checkNotNull(db.tenancyQueries.selectById(it).executeAsOneOrNull()) { "no such tenancy: $it" }
        }

        val now = clock.nowMs()
        val previousId = resolvePrevious(propertyId, type, scheduledAt)
        val baseline = resolveBaseline(tenancy)
        val inspectionId = uuid.next()
        val roomKeys = activeRoomKeysInOrder(propertyId, templateVersionId)
        val roomInstanceIds = mutableListOf<String>()

        db.transaction {
            db.inspectionQueries.insert(
                id = inspectionId,
                type = type,
                property_id = propertyId,
                tenancy_id = tenancyId,
                template_version_id = templateVersionId,
                scheduled_at = scheduledAt,
                previous_inspection_id = previousId,
                baseline_inspection_id = baseline.baselineInspectionId,
                status = "DRAFT",
                finalized_at = null,
                data_hash = null,
                created_at = now,
                updated_at = now,
            )
            roomKeys.forEach { roomKey ->
                val roomInstanceId = uuid.next()
                val affected = db.roomInstanceQueries.insert(
                    id = roomInstanceId,
                    inspection_id = inspectionId,
                    room_key = roomKey,
                    instance_no = 1,
                    display_label = roomKey,
                    created_at = now,
                    updated_at = now,
                ).value
                check(affected == 1L) {
                    "room_instance insert affected $affected rows for room '$roomKey' (guard rejected the write)"
                }
                roomInstanceIds.add(roomInstanceId)
            }
        }

        return CreatedInspection(
            inspectionId = inspectionId,
            previousInspectionId = previousId,
            baselineInspectionId = baseline.baselineInspectionId,
            noBaselineReason = baseline.noBaselineReason,
            roomInstanceIds = roomInstanceIds,
        )
    }

    /**
     * 置某项当前状态/备注（房间粒度自动保存——每次调用即落库一行，没有额外的"提交房间"步骤）。
     * 首次写入该 (room_instance, stable_id) 时新建行，此后原地更新（唯一活跃索引保证不会重复）。
     *
     * 状态合法性按**该项自己的** `allowedStatuses`（模板加载期已校验为其模板类型域的子集，见
     * T1-TEMPLATE-ENGINE）——比只判类型域更精确。非法状态/未知 (inspection/项) 是调用方错误，
     * 用 [require]/[checkNotNull] 当场炸；已 FINALIZED 巡检上的写入被 SQL 层 `finalized_at IS NULL`
     * 守卫拒绝（0 行），[check] 把它转成显式失败，不静默吞掉。
     *
     * **两条额外的调用方契约校验**（Codex R3 round 1 抓到的真缺陷——冻结的 `inspection_item.insert`
     * 只守「同一巡检」，不守下面两条）：① [roomInstanceId] 必须真的属于 [stableId] 定义所在的房间键
     * （`def.room`），否则一个传错的 room_instance_id 能把条目写进错误的房间，完备性/进度计算会静默算错；
     * ② [stableId] 不得是本物业**当前**被抑制的项——被抑制的项不出现在任何完备性查询里（见
     * `activeCheckItemDefs`），写入它只会产生一条谁都看不见、却仍占着 id 的幽灵记录。
     */
    fun setItemStatus(inspectionId: String, roomInstanceId: String, stableId: String, status: String, note: String?) {
        val now = clock.nowMs()
        val inspection = checkNotNull(db.inspectionQueries.selectById(inspectionId).executeAsOneOrNull()) {
            "no such inspection: $inspectionId"
        }
        val def = checkNotNull(
            db.checkItemDefQueries.selectByTemplateVersion(inspection.template_version_id).executeAsList()
                .find { it.stable_id == stableId },
        ) { "stable_id '$stableId' not defined in template version ${inspection.template_version_id}" }
        require(stableId !in suppressedStableIds(inspection.property_id)) {
            "stable_id '$stableId' is currently suppressed for property ${inspection.property_id}"
        }
        val roomInstance = checkNotNull(db.roomInstanceQueries.selectById(roomInstanceId).executeAsOneOrNull()) {
            "no such room_instance: $roomInstanceId"
        }
        require(roomInstance.room_key == def.room) {
            "room_instance $roomInstanceId is '${roomInstance.room_key}', but '$stableId' belongs to room '${def.room}'"
        }
        val allowed = Json.decodeFromString(STATUSES, def.allowed_statuses)
        require(status in allowed) { "status '$status' not allowed for '$stableId' (allowed: $allowed)" }

        val existing = db.inspectionItemQueries.selectByInspection(inspectionId).executeAsList()
            .find { it.room_instance_id == roomInstanceId && it.stable_id == stableId }

        val affected = if (existing != null) {
            val statusAffected = db.inspectionItemQueries.updateStatusIfDraft(
                status = status, note = note, updated_at = now, id = existing.id,
            ).value
            check(statusAffected == 1L) {
                "inspection_item write affected $statusAffected rows for '$stableId' (finalize guard rejected the write)"
            }
            // 状态一变，此前记录的 wear_or_damage 分类即失效——它的合法性绑定在"此刻与基线有差异"这一
            // 判断上（见 setWearOrDamage），状态变了这个判断要重新做，不能让旧分类悄悄挂着（Codex R3
            // round 1 抓到的真缺陷：写差异分类 → 状态改回与基线一致 → wear_or_damage 仍停在旧值）。
            // 用已存在的 updateWearOrDamageIfDraft 清空即可，无需新增查询（冻结面不可改）。
            db.inspectionItemQueries.updateWearOrDamageIfDraft(wear_or_damage = null, updated_at = now, id = existing.id).value
        } else {
            db.inspectionItemQueries.insert(
                id = uuid.next(), inspection_id = inspectionId, room_instance_id = roomInstanceId,
                stable_id = stableId, status = status, note = note, wear_or_damage = null,
                created_at = now, updated_at = now,
            ).value
        }
        check(affected == 1L) {
            "inspection_item write affected $affected rows for '$stableId' (finalize guard rejected the write)"
        }
    }

    /**
     * 置 `wear_or_damage`：仅 EXIT 巡检、且该项状态与基线巡检里同 stable_id 的状态有差异时才允许写
     * （差异按 stable_id 对齐比较 status，卡片正文已定）。四种非成功态都是合法结果，不是异常——
     * 调用方（UI）据此渲染相应提示，不需要 try/catch。
     *
     * [itemId] 必须真的属于 [inspectionId]（Codex R3 round 1 抓到的真缺陷：两者此前各自独立解析，
     * 从未互相核对——调用方能拿一个属于**另一次**巡检的 itemId，搭一个恰好是 EXIT 且有基线的
     * inspectionId，把 wear_or_damage 写进一条毫不相干的条目，且该条目所属巡检自己的 finalize 守卫
     * 与本次判断完全脱钩）。
     */
    fun setWearOrDamage(inspectionId: String, itemId: String, wearOrDamage: String): WearOrDamageOutcome {
        require(wearOrDamage in WEAR_OR_DAMAGE_VALUES) { "unknown wear_or_damage value: '$wearOrDamage'" }
        val now = clock.nowMs()
        val inspection = checkNotNull(db.inspectionQueries.selectById(inspectionId).executeAsOneOrNull()) {
            "no such inspection: $inspectionId"
        }
        if (inspection.type != "EXIT") return WearOrDamageOutcome.NotExitType
        val baselineId = inspection.baseline_inspection_id ?: return WearOrDamageOutcome.NoBaseline

        val item = checkNotNull(db.inspectionItemQueries.selectById(itemId).executeAsOneOrNull()) {
            "no such inspection_item: $itemId"
        }
        require(item.inspection_id == inspectionId) {
            "inspection_item $itemId belongs to inspection ${item.inspection_id}, not $inspectionId"
        }
        val baselineItem = db.inspectionItemQueries.selectByInspection(baselineId).executeAsList()
            .find { it.stable_id == item.stable_id } ?: return WearOrDamageOutcome.NoBaselineItem
        if (baselineItem.status == item.status) return WearOrDamageOutcome.NoDifference

        val affected = db.inspectionItemQueries.updateWearOrDamageIfDraft(
            wear_or_damage = wearOrDamage, updated_at = now, id = itemId,
        ).value
        check(affected == 1L) {
            "wear_or_damage write affected $affected rows for $itemId (finalize guard rejected the write)"
        }
        return WearOrDamageOutcome.Written
    }

    /**
     * 物业级条目抑制/恢复（「本物业不存在此项」，跨巡检永久生效直到显式恢复）。恢复 = 置 suppressed=0，
     * 不是软删这一行——见 PropertyItemOverride.sq 的同名注释，该状态本就可逆。
     */
    fun setItemSuppression(propertyId: String, stableId: String, suppressed: Boolean) {
        val now = clock.nowMs()
        val existing = db.propertyItemOverrideQueries.selectByPropertyAndStableId(propertyId, stableId).executeAsOneOrNull()
        if (existing != null) {
            db.propertyItemOverrideQueries.setSuppressed(
                suppressed = if (suppressed) 1L else 0L, updated_at = now, id = existing.id,
            )
        } else {
            db.propertyItemOverrideQueries.insert(
                id = uuid.next(), property_id = propertyId, stable_id = stableId,
                suppressed = if (suppressed) 1L else 0L, created_at = now, updated_at = now,
            )
        }
    }

    /** 整次巡检的走查进度（房间粒度）。无状态查询——process-death 恢复即"再查一次"。 */
    fun walkProgress(inspectionId: String): WalkProgress {
        val inspection = checkNotNull(db.inspectionQueries.selectById(inspectionId).executeAsOneOrNull()) {
            "no such inspection: $inspectionId"
        }
        val rooms = loadRoomSnapshots(inspection)
        return WalkProgress(inspectionId, rooms.map { computeRoomProgress(it) })
    }

    /** 两级拍照完备性：哪些房间还缺全景照、哪些不利发现项还缺项目照。 */
    fun missingPhotos(inspectionId: String): PhotoCompleteness {
        val inspection = checkNotNull(db.inspectionQueries.selectById(inspectionId).executeAsOneOrNull()) {
            "no such inspection: $inspectionId"
        }
        return computeMissingPhotos(inspection.type, loadRoomSnapshots(inspection))
    }

    /** 不利发现强制备注的缺口。 */
    fun missingNotes(inspectionId: String): List<MissingNote> {
        val inspection = checkNotNull(db.inspectionQueries.selectById(inspectionId).executeAsOneOrNull()) {
            "no such inspection: $inspectionId"
        }
        return computeMissingNotes(inspection.type, loadRoomSnapshots(inspection))
    }

    /**
     * 该模板版本里、本物业当前**未被抑制**的项，按房间键**首次出现的模板序**去重后的房间键列表。
     * `check_item_def.selectByTemplateVersion` 已按 (sort, id) 排好模板序，`LinkedHashSet` 保留这个
     * 首次出现顺序。
     */
    private fun activeRoomKeysInOrder(propertyId: String, templateVersionId: String): List<String> {
        val defs = activeCheckItemDefs(propertyId, templateVersionId)
        return LinkedHashSet(defs.map { it.room }).toList()
    }

    /** 本物业当前 suppressed=1 的 stable_id 集合。 */
    private fun suppressedStableIds(propertyId: String): Set<String> =
        db.propertyItemOverrideQueries.selectByProperty(propertyId).executeAsList()
            .filter { it.suppressed == 1L }
            .map { it.stable_id }
            .toSet()

    /** 该模板版本的项定义，排除本物业当前被抑制的（「完备性查询天然不含被抑制项」，卡片正文）。 */
    private fun activeCheckItemDefs(propertyId: String, templateVersionId: String) =
        db.checkItemDefQueries.selectByTemplateVersion(templateVersionId).executeAsList()
            .filterNot { it.stable_id in suppressedStableIds(propertyId) }

    /**
     * `room_instance.selectByInspection`（冻结查询）没有 ORDER BY，返回顺序由 SQLite 的查询计划决定
     * （实测：常走 `idx_room_instance_active`，按 room_key 字典序而非插入/模板序）——[WalkProgress.rooms]
     * 因此**不承诺**模板序。需要模板序时用 [CreatedInspection.roomInstanceIds]（仓储在内存里按模板序
     * 组装的那份）。
     */
    private fun loadRoomSnapshots(inspection: Inspection): List<RoomSnapshot> {
        val defsByRoom = activeCheckItemDefs(inspection.property_id, inspection.template_version_id)
            .groupBy { it.room }
        val roomInstances = db.roomInstanceQueries.selectByInspection(inspection.id).executeAsList()
        val items = db.inspectionItemQueries.selectByInspection(inspection.id).executeAsList()

        return roomInstances.mapNotNull { ri ->
            val roomDefs = defsByRoom[ri.room_key] ?: return@mapNotNull null
            val roomItems = items.filter { it.room_instance_id == ri.id }
            val recordedItems = roomItems.associate { it.stable_id to RecordedItem(it.status, it.note) }
            val itemIdToStableId = roomItems.associate { it.id to it.stable_id }

            val roomPhotos = db.photoQueries.selectByRoomInstance(ri.id).executeAsList()
            val roomPhotoCount = roomPhotos.count { it.inspection_item_id == null }
            val itemPhotoCounts = roomPhotos
                .mapNotNull { p -> p.inspection_item_id?.let { itemIdToStableId[it] } }
                .groupingBy { it }
                .eachCount()

            RoomSnapshot(
                roomInstanceId = ri.id,
                roomKey = ri.room_key,
                displayLabel = ri.display_label,
                items = roomDefs.map { ItemDef(it.stable_id, it.photo_rule, Json.decodeFromString(STATUSES, it.allowed_statuses)) },
                recordedItems = recordedItems,
                roomPhotoCount = roomPhotoCount,
                itemPhotoCounts = itemPhotoCounts,
            )
        }
    }

    /** previous_inspection = 同物业、同类型、时间上严格前一次**已 FINALIZED** 的巡检（ANNUAL 同规则=上次年检）。 */
    private fun resolvePrevious(propertyId: String, type: String, scheduledAt: Long): String? =
        db.inspectionQueries.selectActive().executeAsList()
            .asSequence()
            .filter { it.property_id == propertyId && it.type == type && it.status == "FINALIZED" && it.scheduled_at < scheduledAt }
            .maxWithOrNull(compareBy({ it.scheduled_at }, { it.id }))
            ?.id

    /**
     * baseline_inspection = 该 tenancy 的权威基线指针（`tenancy.baseline_inspection_id`），历史事实、
     * 写死不改。收**已解析的行**而非 id——tenancy 是否存在是 [createInspection] 的入参校验职责
     * （调用方传错 id 是调用方错误，须当场炸，不是"无基线"这一合法业务态），本函数只负责从一个
     * **已知存在**的 tenancy（或没有 tenancy）里读出基线指针。
     */
    private fun resolveBaseline(tenancy: Tenancy?): BaselineResolution {
        if (tenancy == null) return BaselineResolution(null, NoBaselineReason.NO_TENANCY)
        val baselineId = tenancy.baseline_inspection_id
        return if (baselineId != null) {
            BaselineResolution(baselineId, null)
        } else {
            BaselineResolution(null, NoBaselineReason.NO_INGOING)
        }
    }

    private data class BaselineResolution(val baselineInspectionId: String?, val noBaselineReason: NoBaselineReason?)

    private companion object {
        val STATUSES = ListSerializer(String.serializer())
    }
}
