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
 * schema contract, not this card's code. This test only pins that [OrphanedAssetCleanup] is a faithful
 * pass-through: what the query reports is exactly what the use case hands the caller, nothing added or
 * dropped, and a FINALIZED inspection's asset never appears (spot-check of the structural guarantee, not
 * a re-derivation of it).
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

        val cleanup = OrphanedAssetCleanup(database)
        assertEquals(
            database.photoQueries.orphanedAssets().executeAsList(),
            cleanup.pendingDeletions(),
            "the use case must be a faithful pass-through of the frozen query, not a re-derivation",
        )
        assertEquals(listOf("photos/orphan.jpg"), cleanup.pendingDeletions())
    }
}
