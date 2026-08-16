package nz.myinspection.core.media

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator

/**
 * `photo.orphanedAssets()` itself is already exhaustively covered (finalize-guard, per-rel_path liveness,
 * shared-hash edge cases) by `DbDownstreamQueriesTest` in T1-SCHEMA-CORE — that behavior is a frozen
 * schema contract, not this card's code. This test pins what IS this card's code:
 *  - [OrphanedAssetCleanup.pendingDeletions] sorts by rel_path (L222: the frozen query has no ORDER BY,
 *    so the raw return order is not a contract this code may rely on or expose).
 *  - [OrphanedAssetCleanup.run] actually invokes the injected [OrphanFileDeleter] for every orphan and
 *    for nothing else (a still-active/FINALIZED path must never even reach the deleter).
 *  - a rel_path that fails [MediaPaths.isPhotoRelPathShape] is rejected outright — never handed to the
 *    deleter, and reported separately from `deleted`/`failed`.
 *  - the [OrphanFileDeleter] exception contract: `IOException`/`SecurityException` are caught with their
 *    cause preserved in `FailedDeletion`; anything else propagates out of `run()` uncaught.
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

    private fun orphanIn(roomId: String, relPath: String, hash: String) {
        val id = insertPhoto(roomId, relPath, hash, now)
        database.photoQueries.softDelete(deleted_at = now + 1, id = id)
    }

    @Test
    fun `pendingDeletions lists an orphaned rel_path and omits a still-active, finalized one`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)

        val draftInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val draftRoomId = DbTestFixtures.insertRoomInstance(database, uuid, draftInspectionId, now = now)
        orphanIn(draftRoomId, "photos/prop-1/insp-1/orphan.jpg", "orphan-hash")

        val finalInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now + 10)
        val finalRoomId = DbTestFixtures.insertRoomInstance(database, uuid, finalInspectionId, now = now + 10)
        insertPhoto(finalRoomId, "photos/prop-1/insp-2/kept.jpg", "kept-hash", now + 10)
        database.inspectionQueries.finalizeIfDraft(finalized_at = now + 11, data_hash = "h", updated_at = now + 11, id = finalInspectionId)

        val cleanup = OrphanedAssetCleanup(database) { true }
        assertEquals(listOf("photos/prop-1/insp-1/orphan.jpg"), cleanup.pendingDeletions())
    }

    @Test
    fun `pendingDeletions sorts by rel_path regardless of the frozen query's unordered return`() {
        // photo.orphanedAssets has no ORDER BY (L222) — insert in the REVERSE of sorted order so an
        // implementation that just forwarded the query's raw order would fail this assertion.
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        orphanIn(roomId, "photos/prop-1/insp-1/c.jpg", "hash-c")
        orphanIn(roomId, "photos/prop-1/insp-1/a.jpg", "hash-a")
        orphanIn(roomId, "photos/prop-1/insp-1/b.jpg", "hash-b")

        val cleanup = OrphanedAssetCleanup(database) { true }
        assertEquals(
            listOf("photos/prop-1/insp-1/a.jpg", "photos/prop-1/insp-1/b.jpg", "photos/prop-1/insp-1/c.jpg"),
            cleanup.pendingDeletions(),
        )
    }

    @Test
    fun `run invokes the deleter for every orphan and never for a path a finalized inspection still references`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)

        val draftInspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val draftRoomId = DbTestFixtures.insertRoomInstance(database, uuid, draftInspectionId, now = now)
        orphanIn(draftRoomId, "photos/prop-1/insp-1/orphan.jpg", "orphan-hash")

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
    fun `run splits deleted from failed instead of silently dropping a clean false from the deleter`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        orphanIn(roomId, "photos/prop-1/insp-1/deletes-ok.jpg", "hash-1")
        orphanIn(roomId, "photos/prop-1/insp-1/delete-fails.jpg", "hash-2")

        // A clean `false` (no exception) — e.g. the file was already gone via a race the deleter itself
        // detected and reported honestly.
        val cleanup = OrphanedAssetCleanup(database) { relPath -> relPath != "photos/prop-1/insp-1/delete-fails.jpg" }

        val result = cleanup.run()
        assertEquals(listOf("photos/prop-1/insp-1/deletes-ok.jpg"), result.deleted)
        assertEquals(
            listOf(FailedDeletion("photos/prop-1/insp-1/delete-fails.jpg", cause = null)),
            result.failed,
            "a clean `false` from the deleter must surface as a FailedDeletion with a null cause — attempted, not exceptional",
        )
        assertEquals(emptyList(), result.rejected)
    }

    @Test
    fun `run rejects a shape-invalid orphan row instead of ever calling the deleter on it`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        orphanIn(roomId, "audio/x/y/z.m4a", "hash-corrupt")
        orphanIn(roomId, "photos/prop-1/insp-1/valid.jpg", "hash-valid")

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
    fun `run catches an IOException from the deleter, preserves it as the cause, and still completes the batch`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        orphanIn(roomId, "photos/prop-1/insp-1/deletes-ok.jpg", "hash-ok")
        orphanIn(roomId, "photos/prop-1/insp-1/throws.jpg", "hash-throws")

        val thrown = IOException("simulated disk error")
        val cleanup = OrphanedAssetCleanup(database) { relPath ->
            if (relPath == "photos/prop-1/insp-1/throws.jpg") throw thrown else true
        }

        val result = cleanup.run()
        assertEquals(
            listOf("photos/prop-1/insp-1/deletes-ok.jpg"),
            result.deleted,
            "an exception on one path must not prevent a later path in the same batch from being processed",
        )
        val failure = result.failed.single()
        assertEquals("photos/prop-1/insp-1/throws.jpg", failure.relPath)
        assertEquals(thrown, failure.cause, "the exact thrown exception must be preserved, not discarded or replaced")
        assertEquals(emptyList(), result.rejected)
    }

    @Test
    fun `run catches a SecurityException from the deleter and preserves it as the cause`() {
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        orphanIn(roomId, "photos/prop-1/insp-1/denied.jpg", "hash-denied")

        val thrown = SecurityException("permission denied")
        val cleanup = OrphanedAssetCleanup(database) { throw thrown }

        val result = cleanup.run()
        val failure = result.failed.single()
        assertEquals("photos/prop-1/insp-1/denied.jpg", failure.relPath)
        assertEquals(thrown, failure.cause)
    }

    @Test
    fun `run lets an exception outside the deleter contract propagate rather than silently absorbing it`() {
        // Anything other than IOException/SecurityException is not a "this one deletion failed" signal —
        // it is a bug in the deleter, and must surface loudly rather than being folded into `failed`.
        val propertyId = DbTestFixtures.insertProperty(database, uuid, now)
        val templateVersionId = DbTestFixtures.insertTemplateVersion(database, uuid, now = now)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateVersionId, now = now)
        val roomId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId, now = now)

        orphanIn(roomId, "photos/prop-1/insp-1/buggy.jpg", "hash-buggy")

        val cleanup = OrphanedAssetCleanup(database) { throw IllegalStateException("deleter has a real bug") }

        val ex = assertFailsWith<IllegalStateException> { cleanup.run() }
        assertIs<IllegalStateException>(ex)
        assertEquals("deleter has a real bug", ex.message)
    }
}
