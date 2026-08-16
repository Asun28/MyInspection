package nz.myinspection.core.finalize

import nz.myinspection.core.canon.canonicalJson
import nz.myinspection.core.canon.sha256Hex
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.SystemClockMs

/**
 * `finalize()` 的可能结局。拒绝态一律显式返回、不抛异常——完备性不达标或重复 finalize 是正常业务流程
 * 会遇到的情形，不是程序错误。
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
 * finalize 事务（ADR-0003 事务序）：① 完备性校验（[CompletenessPort]，缺则拒并列逐项清单）
 * ② 物化 [nz.myinspection.core.model.InspectionSnapshot]（唯一装配正门 [InspectionSnapshotAssembler]）
 * ③ `data_hash = sha256Hex(canonicalJson(snapshot))` ④ 同一事务写 `finalized_at`+`data_hash`。
 *
 * ①-④ 全程在一个 `database.transactionWithResult { }` 里：完备性校验/快照物化必须读到与最终写入
 * 同一个一致的数据库状态，否则会出现"校验时读到 A、写入时数据库已是 B"的 TOCTOU 窗口。
 *
 * 对默认实现（[CompletenessPort] 的 [DbCompletenessChecker]）而言，①-③ 全是读或纯内存计算，本方法
 * 在到达④之前不写库；④是单条 `UPDATE … WHERE finalized_at IS NULL` 语句——它的 WHERE 子句同时兼任
 * "再核一次仍是 DRAFT"与"写入"，两者是同一条语句、没有可插入失败的中间窗口。但 [CompletenessPort]
 * 是调用方可换的接口，不保证每个实现都只读——即便某个实现在①里产生了写副作用，事务边界仍然兜底：
 * 该写会随后续任一步的异常一起回滚（见 `FinalizeInspectionUseCaseTest` 的
 * "if a seam inside the transaction throws after writing..." 用例，已用移除事务包装的变异验证过
 * 这条测试确实会因此变红）。故"任一步败=全回滚"由事务边界保证，不依赖①-③"不写库"这条假设；
 * ④要么整条落地（`affected == 1L`）要么整条不落地（判定为 `RejectedAlreadyFinalized`，不当成异常
 * 抛出——两个调用者都在合法地尝试 finalize 同一份数据，后到者理应拿到一个可处理的业务结果）。
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

        // finalizedAt 须在算哈希前定下来：data_hash 覆盖的快照含 finalizedAt 字段（哈希域），写库时
        // 必须用同一个值。
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
