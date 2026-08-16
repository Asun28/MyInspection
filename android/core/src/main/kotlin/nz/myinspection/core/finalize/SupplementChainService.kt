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

    /** 巡检还是 DRAFT——补充说明只能追加在 finalize **之后**（需求 §5：finalize 后只可追加）。 */
    data object RejectedNotFinalized : AddSupplementOutcome
    data object RejectedNotFound : AddSupplementOutcome

    /**
     * 新补充说明的时间戳**不晚于**链上最后一条——若放行，读回顺序（`Supplement.sq` 的
     * `ORDER BY created_at ASC, id ASC`）可能把它排到已写死的 `prev_hash` 指向的那条**之前**，
     * 于是 [verifyChain] 会在一个从未真正断裂的链上报错。**同毫秒也必须拒**，不只是"更早"：
     * `id` 是 UUIDv7，同一毫秒内的排序取决于各自 [Uuid7Generator] 实例当时的计数器/随机位，
     * 与两条 supplement 实际的链接顺序（谁的 `prev_hash` 指向谁）没有任何保证关系——`now == tip.created_at`
     * 时新纪录的 id 完全可能小于 tip 的 id，读回序就会先出新纪录、后出 tip，而新纪录的 `prev_hash` 却
     * 指向 tip，链看起来从中间断开。故要求严格晚于（`now > tip.created_at`），不接受相等。
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
 * Supplement 追加哈希链（需求 §5）：`addSupplement` 写入、`verifyChain` 复验，供报告/备份复用
 * （T3-REPORT-COMPOSER / T5-BACKUP-FORMAT 消费，本卡只提供纯函数级的可复用实现）。
 *
 * 哈希算法本身消费 `core/canon` 的 [supplementChainHash]（冻结物，只读不改）：
 * `chain_hash(n) = SHA-256(canonical({createdAt, text}) + prev_hash)`，`prev_hash(1) = inspection.data_hash`。
 */
class SupplementChainService(
    private val database: MyInspectionDatabase,
    private val uuid: Uuid7Generator = Uuid7Generator(),
    private val clock: ClockMs = SystemClockMs,
) {
    /**
     * 读锚点/链尾 → 校验时序 → 算哈希 → 插入，全程在一个 `transactionWithResult` 里：读链尾与插入
     * 之间若不加事务边界，两个并发调用者能读到同一个链尾、各自算出以它为 `prev_hash` 的新纪录再各自
     * 插入——链就此分叉成两条互不相连的支线，而 `Supplement.sq` 没有任何 UNIQUE 约束能拦住这种分叉
     * （append-only 表本就不设约束防止重复 `prev_hash`）。包成一个事务后，单一连接的写事务会把第二个
     * 调用者的整个"读链尾→插入"序列**串行化**在第一个调用者提交之后，它读到的链尾自然是最新的。
     */
    fun addSupplement(inspectionId: String, text: String): AddSupplementOutcome = database.transactionWithResult {
        val inspection = database.inspectionQueries.selectById(inspectionId).executeAsOneOrNull()
            ?: return@transactionWithResult AddSupplementOutcome.RejectedNotFound
        // status/finalized_at/data_hash 三者联动一致是 schema CHECK 约束（Inspection.sq）——data_hash
        // 非空即已 FINALIZED，不必再查一次 status 列。
        val dataHash = inspection.data_hash ?: return@transactionWithResult AddSupplementOutcome.RejectedNotFinalized

        val existing = database.supplementQueries.selectByInspection(inspectionId).executeAsList()
        val tip = existing.lastOrNull()
        val prev = tip?.chain_hash ?: dataHash

        val now = clock.nowMs()
        if (tip != null && now <= tip.created_at) {
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
     * 任一条对不上即报告那一条的 id 并停止（不掩盖具体哪一环断了）。
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
