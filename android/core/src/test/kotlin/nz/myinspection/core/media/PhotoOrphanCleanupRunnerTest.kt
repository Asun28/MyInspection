package nz.myinspection.core.media

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.io.RandomAccessFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nz.myinspection.core.db.DbTestFixtures
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator

class PhotoOrphanCleanupRunnerTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: MyInspectionDatabase
    private lateinit var uuid: Uuid7Generator
    private lateinit var roomInstanceId: String

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MyInspectionDatabase.Schema.create(driver)
        database = MyInspectionDatabase(driver)
        uuid = Uuid7Generator()
        val propertyId = DbTestFixtures.insertProperty(database, uuid)
        val templateId = DbTestFixtures.insertTemplateVersion(database, uuid)
        val inspectionId = DbTestFixtures.insertDraftInspection(database, uuid, propertyId, templateId)
        roomInstanceId = DbTestFixtures.insertRoomInstance(database, uuid, inspectionId)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `runner readopts an active exact pending photo and reports success`() = inTempDir { root ->
        val photoId = "pending-active"
        val relPath = "photos/property/inspection/$photoId.jpg"
        val asset = asset(root, relPath)
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)
        insertActive(photoId, relPath)

        val decision = PhotoOrphanCleanupRunner(database, root, OrphanFileDeleter { false }).run()

        assertEquals(PhotoOrphanCleanupDecision.SUCCESS, decision)
        assertTrue(asset.isFile, "an active exact DB row adopts the JPEG instead of deleting it")
        assertFalse(File(asset.parentFile, "$photoId.jpg.pending").exists())
    }

    @Test
    fun `runner maps a sidecar delete failure to retry`() = inTempDir { root ->
        val photoId = "pending-delete-fails"
        val relPath = "photos/property/inspection/$photoId.jpg"
        val asset = asset(root, relPath)
        val marker = File(asset.parentFile, "$photoId.jpg.pending")
        PendingPhotoLease.acquire(asset).closeAfter(PendingPhotoLeaseDisposition.RETAIN)

        val decision = PhotoOrphanCleanupRunner(database, root, OrphanFileDeleter { false }).run()

        assertEquals(PhotoOrphanCleanupDecision.RETRY, decision)
        assertTrue(asset.isFile)
        assertTrue(marker.isFile, "a failed delete must leave recovery state intact for the next worker run")
    }

    @Test
    fun `runner maps unresolved marker cleanup to retry after deleting its unadopted JPEG`() = inTempDir { root ->
        val photoId = "pending-marker-delete-fails"
        val relPath = "photos/property/inspection/$photoId.jpg"
        val asset = asset(root, relPath)
        val marker = File(asset.parentFile, "$photoId.jpg.pending")
        val lease = PendingPhotoLease.acquire(asset)

        RandomAccessFile(marker, "rw").use {
            assertFalse(lease.closeAfter(PendingPhotoLeaseDisposition.RECORDED))
            val decision = PhotoOrphanCleanupRunner(
                database,
                root,
                OrphanFileDeleter { path -> File(root, path).delete() || !File(root, path).exists() },
            ).run()

            assertEquals(PhotoOrphanCleanupDecision.RETRY, decision)
            assertFalse(asset.exists(), "the marker retry must not undo a successfully removed unadopted JPEG")
            assertTrue(marker.isFile)
            assertTrue(marker.length() > 0L, "delete failure must preserve the resolving handoff for a later worker")
        }
    }

    @Test
    fun `runner executes the existing orphan cleaner and maps rejected data to failure`() = inTempDir { root ->
        val corruptRelPath = "audio/property/inspection/not-a-photo.m4a"
        val photoId = "soft-deleted-corrupt"
        insertActive(photoId, corruptRelPath)
        database.photoQueries.softDelete(deleted_at = DbTestFixtures.NOW + 1, id = photoId)
        val calls = mutableListOf<String>()

        val decision = PhotoOrphanCleanupRunner(
            database,
            root,
            OrphanFileDeleter { path -> calls += path; true },
        ).run()

        assertEquals(PhotoOrphanCleanupDecision.FAILURE, decision)
        assertTrue(calls.isEmpty(), "the existing cleaner must keep rejecting malformed stored paths")
    }

    @Test
    fun `runner maps a rejected pending sidecar to failure without any rejected soft-delete row`() = inTempDir { root ->
        val directory = File(root, "photos/property/inspection").also { assertTrue(it.mkdirs()) }
        File(directory, " .jpg.pending").writeText("")
        val calls = mutableListOf<String>()

        val decision = PhotoOrphanCleanupRunner(
            database,
            root,
            OrphanFileDeleter { path -> calls += path; true },
        ).run()

        assertEquals(PhotoOrphanCleanupDecision.FAILURE, decision)
        assertTrue(calls.isEmpty(), "pending rejection alone must fail closed before any deletion")
    }

    private fun insertActive(photoId: String, relPath: String) {
        database.photoQueries.insert(
            id = photoId,
            inspection_item_id = null,
            room_instance_id = roomInstanceId,
            rel_path = relPath,
            content_hash = "hash-$photoId",
            exif_time_ms = null,
            source = "CAMERA",
            privacy_flag = 0,
            created_at = DbTestFixtures.NOW,
            updated_at = DbTestFixtures.NOW,
        )
    }

    private fun asset(root: File, relPath: String): File = File(root, relPath).also {
        assertTrue(it.parentFile!!.mkdirs())
        it.writeText("evidence")
    }

    private fun inTempDir(block: (File) -> Unit) {
        val root = kotlin.io.path.createTempDirectory("td14-runner-").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
