package nz.myinspection.core.capture

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.db.QueryResult
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

    /** 直接读 `inspection` 行本身——校验双轨引用等真的落了库，不是只活在返回的 DTO 里。 */
    private fun storedInspection(inspectionId: String) = database.inspectionQueries.selectById(inspectionId).executeAsOne()

    // ---- 双轨引用解析 ----

    @Test
    fun `a property with no history gets no previous and no baseline`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)

        val created = repo.createInspection("ROUTINE", propertyId, tenancyId = null, templateVersionId = templateId, scheduledAt = now)

        assertNull(created.previousInspectionId)
        assertNull(created.baselineInspectionId)
        assertEquals(NoBaselineReason.NO_TENANCY, created.noBaselineReason)
        val row = storedInspection(created.inspectionId)
        assertNull(row.previous_inspection_id)
        assertNull(row.baseline_inspection_id)
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
        assertEquals(first.inspectionId, storedInspection(second.inspectionId).previous_inspection_id)
    }

    @Test
    fun `a still-draft inspection is never used as previous`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)

        repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        now += 1_000
        val second = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)

        assertNull(second.previousInspectionId)
        assertNull(storedInspection(second.inspectionId).previous_inspection_id)
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
        assertNull(storedInspection(exitOnA.inspectionId).previous_inspection_id)

        val routineOnB = repo.createInspection("ROUTINE", propertyB, null, routineTemplate, scheduledAt = now)
        assertNull(routineOnB.previousInspectionId, "different property must not resolve as previous")
        assertNull(storedInspection(routineOnB.inspectionId).previous_inspection_id)
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
        assertNull(storedInspection(earlier.inspectionId).previous_inspection_id)
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
    fun `createInspection rejects a soft-deleted property`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        driver.execute(null, "UPDATE property SET deleted_at = $now WHERE id = '$propertyId'", 0)

        assertFailsWith<IllegalStateException> {
            repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        }
        assertTrue(database.inspectionQueries.selectActive().executeAsList().isEmpty())
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
    fun `createInspection rejects a soft-deleted template version`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        driver.execute(null, "UPDATE template_version SET deleted_at = $now WHERE id = '$templateId'", 0)

        assertFailsWith<IllegalStateException> {
            repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        }
        assertTrue(database.inspectionQueries.selectActive().executeAsList().isEmpty())
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
    fun `createInspection rejects a soft-deleted tenancy`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "EXIT")
        val tenancyId = CaptureTestFixtures.insertTenancy(database, uuid, propertyId)
        driver.execute(null, "UPDATE tenancy SET deleted_at = $now WHERE id = '$tenancyId'", 0)

        assertFailsWith<IllegalStateException> {
            repo.createInspection("EXIT", propertyId, tenancyId, templateId, scheduledAt = now)
        }
        assertTrue(database.inspectionQueries.selectActive().executeAsList().isEmpty())
    }

    @Test
    fun `creating an INGOING with no existing baseline assigns itself as the tenancy's baseline`() {
        // 不经手工调 baseline 查询——建 INGOING 这一动作本身就该经具名的初始入口把指针立起来
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

        now += 1_000
        val exit = repo.createInspection("EXIT", propertyId, tenancyId, exitTemplate, scheduledAt = now)

        assertEquals(ingoing.inspectionId, exit.baselineInspectionId)
        assertEquals(ingoing.inspectionId, storedInspection(exit.inspectionId).baseline_inspection_id)
    }

    @Test
    fun `no baseline is marked when the tenancy has not had one assigned yet`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "EXIT")
        val tenancyId = CaptureTestFixtures.insertTenancy(database, uuid, propertyId, baselineInspectionId = null)

        val exit = repo.createInspection("EXIT", propertyId, tenancyId, templateId, scheduledAt = now)

        assertNull(exit.baselineInspectionId)
        assertEquals(NoBaselineReason.NO_INGOING, exit.noBaselineReason)
        assertNull(storedInspection(exit.inspectionId).baseline_inspection_id)
    }

    @Test
    fun `baseline resolves uniformly for ROUTINE, not just EXIT`() {
        // 澄清后的契约（specs/archive/tasks/T2-CAPTURE-CORE.md，R3 仲裁）：baseline 对所有巡检类型统一解析入库，
        // EXIT 只是主要消费者，不是唯一持有者。
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val ingoingTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "INGOING")
        val routineTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "ROUTINE")
        val tenancyId = CaptureTestFixtures.insertTenancy(database, uuid, propertyId, baselineInspectionId = null)

        val ingoing = repo.createInspection("INGOING", propertyId, tenancyId, ingoingTemplate, scheduledAt = now)
        now += 1_000
        val routine = repo.createInspection("ROUTINE", propertyId, tenancyId, routineTemplate, scheduledAt = now)

        assertEquals(ingoing.inspectionId, routine.baselineInspectionId)
        assertEquals(ingoing.inspectionId, storedInspection(routine.inspectionId).baseline_inspection_id)
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
        assertEquals(ingoing.inspectionId, storedInspection(annual.inspectionId).baseline_inspection_id)
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
        assertEquals(first.inspectionId, storedInspection(second.inspectionId).baseline_inspection_id)
        assertEquals(first.inspectionId, database.tenancyQueries.selectById(tenancyId).executeAsOne().baseline_inspection_id)
    }

    // ---- 房间实例化 ----

    @Test
    fun `schema v4 migration adds the bounded property room configuration without rewriting v3 data`() {
        val v3Driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            v3Driver.execute(
                null,
                """CREATE TABLE property (
                    id TEXT NOT NULL PRIMARY KEY,
                    address TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    is_boarding_house INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    deleted_at INTEGER,
                    CHECK (kind IN ('RENTAL', 'OWNER_OCCUPIED')),
                    CHECK (is_boarding_house IN (0, 1))
                )""".trimIndent(),
                0,
            )
            v3Driver.execute(
                null,
                """INSERT INTO property (id, address, kind, is_boarding_house, created_at, updated_at)
                    VALUES ('property-1', 'preserved address', 'RENTAL', 0, 1, 1)""".trimIndent(),
                0,
            )

            MyInspectionDatabase.Schema.migrate(v3Driver, 3, 4)

            val columns = v3Driver.executeQuery(
                null,
                "PRAGMA table_info(property_room_config)",
                { cursor ->
                    val names = mutableListOf<String>()
                    while (cursor.next().value) names += checkNotNull(cursor.getString(1))
                    QueryResult.Value(names)
                },
                0,
            ).value
            assertEquals(
                listOf("id", "property_id", "room_key", "instance_count", "created_at", "updated_at", "deleted_at"),
                columns,
            )
            val preservedAddress = v3Driver.executeQuery(
                null,
                "SELECT address FROM property WHERE id = 'property-1'",
                { cursor ->
                    check(cursor.next().value)
                    QueryResult.Value(cursor.getString(0))
                },
                0,
            ).value
            assertEquals("preserved address", preservedAddress)

            fun insertCount(count: Int) {
                v3Driver.execute(
                    null,
                    """INSERT INTO property_room_config
                        (id, property_id, room_key, instance_count, created_at, updated_at)
                        VALUES ('config-$count', 'property-1', 'BEDROOM', $count, 1, 1)""".trimIndent(),
                    0,
                )
            }
            insertCount(1)
            assertFailsWith<Exception> { insertCount(0) }
            assertFailsWith<Exception> { insertCount(100) }
        } finally {
            v3Driver.close()
        }
    }

    @Test
    fun `current property room schema enforces the count bound and active business key`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)

        fun insertRaw(id: String, roomKey: String, count: Int) {
            driver.execute(
                null,
                """INSERT INTO property_room_config
                    (id, property_id, room_key, instance_count, created_at, updated_at)
                    VALUES ('$id', '$propertyId', '$roomKey', $count, 1, 1)""".trimIndent(),
                0,
            )
        }

        insertRaw("config-1", "BEDROOM", 1)
        assertFailsWith<Exception> { insertRaw("config-0", "BATHROOM", 0) }
        assertFailsWith<Exception> { insertRaw("config-100", "LOUNGE", 100) }
        assertFailsWith<Exception> { insertRaw("config-duplicate", "BEDROOM", 2) }

        driver.execute(null, "UPDATE property_room_config SET deleted_at = 2 WHERE id = 'config-1'", 0)
        insertRaw("config-replacement", "BEDROOM", 2)
        assertEquals(
            "config-replacement",
            database.propertyRoomConfigQueries.selectActiveByPropertyAndRoom(propertyId, "BEDROOM").executeAsOne().id,
        )
    }

    @Test
    fun `a persisted repeatable room count creates instances in template then instance order`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        repo.setRepeatableRoomCount(propertyId, roomKey = "BEDROOM", instanceCount = 2)

        val stored = database.propertyRoomConfigQueries
            .selectActiveByPropertyAndRoom(propertyId, "BEDROOM")
            .executeAsOne()
        assertEquals(2L, stored.instance_count)

        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        val identities = created.roomInstanceIds.map { id ->
            database.roomInstanceQueries.selectById(id).executeAsOne().let { it.room_key to it.instance_no }
        }
        assertEquals(
            listOf("KITCHEN" to 1L, "BEDROOM" to 1L, "BEDROOM" to 2L),
            identities,
        )
    }

    @Test
    fun `updating an existing repeatable room count drives a fresh repository and inspection`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        repo.setRepeatableRoomCount(propertyId, roomKey = "BEDROOM", instanceCount = 2)
        repo.setRepeatableRoomCount(propertyId, roomKey = "BEDROOM", instanceCount = 3)

        assertEquals(
            3L,
            database.propertyRoomConfigQueries
                .selectActiveByPropertyAndRoom(propertyId, "BEDROOM")
                .executeAsOne().instance_count,
        )
        val created = freshRepository().createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        assertEquals(
            listOf(1L, 2L, 3L),
            created.roomInstanceIds
                .map { database.roomInstanceQueries.selectById(it).executeAsOne() }
                .filter { it.room_key == "BEDROOM" }
                .map { it.instance_no },
        )
    }

    @Test
    fun `repeatable room count rejects non-repeatable rooms and values outside one through ninety-nine`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        CaptureTestFixtures.insertRoutineTemplate(database, uuid)

        assertFailsWith<IllegalArgumentException> {
            repo.setRepeatableRoomCount(propertyId, roomKey = "BEDROOM", instanceCount = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            repo.setRepeatableRoomCount(propertyId, roomKey = "BEDROOM", instanceCount = 100)
        }
        assertFailsWith<IllegalArgumentException> {
            repo.setRepeatableRoomCount(propertyId, roomKey = "KITCHEN", instanceCount = 2)
        }

        repo.setRepeatableRoomCount(propertyId, roomKey = "BEDROOM", instanceCount = 99)
        assertEquals(
            99L,
            database.propertyRoomConfigQueries.selectActiveByPropertyAndRoom(propertyId, "BEDROOM")
                .executeAsOne().instance_count,
        )
    }

    @Test
    fun `repeatable room count cannot change while the property has a draft inspection`() {
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)

        val propertyWithSingletonDraft = DbTestFixtures.insertProperty(database, uuid)
        repo.createInspection("ROUTINE", propertyWithSingletonDraft, null, templateId, scheduledAt = now)
        assertFailsWith<IllegalArgumentException> {
            repo.setRepeatableRoomCount(propertyWithSingletonDraft, roomKey = "BEDROOM", instanceCount = 2)
        }
        assertNull(
            database.propertyRoomConfigQueries
                .selectActiveByPropertyAndRoom(propertyWithSingletonDraft, "BEDROOM")
                .executeAsOneOrNull(),
        )

        val propertyWithRepeatableDraft = DbTestFixtures.insertProperty(database, uuid)
        repo.setRepeatableRoomCount(propertyWithRepeatableDraft, roomKey = "BEDROOM", instanceCount = 2)
        repo.createInspection("ROUTINE", propertyWithRepeatableDraft, null, templateId, scheduledAt = now)
        assertFailsWith<IllegalArgumentException> {
            repo.setRepeatableRoomCount(propertyWithRepeatableDraft, roomKey = "BEDROOM", instanceCount = 1)
        }
        assertEquals(
            2L,
            database.propertyRoomConfigQueries
                .selectActiveByPropertyAndRoom(propertyWithRepeatableDraft, "BEDROOM")
                .executeAsOne().instance_count,
        )
    }

    @Test
    fun `property room config SQL guards reject inserts and updates while a draft exists`() {
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)

        val insertPropertyId = DbTestFixtures.insertProperty(database, uuid)
        repo.createInspection("ROUTINE", insertPropertyId, null, templateId, scheduledAt = now)
        assertEquals(
            0L,
            database.propertyRoomConfigQueries.insert(
                id = uuid.next(), property_id = insertPropertyId, room_key = "BEDROOM", instance_count = 2,
                created_at = now, updated_at = now,
            ).value,
        )

        val updatePropertyId = DbTestFixtures.insertProperty(database, uuid)
        repo.setRepeatableRoomCount(updatePropertyId, roomKey = "BEDROOM", instanceCount = 2)
        val config = database.propertyRoomConfigQueries
            .selectActiveByPropertyAndRoom(updatePropertyId, "BEDROOM")
            .executeAsOne()
        repo.createInspection("ROUTINE", updatePropertyId, null, templateId, scheduledAt = now)
        assertEquals(
            0L,
            database.propertyRoomConfigQueries.updateCount(instance_count = 1, updated_at = now + 1, id = config.id).value,
        )
        assertEquals(
            2L,
            database.propertyRoomConfigQueries
                .selectActiveByPropertyAndRoom(updatePropertyId, "BEDROOM")
                .executeAsOne().instance_count,
        )
    }

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
    fun `a partially declared legacy template keeps undeclared item rooms as singletons`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = DbTestFixtures.insertTemplateVersion(database, uuid)
        CaptureTestFixtures.insertTemplateRoomDef(
            database, uuid, templateId, roomKey = "KITCHEN", repeatable = false, sort = 0, now = now,
        )
        CaptureTestFixtures.insertCheckItemDef(
            database, uuid, templateId, stableId = "kitchen.item", room = "KITCHEN", sort = 0, now = now,
        )
        CaptureTestFixtures.insertCheckItemDef(
            database, uuid, templateId, stableId = "bedroom.item", room = "BEDROOM", sort = 1, now = now,
        )

        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        val identities = created.roomInstanceIds.map { id ->
            database.roomInstanceQueries.selectById(id).executeAsOne().let { it.room_key to it.instance_no }
        }

        assertEquals(listOf("KITCHEN" to 1L, "BEDROOM" to 1L), identities)
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

    @Test
    fun `setItemSuppression rejects a soft-deleted property`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        driver.execute(null, "UPDATE property SET deleted_at = $now WHERE id = '$propertyId'", 0)

        assertFailsWith<IllegalStateException> {
            repo.setItemSuppression(propertyId, "BED-WALL-01", suppressed = true)
        }
        assertTrue(database.propertyItemOverrideQueries.selectByProperty(propertyId).executeAsList().isEmpty())
    }

    @Test
    fun `suppression and restoration are scoped to their own property, never leaking to another`() {
        val propertyA = DbTestFixtures.insertProperty(database, uuid)
        val propertyB = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)

        // 两处物业都先抑制 BED-WALL-01，各自建一次巡检——此刻两条巡检都只有 KITCHEN。
        repo.setItemSuppression(propertyA, "BED-WALL-01", suppressed = true)
        repo.setItemSuppression(propertyB, "BED-WALL-01", suppressed = true)
        val createdA = repo.createInspection("ROUTINE", propertyA, null, templateId, scheduledAt = now)
        val createdB = repo.createInspection("ROUTINE", propertyB, null, templateId, scheduledAt = now)
        assertEquals(1, database.roomInstanceQueries.selectByInspection(createdA.inspectionId).executeAsList().size)
        assertEquals(1, database.roomInstanceQueries.selectByInspection(createdB.inspectionId).executeAsList().size)

        // 只恢复 A：A 的草稿该补房间；B 的抑制状态与草稿都不该被碰——恢复路径按 property_id 过滤
        // （ensureRoomInstancesForRestoredItem），这条测试直接证明那个过滤条件真的在起作用。
        repo.setItemSuppression(propertyA, "BED-WALL-01", suppressed = false)

        assertEquals(
            setOf("KITCHEN", "BEDROOM"),
            database.roomInstanceQueries.selectByInspection(createdA.inspectionId).executeAsList().map { it.room_key }.toSet(),
        )
        assertEquals(
            listOf("KITCHEN"),
            database.roomInstanceQueries.selectByInspection(createdB.inspectionId).executeAsList().map { it.room_key },
        )
        val bOverride = database.propertyItemOverrideQueries.selectByProperty(propertyB).executeAsList().single { it.stable_id == "BED-WALL-01" }
        assertEquals(1L, bOverride.suppressed, "restoring A must not touch B's override row")
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
    fun `setWearOrDamage treats an unfinalized baseline as no baseline`() {
        // 基线指针在建 INGOING 时就自动指派（见 createInspection），但没有随之要求那次 INGOING 已 FINALIZED——
        // 拿一份仍可能被继续改的草稿去算"与基线的差异"没有意义。指针在但未 finalize 时，本方法把它当
        // 卡片正文的"无基线"标记处理，不是另立第五种结果。
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val ingoingTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "INGOING")
        val exitTemplate = CaptureTestFixtures.insertRoutineTemplate(database, uuid, type = "EXIT")
        val tenancyId = CaptureTestFixtures.insertTenancy(database, uuid, propertyId, baselineInspectionId = null)

        val ingoing = repo.createInspection("INGOING", propertyId, tenancyId, ingoingTemplate, scheduledAt = now)
        // 故意不 finalize：ingoing 仍是 DRAFT，但已经通过自动指派成了 tenancy 的基线指针。
        now += 1_000
        val exit = repo.createInspection("EXIT", propertyId, tenancyId, exitTemplate, scheduledAt = now)
        assertEquals(ingoing.inspectionId, exit.baselineInspectionId, "sanity: EXIT's own baseline pointer is set")
        repo.setItemStatus(exit.inspectionId, exit.roomInstanceIds.first(), "KIT-BENCH-01", "POOR", "note")
        val itemId = database.inspectionItemQueries.selectByInspection(exit.inspectionId).executeAsList().single().id

        val outcome = repo.setWearOrDamage(exit.inspectionId, itemId, "DAMAGE")

        assertEquals(WearOrDamageOutcome.NoBaseline, outcome)
        assertNull(database.inspectionItemQueries.selectById(itemId).executeAsOne().wear_or_damage)
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
    fun `setWearOrDamage matches the same room instance regardless of baseline item insertion order`() {
        listOf(listOf(1L, 2L), listOf(2L, 1L)).forEachIndexed { index, insertionOrder ->
            val propertyId = DbTestFixtures.insertProperty(database, uuid)
            val templateVersion = index.toLong() + 1L
            val ingoingTemplate = CaptureTestFixtures.insertRoutineTemplate(
                database, uuid, type = "INGOING", version = templateVersion,
            )
            val exitTemplate = CaptureTestFixtures.insertRoutineTemplate(
                database, uuid, type = "EXIT", version = templateVersion,
            )
            val tenancyId = CaptureTestFixtures.insertTenancy(database, uuid, propertyId)
            repo.setRepeatableRoomCount(propertyId, roomKey = "BEDROOM", instanceCount = 2)

            val ingoing = repo.createInspection("INGOING", propertyId, tenancyId, ingoingTemplate, scheduledAt = now)
            val baselineBedrooms = ingoing.roomInstanceIds
                .map { database.roomInstanceQueries.selectById(it).executeAsOne() }
                .filter { it.room_key == "BEDROOM" }
                .associateBy { it.instance_no }
            insertionOrder.forEach { instanceNo ->
                val status = if (instanceNo == 1L) "GOOD" else "POOR"
                repo.setItemStatus(
                    ingoing.inspectionId,
                    baselineBedrooms.getValue(instanceNo).id,
                    "BED-WALL-01",
                    status,
                    note = null,
                )
            }
            CaptureTestFixtures.finalize(database, ingoing.inspectionId, now)

            now += 1_000
            val exit = repo.createInspection("EXIT", propertyId, tenancyId, exitTemplate, scheduledAt = now)
            val exitBedroom2 = exit.roomInstanceIds
                .map { database.roomInstanceQueries.selectById(it).executeAsOne() }
                .single { it.room_key == "BEDROOM" && it.instance_no == 2L }
            repo.setItemStatus(exit.inspectionId, exitBedroom2.id, "BED-WALL-01", "POOR", note = null)
            val exitItemId = database.inspectionItemQueries.selectByInspection(exit.inspectionId).executeAsList()
                .single { it.room_instance_id == exitBedroom2.id && it.stable_id == "BED-WALL-01" }.id

            assertEquals(
                WearOrDamageOutcome.NoDifference,
                repo.setWearOrDamage(exit.inspectionId, exitItemId, "DAMAGE"),
                "BEDROOM #2 must compare with baseline BEDROOM #2 for insertion order $insertionOrder",
            )
            now += 1_000
        }
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

        now += 1_000
        val exit = repo.createInspection("EXIT", propertyId, tenancyId, exitTemplate, scheduledAt = now)
        repo.setItemStatus(exit.inspectionId, exit.roomInstanceIds.first(), "KIT-BENCH-01", "POOR", "note")
        val itemId = database.inspectionItemQueries.selectByInspection(exit.inspectionId).executeAsList().single().id

        val outcome = repo.setWearOrDamage(exit.inspectionId, itemId, "DAMAGE")
        assertEquals(WearOrDamageOutcome.NoBaselineItem, outcome)
    }

    // ---- 时间戳出自注入的 Clock，不是系统时钟 ----

    @Test
    fun `createInspection persists created_at and updated_at from the injected clock`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)

        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)

        val inspectionRow = storedInspection(created.inspectionId)
        assertEquals(now, inspectionRow.created_at)
        assertEquals(now, inspectionRow.updated_at)
        val roomRow = database.roomInstanceQueries.selectById(created.roomInstanceIds.first()).executeAsOne()
        assertEquals(now, roomRow.created_at)
        assertEquals(now, roomRow.updated_at)
    }

    @Test
    fun `setItemStatus persists updated_at from the injected clock and never changes created_at on update`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)
        val kitchenRoomId = created.roomInstanceIds.first()

        repo.setItemStatus(created.inspectionId, kitchenRoomId, "KIT-BENCH-01", "GOOD", null)
        val firstRow = database.inspectionItemQueries.selectByInspection(created.inspectionId).executeAsList().single()
        assertEquals(now, firstRow.created_at)
        assertEquals(now, firstRow.updated_at)

        now += 1_000
        repo.setItemStatus(created.inspectionId, kitchenRoomId, "KIT-BENCH-01", "FAIR", "chip")
        val secondRow = database.inspectionItemQueries.selectByInspection(created.inspectionId).executeAsList().single()
        assertEquals(firstRow.created_at, secondRow.created_at, "created_at must not change on update")
        assertEquals(now, secondRow.updated_at)
    }

    @Test
    fun `setWearOrDamage persists updated_at from the injected clock`() {
        val (exitId, roomId) = setUpExitWithBaseline(baselineStatus = "GOOD")
        repo.setItemStatus(exitId, roomId, "KIT-BENCH-01", "POOR", "scratched")
        val itemId = database.inspectionItemQueries.selectByInspection(exitId).executeAsList().single().id

        now += 1_000
        repo.setWearOrDamage(exitId, itemId, "DAMAGE")

        assertEquals(now, database.inspectionItemQueries.selectById(itemId).executeAsOne().updated_at)
    }

    @Test
    fun `setItemSuppression persists created_at and updated_at from the injected clock`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        CaptureTestFixtures.insertRoutineTemplate(database, uuid)

        repo.setItemSuppression(propertyId, "BED-WALL-01", suppressed = true)
        val row = database.propertyItemOverrideQueries.selectByProperty(propertyId).executeAsList().single()
        assertEquals(now, row.created_at)
        assertEquals(now, row.updated_at)

        now += 1_000
        repo.setItemSuppression(propertyId, "BED-WALL-01", suppressed = false)
        val updatedRow = database.propertyItemOverrideQueries.selectByProperty(propertyId).executeAsList().single()
        assertEquals(row.created_at, updatedRow.created_at, "created_at must not change on update")
        assertEquals(now, updatedRow.updated_at)
    }

    @Test
    fun `a room instance created by the restore path persists created_at and updated_at from the injected clock`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = CaptureTestFixtures.insertRoutineTemplate(database, uuid)
        repo.setItemSuppression(propertyId, "BED-WALL-01", suppressed = true)
        val created = repo.createInspection("ROUTINE", propertyId, null, templateId, scheduledAt = now)

        now += 1_000
        repo.setItemSuppression(propertyId, "BED-WALL-01", suppressed = false)

        val bedroom = database.roomInstanceQueries.selectByInspection(created.inspectionId).executeAsList().single { it.room_key == "BEDROOM" }
        assertEquals(now, bedroom.created_at)
        assertEquals(now, bedroom.updated_at)
    }
}
