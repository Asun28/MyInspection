package nz.myinspection.core.media

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Android has no JVM runtime here, so this is a narrow source guard around the already-executable core lifecycle. */
class PhotoOrphanCleanupWiringTest {
    @Test
    fun `both ingest adapters acquire the sidecar after planning before publish and resolve it after recording`() {
        val media = appMedia()
        val camera = Files.readString(media.resolve("CameraPhotoIngestPipeline.kt"))
        val imported = Files.readString(media.resolve("PhotoImportPipeline.kt"))
        val lease = Files.readString(media.resolve("PhotoIngestPendingLease.kt"))
        val coreLease = Files.readString(
            androidRoot().resolve("core/src/main/kotlin/nz/myinspection/core/media/PendingPhotoLease.kt"),
        )

        assertLeaseIsWired(camera, "camera")
        assertLeaseIsWired(imported, "import")
        assertTrue(
            lease.contains("is PhotoIngestOutcome.Recorded -> PendingPhotoLeaseDisposition.RECORDED"),
            "only a recorded association may clear its sidecar",
        )
        assertTrue(
            lease.contains("is PhotoIngestOutcome.RejectedByGuard -> if (!result.orphanedFileRemains)"),
            "a guard rejection may clear its sidecar only after compensation removed the JPEG",
        )
        assertTrue(
            lease.contains("lease.closeAfter(disposition)"),
            "the adapter must release and resolve the real durable lease, not a memory-only flag",
        )
        assertInOrder(
            lease,
            "PendingPhotoLease.acquire(target, ::syncParentDirectory)",
            "Os.open(parent.path, OsConstants.O_RDONLY, 0)",
            "Os.fsync(descriptor)",
            "Os.close(descriptor)",
        )
        assertTrue(
            lease.contains("activeFailure.addSuppressed(closeFailure)"),
            "directory descriptor close must stay suppressed beneath an fsync primary",
        )
        assertFalse(
            lease.contains("check(isExpectedEnvironmentFailure(failure))"),
            "post-record cleanup logging must not turn an unknown cleanup failure into an ingest failure",
        )
        assertInOrder(
            coreLease,
            "fun acquire(target: File, syncParentDirectory: (File) -> Unit)",
            "acquireWithDurability(",
            "forceMarker = { channel -> channel.force(true) }",
        )
        assertTrue(
            coreLease.contains("FileChannel.open(path, READ, WRITE, CREATE_NEW, NOFOLLOW_LINKS)"),
            "marker creation must stay atomic and refuse a link leaf",
        )
        assertTrue(
            coreLease.contains("FileChannel.open(marker.toPath(), READ, NOFOLLOW_LINKS)"),
            "scan and delete verification must reopen marker paths without following links",
        )
        assertTrue(
            lease.contains("override fun onCompletedCleanupFailure(failure: Throwable)"),
            "a post-record lease close error must be logged without replacing the recorded outcome",
        )
    }

    @Test
    fun `worker uses internal runtime storage closes its driver and scheduler keeps one constrained daily job`() {
        val app = androidRoot().resolve("app/src/main/kotlin/nz/myinspection/app")
        val media = app.resolve("media")
        val runtimeStorage = Files.readString(media.resolve("PhotoRuntimeStorage.kt"))
        val worker = Files.readString(media.resolve("PhotoOrphanCleanupWorker.kt"))
        val cleanupExecutor = Files.readString(media.resolve("PhotoAssetCleanupExecutor.kt"))
        val noFollowDeletion = Files.readString(
            androidRoot().resolve("core/src/main/kotlin/nz/myinspection/core/media/NoFollowLeafDeletion.kt"),
        )
        val scheduler = Files.readString(media.resolve("PhotoOrphanCleanupScheduler.kt"))
        val main = Files.readString(app.resolve("MainActivity.kt"))

        assertTrue(runtimeStorage.contains("File(context.filesDir, \"media\")"), "cleanup storage must stay internal")
        assertTrue(runtimeStorage.contains("const val DATABASE_NAME = \"myinspection.db\""))
        assertTrue(
            worker.contains("AndroidSqliteDriver(MyInspectionDatabase.Schema, applicationContext, storage.databaseName)"),
            "the worker must use the single runtime storage database name",
        )
        assertInOrder(
            worker,
            "PhotoOrphanCleanupExecution.run(",
            "PhotoRuntimeStorage.from(applicationContext)",
            "AndroidSqliteDriver(MyInspectionDatabase.Schema",
            "MyInspectionDatabase(resources.driver)",
            "PhotoAssetCleanupExecutor(resources.storage.mediaRoot)",
            "PhotoOrphanCleanupRunner",
        )
        assertTrue(worker.contains("retryable = ::isRetryableEnvironmentFailure"))
        assertTrue(
            worker.contains("isRetryablePhotoOrphanSqliteFailure(classifySqliteFailure(failure))"),
            "the Android adapter must delegate its exact SQLite subtype mapping to the executable core classifier",
        )
        assertInOrder(
            worker,
            "private fun classifySqliteFailure",
            "is SQLiteDatabaseLockedException -> PhotoOrphanSqliteFailureKind.DATABASE_LOCKED",
            "is SQLiteTableLockedException -> PhotoOrphanSqliteFailureKind.TABLE_LOCKED",
            "is SQLiteDiskIOException -> PhotoOrphanSqliteFailureKind.DISK_IO",
            "is SQLiteCantOpenDatabaseException -> PhotoOrphanSqliteFailureKind.CANT_OPEN",
            "else -> PhotoOrphanSqliteFailureKind.OTHER",
        )
        assertTrue(cleanupExecutor.contains("override fun deleteNoFollow(relPath: String): Boolean"))
        assertTrue(
            cleanupExecutor.contains("NoFollowLeafDeletion.delete(mediaRoot, relPath)"),
            "the Android adapter must use the executable no-follow parent and leaf boundary",
        )
        assertTrue(noFollowDeletion.contains("Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)"))
        assertTrue(noFollowDeletion.contains("Files.deleteIfExists(boundary)"), "only the validated leaf entry may be deleted")
        assertFalse(
            noFollowDeletion.contains("Path.of("),
            "the core helper ships to minSdk 26 and must not reference the Java 11 static Path factory",
        )
        assertTrue(worker.contains("PhotoOrphanCleanupDecision.RETRY -> Result.retry()"))
        assertTrue(worker.contains("PhotoOrphanCleanupDecision.FAILURE -> Result.failure()"))
        assertTrue(worker.contains("execution.failure"), "the app adapter must retain core primary/suppressed failure evidence")
        assertTrue(worker.contains("cleanupReport?.issues()?.forEach"), "every runner issue must be logged, not collapsed")
        assertTrue(worker.contains("workId=\$id"))
        assertTrue(worker.contains("runAttemptCount=\$runAttemptCount"))
        assertTrue(worker.contains("result=\${issue.result.logValue}"))
        assertTrue(worker.contains("bucket=\${issue.bucket.logValue}"))
        assertTrue(worker.contains("path=\${issue.path}"))
        assertTrue(worker.contains("cause=\$causeName"))
        assertTrue(worker.contains("result=execution_failure bucket=execution path=none"))
        assertInOrder(worker, "private data class CleanupResources", "override fun close()", "driver.close()")
        assertTrue(scheduler.contains("PeriodicWorkRequestBuilder<PhotoOrphanCleanupWorker>(24, TimeUnit.HOURS)"))
        assertTrue(scheduler.contains("setRequiresStorageNotLow(true)"))
        assertTrue(scheduler.contains("ExistingPeriodicWorkPolicy.KEEP"))
        assertTrue(scheduler.contains("enqueueUniquePeriodicWork(UNIQUE_WORK_NAME"))
        assertTrue(main.contains("PhotoOrphanCleanupScheduler.schedule(this)"), "app startup must install the durable cleanup schedule")
    }

    private fun assertLeaseIsWired(source: String, pipeline: String) {
        assertInOrder(
            source,
            "plan =",
            "publicationLease =",
            "PhotoIngestPendingLease.acquire",
            "publish =",
            "record =",
        )
        assertTrue(source.contains("PhotoIngestPlan.WriteNewAsset"), "$pipeline must skip sidecars for reused assets")
    }

    private fun assertInOrder(source: String, vararg fragments: String) {
        var previous = -1
        for (fragment in fragments) {
            val current = source.indexOf(fragment, startIndex = previous + 1)
            assertTrue(current > previous, "expected `$fragment` after the previous lifecycle boundary")
            previous = current
        }
    }

    private fun appMedia(): Path = androidRoot().resolve("app/src/main/kotlin/nz/myinspection/app/media")

    private fun androidRoot(): Path =
        generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .firstOrNull { Files.isDirectory(it.resolve("app/src/main/kotlin/nz/myinspection/app/media")) }
            ?: error("could not locate Android app sources from ${System.getProperty("user.dir")}")
}
