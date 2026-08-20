package nz.myinspection.core.media

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
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

        val report = PhotoOrphanCleanupRunner(database, root, OrphanFileDeleter { false }).run()

        assertEquals(PhotoOrphanCleanupDecision.SUCCESS, report.decision)
        assertEquals(listOf(relPath), report.pending.readopted)
        assertEquals(CleanupResult(emptyList(), emptyList(), emptyList()), report.softDelete)
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

        val report = PhotoOrphanCleanupRunner(database, root, OrphanFileDeleter { false }).run()

        assertEquals(PhotoOrphanCleanupDecision.RETRY, report.decision)
        assertEquals(listOf(FailedDeletion(relPath, cause = null)), report.pending.failed)
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
            val report = PhotoOrphanCleanupRunner(
                database,
                root,
                OrphanFileDeleter { path -> File(root, path).delete() || !File(root, path).exists() },
            ).run()

            assertEquals(PhotoOrphanCleanupDecision.RETRY, report.decision)
            assertEquals(listOf(FailedDeletion(relPath, cause = null)), report.pending.failed)
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

        val report = PhotoOrphanCleanupRunner(
            database,
            root,
            OrphanFileDeleter { path -> calls += path; true },
        ).run()

        assertEquals(PhotoOrphanCleanupDecision.FAILURE, report.decision)
        assertEquals(listOf(corruptRelPath), report.softDelete.rejected)
        assertEquals(CleanupResult(emptyList(), emptyList(), emptyList()), report.pending)
        assertTrue(calls.isEmpty(), "the existing cleaner must keep rejecting malformed stored paths")
    }

    @Test
    fun `runner maps a rejected pending sidecar to failure without any rejected soft-delete row`() = inTempDir { root ->
        val directory = File(root, "photos/property/inspection").also { assertTrue(it.mkdirs()) }
        File(directory, " .jpg.pending").writeText("")
        val calls = mutableListOf<String>()

        val report = PhotoOrphanCleanupRunner(
            database,
            root,
            OrphanFileDeleter { path -> calls += path; true },
        ).run()

        assertEquals(PhotoOrphanCleanupDecision.FAILURE, report.decision)
        assertEquals(listOf("photos/property/inspection/ .jpg.pending"), report.pending.rejected)
        assertEquals(CleanupResult(emptyList(), emptyList(), emptyList()), report.softDelete)
        assertTrue(calls.isEmpty(), "pending rejection alone must fail closed before any deletion")
    }

    @Test
    fun `runner report and issue projection keep pending and soft-delete rejections distinct`() = inTempDir { root ->
        val pendingPath = "photos/property/inspection/ .jpg.pending"
        File(root, pendingPath).also {
            assertTrue(it.parentFile!!.mkdirs())
            it.writeText("")
        }
        val softDeletePath = "audio/property/inspection/not-a-photo.m4a"
        val photoId = "soft-deleted-observable"
        insertActive(photoId, softDeletePath)
        database.photoQueries.softDelete(deleted_at = DbTestFixtures.NOW + 1, id = photoId)

        val report = PhotoOrphanCleanupRunner(database, root, OrphanFileDeleter { true }).run()

        assertEquals(PhotoOrphanCleanupDecision.FAILURE, report.decision)
        assertEquals(listOf(pendingPath), report.pending.rejected)
        assertEquals(listOf(softDeletePath), report.softDelete.rejected)
        assertEquals(
            listOf(
                PhotoOrphanCleanupIssue(
                    result = PhotoOrphanCleanupIssueResult.REJECTED,
                    bucket = PhotoOrphanCleanupBucket.PENDING,
                    path = pendingPath,
                    cause = null,
                ),
                PhotoOrphanCleanupIssue(
                    result = PhotoOrphanCleanupIssueResult.REJECTED,
                    bucket = PhotoOrphanCleanupBucket.SOFT_DELETE,
                    path = softDeletePath,
                    cause = null,
                ),
            ),
            report.issues(),
        )
    }

    @Test
    fun `report issue projection retains each failed path and cause without collapsing buckets`() {
        val pendingCause = IOException("pending marker locked")
        val softDeleteCause = SecurityException("soft-delete asset denied")
        val pendingPath = "photos/property/inspection/pending.jpg"
        val softDeletePath = "photos/property/inspection/soft-delete.jpg"

        val report = PhotoOrphanCleanupReport.from(
            pending = CleanupResult(
                deleted = emptyList(),
                failed = listOf(FailedDeletion(pendingPath, pendingCause)),
                rejected = emptyList(),
            ),
            softDelete = CleanupResult(
                deleted = emptyList(),
                failed = listOf(FailedDeletion(softDeletePath, softDeleteCause)),
                rejected = emptyList(),
            ),
        )

        assertEquals(PhotoOrphanCleanupDecision.RETRY, report.decision)
        val issues = report.issues()
        assertEquals(2, issues.size)
        assertEquals(PhotoOrphanCleanupIssueResult.FAILED, issues[0].result)
        assertEquals(PhotoOrphanCleanupBucket.PENDING, issues[0].bucket)
        assertEquals(pendingPath, issues[0].path)
        assertSame(pendingCause, issues[0].cause)
        assertEquals(PhotoOrphanCleanupIssueResult.FAILED, issues[1].result)
        assertEquals(PhotoOrphanCleanupBucket.SOFT_DELETE, issues[1].bucket)
        assertEquals(softDeletePath, issues[1].path)
        assertSame(softDeleteCause, issues[1].cause)
    }

    @Test
    fun `report fails closed when a retryable primary retains an unknown suppressed cleanup error`() {
        val primary = IOException("marker filesystem unavailable")
        primary.addSuppressed(IllegalStateException("lease close contract violated"))

        val report = PhotoOrphanCleanupReport.from(
            pending = CleanupResult(
                deleted = emptyList(),
                failed = listOf(FailedDeletion("photos/property/inspection/photo.jpg", primary)),
                rejected = emptyList(),
            ),
            softDelete = CleanupResult(emptyList(), emptyList(), emptyList()),
        )

        assertEquals(PhotoOrphanCleanupDecision.FAILURE, report.decision)
        assertSame(primary, report.pending.failed.single().cause)
        val issue = report.issues().single()
        assertEquals(PhotoOrphanCleanupBucket.PENDING, issue.bucket)
        assertEquals(PhotoOrphanCleanupIssueResult.FAILED, issue.result)
        assertEquals("photos/property/inspection/photo.jpg", issue.path)
        assertSame(primary, issue.cause)
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
