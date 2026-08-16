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
    fun `createInspection throws when the tenancy id does not exist`() {
        // 调用方传错 tenancy id 是调用方错误，须当场炸——不是"这处物业没有基线"这一合法业务态。
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "EXIT")
        val bogusTenancyId = uuid.next() // 从未插入 tenancy 表

        assertFailsWith<IllegalStateException> {
            repo.createInspection("EXIT", propertyId, bogusTenancyId, templateId, scheduledAt = now)
        }
        assertTrue(database.inspectionQueries.selectActive().executeAsList().isEmpty(), "must not persist a dangling reference")
    }

    @Test
    fun `createInspection throws when the property id does not exist`() {
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val bogusPropertyId = uuid.next()

        assertFailsWith<IllegalStateException> {
            repo.createInspection("ROUTINE", bogusPropertyId, null, templateId, scheduledAt = now)
        }
    }

    @Test
    fun `createInspection throws when the template version id does not exist`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val bogusTemplateId = uuid.next()

        assertFailsWith<IllegalStateException> {
            repo.createInspection("ROUTINE", propertyId, null, bogusTemplateId, scheduledAt = now)
        }
    }

    @Test
    fun `createInspection throws when type does not match the template version's own type`() {
        // 否则会拿 EXIT 的评级域校验 ROUTINE 巡检的项（或反之）——状态合法性检查会判错。
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val exitTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "EXIT")

        assertFailsWith<IllegalArgumentException> {
            repo.createInspection("ROUTINE", propertyId, null, exitTemplate, scheduledAt = now)
        }
    }

    @Test
    fun `createInspection throws when the tenancy belongs to a different property`() {
        val propertyA = DbTestFixtures.insertProperty(database, uuid)
        val propertyB = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "EXIT")
        val tenancyOnB = CaptureTestFixtures.insertTenancy(database, uuid, propertyB)

        assertFailsWith<IllegalArgumentException> {
            repo.createInspection("EXIT", propertyA, tenancyOnB, templateId, scheduledAt = now)
        }
        assertTrue(database.inspectionQueries.selectActive().executeAsList().isEmpty())
    }

    @Test
    fun `creating an INGOING with no existing baseline assigns itself as the tenancy's baseline`() {
        // 不经手工调 tenancyQueries.updateBaselineInspection——建 INGOING 这一动作本身就该把指针立起来
        // （需求 §6「baseline_inspection = 该 tenancy 的 Ingoing」）。
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val ingoingTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "INGOING")
        val tenancyId = CaptureTestFixtures.insertTenancy(database, uuid, propertyId, baselineInspectionId = null)

        val ingoing = repo.createInspection("INGOING", propertyId, tenancyId, ingoingTemplate, scheduledAt = now)

        assertEquals(ingoing.inspectionId, database.tenancyQueries.selectById(tenancyId).executeAsOne().baseline_inspection_id)
    }

    @Test
    fun `a second INGOING never overwrites an already-assigned baseline`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val ingoingTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "INGOING")
        val tenancyId = CaptureTestFixtures.insertTenancy(database, uuid, propertyId, baselineInspectionId = null)

        val first = repo.createInspection("INGOING", propertyId, tenancyId, ingoingTemplate, scheduledAt = now)
        now += 1_000
        repo.createInspection("INGOING", propertyId, tenancyId, ingoingTemplate, scheduledAt = now)

        assertEquals(first.inspectionId, database.tenancyQueries.selectById(tenancyId).executeAsOne().baseline_inspection_id)
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

    @Test
    fun `baseline resolves uniformly for ROUTINE, not just EXIT`() {
        // 澄清后的契约（specs/tasks/T2-CAPTURE-CORE.md，R3 仲裁）：baseline 对所有巡检类型统一解析入库，
        // EXIT 只是主要消费者，不是唯一持有者。
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val ingoingTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "INGOING")
        val routineTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "ROUTINE")
        val tenancyId = CaptureTestFixtures.insertTenancy(database, uuid, propertyId, baselineInspectionId = null)

        val ingoing = repo.createInspection("INGOING", propertyId, tenancyId, ingoingTemplate, scheduledAt = now)
        now += 1_000
        val routine = repo.createInspection("ROUTINE", propertyId, tenancyId, routineTemplate, scheduledAt = now)

        assertEquals(ingoing.inspectionId, routine.baselineInspectionId)
    }

    @Test
    fun `baseline resolves uniformly for ANNUAL, not just EXIT`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val ingoingTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "INGOING")
        val annualTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "ANNUAL")
        val tenancyId = CaptureTestFixtures.insertTenancy(database, uuid, propertyId, baselineInspectionId = null)

        val ingoing = repo.createInspection("INGOING", propertyId, tenancyId, ingoingTemplate, scheduledAt = now)
        now += 1_000
        val annual = repo.createInspection("ANNUAL", propertyId, tenancyId, annualTemplate, scheduledAt = now)

        assertEquals(ingoing.inspectionId, annual.baselineInspectionId)
    }

    @Test
    fun `a second INGOING resolves its own baseline to the first, without the tenancy pointer moving`() {
        // 补 "a second INGOING never overwrites an already-assigned baseline" 遗漏的一面：那个测的是
        // tenancy 指针不被覆盖，这个测的是第二次 INGOING **自己**这一行的 baseline_inspection_id
        // 也按统一规则解析——此时 tenancy 已有基线（第一次 INGOING），第二次理应读到那个值。
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val ingoingTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "INGOING")
        val tenancyId = CaptureTestFixtures.insertTenancy(database, uuid, propertyId, baselineInspectionId = null)

        val first = repo.createInspection("INGOING", propertyId, tenancyId, ingoingTemplate, scheduledAt = now)
        now += 1_000
        val second = repo.createInspection("INGOING", propertyId, tenancyId, ingoingTemplate, scheduledAt = now)

        assertEquals(first.inspectionId, second.baselineInspectionId)
        assertEquals(first.inspectionId, database.tenancyQueries.selectById(tenancyId).executeAsOne().baseline_inspection_id)
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

    @Test
    fun `restoring a suppressed item during an in-progress draft creates the missing room instance`() {
        // BEDROOM 的唯一项在建巡检时已被抑制——巡检创建时只得到 KITCHEN。随后在这次巡检仍是 DRAFT 期间
        // 恢复该项：完备性查询立刻会把它算作活跃（天然不看创建时快照），但没有房间可挂就无法记录，
        // 空洞地报"整间都完成"。恢复必须把缺的房间补上，让这项真能被记录。
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        repo.setItemSuppression(propertyId, "BED-WALL-01", suppressed = true)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        assertEquals(1, created.roomInstanceIds.size, "BEDROOM must not exist yet")

        repo.setItemSuppression(propertyId, "BED-WALL-01", suppressed = false)

        val rooms = database.roomInstanceQueries.selectByInspection(created.inspectionId).executeAsList()
        val bedroom = rooms.singleOrNull { it.room_key == "BEDROOM" }
        assertTrue(bedroom != null, "restoring must create the missing BEDROOM room instance for this draft")

        // 而且这间房现在真能被记录——不是补了一行摆设。
        repo.setItemStatus(created.inspectionId, bedroom!!.id, "BED-WALL-01", "GOOD", null)
        assertTrue(repo.walkProgress(created.inspectionId).rooms.single { it.roomKey == "BEDROOM" }.isComplete)
    }

    @Test
    fun `restoring an item does not create a room instance for an already-finalized inspection`() {
        // 抑制/恢复跨巡检永久生效，但已 FINALIZED 的巡检快照写死不改——恢复不得往它头上补房间。
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        repo.setItemSuppression(propertyId, "BED-WALL-01", suppressed = true)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        CaptureTestFixtures.finalize(database, created.inspectionId, now)

        repo.setItemSuppression(propertyId, "BED-WALL-01", suppressed = false)

        val rooms = database.roomInstanceQueries.selectByInspection(created.inspectionId).executeAsList()
        assertTrue(rooms.none { it.room_key == "BEDROOM" })
    }

    @Test
    fun `setItemSuppression throws for a stable id that is not defined in any template`() {
        // 一个拼错/伪造的 stable_id 若放行，会铸出一条谁都匹配不到、只占着索引位的死 override 行。
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        CaptureTestFixtures.insertRoutineTemplate(database, uuid) // 定义 KIT-ROOM-01/KIT-BENCH-01/BED-WALL-01

        assertFailsWith<IllegalArgumentException> {
            repo.setItemSuppression(propertyId, "NO-SUCH-ITEM-01", suppressed = true)
        }
        assertTrue(database.propertyItemOverrideQueries.selectByProperty(propertyId).executeAsList().isEmpty())
    }

    @Test
    fun `setItemSuppression throws for a property id that does not exist`() {
        // 对称于 stable_id 校验：一个不存在的 property_id 若放行，同样会铸出一条死 override 行。
        CaptureTestFixtures.insertRoutineTemplate(database, uuid) // 定义 KIT-ROOM-01/KIT-BENCH-01/BED-WALL-01
        val bogusPropertyId = uuid.next()

        assertFailsWith<IllegalStateException> {
            repo.setItemSuppression(bogusPropertyId, "BED-WALL-01", suppressed = true)
        }
        assertTrue(database.propertyItemOverrideQueries.selectByProperty(bogusPropertyId).executeAsList().isEmpty())
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
    fun `setItemStatus throws when the room instance does not match the item's own room key`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        val bedroomId = created.roomInstanceIds[1]

        // KIT-BENCH-01 属于 KITCHEN，故意递一个 BEDROOM 的 room_instance_id。
        assertFailsWith<IllegalArgumentException> {
            repo.setItemStatus(created.inspectionId, bedroomId, "KIT-BENCH-01", "GOOD", null)
        }
        assertTrue(database.inspectionItemQueries.selectByInspection(created.inspectionId).executeAsList().isEmpty())
    }

    @Test
    fun `setItemStatus throws for a stable id currently suppressed for the property`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        val kitchenRoomId = created.roomInstanceIds.first()
        repo.setItemSuppression(propertyId, "KIT-BENCH-01", suppressed = true)

        assertFailsWith<IllegalArgumentException> {
            repo.setItemStatus(created.inspectionId, kitchenRoomId, "KIT-BENCH-01", "GOOD", null)
        }
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
    fun `walk progress rooms are ordered by template order, not database natural order`() {
        // 本夹具模板序是 KITCHEN,BEDROOM；`room_instance` 冻结查询按 idx_room_instance_active 的字典序
        // 回表则是 BEDROOM,KITCHEN（room_instances 相关测试的既有发现）——这条测试直接证明
        // loadRoomSnapshots 的 Kotlin 侧排序修正了它，而不是恰好凑对。
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)

        assertEquals(listOf("KITCHEN", "BEDROOM"), repo.walkProgress(created.inspectionId).rooms.map { it.roomKey })
    }

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
    fun `walk progress does not count an adverse item without a note as completed`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        val kitchenRoomId = created.roomInstanceIds.first()

        repo.setItemStatus(created.inspectionId, kitchenRoomId, "KIT-BENCH-01", "FAIR", null)

        val kitchen = repo.walkProgress(created.inspectionId).rooms.single { it.roomKey == "KITCHEN" }
        assertEquals(0, kitchen.completedItems, "an adverse status with no note must not count as completed")
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
    fun `a real item-linked photo row clears the adverse-only item gap through the repository`() {
        // 端到端：真插一条 photo(inspection_item_id = 该项的行 id) 并核对 missingPhotos 真的不再报它——
        // 只测纯函数（CompletenessTest）测不到 loadRoomSnapshots 里 photo.inspection_item_id → stable_id
        // 这段真实 DB 关联逻辑本身可能被改坏。
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        val kitchenRoomId = created.roomInstanceIds.first()
        repo.setItemStatus(created.inspectionId, kitchenRoomId, "KIT-BENCH-01", "POOR", "note")
        val itemId = database.inspectionItemQueries.selectByInspection(created.inspectionId).executeAsList()
            .single { it.stable_id == "KIT-BENCH-01" }.id

        assertEquals(listOf(MissingItemPhoto(kitchenRoomId, "KIT-BENCH-01")), repo.missingPhotos(created.inspectionId).missingItemPhotos)

        CaptureTestFixtures.insertRoomPhoto(database, uuid, kitchenRoomId, inspectionItemId = itemId)

        assertTrue(repo.missingPhotos(created.inspectionId).missingItemPhotos.isEmpty())
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
    fun `setWearOrDamage throws when the item belongs to a different inspection`() {
        // 拿一个属于另一次（还是 DRAFT 的 ROUTINE）巡检的 itemId，套一个恰好是 EXIT 且有基线的
        // inspectionId——两者各自独立解析，若不核对就能把 wear_or_damage 写进毫不相干的条目。
        val (exitId, _) = setUpExitWithBaseline(baselineStatus = "GOOD")

        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val otherTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val other = repo.createInspection("ROUTINE", propertyId, null, otherTemplate, scheduledAt = now)
        repo.setItemStatus(other.inspectionId, other.roomInstanceIds.first(), "KIT-BENCH-01", "POOR", "note")
        val foreignItemId = database.inspectionItemQueries.selectByInspection(other.inspectionId).executeAsList().single().id

        assertFailsWith<IllegalArgumentException> {
            repo.setWearOrDamage(exitId, foreignItemId, "DAMAGE")
        }
    }

    @Test
    fun `reverting status back to the baseline value clears a previously written wear_or_damage`() {
        val (exitId, roomId) = setUpExitWithBaseline(baselineStatus = "GOOD")
        repo.setItemStatus(exitId, roomId, "KIT-BENCH-01", "POOR", "scratched")
        val itemId = database.inspectionItemQueries.selectByInspection(exitId).executeAsList().single().id
        assertEquals(WearOrDamageOutcome.Written, repo.setWearOrDamage(exitId, itemId, "DAMAGE"))

        // 状态改回与基线一致——之前那条 DAMAGE 分类的前提（"有差异"）已经不成立，不能悄悄留着。
        repo.setItemStatus(exitId, roomId, "KIT-BENCH-01", "GOOD", null)

        assertNull(database.inspectionItemQueries.selectById(itemId).executeAsOne().wear_or_damage)
    }

    @Test
    fun `an idempotent status write with the same status preserves an existing wear_or_damage classification`() {
        // 只改备注、状态原地不动的幂等自动保存——不得把仍然有效的 EXIT 分类悄悄删掉
        // （与上一个测试互补：那个测的是"真的变了要清"，这个测的是"没变就不能清"）。
        val (exitId, roomId) = setUpExitWithBaseline(baselineStatus = "GOOD")
        repo.setItemStatus(exitId, roomId, "KIT-BENCH-01", "POOR", "scratched")
        val itemId = database.inspectionItemQueries.selectByInspection(exitId).executeAsList().single().id
        assertEquals(WearOrDamageOutcome.Written, repo.setWearOrDamage(exitId, itemId, "DAMAGE"))

        // 房间粒度自动保存再次写同一个 POOR——只是路过这间房、状态没变。
        repo.setItemStatus(exitId, roomId, "KIT-BENCH-01", "POOR", "scratched, more detail")

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
