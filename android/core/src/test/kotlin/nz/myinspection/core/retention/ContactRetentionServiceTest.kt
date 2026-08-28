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
        id: String = uuid.next(),
    ): String {
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
    fun `listStatuses sorts by tenancyId lexically, not by SQLite's physical insertion order`() {
        // 上一条测试的 id 都来自 Uuid7Generator（单调递增），插入序与字典序天然重合——删掉
        // ContactRetentionService.listStatuses 里的 sortedBy 那条测试也不会翻红。这里故意反过来插入
        // （字典序更大的先插），逼 SQLite 的物理返回序（近似插入序）与字典序相反，sortedBy 缺席时
        // 断言必败。
        val propertyId = DbTestFixtures.insertProperty(db, uuid, now)
        insertTenancy(propertyId, endMs = null, id = "zz-tenancy")
        insertTenancy(propertyId, endMs = null, id = "aa-tenancy")
        insertTenancy(propertyId, endMs = null, id = "mm-tenancy")

        val service = ContactRetentionService(db, ClockMs { now })

        assertEquals(
            listOf("aa-tenancy", "mm-tenancy", "zz-tenancy"),
            service.listStatuses().map { it.tenancyId },
        )
    }

    @Test
    fun `listStatuses returns a list that rejects element replacement through a MutableList cast`() {
        // 用 .set() 而非 .add()/.clear()：Kotlin `sortedBy` 对 size<=1 的输入走 listOf(单元素) 分支
        // （java.util.Collections.singletonList，本就拒一切结构性改动），size>1 时走 array.asList()
        // 分支（java.util.Arrays 的固定长度视图，同样拒 add/remove/clear，但**放行 .set()**）——
        // 两个分支单靠 add/clear 都测不出 ContactRetentionService.kt 里那层 Collections.unmodifiableList
        // 是否真的在起作用。.set() 是唯一能把两者分开的操作：不裹一层时它会静默成功、真的把返回值的第
        // 一个元素换掉。
        val propertyId = DbTestFixtures.insertProperty(db, uuid, now)
        insertTenancy(propertyId, endMs = null, id = "aa-tenancy")
        insertTenancy(propertyId, endMs = null, id = "bb-tenancy")
        val service = ContactRetentionService(db, ClockMs { now })

        val statuses = service.listStatuses()
        val before = statuses.toList()

        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (statuses as MutableList<TenancyRetentionStatus>)[0] = statuses[1]
        }
        assertEquals(before, statuses, "a rejected mutation attempt must leave the list unchanged")
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
    fun `purge deliberately reads and clears a soft-deleted historical tenancy`() {
        val propertyId = DbTestFixtures.insertProperty(db, uuid, now)
        val tenancyId = insertTenancy(propertyId, endMs = expiredEndMs())
        driver.execute(null, "UPDATE tenancy SET deleted_at = $now WHERE id = '$tenancyId'", 0)
        val service = ContactRetentionService(db, ClockMs { now })

        assertEquals(ContactRetentionState.PURGED, service.purge(tenancyId).state)

        val row = db.tenancyQueries.selectAnyById(tenancyId).executeAsOne()
        assertNull(row.tenant_name)
        assertNull(row.contact)
        assertEquals(now, row.purged_at)
        assertEquals(now, row.deleted_at, "privacy cleanup must preserve the historical soft-delete marker")
    }

    @Test
    fun `purge succeeds exactly at the expiry instant — the boundary is inclusive here too`() {
        // statusOf's `nowMs >= expiresAtMs` boundary is covered in ContactRetentionPolicyTest, but
        // purge() carries its own separate `now < expiresAtMs` rejection check (ContactRetentionService.kt) —
        // a `<` -> `<=` mutation there would survive every other test in this file (they all use clock
        // values well past or well before the boundary), so the boundary needs its own direct proof here.
        val propertyId = DbTestFixtures.insertProperty(db, uuid, now)
        val endMs = now - 400L * 24 * 60 * 60 * 1000L
        val tenancyId = insertTenancy(propertyId, endMs = endMs)
        val service = ContactRetentionService(db, ClockMs { contactExpiryMs(endMs) })

        val result = service.purge(tenancyId)

        assertEquals(ContactRetentionState.PURGED, result.state)
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
    fun `purge rejects a tenancy still inside the contact retention window, and writes nothing`() {
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
