package nz.myinspection.core.retention

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DB 往返测试，跑在 JdbcSqliteDriver 内存库上（真 SQLite）。`purgeContactInfo` 本身的机械行为
 * （NULL 守卫、行不删除、字段确被清空）已由 T1-SCHEMA-CORE 的 DbInvariantsTest/DbDownstreamQueriesTest
 * 钉住——这里只测 [ContactRetentionService] 加的那层业务判断："何时允许清理"与"清理只影响它自己"。
 */
class ContactRetentionServiceTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: MyInspectionDatabase
    private lateinit var uuid: Uuid7Generator
    private val now = DbTestFixtures.NOW

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MyInspectionDatabase.Schema.create(driver)
        db = MyInspectionDatabase(driver)
        uuid = Uuid7Generator()
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun insertTenancy(
        propertyId: String,
        endMs: Long?,
        tenantName: String = "J Doe",
        contact: String = "j@example.com",
    ): String {
        val id = uuid.next()
        db.tenancyQueries.insert(
            id = id, property_id = propertyId, start_ms = now - 86_400_000L, end_ms = endMs,
            tenant_name = tenantName, contact = contact, baseline_inspection_id = null,
            created_at = now, updated_at = now,
        )
        return id
    }

    /** 400 天前：保证晚于「12 个日历月前」（日历 12 个月最长 366 天），固定夹具端点，避免逐用例算边界。 */
    private fun expiredEndMs(): Long = now - 400L * 24 * 60 * 60 * 1000L

    @Test
    fun `listStatuses reflects each tenancy's own state, sorted deterministically by id`() {
        val propertyId = DbTestFixtures.insertProperty(db, uuid, now)
        val ongoing = insertTenancy(propertyId, endMs = null)
        val expired = insertTenancy(propertyId, endMs = expiredEndMs())
        val recent = insertTenancy(propertyId, endMs = now - 1_000L)

        val service = ContactRetentionService(db, ClockMs { now })
        val statuses = service.listStatuses()

        assertEquals(listOf(ongoing, expired, recent).sorted(), statuses.map { it.tenancyId })
        val byId = statuses.associateBy { it.tenancyId }
        assertEquals(ContactRetentionState.ACTIVE_TENANCY, byId.getValue(ongoing).state)
        assertEquals(ContactRetentionState.PURGEABLE, byId.getValue(expired).state)
        assertEquals(ContactRetentionState.AWAITING_EXPIRY, byId.getValue(recent).state)
    }

    @Test
    fun `purge clears the persisted row, not just the returned status`() {
        val propertyId = DbTestFixtures.insertProperty(db, uuid, now)
        val tenancyId = insertTenancy(propertyId, endMs = expiredEndMs())
        val service = ContactRetentionService(db, ClockMs { now })

        val result = service.purge(tenancyId)

        assertEquals(ContactRetentionState.PURGED, result.state)
        val row = db.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertNull(row.tenant_name, "the persisted row must have tenant_name cleared, not just the returned DTO")
        assertNull(row.contact, "the persisted row must have contact cleared, not just the returned DTO")
        assertEquals(now, row.purged_at)
        assertEquals(now, row.updated_at)
    }

    @Test
    fun `purge on tenancy A never touches tenancy B, even under the same property`() {
        val propertyId = DbTestFixtures.insertProperty(db, uuid, now)
        val a = insertTenancy(propertyId, endMs = expiredEndMs(), tenantName = "A", contact = "a@example.com")
        val b = insertTenancy(propertyId, endMs = expiredEndMs(), tenantName = "B", contact = "b@example.com")
        val service = ContactRetentionService(db, ClockMs { now })

        service.purge(a)

        val rowB = db.tenancyQueries.selectById(b).executeAsOne()
        assertEquals("B", rowB.tenant_name, "purging tenancy A must not clear tenancy B's contact info")
        assertEquals("b@example.com", rowB.contact)
        assertNull(rowB.purged_at, "purging tenancy A must not mark tenancy B as purged")
    }

    @Test
    fun `purge across two different properties is scoped to the target tenancy only`() {
        val propertyA = DbTestFixtures.insertProperty(db, uuid, now)
        val propertyB = DbTestFixtures.insertProperty(db, uuid, now + 1L)
        val tenancyA = insertTenancy(propertyA, endMs = expiredEndMs())
        val tenancyB = insertTenancy(propertyB, endMs = expiredEndMs())
        val service = ContactRetentionService(db, ClockMs { now })

        service.purge(tenancyA)

        val rowB = db.tenancyQueries.selectById(tenancyB).executeAsOne()
        assertNotNull(rowB.tenant_name, "purging a tenancy on property A must not touch a tenancy on property B")
        assertNull(rowB.purged_at)
    }

    @Test
    fun `purge rejects an unknown tenancy id`() {
        val service = ContactRetentionService(db, ClockMs { now })
        assertFailsWith<ContactPurgeRejected.TenancyNotFound> { service.purge("no-such-tenancy") }
    }

    @Test
    fun `purge rejects a tenancy that has not ended, and writes nothing`() {
        val propertyId = DbTestFixtures.insertProperty(db, uuid, now)
        val tenancyId = insertTenancy(propertyId, endMs = null)
        val service = ContactRetentionService(db, ClockMs { now })

        assertFailsWith<ContactPurgeRejected.TenancyNotEnded> { service.purge(tenancyId) }

        val row = db.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertEquals("J Doe", row.tenant_name, "a rejected purge must not clear contact fields")
        assertNull(row.purged_at)
    }

    @Test
    fun `purge rejects a tenancy still inside the retention floor, and writes nothing`() {
        val propertyId = DbTestFixtures.insertProperty(db, uuid, now)
        val tenancyId = insertTenancy(propertyId, endMs = now - 1_000L)
        val service = ContactRetentionService(db, ClockMs { now })

        val ex = assertFailsWith<ContactPurgeRejected.RetentionPeriodNotElapsed> { service.purge(tenancyId) }
        assertTrue(ex.expiresAtMs > now, "expiry must be in the future for a just-ended tenancy")

        val row = db.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertEquals("J Doe", row.tenant_name, "a rejected purge must not clear contact fields")
        assertNull(row.purged_at)
    }

    @Test
    fun `purge rejects a tenancy already purged`() {
        val propertyId = DbTestFixtures.insertProperty(db, uuid, now)
        val tenancyId = insertTenancy(propertyId, endMs = expiredEndMs())
        val service = ContactRetentionService(db, ClockMs { now })
        service.purge(tenancyId)

        assertFailsWith<ContactPurgeRejected.AlreadyPurged> { service.purge(tenancyId) }
    }
}
