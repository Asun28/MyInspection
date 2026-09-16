package nz.myinspection.app.platform

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import nz.myinspection.app.media.MediaFileStore
import nz.myinspection.app.media.PhotoImportPipeline
import nz.myinspection.app.media.PhotoIngestOutcome
import nz.myinspection.app.media.PhotoIngestPendingLease
import nz.myinspection.app.media.PhotoOrphanCleanupWorker
import nz.myinspection.core.media.PhotoOrphanCleanupBucket
import nz.myinspection.core.media.PhotoOrphanCleanupDecision
import nz.myinspection.core.media.PhotoOrphanCleanupExecution
import nz.myinspection.core.media.PhotoOrphanCleanupIssue
import nz.myinspection.core.media.PhotoOrphanCleanupIssueResult
import nz.myinspection.core.media.PendingPhotoLeaseDisposition
import nz.myinspection.core.media.StreamEncoder
import nz.myinspection.core.media.VerifiedAssetWorkflow

class SafeLogTest {
    private val photoId = "018f4a5e-1267-7d3a-8b18-0425d7f0b4aa"
    private val workId = "018f4a5e-1267-7d3a-8b18-0425d7f0b4bb"

    @Test
    fun `safe log renders only typed fields and omits malformed optional values`() {
        val sink = CapturingSink()
        val log = SafeLog(sink)

        log.record(
            SafeLogEvent(
                operation = SafeLogOperation.ORPHAN_CLEANUP,
                reason = SafeLogReason.EXECUTION_FAILED,
                opaqueId = SafeLogOpaqueId.fromUuid(workId),
                count = 3,
                durationMs = 17,
            ),
        )

        assertEquals(
            listOf("operation=orphan-cleanup reason=execution-failed opaque_id=$workId count=3 duration_ms=17"),
            sink.messages,
        )
        assertEquals(null, SafeLogOpaqueId.parse("1-1-1-1-1"), "JDK UUID parsing alone accepts non-canonical groups")
        assertEquals(null, SafeLogOpaqueId.parse("42 Example St Jane Tenant secret"))

        log.record(
            SafeLogEvent(
                operation = SafeLogOperation.ORPHAN_CLEANUP,
                reason = SafeLogReason.EXECUTION_FAILED,
                count = -1,
                durationMs = -1,
            ),
        )
        assertEquals(
            "operation=orphan-cleanup reason=execution-failed",
            sink.messages.last(),
            "invalid optional values must be omitted instead of changing the media operation result",
        )
    }

    @Test
    fun `safe log and companion expose no free text or throwable recording entry point`() {
        val methods = SafeLog::class.java.methods + SafeLog.Companion::class.java.methods

        assertTrue(methods.any { it.name == "record" })
        assertFalse(
            methods.any { method ->
                method.parameterTypes.any { type -> type == String::class.java || Throwable::class.java.isAssignableFrom(type) }
            },
        )
    }

    @Test
    fun `media store copy publish and import cleanup preserve their real results when logging fails`() = inSensitiveTempDir { root ->
        val sink = CapturingSink()
        val log = SafeLog(sink)
        val published = MediaFileStore.copyInto(
            source = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            root = root,
            relPath = "media/photo.jpg",
            tempDelete = { false },
            log = log,
        )
        val importTemp = File(root, "content://provider/Raw body Authorization Bearer secret")

        PhotoImportPipeline.cleanupImportTemp(importTemp, photoId, primary = null, delete = { false }, log = log)

        assertEquals(listOf<Byte>(1, 2, 3), published.readBytes().toList())
        assertEquals(
            listOf(
                "operation=media-temp-delete reason=delete-failed",
                "operation=import-temp-delete reason=delete-failed opaque_id=$photoId",
            ),
            sink.messages,
        )
        assertNoLeak(sink.messages)

        val throwingLog = SafeLog(SafeLogSink { throw AssertionError("sink unavailable") })
        val stillPublished = runCatching {
            val copied = MediaFileStore.copyInto(
                source = ByteArrayInputStream(byteArrayOf(4)),
                root = root,
                relPath = "media/still-published.jpg",
                tempDelete = { false },
                log = throwingLog,
            )
            PhotoImportPipeline.cleanupImportTemp(importTemp, photoId, primary = null, delete = { false }, log = throwingLog)
            copied
        }
        assertTrue(stillPublished.isSuccess, "sink failures must preserve the successful copy and import cleanup")
        assertEquals(listOf<Byte>(4), stillPublished.getOrThrow().readBytes().toList())
    }

    @Test
    fun `copy primary survives a failed cleanup and throwing sink without publishing`() = inSensitiveTempDir { root ->
        val primary = IOException("copy primary secret")
        var deleteCalls = 0

        val thrown = assertFailsWith<IOException> {
            MediaFileStore.copyInto(
                source = object : InputStream() { override fun read(): Int = throw primary },
                root = root,
                relPath = "media/not-published.jpg",
                tempDelete = { deleteCalls += 1; false },
                log = SafeLog(SafeLogSink { throw IllegalStateException("sink unavailable") }),
            )
        }

        assertSame(primary, thrown)
        assertEquals(1, deleteCalls, "the original finally cleanup must still execute")
        assertFalse(File(root, "media/not-published.jpg").exists())
    }

    @Test
    fun `import primary keeps cleanup suppression and completed lease cleanup keeps recorded result`() = inSensitiveTempDir { root ->
        val sink = CapturingSink()
        val log = SafeLog(sink)
        val primary = IOException("42 Example St Jane Tenant secret")
        val cleanup = IOException("raw provider body", IllegalStateException("nested cause secret")).also {
            it.addSuppressed(IllegalArgumentException("suppressed Authorization Bearer"))
        }
        val importTemp = File(root, "content://provider/import-secret")

        PhotoImportPipeline.cleanupImportTemp(importTemp, photoId, primary, delete = { throw cleanup }, log = log)
        assertEquals(listOf(cleanup), primary.suppressed.toList())
        val sinkPrimary = IOException("import primary secret")
        PhotoImportPipeline.cleanupImportTemp(
            importTemp,
            photoId,
            sinkPrimary,
            delete = { throw cleanup },
            log = SafeLog(SafeLogSink { throw IllegalStateException("sink unavailable") }),
        )
        assertEquals(listOf(cleanup), sinkPrimary.suppressed.toList())
        val noPrimary = assertFailsWith<IOException> {
            PhotoImportPipeline.cleanupImportTemp(importTemp, photoId, primary = null, delete = { throw cleanup }, log = log)
        }
        assertSame(cleanup, noPrimary, "cleanup must retain its original exception when no import primary exists")

        val recorded = PhotoIngestOutcome.Recorded(photoId, "photos/private.jpg", false, null)
        val lease = PhotoIngestPendingLease(photoId, { throw cleanup }, log)
        val result = VerifiedAssetWorkflow.encodeStagePublishRecord(
            target = File(root, "target.jpg"),
            input = Unit,
            encoder = StreamEncoder { _, output -> output.write(7) },
            plan = { Unit },
            shouldPublish = { true },
            publicationLease = { lease },
            publish = { _, _ -> Unit },
            record = { recorded },
        )

        assertSame(recorded, result)
        val throwingLog = SafeLog(SafeLogSink { throw IllegalStateException("sink unavailable") })
        val sinkFailingLease = PhotoIngestPendingLease(photoId, { throw cleanup }, throwingLog)
        val resultWithThrowingSink = VerifiedAssetWorkflow.encodeStagePublishRecord(
            target = File(root, "target-throwing-sink.jpg"),
            input = Unit,
            encoder = StreamEncoder { _, output -> output.write(8) },
            plan = { Unit },
            shouldPublish = { true },
            publicationLease = { sinkFailingLease },
            publish = { _, _ -> Unit },
            record = { recorded },
        )
        assertSame(recorded, resultWithThrowingSink, "sink failure cannot replace a completed recorded result")
        val dispositions = mutableListOf<PendingPhotoLeaseDisposition>()
        fun leaseFor(result: PhotoIngestOutcome) = PhotoIngestPendingLease(
            photoId,
            { disposition -> dispositions += disposition; true },
            log,
        ).also { lease -> lease.finish(result); lease.close() }
        leaseFor(recorded)
        leaseFor(PhotoIngestOutcome.RejectedByGuard("photos/private.jpg", orphanedFileRemains = false))
        assertEquals(
            listOf(PendingPhotoLeaseDisposition.RECORDED, PendingPhotoLeaseDisposition.REJECTED_WITHOUT_ORPHAN),
            dispositions,
        )
        assertEquals(
            listOf(
                "operation=import-temp-delete reason=cleanup-failed opaque_id=$photoId",
                "operation=import-temp-delete reason=cleanup-failed opaque_id=$photoId",
                "operation=pending-lease-release reason=cleanup-failed opaque_id=$photoId",
            ),
            sink.messages,
        )
        assertNoLeak(sink.messages)
    }

    @Test
    fun `pending lease and orphan worker retain outcome distinctions without leaking core failures`() {
        for (retryable in listOf(true, false)) {
            val expectedDecision = if (retryable) PhotoOrphanCleanupDecision.RETRY else PhotoOrphanCleanupDecision.FAILURE
            val sink = CapturingSink()
            val log = SafeLog(sink)
            val lease = PhotoIngestPendingLease(photoId, { false }, log)
            lease.finish(PhotoIngestOutcome.Recorded(photoId, "photos/private.jpg", false, null))
            lease.close()
            val throwingLogLease = PhotoIngestPendingLease(
                photoId,
                { false },
                SafeLog(SafeLogSink { throw IllegalStateException("sink unavailable") }),
            )
            throwingLogLease.finish(PhotoIngestOutcome.Recorded(photoId, "photos/private.jpg", false, null))
            throwingLogLease.close()

            val nested = IOException("42 Example St Jane Tenant secret", IllegalStateException("nested raw provider body")).also {
                it.addSuppressed(IllegalArgumentException("suppressed Authorization Bearer"))
            }
            val resource = RecordingResource()
            val execution = PhotoOrphanCleanupExecution.run(
                open = { resource },
                cleanup = { throw nested },
                retryable = { retryable },
            )
            val issues = listOf(
                PhotoOrphanCleanupIssue(PhotoOrphanCleanupIssueResult.REJECTED, PhotoOrphanCleanupBucket.PENDING, "content://provider/pending", nested),
                PhotoOrphanCleanupIssue(PhotoOrphanCleanupIssueResult.FAILED, PhotoOrphanCleanupBucket.PENDING, "C:/42 Example St/pending", nested),
                PhotoOrphanCleanupIssue(PhotoOrphanCleanupIssueResult.REJECTED, PhotoOrphanCleanupBucket.SOFT_DELETE, "C:/42 Example St/soft", nested),
                PhotoOrphanCleanupIssue(PhotoOrphanCleanupIssueResult.FAILED, PhotoOrphanCleanupBucket.SOFT_DELETE, "content://provider/soft", nested),
            )

            PhotoOrphanCleanupWorker.reportFailures(issues, execution, workId, 3, log)

            assertEquals(expectedDecision, execution.decision)
            assertEquals(
                listOf(
                    "operation=pending-marker-delete reason=delete-failed opaque_id=$photoId",
                    "operation=orphan-cleanup reason=pending-rejected opaque_id=$workId count=3",
                    "operation=orphan-cleanup reason=pending-failed opaque_id=$workId count=3",
                    "operation=orphan-cleanup reason=soft-delete-rejected opaque_id=$workId count=3",
                    "operation=orphan-cleanup reason=soft-delete-failed opaque_id=$workId count=3",
                    "operation=orphan-cleanup reason=execution-failed opaque_id=$workId count=3",
                ),
                sink.messages,
            )
            assertNoLeak(sink.messages)

            val throwingLog = SafeLog(SafeLogSink { throw IllegalStateException("sink unavailable") })
            val logged = runCatching {
                PhotoOrphanCleanupWorker.reportFailures(issues, execution, workId, 3, throwingLog)
            }
            assertTrue(logged.isSuccess, "sink failure must preserve retryable and non-retryable worker outcomes")
            assertEquals(1, resource.closeCalls)
            assertEquals(expectedDecision, execution.decision)
        }
    }

    @Test
    fun `malformed opaque ids and negative attempts are omitted without changing failure handling`() = inSensitiveTempDir { root ->
        val sink = CapturingSink()
        val log = SafeLog(sink)
        val invalidId = "1-1-1-1-1"
        PhotoImportPipeline.cleanupImportTemp(File(root, "import"), invalidId, primary = null, delete = { false }, log = log)
        val lease = PhotoIngestPendingLease(invalidId, { false }, log)
        lease.finish(PhotoIngestOutcome.Recorded(photoId, "photos/private.jpg", false, null))
        lease.close()
        val resource = RecordingResource()
        val execution = PhotoOrphanCleanupExecution.run(
            open = { resource },
            cleanup = { PhotoOrphanCleanupDecision.SUCCESS },
            retryable = { false },
        )
        PhotoOrphanCleanupWorker.reportFailures(
            listOf(PhotoOrphanCleanupIssue(PhotoOrphanCleanupIssueResult.FAILED, PhotoOrphanCleanupBucket.PENDING, "C:/42 Example St/secret", null)),
            execution,
            invalidId,
            -1,
            log,
        )

        assertEquals(PhotoOrphanCleanupDecision.SUCCESS, execution.decision)
        assertEquals(1, resource.closeCalls)
        assertEquals(
            listOf(
                "operation=import-temp-delete reason=delete-failed",
                "operation=pending-marker-delete reason=delete-failed",
                "operation=orphan-cleanup reason=pending-failed",
            ),
            sink.messages,
        )
        assertNoLeak(sink.messages)
    }

    private fun assertNoLeak(messages: List<String>) {
        val output = messages.joinToString("\n")
        listOf(
            "42 Example St", "Jane Tenant", "content://", "raw provider body", "Authorization", "Bearer", "secret",
            "IOException", "IllegalStateException", "IllegalArgumentException", "nested cause", "suppressed", "at ",
        ).forEach { forbidden -> assertFalse(output.contains(forbidden), "leaked $forbidden: $output") }
    }

    private fun inSensitiveTempDir(block: (File) -> Unit) {
        val root = createTempDirectory("42 Example St Jane Tenant secret").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private class RecordingResource : AutoCloseable {
        var closeCalls = 0
            private set

        override fun close() {
            closeCalls += 1
        }
    }
}

private class CapturingSink : SafeLogSink {
    val messages = mutableListOf<String>()

    override fun write(message: String) {
        messages += message
    }
}
/*
 * R4: 24 semantic mutations, each compiled separately (exit 0), failed a named assertion, and restored source SHA.
 * Gradle targets: :app:compileDebugUnitTestKotlin; :app:testDebugUnitTest --tests nz.myinspection.app.platform.SafeLogTest.
 * M11-M15 temporarily reflect into the same sink with actual caller paths/stacks, retaining best-effort sink isolation.
 * Original M19 exposed the injected Error, not an assertion; M19b rechecks explicit success. No redundant test removed.
 * T1 = copy primary survives a failed cleanup and throwing sink without publishing
 * T2 = import primary keeps cleanup suppression and completed lease cleanup keeps recorded result
 * T3 = malformed opaque ids and negative attempts are omitted without changing failure handling
 * T4 = media store copy publish and import cleanup preserve their real results when logging fails
 * T5 = pending lease and orphan worker retain outcome distinctions without leaking core failures
 * T6 = safe log and companion expose no free text or throwable recording entry point
 * T7 = safe log renders only typed fields and omits malformed optional values
 * M01-media-temp-log-omission -> T4; M02-import-false-log-omission -> T4 (each compile 0 / test 1)
 * M03-import-throw-log-omission -> T2; M05-pending-release-log-omission -> T2 (each compile 0 / test 1)
 * M04-pending-marker-log-omission -> T5 (compile 0 / test 1)
 * M06-worker-pending-rejected-reason -> T5; M07-worker-pending-failed-reason -> T5; M08-worker-soft-delete-rejected-reason -> T5; M09-worker-soft-delete-failed-reason -> T5 (each compile 0 / test 1)
 * M10-worker-execution-log-omission -> T5 (compile 0 / test 1)
 * M11-media-caller-path-reflection-escape -> T4; M12-import-caller-throwable-reflection-escape -> T2; M13-lease-caller-throwable-reflection-escape -> T2 (each compile 0 / test 1)
 * M14-worker-issue-caller-reflection-escape -> T5; M15-worker-execution-caller-reflection-escape -> T5 (each compile 0 / test 1)
 * M16-uuid-shape-guard-bypass -> T3 (compile 0 / test 1)
 * M17-negative-count-guard-bypass -> T3 (compile 0 / test 1)
 * M18-negative-duration-guard-bypass -> T7 (compile 0 / test 1)
 * M19b-sink-throwable-assertion -> T4 (compile 0 / test 1)
 * M20-import-primary-suppression-break -> T2 (compile 0 / test 1)
 * M21-lease-recorded-disposition-break -> T2 (compile 0 / test 1)
 * M22-lease-confirmed-guard-disposition-break -> T2 (compile 0 / test 1)
 * M23-free-text-recording-overload -> T6 (compile 0 / test 1)
 * M24-copy-primary-identity-break -> T1 (compile 0 / test 1)
 * Restored MediaFileStore.kt SHA256 c03158914ed4570dc669ac29ad518a446a500ce747e778b2562160bbd58b0a13
 * Restored PhotoImportPipeline.kt SHA256 4992556a40aa75ced84bff0ea2c54de90c4347e16fb1e5b8a977af9601485f26
 * Restored PhotoIngestPendingLease.kt SHA256 505cd9d6bed4613bdc0cb2528501747fe12756de4217ce9b6e70909acb907da9
 * Restored PhotoOrphanCleanupWorker.kt SHA256 0a8e3ec03dd1a417da57ff3f6df07c9c14f9281706432cfba3b2940427d94f9a
 * Restored SafeLog.kt SHA256 40dcbc8d1f2ea370f57bc9234919a9d73f26a6da0034f39c2f1a4d4de643b173
 */
