package nz.myinspection.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 四条为下游卡补的机械查询（R3 二轮评审要求，编排者裁决：每条须能指到具体需求方卡片的原文，见
 * specs/tasks/T1-SCHEMA-CORE.md「验收」说明）：
 *  - inspection_item.updateWearOrDamageIfDraft（T2-CAPTURE-CORE，allow_paths 不含 core/db/）
 *  - property_item_override.setSuppressed / selectByPropertyAndStableId（T2-CAPTURE-CORE，同上）
 *  - notice.recordDelivery（T4-NOTICES，同上）
 *  - photo.softDelete / orphanedContentHashes（T2-PHOTO-PIPELINE，同上）
 */
class DbDownstreamQueriesTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: MyInspectionDatabase
    private lateinit var uuid: Uuid7Generator

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

    private val now = DbTestFixtures.NOW

    @Test
    fun `updateWearOrDamageIfDraft writes on a draft inspection and is blocked once finalized`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        val itemId = DbTestFixtures.insertInspectionItem(database, uuid, inspectionId, roomInstanceId, now = now)

        val whileDraft = database.inspectionItemQueries.updateWearOrDamageIfDraft(
            wear_or_damage = "DAMAGE", updated_at = now + 1, id = itemId,
        ).value
        assertEquals(1L, whileDraft, "writing wear_or_damage on a DRAFT inspection must succeed")
        assertEquals("DAMAGE", database.inspectionItemQueries.selectById(itemId).executeAsOne().wear_or_damage)

        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 2, data_hash = "h", updated_at = now + 2, id = inspectionId).value

        val afterFinalize = database.inspectionItemQueries.updateWearOrDamageIfDraft(
            wear_or_damage = "FAIR_WEAR", updated_at = now + 3, id = itemId,
        ).value
        assertEquals(0L, afterFinalize, "writing wear_or_damage after finalize must affect 0 rows")
        assertEquals("DAMAGE", database.inspectionItemQueries.selectById(itemId).executeAsOne().wear_or_damage, "the FINALIZED value must be untouched")
    }

    @Test
    fun `property_item_override can be suppressed then restored via the same row`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        assertNull(database.propertyItemOverrideQueries.selectByPropertyAndStableId(propertyId, "hallway.light").executeAsOneOrNull())

        val overrideId = uuid.next()
        database.propertyItemOverrideQueries.insert(id = overrideId, property_id = propertyId, stable_id = "hallway.light", suppressed = 1, created_at = now, updated_at = now)

        val suppressed = database.propertyItemOverrideQueries.selectByPropertyAndStableId(propertyId, "hallway.light").executeAsOne()
        assertEquals(1L, suppressed.suppressed)

        database.propertyItemOverrideQueries.setSuppressed(suppressed = 0, updated_at = now + 1, id = overrideId)
        val restored = database.propertyItemOverrideQueries.selectByPropertyAndStableId(propertyId, "hallway.light").executeAsOne()
        assertEquals(0L, restored.suppressed, "restore must flip suppressed back to 0 on the same row, not create a second row")
        assertEquals(overrideId, restored.id, "restore reuses the existing row rather than inserting a new one")
    }

    private fun insertUnsentNotice(inspectionId: String): String {
        val noticeId = uuid.next()
        database.noticeQueries.insert(
            id = noticeId, inspection_id = inspectionId, full_text = "48h notice text", generated_at = now,
            sent_via = null, sent_at = null, lead_hours = 72, validation_snapshot = "{\"pass\":true}", updated_at = now,
        )
        return noticeId
    }

    @Test
    fun `notice recordDelivery locks after the first call`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val noticeId = insertUnsentNotice(inspectionId)

        val firstRecord = database.noticeQueries.recordDelivery(
            sent_via = "EMAIL", lead_hours = 50, validation_snapshot = "{\"pass\":true}", updated_at = now + 1, id = noticeId,
        ).value
        assertEquals(1L, firstRecord, "the first delivery record must succeed")

        val secondRecord = database.noticeQueries.recordDelivery(
            sent_via = "SMS", lead_hours = 40, validation_snapshot = "{\"pass\":false}", updated_at = now + 2, id = noticeId,
        ).value
        assertEquals(0L, secondRecord, "delivery is locked after the first record — a second call must affect 0 rows")

        val row = database.noticeQueries.selectById(noticeId).executeAsOne()
        assertEquals("EMAIL", row.sent_via, "the second call must not overwrite the first delivery record")
        assertEquals(50L, row.lead_hours)
        assertEquals(now + 1, row.sent_at, "sent_at must equal the updated_at bound at the moment of delivery")
    }

    @Test
    fun `notice recordDelivery with a null sent_via affects zero rows and does not lock the row`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val noticeId = insertUnsentNotice(inspectionId)

        val nullAttempt = database.noticeQueries.recordDelivery(
            sent_via = null, lead_hours = 50, validation_snapshot = "{\"pass\":true}", updated_at = now + 1, id = noticeId,
        ).value
        assertEquals(0L, nullAttempt, "a NULL sent_via must not be accepted as a valid delivery record")

        val stillUnsent = database.noticeQueries.selectById(noticeId).executeAsOne()
        assertNull(stillUnsent.sent_at, "a rejected NULL attempt must not lock the row — a real delivery record must still be possible afterwards")

        val realRecord = database.noticeQueries.recordDelivery(
            sent_via = "EMAIL", lead_hours = 50, validation_snapshot = "{\"pass\":true}", updated_at = now + 2, id = noticeId,
        ).value
        assertEquals(1L, realRecord, "a real delivery record must still succeed after a rejected NULL attempt")
    }

    @Test
    fun `photo softDelete is blocked once the owning inspection is finalized`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)
        val photoId = uuid.next()
        database.photoQueries.insert(
            id = photoId, inspection_item_id = null, room_instance_id = roomInstanceId, rel_path = "a.jpg",
            content_hash = "hash-a", exif_time_ms = null, source = "CAMERA", privacy_flag = 0, created_at = now, updated_at = now,
        )

        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 1, data_hash = "h", updated_at = now + 1, id = inspectionId).value

        val affected = database.photoQueries.softDelete(deleted_at = now + 2, id = photoId).value
        assertEquals(0L, affected, "soft-deleting a photo under a FINALIZED inspection must affect 0 rows")
        assertNull(database.photoQueries.selectById(photoId).executeAsOne().deleted_at)
    }

    @Test
    fun `orphanedContentHashes lists hashes with zero active rows and never a finalized inspection's hash`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)

        // Draft inspection: photo gets soft-deleted -> its content_hash becomes orphaned.
        val draftInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val draftRoomId = DbTestFixtures.insertRoomInstance(database, uuid, draftInspectionId, now = now)
        val orphanPhotoId = uuid.next()
        database.photoQueries.insert(
            id = orphanPhotoId, inspection_item_id = null, room_instance_id = draftRoomId, rel_path = "orphan.jpg",
            content_hash = "orphan-hash", exif_time_ms = null, source = "CAMERA", privacy_flag = 0, created_at = now, updated_at = now,
        )
        val deleted = database.photoQueries.softDelete(deleted_at = now + 1, id = orphanPhotoId).value
        assertEquals(1L, deleted, "soft-deleting a photo under a DRAFT inspection must succeed")

        // Finalized inspection: its photo can never be soft-deleted, so its content_hash can never orphan.
        val finalInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now + 10)
        val finalRoomId = DbTestFixtures.insertRoomInstance(database, uuid, finalInspectionId, now = now + 10)
        database.photoQueries.insert(
            id = uuid.next(), inspection_item_id = null, room_instance_id = finalRoomId, rel_path = "kept.jpg",
            content_hash = "finalized-hash", exif_time_ms = null, source = "CAMERA", privacy_flag = 0, created_at = now + 10, updated_at = now + 10,
        )
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 11, data_hash = "h", updated_at = now + 11, id = finalInspectionId).value

        val orphans = database.photoQueries.orphanedContentHashes().executeAsList()
        assertEquals(listOf("orphan-hash"), orphans, "only the fully-unreferenced hash is orphaned")
        assertTrue("finalized-hash" !in orphans, "a finalized inspection's photo content_hash must never be reported as orphaned")
        assertNotNull(database.photoQueries.selectById(orphanPhotoId).executeAsOne().deleted_at)
    }
}
