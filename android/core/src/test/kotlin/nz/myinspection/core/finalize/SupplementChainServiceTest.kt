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
}
