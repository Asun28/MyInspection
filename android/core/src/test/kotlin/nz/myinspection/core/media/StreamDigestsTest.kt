package nz.myinspection.core.media

import java.io.ByteArrayInputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** 摘要 helper 的精确 size/hash 与关闭失败语义；4096² 真实暂存/发布证明见 [VerifiedAssetWorkflowTest]。 */
class StreamDigestsTest {
    @Test
    fun `verification rejects a stream whose byte count changed after it was written`() {
        val expected = StreamDigest(
            sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sizeBytes = 4,
        )

        assertFailsWith<IllegalStateException> {
            StreamDigests.verify(ByteArrayInputStream("abc".toByteArray(Charsets.US_ASCII)), expected)
        }
    }

    @Test
    fun `verification rejects a stream whose digest changed after it was written`() {
        val expected = StreamDigest(sha256 = "0".repeat(64), sizeBytes = 3)

        assertFailsWith<IllegalStateException> {
            StreamDigests.verify(ByteArrayInputStream("abc".toByteArray(Charsets.US_ASCII)), expected)
        }
    }

    @Test
    fun `producer failure remains primary when closing the output also fails`() {
        val sink = CloseFailingSink()
        val primary = IllegalStateException("producer failed")

        val thrown = assertFailsWith<IllegalStateException> {
            StreamDigests.writeAndClose(sink) { throw primary }
        }

        assertSame(primary, thrown)
        assertEquals(1, sink.closeCalls, "failure still must close the temporary file")
        assertEquals(1, thrown.suppressed.size)
        assertSame(sink.closeFailure, thrown.suppressed.single())
    }

    @Test
    fun `a close failure rejects a producer that otherwise wrote successfully`() {
        val sink = CloseFailingSink()

        val thrown = assertFailsWith<IllegalStateException> {
            StreamDigests.writeAndClose(sink) { it.write(1) }
        }

        assertSame(sink.closeFailure, thrown)
        assertEquals(1, sink.closeCalls)
    }

    private class CloseFailingSink : OutputStream() {
        val closeFailure = IllegalStateException("close failed")
        var closeCalls = 0
            private set

        override fun write(value: Int) = Unit

        override fun close() {
            closeCalls += 1
            throw closeFailure
        }
    }
}
