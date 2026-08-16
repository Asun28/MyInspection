package nz.myinspection.core.finalize

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nz.myinspection.core.canon.canonicalJson
import nz.myinspection.core.canon.sha256Hex
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator

/**
 * [FinalizeInspectionUseCase]：ADR-0003 事务序端到端（① 完备性 → ② 物化快照 → ③ 哈希 → ④ 原子写），
 * 加卡文点名的进攻性测试（对已 FINALIZED 巡检直接调写接口 → 0 行）与重复 finalize 幂等拒绝。
 */
class FinalizeInspectionUseCaseTest {
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

    private fun fixedClock(atMs: Long): ClockMs = ClockMs { atMs }

    @Test
    fun `a complete inspection finalizes with an atomically written finalized_at and data_hash`() {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)
        val useCase = FinalizeInspectionUseCase(database, DbCompletenessChecker(database), fixedClock(now + 500))

        val outcome = useCase.finalize(ready.inspectionId)

        val finalized = assertIs<FinalizeOutcome.Finalized>(outcome)
        assertEquals(now + 500, finalized.finalizedAt)

        val row = database.inspectionQueries.selectById(ready.inspectionId).executeAsOne()
        assertEquals("FINALIZED", row.status)
        assertEquals(now + 500, row.finalized_at)
        assertEquals(finalized.dataHash, row.data_hash)

        // 落库的 data_hash 必须真是"用同一个 finalizedAt 装配出的快照"的哈希——不是随手算的一个值。
        val expectedSnapshot = InspectionSnapshotAssembler.assemble(database, ready.inspectionId, finalizedAt = now + 500)
        assertEquals(sha256Hex(canonicalJson(expectedSnapshot)), finalized.dataHash)
    }

    @Test
    fun `finalize on an unknown inspection id is rejected as not found and writes nothing`() {
        val useCase = FinalizeInspectionUseCase(database, DbCompletenessChecker(database), fixedClock(now))

        val outcome = useCase.finalize(uuid.next())

        assertIs<FinalizeOutcome.RejectedNotFound>(outcome)
    }

    @Test
    fun `an inspection missing an answered item is rejected with the missing item listed and stays DRAFT`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        FinalizeTestFixtures.insertCheckItemDef(database, uuid, templateVersionId, stableId = "wall.paint", sort = 1, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        // 房间已建，但没有人回答 wall.paint。
        val useCase = FinalizeInspectionUseCase(database, DbCompletenessChecker(database), fixedClock(now + 1))

        val outcome = useCase.finalize(inspectionId)

        val rejected = assertIs<FinalizeOutcome.RejectedIncomplete>(outcome)
        assertTrue(rejected.result.itemsMissingStatus.any { it.stableId == "wall.paint" })

        val row = database.inspectionQueries.selectById(inspectionId).executeAsOne()
        assertEquals("DRAFT", row.status, "rejection must not touch the row at all")
        assertNull(row.finalized_at)
        assertNull(row.data_hash)
    }

    @Test
    fun `an inspection missing a mandatory photo is rejected with the missing item listed`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        FinalizeTestFixtures.insertCheckItemDef(
            database, uuid, templateVersionId, stableId = "room.panorama", photoRule = "ROOM_PANORAMA", sort = 1, now = now,
        )
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, stableId = "room.panorama", now = now)
        // 没有补拍房间全景照片。
        val useCase = FinalizeInspectionUseCase(database, DbCompletenessChecker(database), fixedClock(now + 1))

        val outcome = useCase.finalize(inspectionId)

        val rejected = assertIs<FinalizeOutcome.RejectedIncomplete>(outcome)
        assertEquals(listOf(MissingItem(roomInstanceId, "room.panorama")), rejected.result.itemsMissingMandatoryPhoto)
    }

    @Test
    fun `repeat finalize is idempotently rejected and does not overwrite the original hash`() {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)
        val useCase = FinalizeInspectionUseCase(database, DbCompletenessChecker(database), fixedClock(now + 100))

        val first = assertIs<FinalizeOutcome.Finalized>(useCase.finalize(ready.inspectionId))

        val secondUseCase = FinalizeInspectionUseCase(database, DbCompletenessChecker(database), fixedClock(now + 200))
        val second = secondUseCase.finalize(ready.inspectionId)

        assertIs<FinalizeOutcome.RejectedAlreadyFinalized>(second)
        val row = database.inspectionQueries.selectById(ready.inspectionId).executeAsOne()
        assertEquals(first.dataHash, row.data_hash, "the second call must not overwrite the original finalize hash")
        assertEquals(first.finalizedAt, row.finalized_at)
    }

    @Test
    fun `after finalize, direct writes against the original items are 0 rows at the SQL layer, and requireOriginalEntryWritten turns that into an explicit failure at the use-case layer`() {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)
        val useCase = FinalizeInspectionUseCase(database, DbCompletenessChecker(database), fixedClock(now + 1))
        assertIs<FinalizeOutcome.Finalized>(useCase.finalize(ready.inspectionId))

        val updateAffected = database.inspectionItemQueries.updateStatusIfDraft(
            status = "POOR", note = "tampered", updated_at = now + 2, id = ready.itemId,
        ).value
        assertEquals(0L, updateAffected, "writing to an item under a FINALIZED inspection must affect 0 rows")
        // 卡文：「本卡在用例层再挡一道」——裸的 0 行不是本卡对这条纪律的兑现，调用方必须把它转成显式错误
        // 才算挡住；requireOriginalEntryWritten 就是这道闸，直接调写接口对 FINALIZED 巡检 → 抛出。
        assertFailsWith<OriginalEntryWriteRejectedException> {
            requireOriginalEntryWritten(updateAffected, "update item status")
        }

        val insertAffected = database.inspectionItemQueries.insert(
            id = uuid.next(), inspection_id = ready.inspectionId, room_instance_id = ready.roomInstanceId,
            stable_id = "late.item", status = "GOOD", note = null, wear_or_damage = null,
            created_at = now + 2, updated_at = now + 2,
        ).value
        assertEquals(0L, insertAffected, "inserting a new item under a FINALIZED inspection must affect 0 rows")
        assertFailsWith<OriginalEntryWriteRejectedException> {
            requireOriginalEntryWritten(insertAffected, "insert new item")
        }
    }

    @Test
    fun `requireOriginalEntryWritten is transparent to a legitimate DRAFT write`() {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)

        val affected = database.inspectionItemQueries.updateStatusIfDraft(
            status = "POOR", note = "still drafting", updated_at = now + 1, id = ready.itemId,
        ).value

        // 不抛——合法写路径对这道闸完全透明。
        requireOriginalEntryWritten(affected, "update item status")
        assertEquals(1L, affected)
    }

    /**
     * 用一个在被调用时产生写副作用的假 [CompletenessPort]，确定性地复现"读完备性之后、写
     * `finalized_at` 之前，另一条路径抢先把同一巡检 finalize 了"：`finalizeIfDraft` 的
     * `affected != 1` 分支必须干净返回 `RejectedAlreadyFinalized`，不能让调用方看到一个未处理的异常。
     *
     * 本测试用单个 JDBC 连接确定性地驱动这条分支，不代表"跨连接并发写同一行"的锁/隔离行为——那需要
     * 真正独立的两个连接，已知 pinned 的 sqlite-jdbc 在 `cache=shared` 模式下对表级锁冲突直接抛
     * `SQLITE_LOCKED_SHAREDCACHE`（不响应 `busy_timeout`，是驱动限制而非代码缺陷，见 lesson L221），
     * 该差距待 R5 登记为技术债，此处不重复展开。下面这条测试改用同连接内的写副作用来证明事务边界本身
     * 生效：一个抛异常前先写一行的 [CompletenessPort]，其写入必须随异常一起被回滚。
     */
    @Test
    fun `if a seam inside the transaction throws after writing, that write is rolled back with the transaction`() {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)
        val poisonId = uuid.next()
        // 通过同一个 database 对象在事务内先写一行、再抛异常——只有当 finalize() 的整个流程真的共享
        // 同一个事务时，这行写才会随异常一起回滚；若 transactionWithResult 被摘掉换成裸调用，这行写
        // 会独立提交、在异常抛出后依然存在。
        val poisonedCompleteness = CompletenessPort {
            database.supplementQueries.insert(
                id = poisonId, inspection_id = ready.inspectionId, created_at = now, text = "should not survive",
                prev_hash = "0".repeat(64), chain_hash = "1".repeat(64), updated_at = now,
            )
            throw RuntimeException("simulated failure after a side-effect write, before the final write")
        }
        val useCase = FinalizeInspectionUseCase(database, poisonedCompleteness, fixedClock(now + 1))

        assertFailsWith<RuntimeException> { useCase.finalize(ready.inspectionId) }

        assertNull(
            database.supplementQueries.selectById(poisonId).executeAsOneOrNull(),
            "a write made earlier in the same transaction must be rolled back when a later step throws",
        )
        val row = database.inspectionQueries.selectById(ready.inspectionId).executeAsOne()
        assertEquals("DRAFT", row.status, "the inspection itself must remain untouched")
    }

    /**
     * [CompletenessPort] 契约要求 `check()` 只读（见其 KDoc）——但一个违反契约的实现若真的写了东西，
     * 又通过**正常返回**（不是异常）报"不完整"，这条路径不同于上面那条：`return@transactionWithResult`
     * 正常返回本会让 `transactionWithResult` **提交**整个事务，写副作用不会随异常回滚机制被撤销。
     * 这正是 `rollback(rejected)`（而非 `return rejected`）存在的理由——同一个假 `CompletenessPort`，
     * 这次不抛异常、只是老老实实报"缺东西"，写入照样必须被撤销，调用方也照样拿到 `RejectedIncomplete`。
     */
    @Test
    fun `a write performed during a rejected completeness check is rolled back even when the port returns normally`() {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)
        val poisonId = uuid.next()
        val poisonedButNormalCompleteness = CompletenessPort { inspectionId ->
            database.supplementQueries.insert(
                id = poisonId, inspection_id = inspectionId, created_at = now, text = "should not survive either",
                prev_hash = "0".repeat(64), chain_hash = "2".repeat(64), updated_at = now,
            )
            CompletenessResult(
                itemsMissingStatus = listOf(MissingItem(ready.roomInstanceId, "still.missing")),
                itemsMissingMandatoryPhoto = emptyList(),
            )
        }
        val useCase = FinalizeInspectionUseCase(database, poisonedButNormalCompleteness, fixedClock(now + 1))

        val outcome = useCase.finalize(ready.inspectionId)

        assertIs<FinalizeOutcome.RejectedIncomplete>(outcome, "the rejection result must still reach the caller")
        assertNull(
            database.supplementQueries.selectById(poisonId).executeAsOneOrNull(),
            "a write made during a normally-returning-but-rejected completeness check must be rolled back too",
        )
        val row = database.inspectionQueries.selectById(ready.inspectionId).executeAsOne()
        assertEquals("DRAFT", row.status)
    }

    @Test
    fun `if the inspection is finalized by a racing write during the completeness check, finalize rejects cleanly instead of throwing`() {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)
        val racingCompleteness = CompletenessPort { inspectionId ->
            val raceAffected = database.inspectionQueries.finalizeIfDraft(
                finalized_at = now + 1, data_hash = "raced-in-first", updated_at = now + 1, id = inspectionId,
            ).value
            check(raceAffected == 1L) { "race fixture itself failed to land" }
            CompletenessResult(itemsMissingStatus = emptyList(), itemsMissingMandatoryPhoto = emptyList())
        }
        val useCase = FinalizeInspectionUseCase(database, racingCompleteness, fixedClock(now + 500))

        val outcome = useCase.finalize(ready.inspectionId)

        assertIs<FinalizeOutcome.RejectedAlreadyFinalized>(
            outcome,
            "the transaction's own re-check at write time must see the racing write and return cleanly, not throw",
        )
        val row = database.inspectionQueries.selectById(ready.inspectionId).executeAsOne()
        assertEquals("raced-in-first", row.data_hash, "the racing write inside the transaction must be the one that stuck")
        assertEquals(now + 1, row.finalized_at)
    }

    @Test
    fun `the completeness check is a swappable port, not hard-wired to the default DB implementation`() {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)
        // 一个总说"还缺东西"的假实现，证明 FinalizeInspectionUseCase 真的只依赖接口，不依赖
        // DbCompletenessChecker 的具体实现（见 CompletenessPort 顶部说明的集成缝）。
        val alwaysIncomplete = CompletenessPort {
            CompletenessResult(
                itemsMissingStatus = listOf(MissingItem(ready.roomInstanceId, "injected.missing")),
                itemsMissingMandatoryPhoto = emptyList(),
            )
        }
        val useCase = FinalizeInspectionUseCase(database, alwaysIncomplete, fixedClock(now + 1))

        val outcome = useCase.finalize(ready.inspectionId)

        val rejected = assertIs<FinalizeOutcome.RejectedIncomplete>(outcome)
        assertEquals("injected.missing", rejected.result.itemsMissingStatus.single().stableId)
    }
}
