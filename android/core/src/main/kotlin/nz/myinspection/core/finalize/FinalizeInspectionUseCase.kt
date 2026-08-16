package nz.myinspection.core.finalize

import nz.myinspection.core.canon.canonicalJson
import nz.myinspection.core.canon.sha256Hex
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.SystemClockMs

/**
 * `finalize()` 的可能结局。拒绝态一律**显式返回**、不抛异常——完备性不达标或重复 finalize 都是正常业务
 * 流程会遇到的情形，不是程序错误（同 `TemplateLoader.validate` 的"返回问题清单而非抛异常"纪律）。
 */
sealed interface FinalizeOutcome {
    /** 完备性通过、快照已物化、`finalized_at`+`data_hash` 已原子写入。 */
    data class Finalized(val dataHash: String, val finalizedAt: Long) : FinalizeOutcome

    /** 完备性未过：逐项清单原样透出，UI 据此告诉用户还差哪几项。 */
    data class RejectedIncomplete(val result: CompletenessResult) : FinalizeOutcome

    /** 该巡检早已 FINALIZED（幂等拒绝——重复调用不覆盖原有 `data_hash`）。 */
    data object RejectedAlreadyFinalized : FinalizeOutcome

    /** `inspectionId` 查无此巡检。 */
    data object RejectedNotFound : FinalizeOutcome
}

/**
 * finalize 事务（ADR-0003 事务序，见卡文「上下文包」）：
 * ① 完备性校验（[CompletenessPort]，缺则拒并列出逐项清单）
 * ② 物化 [nz.myinspection.core.model.InspectionSnapshot]（唯一装配正门 [InspectionSnapshotAssembler]）
 * ③ `data_hash = sha256Hex(canonicalJson(snapshot))`
 * ④ 同一 DB 事务写 `finalized_at` + `data_hash`（[MyInspectionDatabase.inspectionQueries] 的
 *    `finalizeIfDraft` 本身即"仅当仍是 DRAFT 才成功"的一次性守卫，见 `Inspection.sq`）。
 *
 * 步骤 ①②③ 全是读 + 纯计算，本身不写库——真正的写只有步骤④这一条 UPDATE 语句，天然原子。
 * `database.transaction { }` 仍然包一层（而不是直接裸调 `finalizeIfDraft`）是与 `TemplateStore.persist()`
 * 保持同一纪律：写库这一步就该显式在事务边界内，即便当前只有一条语句。
 *
 * `finalizeIfDraft` 影响行数必为 1：本方法开头已经原子地（单线程同步调用、单一 DB 连接、
 * local-first 单进程应用，见 ADR-0001/0002）读过 `finalized_at IS NULL` 才走到这里，中间只有纯读
 * 与纯计算、没有让出执行权的挂起点，不存在别的调用能在这两步之间插进来改写同一行。若这个不变量真被
 * 打破（比如未来误引入协程/多连接），`check` 会当场炸出来，而不是悄悄把一次真正的异常状态报成看似
 * 合理的"已 FINALIZED"——那种伪装成功的错误更难查（同 `TemplateStore.persist` 对 `check_item_def`
 * 插入结果的处理纪律）。
 */
class FinalizeInspectionUseCase(
    private val database: MyInspectionDatabase,
    private val completeness: CompletenessPort,
    private val clock: ClockMs = SystemClockMs,
) {
    fun finalize(inspectionId: String): FinalizeOutcome {
        val row = database.inspectionQueries.selectById(inspectionId).executeAsOneOrNull()
            ?: return FinalizeOutcome.RejectedNotFound
        if (row.finalized_at != null) return FinalizeOutcome.RejectedAlreadyFinalized

        val completenessResult = completeness.check(inspectionId)
        if (!completenessResult.isComplete) return FinalizeOutcome.RejectedIncomplete(completenessResult)

        // finalizedAt 必须在算哈希之前定下来：data_hash 覆盖的快照本身含 finalizedAt 字段（哈希域，
        // 见 InspectionSnapshot 顶部说明），写库时必须用同一个值，否则哈希与落库的 finalized_at 对不上。
        val finalizedAt = clock.nowMs()
        val snapshot = InspectionSnapshotAssembler.assemble(database, inspectionId, finalizedAt)
        val dataHash = sha256Hex(canonicalJson(snapshot))

        database.transaction {
            val affected = database.inspectionQueries.finalizeIfDraft(
                finalized_at = finalizedAt,
                data_hash = dataHash,
                updated_at = finalizedAt,
                id = inspectionId,
            ).value
            check(affected == 1L) {
                "finalizeIfDraft affected $affected rows for $inspectionId despite the DRAFT check above (guard rejected the write)"
            }
        }
        return FinalizeOutcome.Finalized(dataHash = dataHash, finalizedAt = finalizedAt)
    }
}
