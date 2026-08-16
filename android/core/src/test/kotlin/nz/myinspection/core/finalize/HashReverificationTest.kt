package nz.myinspection.core.finalize

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import nz.myinspection.core.canon.canonicalJson
import nz.myinspection.core.canon.sha256Hex
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator

/**
 * 哈希可复验性：`data_hash` 只覆盖 [nz.myinspection.core.model.InspectionSnapshot] 的哈希域
 * （ADR-0003）。排除域字段（如 `updated_at`）事后改动不得影响复算结果；哈希域字段一旦被绕过 finalize
 * 谓词直接改库（测试用驱动原生 execute 模拟），复算必须与落库的 `data_hash` 对不上——这正是它写进
 * PDF 页脚"自证未被事后修改"的意义所在。
 */
class HashReverificationTest {
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

    private fun recompute(inspectionId: String, finalizedAt: Long?): String =
        sha256Hex(canonicalJson(InspectionSnapshotAssembler.assemble(database, inspectionId, finalizedAt)))

    @Test
    fun `mutating an excluded-domain field after finalize leaves the recomputed hash unchanged`() {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)
        val useCase = FinalizeInspectionUseCase(database, DbCompletenessChecker(database), ClockMs { now + 500 })
        val finalized = assertIs<FinalizeOutcome.Finalized>(useCase.finalize(ready.inspectionId))

        // updated_at 不在哈希域里（InspectionSnapshot 顶部说明明确排除）；finalize 谓词只挡"改内容"的路径，
        // 直接绕过谓词的 SQL 仍能碰到这一列，模拟"数据库文件被工具在排除域上动过手脚"。
        driver.execute(null, "UPDATE inspection_item SET updated_at = ${now + 9999} WHERE id = '${ready.itemId}'", 0)

        val recomputed = recompute(ready.inspectionId, finalized.finalizedAt)

        assertEquals(finalized.dataHash, recomputed, "updated_at is an excluded-domain field; it must not move the hash")
    }

    @Test
    fun `corrupting a hash-domain field via direct SQL makes the recomputed hash diverge from the stored one`() {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)
        val useCase = FinalizeInspectionUseCase(database, DbCompletenessChecker(database), ClockMs { now + 500 })
        val finalized = assertIs<FinalizeOutcome.Finalized>(useCase.finalize(ready.inspectionId))

        // status 是哈希域字段（items[].status）。inspection_item.updateStatusIfDraft 会被 finalize 谓词
        // 挡下（0 行），故用驱动原生 execute 直接改库，模拟"绕过谓词的腐坏"。
        driver.execute(null, "UPDATE inspection_item SET status = 'POOR' WHERE id = '${ready.itemId}'", 0)

        val recomputed = recompute(ready.inspectionId, finalized.finalizedAt)

        assertNotEquals(finalized.dataHash, recomputed, "status is a hash-domain field; corrupting it must be detectable on recompute")
    }
}
