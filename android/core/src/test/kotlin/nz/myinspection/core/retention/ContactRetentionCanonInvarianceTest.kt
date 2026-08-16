package nz.myinspection.core.retention

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nz.myinspection.core.canon.canonicalJson
import nz.myinspection.core.canon.sha256Hex
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Tenancy
import nz.myinspection.core.db.Uuid7Generator
import nz.myinspection.core.model.InspectionItemSnapshot
import nz.myinspection.core.model.InspectionSnapshot
import nz.myinspection.core.model.PhotoSnapshot
import nz.myinspection.core.model.PropertySnapshot
import nz.myinspection.core.model.TemplateSnapshot
import nz.myinspection.core.model.TenancySnapshot
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 卡片 dod_assert 的直接证据：「清理后 verifyChain/data_hash 复验仍绿」——因为 T1-CANON-HASH 的哈希域
 * 刻意不含 tenant_name/contact（[TenancySnapshot] 顶部注释 + `InspectionSnapshotTest`「exactly the
 * declared hash-domain shape」）。这里不满足于引用那份既有测试的结论，而是端到端跑一遍：真建 DB 行 →
 * 用它的字段投影出 [InspectionSnapshot] → 算一次 data_hash → 真执行 [ContactRetentionService.purge] →
 * 重新读回、重新投影 → 再算一次 → 两次哈希必须相等。
 */
class ContactRetentionCanonInvarianceTest {
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

    private fun snapshotFor(
        tenancyRow: Tenancy,
        propertyId: String,
        templateVersionId: String,
        inspectionId: String,
    ) = InspectionSnapshot(
        id = inspectionId,
        type = "EXIT",
        tenancyId = tenancyRow.id,
        scheduledAt = now,
        finalizedAt = now + 1,
        previousInspectionId = null,
        baselineInspectionId = null,
        property = PropertySnapshot(id = propertyId, address = "12 Test St", kind = "RENTAL", isBoardingHouse = false),
        tenancy = TenancySnapshot(id = tenancyRow.id, startMs = tenancyRow.start_ms, endMs = tenancyRow.end_ms),
        template = TemplateSnapshot(id = templateVersionId, type = "EXIT", version = 1, contentHash = "hash-x"),
        items = listOf(InspectionItemSnapshot(stableId = "wall.paint", status = "GOOD", note = null, wearOrDamage = null)),
        photos = listOf(PhotoSnapshot(contentHash = "photohash", source = "CAMERA", exifTimeMs = now, isRoomLevel = false)),
        audios = emptyList(),
    )

    @Test
    fun `data_hash re-verifies identically after purging the tenancy's contact info`() {
        val propertyId = DbTestFixtures.insertProperty(db, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(db, uuid, type = "EXIT", now = now)
        val tenancyId = uuid.next()
        val endMs = now - 400L * 24 * 60 * 60 * 1000L // 显然已过 12 个日历月的保留期下限
        db.tenancyQueries.insert(
            id = tenancyId, property_id = propertyId, start_ms = endMs - 1_000L, end_ms = endMs,
            tenant_name = "J Doe", contact = "j@example.com", baseline_inspection_id = null,
            created_at = now, updated_at = now,
        )
        val inspectionId = DbTestFixtures.insertDraftInspection(
            db, uuid, propertyId, templateVersionId, tenancyId = tenancyId, type = "EXIT", now = now,
        )

        val before = db.tenancyQueries.selectById(tenancyId).executeAsOne()
        val hashBefore = sha256Hex(canonicalJson(snapshotFor(before, propertyId, templateVersionId, inspectionId)))

        ContactRetentionService(db, ClockMs { now + 2 }).purge(tenancyId)

        val after = db.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertNull(after.tenant_name, "purge must actually clear tenant_name on the persisted row")
        assertNull(after.contact, "purge must actually clear contact on the persisted row")
        assertNotNull(after.purged_at)

        val hashAfter = sha256Hex(canonicalJson(snapshotFor(after, propertyId, templateVersionId, inspectionId)))
        assertEquals(
            hashBefore, hashAfter,
            "purging contact info must not change data_hash — the canon hash domain excludes tenant_name/contact by design",
        )
    }
}
