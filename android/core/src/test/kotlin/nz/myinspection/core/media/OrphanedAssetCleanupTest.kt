package nz.myinspection.core.media

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator

/**
 * `photo.orphanedAssets()` itself is already exhaustively covered (finalize-guard, per-rel_path liveness,
 * shared-hash edge cases) by `DbDownstreamQueriesTest` in T1-SCHEMA-CORE — that behavior is a frozen
 * schema contract, not this card's code. This test pins two things that ARE this card's code:
 *  - [OrphanedAssetCleanup.pendingDeletions] is a faithful pass-through of that frozen query.
 *  - [OrphanedAssetCleanup.run] actually invokes the injected [OrphanFileDeleter] for every orphan and
 *    for nothing else (a still-active/FINALIZED path must never even reach the deleter — deletion and
 *    retention are both asserted, not just enumeration).
 */
class OrphanedAssetCleanupTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: MyInspectionDatabase
    private lateinit var uuid: Uuid7Generator
    private val now = DbTestFixtures.NOW

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

    @Test
    fun `pendingDeletions lists an orphaned rel_path and omits a still-active, finalized one`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)

        val draftInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val draftRoomId = DbTestFixtures.insertRoomInstance(database, uuid, draftInspectionId, now = now)
        val orphanPhotoId = uuid.next()
        database.photoQueries.insert(
            id = orphanPhotoId, inspection_item_id = null, room_instance_id = draftRoomId,
            rel_path = "photos/orphan.jpg", content_hash = "orphan-hash", exif_time_ms = null,
            source = "CAMERA", privacy_flag = 0, created_at = now, updated_at = now,
        )
        database.photoQueries.softDelete(deleted_at = now + 1, id = orphanPhotoId)

        val finalInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now + 10)
        val finalRoomId = DbTestFixtures.insertRoomInstance(database, uuid, finalInspectionId, now = now + 10)
        database.photoQueries.insert(
            id = uuid.next(), inspection_item_id = null, room_instance_id = finalRoomId,
            rel_path = "photos/kept.jpg", content_hash = "kept-hash", exif_time_ms = null,
            source = "CAMERA", privacy_flag = 0, created_at = now + 10, updated_at = now + 10,
        )
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 11, data_hash = "h", updated_at = now + 11, id = finalInspectionId)

        val cleanup = OrphanedAssetCleanup(database) { true }
        assertEquals(
            database.photoQueries.orphanedAssets().executeAsList(),
            cleanup.pendingDeletions(),
            "the use case must be a faithful pass-through of the frozen query, not a re-derivation",
        )
        assertEquals(listOf("photos/orphan.jpg"), cleanup.pendingDeletions())
    }

    @Test
    fun `run invokes the deleter for every orphan and never for a path a finalized inspection still references`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)

        val draftInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val draftRoomId = DbTestFixtures.insertRoomInstance(database, uuid, draftInspectionId, now = now)
        val orphanPhotoId = uuid.next()
        database.photoQueries.insert(
            id = orphanPhotoId, inspection_item_id = null, room_instance_id = draftRoomId,
            rel_path = "photos/orphan.jpg", content_hash = "orphan-hash", exif_time_ms = null,
            source = "CAMERA", privacy_flag = 0, created_at = now, updated_at = now,
        )
        database.photoQueries.softDelete(deleted_at = now + 1, id = orphanPhotoId)

        val finalInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now + 10)
        val finalRoomId = DbTestFixtures.insertRoomInstance(database, uuid, finalInspectionId, now = now + 10)
        database.photoQueries.insert(
            id = uuid.next(), inspection_item_id = null, room_instance_id = finalRoomId,
            rel_path = "photos/kept.jpg", content_hash = "kept-hash", exif_time_ms = null,
            source = "CAMERA", privacy_flag = 0, created_at = now + 10, updated_at = now + 10,
        )
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 11, data_hash = "h", updated_at = now + 11, id = finalInspectionId)

        val deleterCalls = mutableListOf<String>()
        val cleanup = OrphanedAssetCleanup(database) { relPath -> deleterCalls.add(relPath); true }

        assertEquals(listOf("photos/orphan.jpg"), cleanup.run(), "run() must report exactly the paths it deleted")
        assertEquals(
            listOf("photos/orphan.jpg"),
            deleterCalls,
            "the deleter must never be invoked for photos/kept.jpg — a still-active, finalized inspection's evidence must not even be considered for deletion",
        )
    }

    @Test
    fun `run reports only the paths whose deleter call actually reported success`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        fun orphan(relPath: String, hash: String) {
            val id = uuid.next()
            database.photoQueries.insert(
                id = id, inspection_item_id = null, room_instance_id = roomId, rel_path = relPath,
                content_hash = hash, exif_time_ms = null, source = "CAMERA", privacy_flag = 0,
                created_at = now, updated_at = now,
            )
            database.photoQueries.softDelete(deleted_at = now + 1, id = id)
        }
        orphan("photos/deletes-ok.jpg", "hash-1")
        orphan("photos/delete-fails.jpg", "hash-2")

        // Simulates a real filesystem failure (permission denied, already gone via a race, etc.) on one
        // of the two — run() must not report it as deleted just because it was a member of the pending set.
        val cleanup = OrphanedAssetCleanup(database) { relPath -> relPath != "photos/delete-fails.jpg" }

        assertEquals(
            setOf("photos/deletes-ok.jpg"),
            cleanup.run().toSet(),
            "a path whose deleter call returns false must be excluded from the reported result, not silently counted as cleaned up",
        )
    }
}
