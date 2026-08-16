package nz.myinspection.core.capture

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * InspectionRepository 的端到端测试，跑在真 SQLite 内存库上（JdbcSqliteDriver，同 TemplateStoreTest 的
 * 纪律）——finalize 守卫 / 唯一索引 / WHERE EXISTS 前提都是真的在起作用，不是 mock。
 */
class InspectionRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: MyInspectionDatabase
    private lateinit var uuid: Uuid7Generator
    private lateinit var repo: InspectionRepository

    private var now = CaptureTestFixtures.NOW

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MyInspectionDatabase.Schema.create(driver)
        database = MyInspectionDatabase(driver)
        uuid = Uuid7Generator()
        repo = InspectionRepository(database, uuid, ClockMs { now })
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun freshRepository(): InspectionRepository = InspectionRepository(database, uuid, ClockMs { now })

    // ---- 双轨引用解析 ----

    @Test
    fun `a property with no history gets no previous and no baseline`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)

        val created = repo.createInspection("ROUTINE", propertyId, tenancyId = null, templateVersionId = templateId, scheduledAt = now)

        assertNull(created.previousInspectionId)
        assertNull(created.baselineInspectionId)
        assertEquals(NoBaselineReason.NO_TENANCY, created.noBaselineReason)
    }

    @Test
    fun `previous inspection resolves to the most recent finalized inspection of the same property and type`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)

        val first = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        CaptureTestFixtures.finalize(database, first.inspectionId, now)

        now += 1_000
        val second = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)

        assertEquals(first.inspectionId, second.previousInspectionId)
    }

    @Test
    fun `a still-draft inspection is never used as previous`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)

        repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        now += 1_000
        val second = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)

        assertNull(second.previousInspectionId)
    }

    @Test
    fun `previous resolution never crosses type or property boundaries`() {
        val propertyA = DbTestFixtures.insertProperty(database, uuid)
        val propertyB = DbTestFixtures.insertProperty(database, uuid)
        val routineTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "ROUTINE")
        val exitTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "EXIT")

        val routineOnA = repo.createInspection("ROUTINE", propertyA, null, routineTemplate, scheduledAt = now)
        CaptureTestFixtures.finalize(database, routineOnA.inspectionId, now)
        now += 1_000

        val exitOnA = repo.createInspection("EXIT", propertyA, null, exitTemplate, scheduledAt = now)
        assertNull(exitOnA.previousInspectionId, "different type must not resolve as previous")

        val routineOnB = repo.createInspection("ROUTINE", propertyB, null, routineTemplate, scheduledAt = now)
        assertNull(routineOnB.previousInspectionId, "different property must not resolve as previous")
    }

    @Test
    fun `previous resolution never reaches into a finalized inspection scheduled later than this one`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)

        val later = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now + 5_000)
        CaptureTestFixtures.finalize(database, later.inspectionId, now + 5_000)

        // 建一次 scheduledAt 早于 `later` 的巡检——`later` 虽已 FINALIZED、同物业同类型，但时间上在它*之后*，
        // 不得被当作它的 previous（previous_inspection 是"时间上前一次"，不是"任意已存在的一次"）。
        val earlier = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)

        assertNull(earlier.previousInspectionId)
    }

    @Test
    fun `an unresolvable tenancy id is reported distinctly from a missing baseline pointer`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "EXIT")
        val bogusTenancyId = uuid.next() // 从未插入 tenancy 表

        val created = repo.createInspection("EXIT", propertyId, bogusTenancyId, templateId, scheduledAt = now)

        assertNull(created.baselineInspectionId)
        assertEquals(NoBaselineReason.TENANCY_NOT_FOUND, created.noBaselineReason)
    }

    @Test
    fun `baseline resolves from the tenancy's own baseline pointer`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val ingoingTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "INGOING")
        val exitTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "EXIT")
        val tenancyId = CaptureTestFixtures.insertTenancy(database, uuid, propertyId)

        val ingoing = repo.createInspection("INGOING", propertyId, tenancyId, ingoingTemplate, scheduledAt = now)
        CaptureTestFixtures.finalize(database, ingoing.inspectionId, now)
        database.tenancyQueries.updateBaselineInspection(baseline_inspection_id = ingoing.inspectionId, updated_at = now, id = tenancyId)

        now += 1_000
        val exit = repo.createInspection("EXIT", propertyId, tenancyId, exitTemplate, scheduledAt = now)

        assertEquals(ingoing.inspectionId, exit.baselineInspectionId)
    }

    @Test
    fun `no baseline is marked when the tenancy has not had one assigned yet`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "EXIT")
        val tenancyId = CaptureTestFixtures.insertTenancy(database, uuid, propertyId, baselineInspectionId = null)

        val exit = repo.createInspection("EXIT", propertyId, tenancyId, templateId, scheduledAt = now)

        assertNull(exit.baselineInspectionId)
        assertEquals(NoBaselineReason.NO_INGOING, exit.noBaselineReason)
    }

    // ---- 房间实例化 ----

    @Test
    fun `room instances are created one per distinct room key in template order`() {
        // `room_instance.selectByInspection`（冻结查询）没有 ORDER BY——SQLite 会挑一条能覆盖 WHERE 的索引
        // 扫（这里正是 idx_room_instance_active，按 room_key 字典序排），返回顺序因此不代表插入顺序。
        // 顺序断言必须走 CreatedInspection.roomInstanceIds（仓储在内存里按模板序组装的那份），
        // 逐 id 用主键点查（顺序不受索引影响）取回 room_key。
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)

        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)

        val roomKeysInOrder = created.roomInstanceIds.map { database.roomInstanceQueries.selectById(it).executeAsOne().room_key }
        assertEquals(listOf("KITCHEN", "BEDROOM"), roomKeysInOrder)
    }

    @Test
    fun `a room whose only item is suppressed for the property is not instantiated`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        repo.setItemSuppression(propertyId, "BED-WALL-01", suppressed = true)

        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)

        val rooms = database.roomInstanceQueries.selectByInspection(created.inspectionId).executeAsList()
        assertEquals(listOf("KITCHEN"), rooms.map { it.room_key })
    }

    @Test
    fun `restoring a suppressed item makes its room instantiate again`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        repo.setItemSuppression(propertyId, "BED-WALL-01", suppressed = true)
        repo.setItemSuppression(propertyId, "BED-WALL-01", suppressed = false)

        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)

        val roomKeysInOrder = created.roomInstanceIds.map { database.roomInstanceQueries.selectById(it).executeAsOne().room_key }
        assertEquals(listOf("KITCHEN", "BEDROOM"), roomKeysInOrder)
    }

    // ---- 状态写入合法性 ----

    @Test
    fun `setItemStatus rejects a status outside the item's allowed set`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        val kitchenRoomId = created.roomInstanceIds.first()

        assertFailsWith<IllegalArgumentException> {
            repo.setItemStatus(created.inspectionId, kitchenRoomId, "KIT-BENCH-01", "BOGUS", null)
        }
    }

    @Test
    fun `setItemStatus creates the row on first write and updates the same row on the next`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        val kitchenRoomId = created.roomInstanceIds.first()

        repo.setItemStatus(created.inspectionId, kitchenRoomId, "KIT-BENCH-01", "GOOD", null)
        repo.setItemStatus(created.inspectionId, kitchenRoomId, "KIT-BENCH-01", "FAIR", "chip in bench")

        val rows = database.inspectionItemQueries.selectByInspection(created.inspectionId).executeAsList()
            .filter { it.stable_id == "KIT-BENCH-01" }
        assertEquals(1, rows.size, "second write must update, not duplicate, the row")
        assertEquals("FAIR", rows.single().status)
        assertEquals("chip in bench", rows.single().note)
    }

    @Test
    fun `setItemStatus throws once the inspection is finalized`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        CaptureTestFixtures.finalize(database, created.inspectionId, now)

        assertFailsWith<IllegalStateException> {
            repo.setItemStatus(created.inspectionId, created.roomInstanceIds.first(), "KIT-BENCH-01", "GOOD", null)
        }
    }

    // ---- 走查进度 / process-death 恢复 ----

    @Test
    fun `walk progress reflects partial completion and a fresh repository instance sees the same state`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        val kitchenRoomId = created.roomInstanceIds.first()

        repo.setItemStatus(created.inspectionId, kitchenRoomId, "KIT-ROOM-01", "GOOD", null)
        // 房间全景照片尚未拍——KITCHEN 应仍不完整；BEDROOM 完全未走查。
        val progressBeforeDeath = repo.walkProgress(created.inspectionId)

        // 模拟进程死亡：新建仓储实例（同一份 DB），确认恢复到同一进度。
        val revived = freshRepository()
        val progressAfterRevival = revived.walkProgress(created.inspectionId)

        assertEquals(progressBeforeDeath, progressAfterRevival)
        val kitchen = progressAfterRevival.rooms.single { it.roomKey == "KITCHEN" }
        assertEquals(2, kitchen.totalItems)
        assertEquals(1, kitchen.completedItems)
        assertEquals(false, kitchen.isComplete)
        assertEquals(false, progressAfterRevival.isComplete)
    }

    @Test
    fun `walk progress becomes complete once every item is set and the required room photo exists`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        val kitchenRoomId = created.roomInstanceIds[0]
        val bedroomId = created.roomInstanceIds[1]

        repo.setItemStatus(created.inspectionId, kitchenRoomId, "KIT-ROOM-01", "GOOD", null)
        repo.setItemStatus(created.inspectionId, kitchenRoomId, "KIT-BENCH-01", "GOOD", null)
        repo.setItemStatus(created.inspectionId, bedroomId, "BED-WALL-01", "GOOD", null)
        CaptureTestFixtures.insertRoomPhoto(database, uuid, kitchenRoomId)

        val progress = repo.walkProgress(created.inspectionId)
        assertTrue(progress.isComplete)
    }

    // ---- 两级拍照完备性（仓储层装配的冒烟测试；细节边界见 CompletenessTest） ----

    @Test
    fun `missingPhotos reports the room panorama gap through the repository`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)

        val missing = repo.missingPhotos(created.inspectionId)
        assertEquals(listOf(MissingRoomPhoto(created.roomInstanceIds[0], "KITCHEN")), missing.missingRoomPanoramas)
    }

    @Test
    fun `missingNotes reports an adverse item lacking a note through the repository`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        val kitchenRoomId = created.roomInstanceIds.first()

        repo.setItemStatus(created.inspectionId, kitchenRoomId, "KIT-BENCH-01", "POOR", null)

        val missing = repo.missingNotes(created.inspectionId)
        assertEquals(listOf(MissingNote(kitchenRoomId, "KIT-BENCH-01")), missing)
    }

    @Test
    fun `suppressing an item after it was recorded removes it from both completeness queries`() {
        // 完备性查询天然不含被抑制项（卡片正文）——不是"创建时冻结的快照"，是**每次查询都现算**：
        // 一条项即便已经带着不利发现记录在案，只要事后被抑制，就该从两条完备性查询里同时消失。
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        val kitchenRoomId = created.roomInstanceIds.first()
        repo.setItemStatus(created.inspectionId, kitchenRoomId, "KIT-BENCH-01", "POOR", null)

        assertEquals(listOf(MissingItemPhoto(kitchenRoomId, "KIT-BENCH-01")), repo.missingPhotos(created.inspectionId).missingItemPhotos)
        assertEquals(listOf(MissingNote(kitchenRoomId, "KIT-BENCH-01")), repo.missingNotes(created.inspectionId))

        repo.setItemSuppression(propertyId, "KIT-BENCH-01", suppressed = true)

        assertTrue(repo.missingPhotos(created.inspectionId).missingItemPhotos.isEmpty())
        assertTrue(repo.missingNotes(created.inspectionId).isEmpty())
    }

    // ---- wear_or_damage（仅 EXIT + 与基线有差异） ----

    private fun setUpExitWithBaseline(baselineStatus: String): Triple<String, String, String> {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val ingoingTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "INGOING")
        val exitTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "EXIT")
        val tenancyId = CaptureTestFixtures.insertTenancy(database, uuid, propertyId)

        val ingoing = repo.createInspection("INGOING", propertyId, tenancyId, ingoingTemplate, scheduledAt = now)
        repo.setItemStatus(ingoing.inspectionId, ingoing.roomInstanceIds.first(), "KIT-BENCH-01", baselineStatus, null)
        CaptureTestFixtures.finalize(database, ingoing.inspectionId, now)
        database.tenancyQueries.updateBaselineInspection(baseline_inspection_id = ingoing.inspectionId, updated_at = now, id = tenancyId)

        now += 1_000
        val exit = repo.createInspection("EXIT", propertyId, tenancyId, exitTemplate, scheduledAt = now)
        return Triple(exit.inspectionId, exit.roomInstanceIds.first(), tenancyId)
    }

    @Test
    fun `setWearOrDamage rejects a non-EXIT inspection`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        repo.setItemStatus(created.inspectionId, created.roomInstanceIds.first(), "KIT-BENCH-01", "GOOD", null)
        val itemId = database.inspectionItemQueries.selectByInspection(created.inspectionId).executeAsList().single().id

        val outcome = repo.setWearOrDamage(created.inspectionId, itemId, "DAMAGE")
        assertEquals(WearOrDamageOutcome.NotExitType, outcome)
    }

    @Test
    fun `setWearOrDamage rejects an EXIT inspection with no baseline`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "EXIT")
        val created = repo.createInspection("EXIT", propertyId, null, templateId, scheduledAt = now)
        repo.setItemStatus(created.inspectionId, created.roomInstanceIds.first(), "KIT-BENCH-01", "POOR", "note")
        val itemId = database.inspectionItemQueries.selectByInspection(created.inspectionId).executeAsList().single().id

        val outcome = repo.setWearOrDamage(created.inspectionId, itemId, "DAMAGE")
        assertEquals(WearOrDamageOutcome.NoBaseline, outcome)
    }

    @Test
    fun `setWearOrDamage rejects when the current status matches the baseline`() {
        val (exitId, roomId) = setUpExitWithBaseline(baselineStatus = "GOOD")
        repo.setItemStatus(exitId, roomId, "KIT-BENCH-01", "GOOD", null)
        val itemId = database.inspectionItemQueries.selectByInspection(exitId).executeAsList().single().id

        val outcome = repo.setWearOrDamage(exitId, itemId, "FAIR_WEAR")
        assertEquals(WearOrDamageOutcome.NoDifference, outcome)
    }

    @Test
    fun `setWearOrDamage writes when the current status differs from baseline`() {
        val (exitId, roomId) = setUpExitWithBaseline(baselineStatus = "GOOD")
        repo.setItemStatus(exitId, roomId, "KIT-BENCH-01", "POOR", "scratched")
        val itemId = database.inspectionItemQueries.selectByInspection(exitId).executeAsList().single().id

        val outcome = repo.setWearOrDamage(exitId, itemId, "DAMAGE")

        assertEquals(WearOrDamageOutcome.Written, outcome)
        assertEquals("DAMAGE", database.inspectionItemQueries.selectById(itemId).executeAsOne().wear_or_damage)
    }

    @Test
    fun `setWearOrDamage rejects when the baseline never recorded that stable id`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val ingoingTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "INGOING")
        val exitTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "EXIT")
        val tenancyId = CaptureTestFixtures.insertTenancy(database, uuid, propertyId)

        val ingoing = repo.createInspection("INGOING", propertyId, tenancyId, ingoingTemplate, scheduledAt = now)
        // 基线巡检故意不给 KIT-BENCH-01 置状态。
        CaptureTestFixtures.finalize(database, ingoing.inspectionId, now)
        database.tenancyQueries.updateBaselineInspection(baseline_inspection_id = ingoing.inspectionId, updated_at = now, id = tenancyId)

        now += 1_000
        val exit = repo.createInspection("EXIT", propertyId, tenancyId, exitTemplate, scheduledAt = now)
        repo.setItemStatus(exit.inspectionId, exit.roomInstanceIds.first(), "KIT-BENCH-01", "POOR", "note")
        val itemId = database.inspectionItemQueries.selectByInspection(exit.inspectionId).executeAsList().single().id

        val outcome = repo.setWearOrDamage(exit.inspectionId, itemId, "DAMAGE")
        assertEquals(WearOrDamageOutcome.NoBaselineItem, outcome)
    }
}
