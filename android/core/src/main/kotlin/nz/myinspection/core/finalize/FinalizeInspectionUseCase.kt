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
 * **①-④ 全程在同一个 `database.transactionWithResult { }` 里**：DRAFT 判定、完备性校验、快照物化、
 * 哈希计算、最终写入必须读到同一个一致的数据库状态，否则会出现"完备性校验时读到 A、真正写入时数据库
 * 已经是 B"的 TOCTOU 窗口——`data_hash` 覆盖的快照与落库时刻的真实数据就此对不上。单一 SQLite 连接的
 * 写事务本身就会序列化任何试图在窗口期插入的另一次写（见 SQLDelight 文档："in-memory drivers have
 * a single connection, concurrent access will be blocked"），但把校验/物化留在事务外仍会让*读*停留在
 * 事务开始前的旧快照——包起来才是真正堵死这条缝，而不是只指望连接层面的串行化。
 *
 * 事务末尾用 `finalizeIfDraft` 的影响行数**再核一次**「仍是 DRAFT」——不是因为不信任前面已经查过的
 * `row.finalized_at`，而是这次核验发生在同一事务内、紧贴写入前，能兜住"事务内某次内部调用（比如
 * [CompletenessPort] 的实现产生了写副作用）已经把这一行变成 FINALIZED"的情形；命中时**干净返回**
 * `RejectedAlreadyFinalized`，不用 `check()` 断言炸出一个看起来像 bug 的异常——那类真正的竞态不是程序
 * 错误，是两个调用者都在合法地尝试 finalize 同一份数据，后到者理应拿到一个可处理的业务结果。
 */
class FinalizeInspectionUseCase(
    private val database: MyInspectionDatabase,
    private val completeness: CompletenessPort,
    private val clock: ClockMs = SystemClockMs,
) {
    fun finalize(inspectionId: String): FinalizeOutcome = database.transactionWithResult {
        val row = database.inspectionQueries.selectById(inspectionId).executeAsOneOrNull()
            ?: return@transactionWithResult FinalizeOutcome.RejectedNotFound
        if (row.finalized_at != null) return@transactionWithResult FinalizeOutcome.RejectedAlreadyFinalized

        val completenessResult = completeness.check(inspectionId)
        if (!completenessResult.isComplete) {
            return@transactionWithResult FinalizeOutcome.RejectedIncomplete(completenessResult)
        }

        // finalizedAt 必须在算哈希之前定下来：data_hash 覆盖的快照本身含 finalizedAt 字段（哈希域，
        // 见 InspectionSnapshot 顶部说明），写库时必须用同一个值，否则哈希与落库的 finalized_at 对不上。
        val finalizedAt = clock.nowMs()
        val snapshot = InspectionSnapshotAssembler.assemble(database, inspectionId, finalizedAt)
        val dataHash = sha256Hex(canonicalJson(snapshot))

        val affected = database.inspectionQueries.finalizeIfDraft(
            finalized_at = finalizedAt,
            data_hash = dataHash,
            updated_at = finalizedAt,
            id = inspectionId,
        ).value
        if (affected == 1L) {
            FinalizeOutcome.Finalized(dataHash = dataHash, finalizedAt = finalizedAt)
        } else {
            FinalizeOutcome.RejectedAlreadyFinalized
        }
    }
}
