package nz.myinspection.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 两条 CLAUDE.md 关键不变量的 JVM 内存库回归测试（JdbcSqliteDriver in-memory，非 mock）：
 *  1. finalize 后原始条目只读：对 FINALIZED 巡检下的 inspection_item 做 UPDATE 必须 0 行受影响
 *     （inspection_item 自身没有 finalized_at 列，闸门须 join/子查询父 inspection）。
 *  2. 既有租约没有 Ingoing 时，可把某次 Routine 巡检后指定为该 tenancy 的基线
 *     （用户已签认决策，见 specs/tasks/T1-SCHEMA-CORE.md「用户已签认决策」）。
 */
class DbInvariantsTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: MyInspectionDatabase

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MyInspectionDatabase.Schema.create(driver)
        database = MyInspectionDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun insertProperty(id: String, now: Long) {
        database.propertyQueries.insert(
            id = id,
            address = "12 Test St",
            kind = "RENTAL",
            is_boarding_house = 0,
            updated_at = now,
        )
    }

    @Test
    fun `update against a finalized inspection affects zero rows`() {
        val uuid = Uuid7Generator()
        val propertyId = uuid.next()
        val inspectionId = uuid.next()
        val roomInstanceId = uuid.next()
        val itemId = uuid.next()
        val now = 1_700_000_000_000L

        insertProperty(propertyId, now)
        database.inspectionQueries.insert(
            id = inspectionId,
            type = "ROUTINE",
            tenancy_id = null,
            scheduled_at = now,
            previous_inspection_id = null,
            baseline_inspection_id = null,
            status = "FINALIZED",
            finalized_at = now,
            data_hash = "deadbeef",
            updated_at = now,
        )
        database.roomInstanceQueries.insert(
            id = roomInstanceId,
            inspection_id = inspectionId,
            room_key = "BEDROOM",
            instance_no = 1,
            display_label = "Bedroom 1",
            updated_at = now,
        )
        database.inspectionItemQueries.insert(
            id = itemId,
            inspection_id = inspectionId,
            room_instance_id = roomInstanceId,
            stable_id = "wall.paint",
            status = "GOOD",
            note = null,
            wear_or_damage = null,
            updated_at = now,
        )

        val affected = database.inspectionItemQueries.updateStatusIfDraft(
            status = "POOR",
            note = "changed after finalize",
            updated_at = now + 1,
            id = itemId,
        ).value

        assertEquals(0L, affected, "updating an item under a FINALIZED inspection must affect 0 rows")
        val stillGood = database.inspectionItemQueries.selectById(itemId).executeAsOne()
        assertEquals("GOOD", stillGood.status)
    }

    @Test
    fun `update against a draft inspection succeeds`() {
        val uuid = Uuid7Generator()
        val propertyId = uuid.next()
        val inspectionId = uuid.next()
        val roomInstanceId = uuid.next()
        val itemId = uuid.next()
        val now = 1_700_000_000_000L

        insertProperty(propertyId, now)
        database.inspectionQueries.insert(
            id = inspectionId,
            type = "ROUTINE",
            tenancy_id = null,
            scheduled_at = now,
            previous_inspection_id = null,
            baseline_inspection_id = null,
            status = "DRAFT",
            finalized_at = null,
            data_hash = null,
            updated_at = now,
        )
        database.roomInstanceQueries.insert(
            id = roomInstanceId,
            inspection_id = inspectionId,
            room_key = "BEDROOM",
            instance_no = 1,
            display_label = "Bedroom 1",
            updated_at = now,
        )
        database.inspectionItemQueries.insert(
            id = itemId,
            inspection_id = inspectionId,
            room_instance_id = roomInstanceId,
            stable_id = "wall.paint",
            status = "GOOD",
            note = null,
            wear_or_damage = null,
            updated_at = now,
        )

        val affected = database.inspectionItemQueries.updateStatusIfDraft(
            status = "POOR",
            note = "changed while draft",
            updated_at = now + 1,
            id = itemId,
        ).value

        assertEquals(1L, affected, "updating an item under a DRAFT inspection must succeed")
        val updated = database.inspectionItemQueries.selectById(itemId).executeAsOne()
        assertEquals("POOR", updated.status)
    }

    @Test
    fun `finalizeIfDraft is a one-shot guard on inspection itself`() {
        val uuid = Uuid7Generator()
        val propertyId = uuid.next()
        val inspectionId = uuid.next()
        val now = 1_700_000_000_000L

        insertProperty(propertyId, now)
        database.inspectionQueries.insert(
            id = inspectionId,
            type = "ROUTINE",
            tenancy_id = null,
            scheduled_at = now,
            previous_inspection_id = null,
            baseline_inspection_id = null,
            status = "DRAFT",
            finalized_at = null,
            data_hash = null,
            updated_at = now,
        )

        val firstFinalize = database.inspectionQueries.finalizeIfDraft(
            finalized_at = now + 1,
            data_hash = "abc123",
            updated_at = now + 1,
            id = inspectionId,
        ).value
        assertEquals(1L, firstFinalize, "finalizing a DRAFT inspection must succeed exactly once")

        val secondFinalize = database.inspectionQueries.finalizeIfDraft(
            finalized_at = now + 2,
            data_hash = "should-not-land",
            updated_at = now + 2,
            id = inspectionId,
        ).value
        assertEquals(0L, secondFinalize, "re-finalizing an already FINALIZED inspection must affect 0 rows")

        val row = database.inspectionQueries.selectById(inspectionId).executeAsOne()
        assertEquals("abc123", row.data_hash, "the second call must not overwrite the original finalize hash")
    }

    @Test
    fun `tenancy with no Ingoing can designate a Routine inspection as its baseline`() {
        val uuid = Uuid7Generator()
        val propertyId = uuid.next()
        val tenancyId = uuid.next()
        val routineInspectionId = uuid.next()
        val now = 1_700_000_000_000L

        insertProperty(propertyId, now)
        // 既有租约：app 装机时租约已在进行中，从未建过 Ingoing 巡检（真实用户情形，见 findings.md #2）。
        database.tenancyQueries.insert(
            id = tenancyId,
            property_id = propertyId,
            start_ms = now - 86_400_000L,
            end_ms = null,
            tenant_name = "J Doe",
            contact = "j@example.com",
            baseline_inspection_id = null,
            updated_at = now,
        )
        database.inspectionQueries.insert(
            id = routineInspectionId,
            type = "ROUTINE",
            tenancy_id = tenancyId,
            scheduled_at = now,
            previous_inspection_id = null,
            baseline_inspection_id = null,
            status = "DRAFT",
            finalized_at = null,
            data_hash = null,
            updated_at = now,
        )

        val before = database.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertNull(before.baseline_inspection_id, "no baseline designated yet")

        database.tenancyQueries.updateBaselineInspection(
            baseline_inspection_id = routineInspectionId,
            updated_at = now + 1,
            id = tenancyId,
        )

        val after = database.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertEquals(routineInspectionId, after.baseline_inspection_id, "Routine inspection must resolve as the tenancy baseline")
    }
}
