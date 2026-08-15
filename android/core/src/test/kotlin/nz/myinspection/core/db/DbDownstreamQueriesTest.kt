package nz.myinspection.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 为下游卡补的机械查询（R3 二轮评审要求，编排者裁决：每条须能指到具体需求方卡片的原文，见
 * specs/tasks/T1-SCHEMA-CORE.md「验收」说明）：
 *  - inspection_item.updateWearOrDamageIfDraft（T2-CAPTURE-CORE，allow_paths 不含 core/db/）
 *  - property_item_override.setSuppressed / selectByPropertyAndStableId（T2-CAPTURE-CORE，同上）
 *  - notice.recordDelivery（T4-NOTICES，同上）
 *  - photo.softDelete / orphanedAssets（T2-PHOTO-PIPELINE，同上）
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

    private fun insertUnsentNotice(inspectionId: String, scheduledAt: Long = now + 200_000L): String {
        val noticeId = uuid.next()
        database.noticeQueries.insert(
            id = noticeId, inspection_id = inspectionId, full_text = "48h notice text", generated_at = now,
            scheduled_at = scheduledAt, sent_via = null, sent_at = null, lead_hours = 72,
            validation_snapshot = "{\"pass\":true}", updated_at = now,
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
            sent_via = "EMAIL", sent_at = now + 1, lead_hours = 50, validation_snapshot = "{\"pass\":true}", updated_at = now + 1, id = noticeId,
        ).value
        assertEquals(1L, firstRecord, "the first delivery record must succeed")

        val secondRecord = database.noticeQueries.recordDelivery(
            sent_via = "SMS", sent_at = now + 2, lead_hours = 40, validation_snapshot = "{\"pass\":false}", updated_at = now + 2, id = noticeId,
        ).value
        assertEquals(0L, secondRecord, "delivery is locked after the first record — a second call must affect 0 rows")

        val row = database.noticeQueries.selectById(noticeId).executeAsOne()
        assertEquals("EMAIL", row.sent_via, "the second call must not overwrite the first delivery record")
        assertEquals(50L, row.lead_hours)
        assertEquals(now + 1, row.sent_at, "sent_at must be the value bound at the moment of delivery")
    }

    @Test
    fun `notice recordDelivery with a null sent_via or sent_at affects zero rows and does not lock the row`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val noticeId = insertUnsentNotice(inspectionId)

        val nullSentVia = database.noticeQueries.recordDelivery(
            sent_via = null, sent_at = now + 1, lead_hours = 50, validation_snapshot = "{\"pass\":true}", updated_at = now + 1, id = noticeId,
        ).value
        assertEquals(0L, nullSentVia, "a NULL sent_via must not be accepted as a valid delivery record")

        val nullSentAt = database.noticeQueries.recordDelivery(
            sent_via = "EMAIL", sent_at = null, lead_hours = 50, validation_snapshot = "{\"pass\":true}", updated_at = now + 1, id = noticeId,
        ).value
        assertEquals(0L, nullSentAt, "a NULL sent_at must not be accepted as a valid delivery record")

        val stillUnsent = database.noticeQueries.selectById(noticeId).executeAsOne()
        assertNull(stillUnsent.sent_at, "rejected NULL attempts must not lock the row — a real delivery record must still be possible afterwards")

        val realRecord = database.noticeQueries.recordDelivery(
            sent_via = "EMAIL", sent_at = now + 2, lead_hours = 50, validation_snapshot = "{\"pass\":true}", updated_at = now + 2, id = noticeId,
        ).value
        assertEquals(1L, realRecord, "a real delivery record must still succeed after rejected NULL attempts")
    }

    @Test
    fun `notice scheduled_at is stored as its own independent snapshot`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val snapshotScheduledAt = now + 999_000L

        val noticeId = insertUnsentNotice(inspectionId, scheduledAt = snapshotScheduledAt)

        val row = database.noticeQueries.selectById(noticeId).executeAsOne()
        assertEquals(
            snapshotScheduledAt,
            row.scheduled_at,
            "scheduled_at must be its own stored column, not derived live from inspection.scheduled_at at read time",
        )
    }

    @Test
    fun `notice rejects a mismatched sent_via+sent_at pair at insert time`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)

        val sentAtOnly = assertFailsWith<Exception>("sent_at set with sent_via NULL must violate the CHECK constraint") {
            database.noticeQueries.insert(
                id = uuid.next(), inspection_id = inspectionId, full_text = "t", generated_at = now,
                scheduled_at = now + 1000L, sent_via = null, sent_at = now, lead_hours = 72,
                validation_snapshot = "{}", updated_at = now,
            )
        }
        assertTrue(sentAtOnly.message.orEmpty().contains("CHECK", ignoreCase = true), "expected a CHECK violation, got: ${sentAtOnly.message}")

        val sentViaOnly = assertFailsWith<Exception>("sent_via set with sent_at NULL must violate the CHECK constraint") {
            database.noticeQueries.insert(
                id = uuid.next(), inspection_id = inspectionId, full_text = "t", generated_at = now,
                scheduled_at = now + 1000L, sent_via = "EMAIL", sent_at = null, lead_hours = 72,
                validation_snapshot = "{}", updated_at = now,
            )
        }
        assertTrue(sentViaOnly.message.orEmpty().contains("CHECK", ignoreCase = true), "expected a CHECK violation, got: ${sentViaOnly.message}")
    }

    @Test
    fun `supplement prev_hash anchors to the inspection data_hash and stores its own chain_hash`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 1, data_hash = "inspection-data-hash", updated_at = now + 1, id = inspectionId).value

        val supplementId = uuid.next()
        database.supplementQueries.insert(
            id = supplementId, inspection_id = inspectionId, created_at = now + 2, text = "landlord to fix gate latch",
            prev_hash = "inspection-data-hash", chain_hash = "chain-hash-1", updated_at = now + 2,
        )

        val row = database.supplementQueries.selectById(supplementId).executeAsOne()
        assertEquals("inspection-data-hash", row.prev_hash, "the first supplement's prev_hash must anchor to the inspection's data_hash, not be NULL")
        assertEquals("chain-hash-1", row.chain_hash, "chain_hash must be a real stored column so a later verifyChain has something to check against")
    }

    @Test
    fun `supplement selectByInspection breaks created_at ties deterministically by id`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 1, data_hash = "d0", updated_at = now + 1, id = inspectionId).value

        // Two supplements inserted with the *same* created_at millisecond — sort must not depend on
        // insertion order or SQLite's unspecified tie-handling; it must be deterministic by id.
        val sameMoment = now + 2
        val idA = uuid.next()
        val idB = uuid.next()
        val (first, second) = if (idA < idB) idA to idB else idB to idA
        database.supplementQueries.insert(id = second, inspection_id = inspectionId, created_at = sameMoment, text = "second by id", prev_hash = "d0", chain_hash = "c2", updated_at = sameMoment)
        database.supplementQueries.insert(id = first, inspection_id = inspectionId, created_at = sameMoment, text = "first by id", prev_hash = "d0", chain_hash = "c1", updated_at = sameMoment)

        val ordered = database.supplementQueries.selectByInspection(inspectionId).executeAsList()
        assertEquals(listOf(first, second), ordered.map { it.id }, "tied created_at values must break ties by id, deterministically")
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
    fun `orphanedAssets lists content_hash+rel_path with zero active rows and never a finalized inspection's asset`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)

        // Draft inspection: photo gets soft-deleted -> its (content_hash, rel_path) becomes orphaned.
        val draftInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val draftRoomId = DbTestFixtures.insertRoomInstance(database, uuid, draftInspectionId, now = now)
        val orphanPhotoId = uuid.next()
        database.photoQueries.insert(
            id = orphanPhotoId, inspection_item_id = null, room_instance_id = draftRoomId, rel_path = "photos/orphan.jpg",
            content_hash = "orphan-hash", exif_time_ms = null, source = "CAMERA", privacy_flag = 0, created_at = now, updated_at = now,
        )
        val deleted = database.photoQueries.softDelete(deleted_at = now + 1, id = orphanPhotoId).value
        assertEquals(1L, deleted, "soft-deleting a photo under a DRAFT inspection must succeed")

        // Finalized inspection: its photo can never be soft-deleted, so its asset can never orphan.
        val finalInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now + 10)
        val finalRoomId = DbTestFixtures.insertRoomInstance(database, uuid, finalInspectionId, now = now + 10)
        database.photoQueries.insert(
            id = uuid.next(), inspection_item_id = null, room_instance_id = finalRoomId, rel_path = "photos/kept.jpg",
            content_hash = "finalized-hash", exif_time_ms = null, source = "CAMERA", privacy_flag = 0, created_at = now + 10, updated_at = now + 10,
        )
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 11, data_hash = "h", updated_at = now + 11, id = finalInspectionId).value

        val orphans = database.photoQueries.orphanedAssets().executeAsList()
        assertEquals(1, orphans.size, "only the fully-unreferenced asset is orphaned")
        assertEquals("orphan-hash", orphans.single().content_hash)
        assertEquals("photos/orphan.jpg", orphans.single().rel_path, "the caller must be able to recover the physical file path to delete, not just the hash")
        assertTrue(orphans.none { it.content_hash == "finalized-hash" }, "a finalized inspection's photo asset must never be reported as orphaned")
        assertNotNull(database.photoQueries.selectById(orphanPhotoId).executeAsOne().deleted_at)
    }

    @Test
    fun `purgeContactInfo with a null purged_at affects zero rows and does not corrupt the row`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val tenancyId = uuid.next()
        database.tenancyQueries.insert(
            id = tenancyId, property_id = propertyId, start_ms = now - 86_400_000L, end_ms = now,
            tenant_name = "J Doe", contact = "j@example.com", baseline_inspection_id = null, created_at = now, updated_at = now,
        )

        val nullAttempt = database.tenancyQueries.purgeContactInfo(purged_at = null, updated_at = now + 1, id = tenancyId).value
        assertEquals(0L, nullAttempt, "a NULL purged_at must not be accepted — it would clear contact fields while leaving purged_at NULL")

        val untouched = database.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertEquals("J Doe", untouched.tenant_name, "a rejected NULL attempt must not clear contact fields")
        assertNull(untouched.purged_at)

        val realPurge = database.tenancyQueries.purgeContactInfo(purged_at = now + 2, updated_at = now + 2, id = tenancyId).value
        assertEquals(1L, realPurge, "a real purge must still succeed after a rejected NULL attempt")
        val purged = database.tenancyQueries.selectById(tenancyId).executeAsOne()
        assertNull(purged.tenant_name)
        assertEquals(now + 2, purged.purged_at)
    }
}
