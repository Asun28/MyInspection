package nz.myinspection.core.finalize

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import nz.myinspection.core.canon.supplementChainHash
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator
import nz.myinspection.core.model.SupplementSnapshot

/**
 * [SupplementChainService]：finalize 后唯一允许的追加写路径。链锚定在 `inspection.data_hash`
 * （T1-CANON-HASH「prev_hash(1) = inspection.data_hash」），逐条 `chain_hash` 延链，[verifyChain]
 * 可复验、可检出直连 SQL 模拟的腐坏（Supplement.sq 本就不提供 update 查询，append-only 是关键不变量，
 * 故"腐坏"只能靠测试直连驱动模拟，卡文 dod_assert 已明确认可这条路径）。
 */
class SupplementChainServiceTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: MyInspectionDatabase
    private lateinit var uuid: Uuid7Generator
    private val now = DbTestFixtures.NOW

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MyInspectionDatabase.Schema.create(driver)
        database = MyInspectionDatabase(driver)
        uuid = Uuid7Generator()
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun sequenceClock(vararg values: Long): ClockMs {
        var i = 0
        return ClockMs { values[i++] }
    }

    private fun finalizeMinimalInspection(clockAt: Long): String {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)
        val useCase = FinalizeInspectionUseCase(database, DbCompletenessChecker(database), ClockMs { clockAt })
        assertIs<FinalizeOutcome.Finalized>(useCase.finalize(ready.inspectionId))
        return ready.inspectionId
    }

    /** 全库 supplement 行数——`RejectedNotFound` 断言"确实什么都没写"要看全库，不能只看一个（不存在的）
     * inspectionId 下的清单（那样恒为空，测不出"根本没走到 insert"）。 */
    private fun countSupplements(): Long =
        driver.executeQuery(null, "SELECT COUNT(*) FROM supplement", { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0)!! else 0L)
        }, 0).value

    @Test
    fun `addSupplement on a DRAFT inspection is rejected`() {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)
        val service = SupplementChainService(database, uuid, ClockMs { now + 1 })

        val outcome = service.addSupplement(ready.inspectionId, "landlord to fix the latch")

        assertIs<AddSupplementOutcome.RejectedNotFinalized>(outcome)
        assertTrue(database.supplementQueries.selectByInspection(ready.inspectionId).executeAsList().isEmpty())
    }

    @Test
    fun `the first supplement anchors prev_hash to the inspection data_hash`() {
        val inspectionId = finalizeMinimalInspection(clockAt = now + 100)
        val dataHash = database.inspectionQueries.selectById(inspectionId).executeAsOne().data_hash!!
        val service = SupplementChainService(database, uuid, ClockMs { now + 200 })

        val outcome = service.addSupplement(inspectionId, "first note")

        val added = assertIs<AddSupplementOutcome.Added>(outcome)
        val row = database.supplementQueries.selectById(added.id).executeAsOne()
        assertEquals(dataHash, row.prev_hash)
        assertEquals(supplementChainHash(dataHash, SupplementSnapshot(createdAt = now + 200, text = "first note")), row.chain_hash)
        assertEquals(row.chain_hash, added.chainHash)
    }

    @Test
    fun `a second supplement chains from the first one's chain_hash, not the anchor`() {
        val inspectionId = finalizeMinimalInspection(clockAt = now + 100)
        val service = SupplementChainService(database, uuid, sequenceClock(now + 200, now + 300))

        val first = assertIs<AddSupplementOutcome.Added>(service.addSupplement(inspectionId, "first note"))
        val second = assertIs<AddSupplementOutcome.Added>(service.addSupplement(inspectionId, "second note"))

        val secondRow = database.supplementQueries.selectById(second.id).executeAsOne()
        assertEquals(first.chainHash, secondRow.prev_hash)
    }

    @Test
    fun `a supplement whose timestamp precedes the chain tip is rejected as out of order`() {
        val inspectionId = finalizeMinimalInspection(clockAt = now + 100)
        val service = SupplementChainService(database, uuid, sequenceClock(now + 300, now + 200))

        assertIs<AddSupplementOutcome.Added>(service.addSupplement(inspectionId, "later note"))
        val outOfOrder = service.addSupplement(inspectionId, "an earlier note arriving late")

        assertIs<AddSupplementOutcome.RejectedOutOfOrder>(outOfOrder)
        assertEquals(1, database.supplementQueries.selectByInspection(inspectionId).executeAsList().size)
    }

    /**
     * 同毫秒也必须拒：`id` 是 UUIDv7，同一毫秒内两条纪录的相对大小取决于
     * 各自生成时的计数器/随机位，与它们的实际链接顺序（谁的 `prev_hash` 指向谁）无关。若允许
     * `now == tip.created_at`，`Supplement.sq` 的 `ORDER BY created_at ASC, id ASC` 读回序就可能把
     * 新纪录排到 tip 前面，而新纪录的 `prev_hash` 却指向 tip——`verifyChain` 会在一条从未真正断裂的链上
     * 报错。这里用两个**不同的 [SupplementChainService] 实例**（各自一个新 `Uuid7Generator`，避免依赖
     * 同一生成器的计数器巧合递增）在同一毫秒各插一条，第二条必须被拒。
     */
    @Test
    fun `a supplement whose timestamp exactly equals the chain tip is rejected, not just an earlier one`() {
        val inspectionId = finalizeMinimalInspection(clockAt = now + 100)
        val sameInstant = now + 200

        val firstService = SupplementChainService(database, Uuid7Generator(), ClockMs { sameInstant })
        assertIs<AddSupplementOutcome.Added>(firstService.addSupplement(inspectionId, "first at T"))

        val secondService = SupplementChainService(database, Uuid7Generator(), ClockMs { sameInstant })
        val outcome = secondService.addSupplement(inspectionId, "second at the exact same T")

        assertIs<AddSupplementOutcome.RejectedOutOfOrder>(outcome)
        assertEquals(1, database.supplementQueries.selectByInspection(inspectionId).executeAsList().size)
    }

    /**
     * 链首（还没有任何 supplement）时的时序锚点是 `inspection.finalized_at` 本身，不是链尾——链尾
     * 要等第一条落地才存在。这里模拟时钟在 finalize 之后被拨回：finalize 于 `now+100`，第一条
     * supplement 却尝试用 `now+50`（早于 finalized_at）与 `now+100`（等于 finalized_at）两个时间戳，
     * 都必须被拒；`now+101`（严格晚于）才允许落地。
     */
    @Test
    fun `the first supplement is rejected if its timestamp does not strictly postdate finalized_at`() {
        val inspectionId = finalizeMinimalInspection(clockAt = now + 100)

        val beforeFinalize = SupplementChainService(database, uuid, ClockMs { now + 50 })
        assertIs<AddSupplementOutcome.RejectedOutOfOrder>(beforeFinalize.addSupplement(inspectionId, "clock rolled back before finalize"))

        val exactlyAtFinalize = SupplementChainService(database, uuid, ClockMs { now + 100 })
        assertIs<AddSupplementOutcome.RejectedOutOfOrder>(exactlyAtFinalize.addSupplement(inspectionId, "clock exactly at finalize"))

        assertEquals(0, database.supplementQueries.selectByInspection(inspectionId).executeAsList().size, "neither rejected attempt may land a row")

        val afterFinalize = SupplementChainService(database, uuid, ClockMs { now + 101 })
        val outcome = afterFinalize.addSupplement(inspectionId, "genuinely after finalize")
        assertIs<AddSupplementOutcome.Added>(outcome)
    }

    @Test
    fun `addSupplement on an unknown inspection id is rejected as not found and writes nothing`() {
        // 一间已 FINALIZED 的巡检作对照——证明"没写"不是因为全库本来就空，而是这次调用真的没落地。
        finalizeMinimalInspection(clockAt = now + 100)
        val before = countSupplements()
        val service = SupplementChainService(database, uuid, ClockMs { now + 200 })

        val outcome = service.addSupplement(uuid.next(), "orphaned note")

        assertIs<AddSupplementOutcome.RejectedNotFound>(outcome)
        assertEquals(before, countSupplements(), "a not-found rejection must not insert any row, anywhere")
    }

    @Test
    fun `verifyChain on an unknown inspection id reports NotFound`() {
        finalizeMinimalInspection(clockAt = now + 100)

        val verification = SupplementChainService(database, uuid).verifyChain(uuid.next())

        assertIs<ChainVerification.NotFound>(verification)
    }

    @Test
    fun `verifyChain reports NotFinalized on a DRAFT inspection`() {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)

        assertIs<ChainVerification.NotFinalized>(SupplementChainService(database, uuid).verifyChain(ready.inspectionId))
    }

    @Test
    fun `verifyChain is valid on an untouched chain`() {
        val inspectionId = finalizeMinimalInspection(clockAt = now + 100)
        val service = SupplementChainService(database, uuid, sequenceClock(now + 200, now + 300))
        service.addSupplement(inspectionId, "first")
        service.addSupplement(inspectionId, "second")

        assertIs<ChainVerification.Valid>(service.verifyChain(inspectionId))
    }

    @Test
    fun `verifyChain detects a supplement row corrupted via direct SQL bypassing the append-only predicate`() {
        val inspectionId = finalizeMinimalInspection(clockAt = now + 100)
        val service = SupplementChainService(database, uuid, ClockMs { now + 200 })
        val added = assertIs<AddSupplementOutcome.Added>(service.addSupplement(inspectionId, "original text"))

        // Supplement.sq 故意不提供 update 查询（append-only）；模拟"有人绕过谓词直接改库文件"只能走
        // 驱动的原生 execute，卡文 dod_assert 已明确认可这条测试路径。
        driver.execute(null, "UPDATE supplement SET text = 'TAMPERED' WHERE id = '${added.id}'", 0)

        val verification = service.verifyChain(inspectionId)
        val broken = assertIs<ChainVerification.Broken>(verification)
        assertEquals(added.id, broken.supplementId)
    }

    /**
     * `verifyChain` 的两处比对（`prev_hash != prev` 与 `chain_hash` 重算不符）各自独立，上面那条测试
     * 只走到了后者（腐坏 `text` 会连带算出的 `chain_hash` 就已经对不上）；这里单独腐坏 `prev_hash`
     * 本身（`chain_hash` 保持不变），确认 `prev_hash` 衔接检查独立生效，不是靠 `chain_hash` 检查顺带
     * 兜底的。
     */
    @Test
    fun `verifyChain detects a corrupted prev_hash on a later row even when its own chain_hash is untouched`() {
        val inspectionId = finalizeMinimalInspection(clockAt = now + 100)
        val service = SupplementChainService(database, uuid, sequenceClock(now + 200, now + 300))
        service.addSupplement(inspectionId, "first")
        val second = assertIs<AddSupplementOutcome.Added>(service.addSupplement(inspectionId, "second"))

        driver.execute(null, "UPDATE supplement SET prev_hash = '${"f".repeat(64)}' WHERE id = '${second.id}'", 0)

        val verification = service.verifyChain(inspectionId)
        val broken = assertIs<ChainVerification.Broken>(verification)
        assertEquals(second.id, broken.supplementId)
    }
}
