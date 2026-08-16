package nz.myinspection.core.finalize

import nz.myinspection.core.canon.supplementChainHash
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.SystemClockMs
import nz.myinspection.core.db.Uuid7Generator
import nz.myinspection.core.model.SupplementSnapshot

/** [SupplementChainService.addSupplement] 的可能结局。 */
sealed interface AddSupplementOutcome {
    data class Added(val id: String, val chainHash: String) : AddSupplementOutcome

    /** 巡检还是 DRAFT——补充说明只能追加在 finalize 之后（需求 §5）。 */
    data object RejectedNotFinalized : AddSupplementOutcome
    data object RejectedNotFound : AddSupplementOutcome

    /**
     * 新纪录的时间戳不晚于链上最后一条**或**（链尚为空时）不晚于 `inspection.finalized_at`——
     * 后者补的是链首窗口：finalize 之后、追加第一条 supplement 之前，若时钟被拨回到 `finalized_at`
     * 及更早，`created_at` 就会落在 finalize 之前，破坏"补充说明只能追加在 finalize 之后"这条不变量
     * （需求 §5），且这条 supplement 的时间戳会先于它自己声称补充说明的那次巡检本身。
     *
     * 同毫秒也拒，不只是"更早"：`id`（UUIDv7）在同一毫秒内的相对大小与实际链接顺序无关，若允许
     * `now == 锚点时间`，读回序（`ORDER BY created_at ASC, id ASC`）可能把新纪录排到它自己 `prev_hash`
     * 指向的那条之前，让 [verifyChain] 在一条从未真正断裂的链上报错。故两种情形都要求严格晚于。
     */
    data object RejectedOutOfOrder : AddSupplementOutcome
}

/** [SupplementChainService.verifyChain] 的可能结局。 */
sealed interface ChainVerification {
    data object Valid : ChainVerification
    data object NotFinalized : ChainVerification
    data object NotFound : ChainVerification

    /** 链在 [supplementId] 这一条上对不上——它的 `prev_hash` 或 `chain_hash` 与重算值不符。 */
    data class Broken(val supplementId: String) : ChainVerification
}

/**
 * Supplement 追加哈希链（需求 §5）：`addSupplement` 写入、`verifyChain` 复验，供报告/备份复用。
 *
 * 哈希算法消费 `core/canon` 的 [supplementChainHash]（冻结物）：
 * `chain_hash(n) = SHA-256(canonical({createdAt, text}) + prev_hash)`，`prev_hash(1) = inspection.data_hash`。
 */
class SupplementChainService(
    private val database: MyInspectionDatabase,
    private val uuid: Uuid7Generator = Uuid7Generator(),
    private val clock: ClockMs = SystemClockMs,
) {
    /**
     * 读链尾 → 校验时序 → 算哈希 → 插入，全程在一个事务里：不加事务边界的话，两个并发调用者能读到
     * 同一个链尾、各自插入以它为 `prev_hash` 的新纪录，链就此分叉成两条互不相连的支线（append-only
     * 表没有约束能拦住这种分叉）。
     */
    fun addSupplement(inspectionId: String, text: String): AddSupplementOutcome = database.transactionWithResult {
        val inspection = database.inspectionQueries.selectById(inspectionId).executeAsOneOrNull()
            ?: return@transactionWithResult AddSupplementOutcome.RejectedNotFound
        // status/finalized_at/data_hash 三者联动一致是 schema CHECK 约束——data_hash 非空即已 FINALIZED。
        val dataHash = inspection.data_hash ?: return@transactionWithResult AddSupplementOutcome.RejectedNotFinalized

        val existing = database.supplementQueries.selectByInspection(inspectionId).executeAsList()
        val tip = existing.lastOrNull()
        val prev = tip?.chain_hash ?: dataHash
        // 链首（tip 为 null）的时序锚点是 finalized_at 本身，不是链尾——finalized_at 非空由上面
        // dataHash 非空已经保证（CHECK 约束联动），!! 只是把这份保证转述给类型系统。
        val anchorAt = tip?.created_at ?: inspection.finalized_at!!

        val now = clock.nowMs()
        if (now <= anchorAt) {
            return@transactionWithResult AddSupplementOutcome.RejectedOutOfOrder
        }

        val snapshot = SupplementSnapshot(createdAt = now, text = text)
        val chainHash = supplementChainHash(prev, snapshot)
        val id = uuid.next()
        database.supplementQueries.insert(
            id = id,
            inspection_id = inspectionId,
            created_at = now,
            text = text,
            prev_hash = prev,
            chain_hash = chainHash,
            updated_at = now,
        )
        AddSupplementOutcome.Added(id = id, chainHash = chainHash)
    }

    /**
     * 从 `inspection.data_hash`（链的锚点）开始逐条重算 `chain_hash` 并比对 `prev_hash` 衔接，
     * 任一条对不上即报告那一条的 id 并停止。
     */
    fun verifyChain(inspectionId: String): ChainVerification {
        val inspection = database.inspectionQueries.selectById(inspectionId).executeAsOneOrNull()
            ?: return ChainVerification.NotFound
        var prev = inspection.data_hash ?: return ChainVerification.NotFinalized

        for (row in database.supplementQueries.selectByInspection(inspectionId).executeAsList()) {
            if (row.prev_hash != prev) return ChainVerification.Broken(row.id)
            val expected = supplementChainHash(prev, SupplementSnapshot(createdAt = row.created_at, text = row.text))
            if (expected != row.chain_hash) return ChainVerification.Broken(row.id)
            prev = row.chain_hash
        }
        return ChainVerification.Valid
    }
}
