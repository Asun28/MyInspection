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
        assertFailsWith<FinalizedInspectionReadOnlyException> {
            requireOriginalEntryWritten(updateAffected, "update item status")
        }

        val insertAffected = database.inspectionItemQueries.insert(
            id = uuid.next(), inspection_id = ready.inspectionId, room_instance_id = ready.roomInstanceId,
            stable_id = "late.item", status = "GOOD", note = null, wear_or_damage = null,
            created_at = now + 2, updated_at = now + 2,
        ).value
        assertEquals(0L, insertAffected, "inserting a new item under a FINALIZED inspection must affect 0 rows")
        assertFailsWith<FinalizedInspectionReadOnlyException> {
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

    @Test
    fun `if the inspection is finalized by a racing write during the completeness check, finalize rejects cleanly instead of throwing`() {
        val ready = FinalizeTestFixtures.buildMinimalCompleteInspection(database, uuid, now)
        // 模拟"读完备性之后、写 finalized_at 之前，另一条路径抢先把同一巡检 finalize 了"这类竞态窗口：
        // 用一个在被调用时产生写副作用的假 CompletenessPort，在单线程里确定性地复现该窗口，不依赖真实
        // 多线程——单一 JDBC 连接的 in-memory driver 不保证真并发访问的确定性行为（见 SQLDelight 文档
        // "in-memory drivers have a single connection, concurrent access will be blocked"），真拿两个线程
        // 去撞同一个连接只会验证 JDBC 驱动的锁行为，验不出 finalize 自己的事务边界对不对。
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
        // 一个总说"还缺东西"的假实现，证明 FinalizeInspectionUseCase 真的只依赖接口——这正是留给
        // T2-CAPTURE-CORE 未来接管的集成缝（见 CompletenessPort 顶部说明与 TD9）。
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
