package nz.myinspection.core.media

import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The staging seam is the point before either ingest pipeline can publish a final asset or ask the DB recorder to
 * create a photo row. These tests fault the real file lifecycle rather than a mock: a failure leaves neither target
 * nor sibling temp behind, so the pipeline never has a landed file to hand to the recorder.
 */
class StreamFileStagerTest {
    @Test
    fun `a verified stage keeps only a temporary sibling and leaves the final target absent`() = inTempDir { directory ->
        val target = File(directory, "photo.jpg")

        val staged = StreamFileStager.stage(target) { it.write("abc".toByteArray(Charsets.US_ASCII)) }

        assertTrue(staged.file.exists())
        assertFalse(target.exists(), "only the later publish step may create the final target")
        assertEquals(3L, staged.digest.sizeBytes)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", staged.digest.sha256)
        assertTrue(staged.file.delete(), "test-owned staged temp must be removable")
    }

    @Test
    fun `a successful reuse action discards its unpublished staged file`() = inTempDir { directory ->
        val target = File(directory, "photo.jpg")
        val staged = StreamFileStager.stage(target) { it.write(1) }

        val outcome = StreamFileStager.useAndDiscard(staged) { "reused-existing-asset" }

        assertEquals("reused-existing-asset", outcome)
        assertFalse(staged.file.exists(), "a reuse path must not retain its unused staged JPEG")
        assertFalse(target.exists(), "a reuse path must not publish a second final asset")
    }

    @Test
    fun `a producer failure removes the temp and leaves no final file for a DB row`() = inTempDir { directory ->
        val target = File(directory, "photo.jpg")
        val primary = IllegalStateException("Bitmap.compress reported failure")

        val thrown = assertFailsWith<IllegalStateException> {
            StreamFileStager.stageWith(target, { throw primary })
        }

        assertSame(primary, thrown)
        assertNoPublishedOrTempFile(directory, target)
    }

    @Test
    fun `an output write failure removes the temp and keeps the output failure primary`() = inTempDir { directory ->
        val target = File(directory, "photo.jpg")
        val primary = IllegalStateException("disk full")

        val thrown = assertFailsWith<IllegalStateException> {
            StreamFileStager.stageWith(
                target = target,
                producer = { it.write(1) },
                openOutput = { file -> WriteFailingOutputStream(file.outputStream(), primary) },
            )
        }

        assertSame(primary, thrown)
        assertNoPublishedOrTempFile(directory, target)
    }

    @Test
    fun `a successful producer whose close fails removes the temp and surfaces the close failure`() = inTempDir { directory ->
        val target = File(directory, "photo.jpg")
        val closeFailure = IllegalStateException("close failed")

        val thrown = assertFailsWith<IllegalStateException> {
            StreamFileStager.stageWith(
                target = target,
                producer = { it.write(1) },
                openOutput = { file -> CloseFailingOutputStream(file.outputStream(), closeFailure) },
            )
        }

        assertSame(closeFailure, thrown)
        assertNoPublishedOrTempFile(directory, target)
    }

    @Test
    fun `a closed temp whose bytes no longer verify is removed before publish`() = inTempDir { directory ->
        val target = File(directory, "photo.jpg")

        assertFailsWith<IllegalStateException> {
            StreamFileStager.stageWith(
                target = target,
                producer = { it.write("abc".toByteArray(Charsets.US_ASCII)) },
                openInput = { ByteArrayInputStream("changed".toByteArray(Charsets.US_ASCII)) },
            )
        }

        assertNoPublishedOrTempFile(directory, target)
    }

    @Test
    fun `a same-size different digest after close is removed before publish`() = inTempDir { directory ->
        val target = File(directory, "photo.jpg")

        assertFailsWith<IllegalStateException> {
            StreamFileStager.stageWith(
                target = target,
                producer = { it.write("abc".toByteArray(Charsets.US_ASCII)) },
                openInput = { ByteArrayInputStream("xyz".toByteArray(Charsets.US_ASCII)) },
            )
        }

        assertNoPublishedOrTempFile(directory, target)
    }

    @Test
    fun `a cleanup failure is suppressed without replacing the producer failure`() = inTempDir { directory ->
        val target = File(directory, "photo.jpg")
        val primary = IllegalStateException("Bitmap.compress reported failure")

        val thrown = assertFailsWith<IllegalStateException> {
            StreamFileStager.stageWith(
                target = target,
                producer = { throw primary },
                delete = { false },
            )
        }

        assertSame(primary, thrown)
        assertEquals(1, thrown.suppressed.size)
        assertTrue(thrown.suppressed.single().message.orEmpty().contains("temporary file"))
        assertFalse(target.exists(), "the final target remains untouched even when temporary cleanup fails")
    }

    @Test
    fun `a discard failure after successful work surfaces instead of claiming cleanup`() = inTempDir { directory ->
        val staged = StreamFileStager.stage(File(directory, "photo.jpg")) { it.write(1) }

        val thrown = assertFailsWith<IllegalStateException> {
            StreamFileStager.useAndDiscardWith(staged, action = { "recorded" }, delete = { false })
        }

        assertTrue(thrown.message.orEmpty().contains("temporary file"))
        assertTrue(staged.file.exists(), "the test's injected delete failure must remain observable")
    }

    @Test
    fun `a discard failure is suppressed when recording already failed`() = inTempDir { directory ->
        val staged = StreamFileStager.stage(File(directory, "photo.jpg")) { it.write(1) }
        val primary = IllegalStateException("database insert failed")

        val thrown = assertFailsWith<IllegalStateException> {
            StreamFileStager.useAndDiscardWith(staged, action = { throw primary }, delete = { false })
        }

        assertSame(primary, thrown)
        assertEquals(1, thrown.suppressed.size)
        assertTrue(thrown.suppressed.single().message.orEmpty().contains("temporary file"))
    }

    private fun assertNoPublishedOrTempFile(directory: File, target: File) {
        assertFalse(target.exists(), "staging failure must not publish a final asset")
        assertEquals(emptyList(), directory.listFiles()?.toList().orEmpty(), "staging failure must clean its sibling temp")
    }

    private fun inTempDir(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("td15-stream-stage-")
        try {
            block(directory.toFile())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private class WriteFailingOutputStream(
        private val target: OutputStream,
        private val failure: IllegalStateException,
    ) : OutputStream() {
        override fun write(value: Int): Unit = throw failure

        override fun close() = target.close()
    }

    private class CloseFailingOutputStream(
        private val target: OutputStream,
        private val failure: IllegalStateException,
    ) : OutputStream() {
        override fun write(value: Int) = target.write(value)

        override fun close() {
            target.close()
            throw failure
        }
    }
}
