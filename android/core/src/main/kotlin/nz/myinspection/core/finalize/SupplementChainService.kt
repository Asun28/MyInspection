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
     * 新补充说明的时间戳早于链上最后一条——若放行，读回顺序（`Supplement.sq` 的
     * `ORDER BY created_at ASC, id ASC`）会把它排到已写死的 `prev_hash` 指向的那条**之前**，
     * 于是 [verifyChain] 会在一个从未真正断裂的链上报错。故在写入前把这类请求当场拒绝，
     * 而不是留给复验时才发现。
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
    fun addSupplement(inspectionId: String, text: String): AddSupplementOutcome {
        val inspection = database.inspectionQueries.selectById(inspectionId).executeAsOneOrNull()
            ?: return AddSupplementOutcome.RejectedNotFound
        // status/finalized_at/data_hash 三者联动一致是 schema CHECK 约束（Inspection.sq）——data_hash
        // 非空即已 FINALIZED，不必再查一次 status 列。
        val dataHash = inspection.data_hash ?: return AddSupplementOutcome.RejectedNotFinalized

        val existing = database.supplementQueries.selectByInspection(inspectionId).executeAsList()
        val tip = existing.lastOrNull()
        val prev = tip?.chain_hash ?: dataHash

        val now = clock.nowMs()
        if (tip != null && now < tip.created_at) {
            return AddSupplementOutcome.RejectedOutOfOrder
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
        return AddSupplementOutcome.Added(id = id, chainHash = chainHash)
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
