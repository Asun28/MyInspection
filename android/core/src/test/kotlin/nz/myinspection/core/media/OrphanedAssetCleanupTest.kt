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
 * schema contract, not this card's code. This test pins what IS this card's code:
 *  - [OrphanedAssetCleanup.pendingDeletions] is a faithful pass-through of that frozen query.
 *  - [OrphanedAssetCleanup.run] actually invokes the injected [OrphanFileDeleter] for every orphan and
 *    for nothing else (a still-active/FINALIZED path must never even reach the deleter — deletion and
 *    retention are both asserted, not just enumeration).
 *  - a rel_path that fails [MediaPaths.isPhotoRelPathShape] is rejected outright — never handed to the
 *    deleter, and reported separately from `deleted`/`failed` (a corrupted/cross-table row must not be
 *    able to make this use case physically touch a file outside the photo namespace).
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

    private fun insertPhoto(roomInstanceId: String, relPath: String, hash: String, createdAt: Long): String {
        val id = uuid.next()
        database.photoQueries.insert(
            id = id, inspection_item_id = null, room_instance_id = roomInstanceId, rel_path = relPath,
            content_hash = hash, exif_time_ms = null, source = "CAMERA", privacy_flag = 0,
            created_at = createdAt, updated_at = createdAt,
        )
        return id
    }

    @Test
    fun `pendingDeletions lists an orphaned rel_path and omits a still-active, finalized one`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)

        val draftInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val draftRoomId = DbTestFixtures.insertRoomInstance(database, uuid, draftInspectionId, now = now)
        val orphanPhotoId = insertPhoto(draftRoomId, "photos/prop-1/insp-1/orphan.jpg", "orphan-hash", now)
        database.photoQueries.softDelete(deleted_at = now + 1, id = orphanPhotoId)

        val finalInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now + 10)
        val finalRoomId = DbTestFixtures.insertRoomInstance(database, uuid, finalInspectionId, now = now + 10)
        insertPhoto(finalRoomId, "photos/prop-1/insp-2/kept.jpg", "kept-hash", now + 10)
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 11, data_hash = "h", updated_at = now + 11, id = finalInspectionId)

        val cleanup = OrphanedAssetCleanup(database) { true }
        assertEquals(
            database.photoQueries.orphanedAssets().executeAsList(),
            cleanup.pendingDeletions(),
            "the use case must be a faithful pass-through of the frozen query, not a re-derivation",
        )
        assertEquals(listOf("photos/prop-1/insp-1/orphan.jpg"), cleanup.pendingDeletions())
    }

    @Test
    fun `run invokes the deleter for every orphan and never for a path a finalized inspection still references`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)

        val draftInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val draftRoomId = DbTestFixtures.insertRoomInstance(database, uuid, draftInspectionId, now = now)
        val orphanPhotoId = insertPhoto(draftRoomId, "photos/prop-1/insp-1/orphan.jpg", "orphan-hash", now)
        database.photoQueries.softDelete(deleted_at = now + 1, id = orphanPhotoId)

        val finalInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now + 10)
        val finalRoomId = DbTestFixtures.insertRoomInstance(database, uuid, finalInspectionId, now = now + 10)
        insertPhoto(finalRoomId, "photos/prop-1/insp-2/kept.jpg", "kept-hash", now + 10)
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 11, data_hash = "h", updated_at = now + 11, id = finalInspectionId)

        val deleterCalls = mutableListOf<String>()
        val cleanup = OrphanedAssetCleanup(database) { relPath -> deleterCalls.add(relPath); true }

        val result = cleanup.run()
        assertEquals(listOf("photos/prop-1/insp-1/orphan.jpg"), result.deleted, "run() must report exactly the paths it deleted")
        assertEquals(emptyList(), result.failed)
        assertEquals(emptyList(), result.rejected)
        assertEquals(
            listOf("photos/prop-1/insp-1/orphan.jpg"),
            deleterCalls,
            "the deleter must never be invoked for photos/prop-1/insp-2/kept.jpg — a still-active, finalized inspection's evidence must not even be considered for deletion",
        )
    }

    @Test
    fun `run splits deleted from failed instead of silently dropping a failed deletion`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        fun orphan(relPath: String, hash: String) {
            val id = insertPhoto(roomId, relPath, hash, now)
            database.photoQueries.softDelete(deleted_at = now + 1, id = id)
        }
        orphan("photos/prop-1/insp-1/deletes-ok.jpg", "hash-1")
        orphan("photos/prop-1/insp-1/delete-fails.jpg", "hash-2")

        // Simulates a real filesystem failure (permission denied, already gone via a race, etc.) on one
        // of the two — a caller must be able to see WHICH path failed, not just a shorter success list.
        val cleanup = OrphanedAssetCleanup(database) { relPath -> relPath != "photos/prop-1/insp-1/delete-fails.jpg" }

        val result = cleanup.run()
        assertEquals(
            listOf("photos/prop-1/insp-1/deletes-ok.jpg"),
            result.deleted,
            "a path whose deleter call returns false must be excluded from `deleted`, not silently counted as cleaned up",
        )
        assertEquals(
            listOf("photos/prop-1/insp-1/delete-fails.jpg"),
            result.failed,
            "a failed deletion must surface in `failed` with its own path — not vanish, leaving the caller unable to log/retry/alert on it",
        )
        assertEquals(emptyList(), result.rejected)
    }

    @Test
    fun `run rejects a shape-invalid orphan row instead of ever calling the deleter on it`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        // A corrupted/cross-table row: schema does not constrain rel_path to the photos/ namespace shape.
        // This simulates the exact scenario an orphan photo row pointing at an audio asset (or the media
        // root itself) would produce — the use case must refuse to physically touch it.
        fun orphan(relPath: String, hash: String) {
            val id = insertPhoto(roomId, relPath, hash, now)
            database.photoQueries.softDelete(deleted_at = now + 1, id = id)
        }
        orphan("audio/x/y/z.m4a", "hash-corrupt")
        orphan("photos/prop-1/insp-1/valid.jpg", "hash-valid")

        val deleterCalls = mutableListOf<String>()
        val cleanup = OrphanedAssetCleanup(database) { relPath -> deleterCalls.add(relPath); true }

        val result = cleanup.run()
        assertEquals(listOf("photos/prop-1/insp-1/valid.jpg"), result.deleted)
        assertEquals(emptyList(), result.failed)
        assertEquals(listOf("audio/x/y/z.m4a"), result.rejected, "a shape-invalid path must be reported, not silently dropped")
        assertEquals(
            listOf("photos/prop-1/insp-1/valid.jpg"),
            deleterCalls,
            "the deleter must never be invoked for a path outside the photo namespace shape",
        )
    }

    @Test
    fun `run completes the whole batch even when the injected deleter throws for one path`() {
        // A real OrphanFileDeleter implementation is external IO — one path's IOException (bad permission,
        // corrupted metadata, disk error) must not abort processing of every other orphan queued behind
        // it. The thrown-on entry must land in `failed` (it WAS attempted, unlike `rejected`), and run()
        // must still return a CleanupResult rather than letting the exception propagate out.
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        fun orphan(relPath: String, hash: String) {
            val id = insertPhoto(roomId, relPath, hash, now)
            database.photoQueries.softDelete(deleted_at = now + 1, id = id)
        }
        orphan("photos/prop-1/insp-1/throws.jpg", "hash-throws")
        orphan("photos/prop-1/insp-1/deletes-ok.jpg", "hash-ok")

        val cleanup = OrphanedAssetCleanup(database) { relPath ->
            if (relPath == "photos/prop-1/insp-1/throws.jpg") throw java.io.IOException("simulated disk error") else true
        }

        val result = cleanup.run()
        assertEquals(
            listOf("photos/prop-1/insp-1/deletes-ok.jpg"),
            result.deleted,
            "an exception on one path must not prevent a later path in the same batch from being processed and deleted",
        )
        assertEquals(
            listOf("photos/prop-1/insp-1/throws.jpg"),
            result.failed,
            "a thrown exception must be caught and surfaced as `failed` (it was attempted), not left to propagate out of run()",
        )
        assertEquals(emptyList(), result.rejected)
    }
}
