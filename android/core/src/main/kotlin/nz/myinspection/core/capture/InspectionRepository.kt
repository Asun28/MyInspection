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

    /** 该巡检没有可用基线（tenancy 无、tenancy 尚未指定 baseline_inspection_id、或基线巡检尚未 FINALIZED）。 */
    object NoBaseline : WearOrDamageOutcome()

    /** 基线巡检里没有同 `(room_key, instance_no, stable_id)` 的已记录项——差异无从计算。 */
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
 * 房间实例化由模板房间序、`repeatable` 标记与物业级持久化数量共同决定；缺少物业配置时数量默认为 1，
 * 因而旧物业与不含 `template_room_def` 的旧模板保持原有单例行为。重复房间的稳定身份是
 * `(room_key, instance_no)`，不能从数据库自然行序推断。
 *
 * **本仓储只有一个数据库连接**：每个公开方法内部的读（判断依据）与写都包在同一个 `db.transaction{}` 里，
 * 这样该方法对同连接上的其它调用而言是不可分割的一步——判断依据与写入结果保证出自同一个数据库状态。
 */
class InspectionRepository(
    private val db: MyInspectionDatabase,
    private val uuid: Uuid7Generator = Uuid7Generator(),
    private val clock: ClockMs = SystemClockMs,
) {
    /** 持久化活跃 repeatable 房间的 1..99 数量；物业有 DRAFT 时拒绝变更。 */
    fun setRepeatableRoomCount(propertyId: String, roomKey: String, instanceCount: Int) {
        require(instanceCount in 1..99) { "instanceCount must be between 1 and 99" }
        val now = clock.nowMs()
        db.transaction {
            checkNotNull(db.propertyQueries.selectActiveById(propertyId).executeAsOneOrNull()) {
                "no such active property: $propertyId"
            }
            require(db.hasActiveRepeatableRoom(roomKey)) {
                "room '$roomKey' is not declared repeatable by an active template"
            }
            require(db.inspectionQueries.selectDraftByProperty(propertyId).executeAsList().isEmpty()) {
                "repeatable room counts cannot change while property $propertyId has a DRAFT inspection"
            }
            val existing = db.propertyRoomConfigQueries
                .selectActiveByPropertyAndRoom(propertyId, roomKey)
                .executeAsOneOrNull()
            val affected = if (existing == null) {
                db.propertyRoomConfigQueries.insert(
                    id = uuid.next(),
                    property_id = propertyId,
                    room_key = roomKey,
                    instance_count = instanceCount.toLong(),
                    created_at = now,
                    updated_at = now,
                ).value
            } else {
                db.propertyRoomConfigQueries.updateCount(
                    instance_count = instanceCount.toLong(),
                    updated_at = now,
                    id = existing.id,
                ).value
            }
            check(affected == 1L) {
                "property_room_config write affected $affected rows for '$roomKey'"
            }
        }
    }

    /**
     * 建一次新巡检：校验入参的逻辑父行确实存在且互相一致 → 解析 previous/baseline 双轨引用 → 落
     * `inspection` 行 → 按模板房间键（排除本物业当前 suppressed 的项后）实例化 `room_instance`。
     * 单事务——半份巡检（有巡检行、房间缺几间）比整体失败更坏。
     *
     * **入参校验先于任何写入**：property/template_version/tenancy 都是逻辑外键（schema 不强制）——
     * 放行错误 id 会静默造出零房间的空壳巡检（模板 id 不存在，房间计划器查不到任何项定义，
     * `WalkProgress.isComplete` 因"没有房间"而空洞为真），或写进一条悬空引用（tenancy id 不存在，且会被
     * [resolveBaseline] 误判成"这处物业没有基线"这一正常业务态）。`template_version.type` 与入参 `type`
     * 不一致同样必须拦，否则状态合法性检查会用错评级域判定。
     *
     * 不预先创建 `inspection_item` 行：项目行在 [setItemStatus] 首次写入时才产生（`inspection_item.status`
     * 是 NOT NULL 列，创建时没有值可填；走查进度改用"该房间应有项数 vs 已记录项数"的计数比较，见
     * [computeRoomProgress]），这样也天然满足"进程死亡恢复"——没有中间态需要另外持久化。
     *
     * baseline 字段对**所有**巡检类型统一解析入库——它是创建时刻该 tenancy 基线的快照，EXIT 是主要
     * **消费者**（[setWearOrDamage]），非唯一持有者；按类型条件置空不属本卡契约（见卡片正文澄清）。
     */
    fun createInspection(
        type: String,
        propertyId: String,
        tenancyId: String?,
        templateVersionId: String,
        scheduledAt: Long,
    ): CreatedInspection {
        checkNotNull(db.propertyQueries.selectActiveById(propertyId).executeAsOneOrNull()) {
            "no such active property: $propertyId"
        }
        val templateVersion = checkNotNull(db.templateVersionQueries.selectActiveById(templateVersionId).executeAsOneOrNull()) {
            "no such active template version: $templateVersionId"
        }
        require(templateVersion.type == type) {
            "template version $templateVersionId is type '${templateVersion.type}', not '$type'"
        }
        val tenancy = tenancyId?.let {
            checkNotNull(db.tenancyQueries.selectActiveById(it).executeAsOneOrNull()) { "no such active tenancy: $it" }
        }
        if (tenancy != null) {
            require(tenancy.property_id == propertyId) {
                "tenancy $tenancyId belongs to property ${tenancy.property_id}, not $propertyId"
            }
        }

        val now = clock.nowMs()
        val inspectionId = uuid.next()
        var previousId: String? = null
        var baseline = BaselineResolution(null, null)
        val roomInstanceIds = mutableListOf<String>()

        db.transaction {
            // tenancy 在事务内重新点查一次而非复用入参校验期读到的 [tenancy]：baseline 的解析与下面
            // "是否需要把这次 INGOING 立成基线"的判断必须出自同一份、事务当下的真实状态，两处若各用
            // 各的快照会互相矛盾。
            val freshTenancy = tenancy?.let {
                checkNotNull(db.tenancyQueries.selectActiveById(it.id).executeAsOneOrNull()) {
                    "active tenancy ${it.id} disappeared mid-transaction"
                }
            }
            previousId = resolvePrevious(propertyId, type, scheduledAt)
            baseline = resolveBaseline(freshTenancy)

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
            // 建 INGOING 时，若该 tenancy 尚无基线指针，把这次 INGOING 自身立成基线（需求 §6「baseline_inspection
            // = 该 tenancy 的 Ingoing」）。不覆盖已有指针——一个 tenancy 只能有一个权威基线，重复建 INGOING
            // 不应该悄悄把基线换掉；"没有 Ingoing 时改指某次 finalized Routine" 那条例外路径只走
            // tenancy.assignFinalizedRoutineFallbackBaseline（见 Tenancy.sq）。这条 INGOING 自身的 baseline_inspection_id 列
            // 仍解析为 null（上面 resolveBaseline 在指针更新前就已算出）——它不需要引用自己。
            if (type == "INGOING" && freshTenancy != null && freshTenancy.baseline_inspection_id == null) {
                val affected = db.tenancyQueries.assignInitialIngoingBaseline(
                    baseline_inspection_id = inspectionId,
                    updated_at = now,
                    id = freshTenancy.id,
                ).value
                check(affected == 1L) { "initial INGOING baseline guard rejected inspection $inspectionId" }
            }
            db.planRoomInstances(propertyId, templateVersionId, suppressedStableIds(propertyId)).forEach { planned ->
                val roomInstanceId = uuid.next()
                val affected = db.roomInstanceQueries.insert(
                    id = roomInstanceId,
                    inspection_id = inspectionId,
                    room_key = planned.roomKey,
                    instance_no = planned.instanceNo,
                    display_label = planned.displayLabel,
                    created_at = now,
                    updated_at = now,
                ).value
                check(affected == 1L) {
                    "room_instance insert affected $affected rows for room '${planned.roomKey}' " +
                        "#${planned.instanceNo} (guard rejected the write)"
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
     * **两条调用方契约校验**（冻结的 `inspection_item.insert` 只守「同一巡检」，不覆盖下面两条）：
     * ① [roomInstanceId] 必须真的属于 [stableId] 定义所在的房间键（`def.room`），否则一个传错的
     * room_instance_id 能把条目写进错误的房间，完备性/进度计算会静默算错；② [stableId] 不得是本物业
     * **当前**被抑制的项——被抑制的项不出现在任何完备性查询里（见 `activeCheckItemDefs`），写入它只会
     * 产生一条谁都看不见、却仍占着 id 的幽灵记录。
     */
    fun setItemStatus(inspectionId: String, roomInstanceId: String, stableId: String, status: String, note: String?) {
        val now = clock.nowMs()
        db.transaction {
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
                if (existing.status != status) {
                    // 状态真的变了时，此前记录的 wear_or_damage 分类即失效——其合法性绑定在"此刻与基线有
                    // 差异"这一判断上（见 [setWearOrDamage]），状态变了这个判断要重新做，不能让旧分类
                    // 悄悄挂着。用已存在的 updateWearOrDamageIfDraft 清空即可，无需新增查询。
                    // 状态未变时绝不清空——否则一次只改备注、状态原地不动的幂等自动保存会把一条仍然
                    // 有效的 EXIT 分类悄悄删掉。
                    db.inspectionItemQueries.updateWearOrDamageIfDraft(wear_or_damage = null, updated_at = now, id = existing.id).value
                } else {
                    statusAffected
                }
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
    }

    /**
     * 置 `wear_or_damage`：仅 EXIT 巡检、且该项状态与基线巡检里同
     * `(room_key, instance_no, stable_id)` 的状态有差异时才允许写。四种非成功态都是合法结果，不是异常——
     * 调用方（UI）据此渲染相应提示，不需要 try/catch。
     *
     * [itemId] 必须真的属于 [inspectionId]——两者各自独立解析，若不核对，调用方能拿一个属于**另一次**
     * 巡检的 itemId，搭配一个恰好是 EXIT 且有基线的 inspectionId，把 wear_or_damage 写进毫不相干的条目。
     *
     * **基线巡检必须已 FINALIZED，否则视同无基线**：`baseline_inspection_id` 指针在建巡检时即写入（见
     * [createInspection]），但指向的那次巡检当时可能仍是 DRAFT、其条目状态还会继续改。拿一份仍在变化的
     * 草稿去算"与基线的差异"没有意义——差异算出来的下一刻，基线本身就可能又变了，写进去的分类会静默
     * 过期。故未 FINALIZED 的基线在这里等同卡片正文的"无基线"标记：拒写，不是另立第五种结果。这与"建
     * INGOING 时立即指派基线指针"这条不变量并不冲突——指针语义不变（历史事实、写死不改），只是**消费方**
     * （本方法）多一层"必须已定型"的前提。
     */
    fun setWearOrDamage(inspectionId: String, itemId: String, wearOrDamage: String): WearOrDamageOutcome {
        require(wearOrDamage in WEAR_OR_DAMAGE_VALUES) { "unknown wear_or_damage value: '$wearOrDamage'" }
        val now = clock.nowMs()
        val inspection = checkNotNull(db.inspectionQueries.selectById(inspectionId).executeAsOneOrNull()) {
            "no such inspection: $inspectionId"
        }
        if (inspection.type != "EXIT") return WearOrDamageOutcome.NotExitType
        val baselineId = inspection.baseline_inspection_id ?: return WearOrDamageOutcome.NoBaseline

        // 取基线巡检 → 核对已 FINALIZED → 取项 → 核对归属 → 取基线项 → 比较 → 写整段包进一个事务
        // （理由同 [createInspection]）；用外层可变量带出结果，因为 `db.transaction{}` 是 Unit 返回体。
        var outcome: WearOrDamageOutcome = WearOrDamageOutcome.Written
        db.transaction {
            val baselineInspection = checkNotNull(db.inspectionQueries.selectById(baselineId).executeAsOneOrNull()) {
                "baseline inspection $baselineId referenced by $inspectionId does not exist"
            }
            if (baselineInspection.finalized_at == null) {
                outcome = WearOrDamageOutcome.NoBaseline
                return@transaction
            }
            val item = checkNotNull(db.inspectionItemQueries.selectById(itemId).executeAsOneOrNull()) {
                "no such inspection_item: $itemId"
            }
            require(item.inspection_id == inspectionId) {
                "inspection_item $itemId belongs to inspection ${item.inspection_id}, not $inspectionId"
            }
            val currentRoom = checkNotNull(db.roomInstanceQueries.selectById(item.room_instance_id).executeAsOneOrNull()) {
                "room_instance ${item.room_instance_id} referenced by item $itemId does not exist"
            }
            require(currentRoom.inspection_id == inspectionId) {
                "room_instance ${currentRoom.id} belongs to inspection ${currentRoom.inspection_id}, not $inspectionId"
            }
            val baselineRoom = db.roomInstanceQueries.selectActiveByIdentity(
                inspection_id = baselineId,
                room_key = currentRoom.room_key,
                instance_no = currentRoom.instance_no,
            ).executeAsOneOrNull()
            if (baselineRoom == null) {
                outcome = WearOrDamageOutcome.NoBaselineItem
                return@transaction
            }
            val baselineItem = db.inspectionItemQueries.selectActiveByRoomAndStableId(
                inspection_id = baselineId,
                room_instance_id = baselineRoom.id,
                stable_id = item.stable_id,
            ).executeAsOneOrNull()
            if (baselineItem == null) {
                outcome = WearOrDamageOutcome.NoBaselineItem
                return@transaction
            }
            if (baselineItem.status == item.status) {
                outcome = WearOrDamageOutcome.NoDifference
                return@transaction
            }

            val affected = db.inspectionItemQueries.updateWearOrDamageIfDraft(
                wear_or_damage = wearOrDamage, updated_at = now, id = itemId,
            ).value
            check(affected == 1L) {
                "wear_or_damage write affected $affected rows for $itemId (finalize guard rejected the write)"
            }
        }
        return outcome
    }

    /**
     * 物业级条目抑制/恢复（「本物业不存在此项」，跨巡检永久生效直到显式恢复）。恢复 = 置 suppressed=0，
     * 不是软删这一行——见 PropertyItemOverride.sq 的同名注释，该状态本就可逆。
     *
     * [propertyId] 与 [stableId] 都先校验存在——`property_item_override` 是逻辑外键（schema 不强制），
     * 一个不存在的 property_id 或未曾出现在任何模板版本里的 stable_id 若放行，都会铸出一条谁都匹配不到、
     * 只占着索引位的死 override 行（[stableId] 跨模板校验，不绑定某一版本——见 [stableIdIsKnownToAnyTemplate]）。
     *
     * **恢复时补建缺失的 room_instance**：若某房间的唯一项在建巡检时已被抑制，该房间当时不会被实例化
     * （见 [planRoomInstances]）；此后若在**该巡检仍是 DRAFT 期间**恢复这项，完备性查询会立刻把它
     * 算作"活跃"（天然不看创建时快照，见 `activeCheckItemDefs`），但没有房间可挂——[setItemStatus] 无
     * room_instance 可传，这条项事实上无法被记录，`WalkProgress.isComplete` 却因"压根没这间房"而空洞
     * 为真。故恢复时为每个当前仍是 DRAFT 的巡检补建缺失的房间实例，抑制则不需要对称处理——抑制不使
     * 已建好的房间/条目消失，只是让"新建巡检"与"完备性查询"以后不再计入它。
     *
     * override 写入与补房间在同一个事务里（理由同 [createInspection]）。
     */
    fun setItemSuppression(propertyId: String, stableId: String, suppressed: Boolean) {
        val now = clock.nowMs()
        db.transaction {
            checkNotNull(db.propertyQueries.selectActiveById(propertyId).executeAsOneOrNull()) {
                "no such active property: $propertyId"
            }
            require(stableIdIsKnownToAnyTemplate(stableId)) {
                "stable_id '$stableId' is not defined in any template version"
            }
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
            if (!suppressed) {
                ensureRoomInstancesForRestoredItem(propertyId, stableId, now)
            }
        }
    }

    /** [setItemSuppression] 恢复路径的补建逻辑——见其 KDoc。逐个仍是 DRAFT 的巡检补齐全部计划实例。 */
    private fun ensureRoomInstancesForRestoredItem(propertyId: String, stableId: String, now: Long) {
        db.inspectionQueries.selectActive().executeAsList()
            .filter { it.property_id == propertyId && it.status == "DRAFT" }
            .forEach { inspection ->
                val def = db.checkItemDefQueries.selectByTemplateVersion(inspection.template_version_id).executeAsList()
                    .find { it.stable_id == stableId } ?: return@forEach
                val existingIdentities = db.roomInstanceQueries.selectByInspection(inspection.id).executeAsList()
                    .mapTo(mutableSetOf()) { it.room_key to it.instance_no }
                db.planRoomInstances(propertyId, inspection.template_version_id, suppressedStableIds(propertyId))
                    .filter { it.roomKey == def.room && (it.roomKey to it.instanceNo) !in existingIdentities }
                    .forEach { planned ->
                        val affected = db.roomInstanceQueries.insert(
                            id = uuid.next(), inspection_id = inspection.id, room_key = planned.roomKey,
                            instance_no = planned.instanceNo, display_label = planned.displayLabel,
                            created_at = now, updated_at = now,
                        ).value
                        check(affected == 1L) {
                            "room_instance insert affected $affected rows for restored room '${planned.roomKey}' " +
                                "#${planned.instanceNo} (guard rejected the write)"
                        }
                    }
            }
    }

    /** 整次巡检的走查进度（房间粒度）。无状态查询——process-death 恢复即"再查一次"。 */
    fun walkProgress(inspectionId: String): WalkProgress {
        val inspection = checkNotNull(db.inspectionQueries.selectById(inspectionId).executeAsOneOrNull()) {
            "no such inspection: $inspectionId"
        }
        val rooms = loadRoomSnapshots(inspection)
        return WalkProgress(inspectionId, rooms.map { computeRoomProgress(inspection.type, it) })
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
     * 该 stable_id 是否曾出现在**任意**模板版本的项定义里。跨模板校验而非绑定某一版本——物业没有
     * 固定的单一模板（不同巡检类型各自的模板版本各异，且抑制常常发生在该物业第一次巡检之前），
     * 只要求这个 id 是系统里真实存在过的模板项，不是拼错/伪造的字符串。
     */
    private fun stableIdIsKnownToAnyTemplate(stableId: String): Boolean =
        db.templateVersionQueries.selectActive().executeAsList().any { tv ->
            db.checkItemDefQueries.selectByTemplateVersion(tv.id).executeAsList().any { it.stable_id == stableId }
        }

    /**
     * `room_instance.selectByInspection`（冻结查询）没有 ORDER BY，返回顺序由 SQLite 的查询计划决定
     * （实测：常走 `idx_room_instance_active`，按 room_key 字典序而非插入/模板序），故本方法**在 Kotlin
     * 侧显式排序**——[WalkProgress.rooms] 及两条完备性查询的房间顺序因此保证等于模板房间序、再按
     * `instance_no`；计划外历史行以 `(room_key, instance_no, id)` 收尾，仍形成确定全序。
     *
     * 整份读组在一个事务里完成（理由同 [createInspection]）：definitions/room_instances/items/photos
     * 四组各自独立的 SELECT 必须出自同一个数据库状态——这份快照正是 [missingPhotos]/[missingNotes] 共用
     * 的完备性依据，撕裂读会让它一半旧一半新。
     */
    private fun loadRoomSnapshots(inspection: Inspection): List<RoomSnapshot> {
        var snapshots: List<RoomSnapshot> = emptyList()
        db.transaction {
            val defsByRoom = activeCheckItemDefs(inspection.property_id, inspection.template_version_id)
                .groupBy { it.room }
            val plannedOrder = db.planRoomInstances(
                inspection.property_id,
                inspection.template_version_id,
                suppressedStableIds(inspection.property_id),
            ).mapIndexed { index, room -> (room.roomKey to room.instanceNo) to index }.toMap()
            val roomInstances = db.roomInstanceQueries.selectByInspection(inspection.id).executeAsList()
                .sortedWith(
                    compareBy(
                        { plannedOrder[it.room_key to it.instance_no] ?: Int.MAX_VALUE },
                        { it.room_key },
                        { it.instance_no },
                        { it.id },
                    ),
                )
            val items = db.inspectionItemQueries.selectByInspection(inspection.id).executeAsList()

            snapshots = roomInstances.mapNotNull { ri ->
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
        return snapshots
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
     * （调用方传错 id 是调用方错误，须当场炸，不是"无基线"这一合法业务态）；调用方须传入事务内新鲜
     * 读到的那一份（见 [createInspection] 的 `freshTenancy`），本函数只负责从中读出基线指针。
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
