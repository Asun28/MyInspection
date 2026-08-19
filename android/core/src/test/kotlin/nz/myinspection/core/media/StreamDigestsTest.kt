package nz.myinspection.core.media

import java.io.ByteArrayInputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 流式 JPEG 的纯 JVM 证明：生产者只得到一个有界输出流，测试 sink 不保留全部内容且拒绝超大单次 write。
 * 4096 x 4096 这里按一字节/像素的高熵 JPEG 上界夹具模拟；期望摘要由独立 .NET SHA-256 预计算，
 * 不由被测代码或其 helper 推导。
 */
class StreamDigestsTest {
    @Test
    fun `a 4096px high entropy producer writes bounded chunks closes and returns the exact digest`() {
        val sink = ChunkBoundedSink(maximumChunkBytes = CHUNK_BYTES)

        val actual = StreamDigests.writeAndClose(sink) { output ->
            writeHighEntropy(PHOTO_BYTES, output)
        }

        assertEquals(PHOTO_BYTES, actual.sizeBytes)
        assertEquals("cbbc6cdda9c3cb4420756d843b2cae5dcb614795cf2a44b261d1f2f6ff6c5b5c", actual.sha256)
        assertEquals(PHOTO_BYTES, sink.bytesWritten, "the producer's bytes must reach the real sink")
        assertEquals(CHUNK_BYTES, sink.largestWriteBytes, "a whole JPEG must never arrive as one write")
        assertTrue(sink.closed, "success is not complete until the file stream closes")
    }

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

    private fun writeHighEntropy(totalBytes: Long, output: OutputStream) {
        var state = 0x13579BDF
        var remaining = totalBytes
        val chunk = ByteArray(CHUNK_BYTES)
        while (remaining > 0) {
            val count = minOf(remaining, CHUNK_BYTES.toLong()).toInt()
            for (index in 0 until count) {
                state = state * 1_664_525 + 1_013_904_223
                chunk[index] = (state ushr 24).toByte()
            }
            output.write(chunk, 0, count)
            remaining -= count
        }
    }

    private class ChunkBoundedSink(private val maximumChunkBytes: Int) : OutputStream() {
        var bytesWritten = 0L
            private set
        var largestWriteBytes = 0
            private set
        var closed = false
            private set

        override fun write(value: Int) = write(byteArrayOf(value.toByte()))

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            check(length <= maximumChunkBytes) { "received $length bytes in one write; expected bounded streaming" }
            bytesWritten += length
            largestWriteBytes = maxOf(largestWriteBytes, length)
        }

        override fun close() {
            closed = true
        }
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

    private companion object {
        const val CHUNK_BYTES = 16 * 1024
        const val PHOTO_BYTES = 4_096L * 4_096L
    }
}
