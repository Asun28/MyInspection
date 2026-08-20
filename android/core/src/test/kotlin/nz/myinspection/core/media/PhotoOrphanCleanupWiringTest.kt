package nz.myinspection.core.media

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Android has no JVM runtime here, so this is a narrow source guard around the already-executable core lifecycle. */
class PhotoOrphanCleanupWiringTest {
    @Test
    fun `both ingest adapters acquire the sidecar after planning before publish and resolve it after recording`() {
        val media = appMedia()
        val camera = Files.readString(media.resolve("CameraPhotoIngestPipeline.kt"))
        val imported = Files.readString(media.resolve("PhotoImportPipeline.kt"))
        val lease = Files.readString(media.resolve("PhotoIngestPendingLease.kt"))

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
            worker.contains("failure is IOException || failure is SecurityException || failure is SQLiteException"),
            "database and filesystem environment failures must request retry; unknown exceptions fail closed",
        )
        assertTrue(worker.contains("PhotoOrphanCleanupDecision.RETRY -> Result.retry()"))
        assertTrue(worker.contains("PhotoOrphanCleanupDecision.FAILURE -> Result.failure()"))
        assertTrue(worker.contains("execution.failure"), "the app adapter must retain core primary/suppressed failure evidence")
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
