package nz.myinspection.core.media

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Executable lifecycle evidence for the one shared camera/import final-JPEG path. The custom stagers below delegate
 * to [StreamFileStager]; they only observe or inject its file edges, so the workflow never gets a fake stage result.
 */
class VerifiedAssetWorkflowTest {
    @Test
    fun `workflow closes and verifies before publishing then records`() = inTempDir { directory ->
        val target = File(directory, "photo.jpg")
        val events = mutableListOf<String>()

        val result = VerifiedAssetWorkflow.encodeStagePublishRecordWith(
            stager = VerificationRecordingStager(events),
            target = target,
            input = "abc",
            encoder = StreamEncoder<String> { value, output ->
                events += "encode"
                output.write(value.toByteArray(Charsets.US_ASCII))
            },
            plan = { staged ->
                events += "plan"
                assertTrue(staged.file.exists(), "publish receives the closed verified temp")
                "new"
            },
            shouldPublish = { true },
            publish = { staged, _ ->
                events += "publish"
                Files.move(staged.file.toPath(), target.toPath())
            },
            record = {
                events += "record"
                "recorded"
            },
        )

        assertEquals("recorded", result)
        assertEquals(listOf("stage", "encode", "verify", "plan", "publish", "record"), events)
        assertTrue(target.exists())
    }

    @Test
    fun `a new asset lease begins before publish and ends only after recording`() = inTempDir { directory ->
        val target = File(directory, "photo.jpg")
        val events = mutableListOf<String>()
        val lease = RecordingPublicationLease(events)

        val result = VerifiedAssetWorkflow.encodeStagePublishRecordWith(
            stager = VerificationRecordingStager(events),
            target = target,
            input = "asset",
            encoder = StreamEncoder { value, output ->
                events += "encode"
                output.write(value.toByteArray(Charsets.US_ASCII))
            },
            plan = { events += "plan"; "new" },
            shouldPublish = { true },
            publicationLease = { events += "lease"; lease },
            publish = { staged, _ ->
                events += "publish"
                Files.move(staged.file.toPath(), target.toPath())
            },
            record = {
                events += "record"
                "recorded"
            },
        )

        assertEquals("recorded", result)
        assertEquals(
            listOf("stage", "encode", "verify", "plan", "lease", "publish", "record", "finish:recorded", "close"),
            events,
            "a marker lease must span publish through the completed record, not either half alone",
        )
    }

    @Test
    fun `lease close failure is suppressed beneath the publish failure`() = inTempDir { directory ->
        val primary = IllegalStateException("publish failed")
        val closeFailure = IllegalStateException("lease close failed")
        val lease = ThrowingClosePublicationLease(closeFailure)

        val thrown = assertFailsWith<IllegalStateException> {
            VerifiedAssetWorkflow.encodeStagePublishRecord(
                target = File(directory, "photo.jpg"),
                input = Unit,
                encoder = StreamEncoder { _, output -> output.write(1) },
                plan = { "new" },
                shouldPublish = { true },
                publicationLease = { lease },
                publish = { _, _ -> throw primary },
                record = { error("record must not run after publish failure") },
            )
        }

        assertSame(primary, thrown)
        assertEquals(listOf(closeFailure), thrown.suppressed.toList(), "lease cleanup must not replace the publish failure")
        assertTrue(lease.closed, "a failed publish must still release the marker lock")
    }

    @Test
    fun `lease close failure after a recorded result is reported without replacing that result`() = inTempDir { directory ->
        val closeFailure = IOException("lease filesystem unavailable")
        val lease = ReportingClosePublicationLease(closeFailure)
        val target = File(directory, "photo.jpg")

        val result = VerifiedAssetWorkflow.encodeStagePublishRecord(
            target = target,
            input = Unit,
            encoder = StreamEncoder { _, output -> output.write(1) },
            plan = { "new" },
            shouldPublish = { true },
            publicationLease = { lease },
            publish = { staged, _ -> Files.move(staged.file.toPath(), target.toPath()) },
            record = { "recorded" },
        )

        assertEquals("recorded", result, "DB success must remain visible when only marker release failed")
        assertTrue(target.isFile)
        assertTrue(lease.closed)
        assertSame(closeFailure, lease.reportedCleanupFailure)
    }

    @Test
    fun `unknown lease close failure after recording propagates without being reported as recoverable`() = inTempDir { directory ->
        val closeFailure = IllegalStateException("lease close contract violated")
        val lease = ReportingClosePublicationLease(closeFailure)
        val target = File(directory, "photo.jpg")

        val thrown = assertFailsWith<IllegalStateException> {
            VerifiedAssetWorkflow.encodeStagePublishRecord(
                target = target,
                input = Unit,
                encoder = StreamEncoder { _, output -> output.write(1) },
                plan = { "new" },
                shouldPublish = { true },
                publicationLease = { lease },
                publish = { staged, _ -> Files.move(staged.file.toPath(), target.toPath()) },
                record = { "recorded" },
            )
        }

        assertSame(closeFailure, thrown)
        assertTrue(target.isFile, "recorded evidence stays published even though the contract error fails closed")
        assertSame(null, lease.reportedCleanupFailure, "unknown errors must not enter the recoverable logging hook")
    }

    @Test
    fun `unknown post-record reporting failure propagates beneath the environmental close primary`() = inTempDir { directory ->
        val closeFailure = IOException("lease filesystem unavailable")
        val reportFailure = IllegalStateException("cleanup reporting contract violated")
        val lease = FailingReportPublicationLease(closeFailure, reportFailure)

        val thrown = assertFailsWith<IOException> {
            VerifiedAssetWorkflow.encodeStagePublishRecord(
                target = File(directory, "photo.jpg"),
                input = Unit,
                encoder = StreamEncoder { _, output -> output.write(1) },
                plan = { "new" },
                shouldPublish = { true },
                publicationLease = { lease },
                publish = { _, _ -> Unit },
                record = { "recorded" },
            )
        }

        assertSame(closeFailure, thrown)
        assertEquals(listOf(reportFailure), thrown.suppressed.toList())
    }

    @Test
    fun `environment close with unknown suppressed cleanup error fails closed without recoverable reporting`() =
        inTempDir { directory ->
            val closeFailure = IOException("lease filesystem unavailable")
            val unknownCleanup = IllegalStateException("lease close contract violated")
            closeFailure.addSuppressed(unknownCleanup)
            val lease = ReportingClosePublicationLease(closeFailure)

            val thrown = assertFailsWith<IOException> {
                VerifiedAssetWorkflow.encodeStagePublishRecord(
                    target = File(directory, "photo.jpg"),
                    input = Unit,
                    encoder = StreamEncoder { _, output -> output.write(1) },
                    plan = { "new" },
                    shouldPublish = { true },
                    publicationLease = { lease },
                    publish = { _, _ -> Unit },
                    record = { "recorded" },
                )
            }

            assertSame(closeFailure, thrown)
            assertEquals(listOf(unknownCleanup), thrown.suppressed.toList())
            assertSame(null, lease.reportedCleanupFailure, "mixed failure trees must not enter the recoverable hook")
        }

    @Test
    fun `camera mode derives its plan hash from the closed staged JPEG`() = inTempDir { directory ->
        val target = File(directory, "camera.jpg")
        val finalJpeg = "final-camera-jpeg".toByteArray(Charsets.US_ASCII)
        var planHash: String? = null

        val recordedHash = VerifiedAssetWorkflow.encodeStagePublishRecord(
            target = target,
            input = finalJpeg,
            encoder = StreamEncoder { bytes, output -> output.write(bytes) },
            plan = { staged ->
                planHash = staged.digest.sha256
                staged.digest.sha256
            },
            shouldPublish = { false },
            publish = { _, _ -> error("camera reuse must not publish") },
            record = { it },
        )

        val expectedFinalHash = ContentHash.sha256Hex(finalJpeg)
        assertEquals(expectedFinalHash, planHash)
        assertEquals(expectedFinalHash, recordedHash)
        assertFalse(target.exists(), "camera reuse leaves no unpublished staged file")
    }

    @Test
    fun `import mode records its precomputed original source hash not the staged JPEG digest`() = inTempDir { directory ->
        val target = File(directory, "import.jpg")
        val finalJpeg = "derived-final-jpeg".toByteArray(Charsets.US_ASCII)
        val originalSourceHash = ContentHash.sha256Hex("original-import-source".toByteArray(Charsets.US_ASCII))
        var observedStagedHash: String? = null

        val recordedHash = VerifiedAssetWorkflow.encodeStagePublishRecord(
            target = target,
            input = finalJpeg,
            encoder = StreamEncoder { bytes, output -> output.write(bytes) },
            plan = { staged ->
                observedStagedHash = staged.digest.sha256
                originalSourceHash
            },
            shouldPublish = { true },
            publish = { staged, _ -> Files.move(staged.file.toPath(), target.toPath()) },
            record = { it },
        )

        assertNotEquals(originalSourceHash, observedStagedHash)
        assertEquals(originalSourceHash, recordedHash)
        assertTrue(target.exists(), "a new import asset still publishes its derived JPEG")
    }

    @Test
    fun `stage failure never reaches publish or record`() = inTempDir { directory ->
        val target = File(directory, "photo.jpg")
        val primary = IllegalStateException("encoder failed")
        var publishCalls = 0
        var recordCalls = 0

        val thrown = assertFailsWith<IllegalStateException> {
            VerifiedAssetWorkflow.encodeStagePublishRecord(
                target = target,
                input = Unit,
                encoder = StreamEncoder { _, _ -> throw primary },
                plan = { error("plan must not run after staging failure") },
                shouldPublish = { true },
                publish = { _, _ -> publishCalls += 1 },
                record = { recordCalls += 1 },
            )
        }

        assertSame(primary, thrown)
        assertEquals(0, publishCalls)
        assertEquals(0, recordCalls)
        assertNoPublishedOrTempFile(directory, target)
    }

    @Test
    fun `verification read failure never reaches publish or record`() = inTempDir { directory ->
        val target = File(directory, "photo.jpg")
        val primary = IllegalStateException("verification read failed")
        var publishCalls = 0
        var recordCalls = 0

        val thrown = assertFailsWith<IllegalStateException> {
            VerifiedAssetWorkflow.encodeStagePublishRecordWith(
                stager = ReadFailingStager(primary),
                target = target,
                input = Unit,
                encoder = StreamEncoder { _, output -> output.write(1) },
                plan = { error("plan must not run after verification failure") },
                shouldPublish = { true },
                publish = { _, _ -> publishCalls += 1 },
                record = { recordCalls += 1 },
            )
        }

        assertSame(primary, thrown)
        assertEquals(0, publishCalls)
        assertEquals(0, recordCalls)
        assertNoPublishedOrTempFile(directory, target)
    }

    @Test
    fun `publish failure never records and discards the staged temp`() = inTempDir { directory ->
        val target = File(directory, "photo.jpg")
        val primary = IllegalStateException("publish failed")
        var publishCalls = 0
        var recordCalls = 0

        val thrown = assertFailsWith<IllegalStateException> {
            VerifiedAssetWorkflow.encodeStagePublishRecord(
                target = target,
                input = Unit,
                encoder = StreamEncoder { _, output -> output.write(1) },
                plan = { "new" },
                shouldPublish = { true },
                publish = { _, _ ->
                    publishCalls += 1
                    throw primary
                },
                record = { recordCalls += 1 },
            )
        }

        assertSame(primary, thrown)
        assertEquals(1, publishCalls)
        assertEquals(0, recordCalls)
        assertNoPublishedOrTempFile(directory, target)
    }

    @Test
    fun `a 4096px high entropy encoder stages verifies publishes and records without a full JPEG buffer`() = inTempDir { directory ->
        val target = File(directory, "photo.jpg")
        val boundedStager = BoundedWriteStager(CHUNK_BYTES)
        var encodeCalls = 0
        var largestProducedChunk = 0
        var stagedDigest: StreamDigest? = null
        val events = mutableListOf<String>()

        val result = VerifiedAssetWorkflow.encodeStagePublishRecordWith(
            stager = boundedStager,
            target = target,
            input = Unit,
            encoder = StreamEncoder { _, output ->
                encodeCalls += 1
                writeHighEntropy(PHOTO_BYTES, output) { chunkSize -> largestProducedChunk = maxOf(largestProducedChunk, chunkSize) }
            },
            plan = { staged ->
                stagedDigest = staged.digest
                events += "plan"
                "new"
            },
            shouldPublish = { true },
            publish = { staged, _ ->
                events += "publish"
                Files.move(staged.file.toPath(), target.toPath())
            },
            record = {
                events += "record"
                "recorded"
            },
        )

        assertEquals("recorded", result)
        assertEquals(1, encodeCalls, "one final JPEG must be encoded exactly once")
        assertEquals(CHUNK_BYTES, largestProducedChunk, "the producer owns only its bounded reusable chunk")
        assertEquals(CHUNK_BYTES, boundedStager.largestWriteBytes, "the real staging file stream rejects whole-JPEG writes")
        assertTrue(boundedStager.outputClosed, "the real staging file stream closes before verification and publication")
        assertEquals(PHOTO_BYTES, target.length())
        assertEquals(EXPECTED_HIGH_ENTROPY_SHA256, stagedDigest?.sha256)
        assertEquals(PHOTO_BYTES, stagedDigest?.sizeBytes)
        assertEquals(EXPECTED_HIGH_ENTROPY_SHA256, sha256Of(target))
        assertEquals(listOf("plan", "publish", "record"), events)
    }

    private class VerificationRecordingStager(
        private val events: MutableList<String>,
    ) : VerifiedAssetStager {
        override fun stage(target: File, producer: (OutputStream) -> Unit): StagedFile {
            events += "stage"
            return StreamFileStager.stageWith(
                target = target,
                producer = producer,
                openInput = { file -> VerificationRecordingInputStream(file.inputStream(), events) },
            )
        }

        override fun <T> useAndDiscard(staged: StagedFile, action: () -> T): T =
            StreamFileStager.useAndDiscard(staged, action)
    }

    private class RecordingPublicationLease(
        private val events: MutableList<String>,
    ) : PublicationLease<String> {
        override fun finish(result: String) {
            events += "finish:$result"
        }

        override fun close() {
            events += "close"
        }
    }

    private class ThrowingClosePublicationLease(
        private val closeFailure: IllegalStateException,
    ) : PublicationLease<Nothing> {
        var closed = false
            private set

        override fun finish(result: Nothing) = error("finish must not run after publish failure")

        override fun close() {
            closed = true
            throw closeFailure
        }
    }

    private class ReportingClosePublicationLease(
        private val closeFailure: Throwable,
    ) : PublicationLease<String> {
        var closed = false
            private set
        var reportedCleanupFailure: Throwable? = null
            private set

        override fun finish(result: String) = Unit

        override fun close() {
            closed = true
            throw closeFailure
        }

        override fun onCompletedCleanupFailure(failure: Throwable) {
            reportedCleanupFailure = failure
        }
    }

    private class FailingReportPublicationLease(
        private val closeFailure: Throwable,
        private val reportFailure: Throwable,
    ) : PublicationLease<String> {
        override fun finish(result: String) = Unit

        override fun close() {
            throw closeFailure
        }

        override fun onCompletedCleanupFailure(failure: Throwable) {
            throw reportFailure
        }
    }

    private class ReadFailingStager(
        private val failure: IllegalStateException,
    ) : VerifiedAssetStager {
        override fun stage(target: File, producer: (OutputStream) -> Unit): StagedFile =
            StreamFileStager.stageWith(
                target = target,
                producer = producer,
                openInput = { file -> ReadFailingInputStream(file.inputStream(), failure) },
            )

        override fun <T> useAndDiscard(staged: StagedFile, action: () -> T): T =
            StreamFileStager.useAndDiscard(staged, action)
    }

    private class BoundedWriteStager(
        private val maximumWriteBytes: Int,
    ) : VerifiedAssetStager {
        var largestWriteBytes = 0
            private set
        var outputClosed = false
            private set

        override fun stage(target: File, producer: (OutputStream) -> Unit): StagedFile =
            StreamFileStager.stageWith(
                target = target,
                producer = producer,
                openOutput = { file ->
                    BoundedFileOutputStream(file.outputStream(), maximumWriteBytes) { length ->
                        largestWriteBytes = maxOf(largestWriteBytes, length)
                    }.also { stream ->
                        stream.onClose = { outputClosed = true }
                    }
                },
            )

        override fun <T> useAndDiscard(staged: StagedFile, action: () -> T): T =
            StreamFileStager.useAndDiscard(staged, action)
    }

    private class VerificationRecordingInputStream(
        private val delegate: InputStream,
        private val events: MutableList<String>,
    ) : InputStream() {
        private var observedRead = false

        override fun read(): Int {
            recordVerification()
            return delegate.read()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            recordVerification()
            return delegate.read(buffer, offset, length)
        }

        override fun close() = delegate.close()

        private fun recordVerification() {
            if (!observedRead) {
                observedRead = true
                events += "verify"
            }
        }
    }

    private class ReadFailingInputStream(
        private val delegate: InputStream,
        private val failure: IllegalStateException,
    ) : InputStream() {
        override fun read(): Int = throw failure

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw failure

        override fun close() = delegate.close()
    }

    private class BoundedFileOutputStream(
        private val delegate: OutputStream,
        private val maximumWriteBytes: Int,
        private val observeWrite: (Int) -> Unit,
    ) : OutputStream() {
        var onClose: () -> Unit = {}

        override fun write(value: Int) = write(byteArrayOf(value.toByte()))

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            check(length <= maximumWriteBytes) { "received $length bytes in one staging-file write; expected bounded streaming" }
            observeWrite(length)
            delegate.write(buffer, offset, length)
        }

        override fun close() {
            delegate.close()
            onClose()
        }
    }

    private fun writeHighEntropy(totalBytes: Long, output: OutputStream, observeChunk: (Int) -> Unit) {
        var state = 0x13579BDF
        var remaining = totalBytes
        val chunk = ByteArray(CHUNK_BYTES)
        while (remaining > 0) {
            val count = minOf(remaining, CHUNK_BYTES.toLong()).toInt()
            for (index in 0 until count) {
                state = state * 1_664_525 + 1_013_904_223
                chunk[index] = (state ushr 24).toByte()
            }
            observeChunk(count)
            output.write(chunk, 0, count)
            remaining -= count
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(CHUNK_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return ContentHash.hex(digest.digest())
    }

    private fun assertNoPublishedOrTempFile(directory: File, target: File) {
        assertFalse(target.exists(), "failure must not publish a final asset")
        assertEquals(emptyList(), directory.listFiles()?.toList().orEmpty(), "failure must clean the staging temp")
    }

    private fun inTempDir(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("td15-workflow-")
        try {
            block(directory.toFile())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val CHUNK_BYTES = 16 * 1024
        const val PHOTO_BYTES = 4_096L * 4_096L
        const val EXPECTED_HIGH_ENTROPY_SHA256 = "cbbc6cdda9c3cb4420756d843b2cae5dcb614795cf2a44b261d1f2f6ff6c5b5c"
    }
}
