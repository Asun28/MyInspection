package nz.myinspection.core.retention

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nz.myinspection.core.canon.canonicalJson
import nz.myinspection.core.canon.sha256Hex
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
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
 * declared hash-domain shape」）。这条测试跑在一份**已 finalize**的巡检上（"data_hash 复验" 这件事
 * 本身只对 FINALIZED 巡检有意义，DRAFT 巡检的 `inspection.data_hash` 列恒为 NULL，无值可复验）：
 * 真建 DB 行（含一张真实持久化的照片，代表证据）→ 从持久化行投影出 [InspectionSnapshot] → 算一次
 * data_hash 并真的 finalize（写进 `inspection.data_hash` 列）→ 真执行 [ContactRetentionService.purge]
 * → 从持久化行重新投影 → 再算一次 → 两次哈希与库里存的 `data_hash` 三者必须相等；证据行（照片）必须
 * 仍是活跃行、内容哈希未变。
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

    /** 每次调用都重新从持久化行读取——不是缓存一份对象改字段，确保"重新投影"是真的重新查库。 */
    private fun currentSnapshot(
        inspectionId: String,
        propertyId: String,
        templateVersionId: String,
        tenancyId: String,
        roomInstanceId: String,
    ): InspectionSnapshot {
        val property = db.propertyQueries.selectById(propertyId).executeAsOne()
        val template = db.templateVersionQueries.selectById(templateVersionId).executeAsOne()
        val tenancy = db.tenancyQueries.selectById(tenancyId).executeAsOne()
        val items = db.inspectionItemQueries.selectByInspectionInTemplateOrder(inspectionId).executeAsList()
        val photos = db.photoQueries.selectByRoomInstance(roomInstanceId).executeAsList()
        return InspectionSnapshot(
            id = inspectionId,
            type = "EXIT",
            tenancyId = tenancyId,
            scheduledAt = now,
            finalizedAt = now + 1,
            previousInspectionId = null,
            baselineInspectionId = null,
            property = PropertySnapshot(
                id = property.id, address = property.address, kind = property.kind,
                isBoardingHouse = property.is_boarding_house == 1L,
            ),
            tenancy = TenancySnapshot(id = tenancy.id, startMs = tenancy.start_ms, endMs = tenancy.end_ms),
            template = TemplateSnapshot(
                id = template.id, type = template.type, version = template.version, contentHash = template.content_hash,
            ),
            items = items.map {
                InspectionItemSnapshot(stableId = it.stable_id, status = it.status, note = it.note, wearOrDamage = it.wear_or_damage)
            },
            photos = photos.map {
                PhotoSnapshot(
                    contentHash = it.content_hash, source = it.source, exifTimeMs = it.exif_time_ms,
                    isRoomLevel = it.inspection_item_id == null,
                )
            },
            audios = emptyList(),
        )
    }

    @Test
    fun `a finalized inspection's stored data_hash re-verifies identically after purging the tenancy's contact info`() {
        val propertyId = DbTestFixtures.insertProperty(db, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(db, uuid, type = "EXIT", now = now)
        val tenancyId = uuid.next()
        val endMs = now - 400L * 24 * 60 * 60 * 1000L // 显然已过 12 个日历月的联系方式清理策略窗口
        db.tenancyQueries.insert(
            id = tenancyId, property_id = propertyId, start_ms = endMs - 1_000L, end_ms = endMs,
            tenant_name = "J Doe", contact = "j@example.com", baseline_inspection_id = null,
            created_at = now, updated_at = now,
        )
        val inspectionId = DbTestFixtures.insertDraftInspection(
            db, uuid, propertyId, templateVersionId, tenancyId = tenancyId, type = "EXIT", now = now,
        )
        val roomInstanceId = DbTestFixtures.insertRoomInstance(db, uuid, inspectionId, now = now)
        DbTestFixtures.insertInspectionItem(db, uuid, inspectionId, roomInstanceId, now = now)
        db.photoQueries.insert(
            id = uuid.next(), inspection_item_id = null, room_instance_id = roomInstanceId,
            rel_path = "exit-hallway.jpg", content_hash = "photohash-real", exif_time_ms = now,
            source = "CAMERA", privacy_flag = 0, created_at = now, updated_at = now,
        )

        val hashBefore = sha256Hex(
            canonicalJson(currentSnapshot(inspectionId, propertyId, templateVersionId, tenancyId, roomInstanceId)),
        )
        val finalizedRows = db.inspectionQueries.finalizeIfDraft(
            finalized_at = now + 1, data_hash = hashBefore, updated_at = now + 1, id = inspectionId,
        ).value
        assertEquals(1L, finalizedRows, "the fixture inspection must actually finalize for this test to mean anything")

        ContactRetentionService(db, ClockMs { now + 2 }).purge(tenancyId)

        val tenancyAfter = db.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertNull(tenancyAfter.tenant_name, "purge must actually clear tenant_name on the persisted row")
        assertNull(tenancyAfter.contact, "purge must actually clear contact on the persisted row")
        assertNotNull(tenancyAfter.purged_at)

        val hashAfter = sha256Hex(
            canonicalJson(currentSnapshot(inspectionId, propertyId, templateVersionId, tenancyId, roomInstanceId)),
        )
        val storedHash = db.inspectionQueries.selectById(inspectionId).executeAsOne().data_hash
        assertEquals(
            hashBefore, hashAfter,
            "purging contact info must not change data_hash — the canon hash domain excludes tenant_name/contact by design",
        )
        assertEquals(storedHash, hashAfter, "re-verification must match the data_hash actually stored at finalize time")

        // 证据（照片）不受清理影响：仍是活跃行，内容哈希未变。
        val photoAfter = db.photoQueries.selectByRoomInstance(roomInstanceId).executeAsList().single()
        assertEquals("photohash-real", photoAfter.content_hash)
        assertNull(photoAfter.deleted_at, "purge must not touch inspection evidence")
    }
}
