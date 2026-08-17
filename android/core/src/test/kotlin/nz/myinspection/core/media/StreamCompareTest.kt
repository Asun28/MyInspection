package nz.myinspection.core.media

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 流式比对的判定面。用**跨块**的数据（2.5 个块）而非一小段：只用单块数据的话，循环体、块边界与
 * "读到尾才算相等"三处都可以被删掉而测试照样绿。
 */
class StreamCompareTest {
    private fun bytes(size: Int, seed: Int = 0): ByteArray = ByteArray(size) { i -> ((i * 31 + seed) % 251).toByte() }

    private fun stream(data: ByteArray): InputStream = ByteArrayInputStream(data)

    /** 每次 `read` 最多交出一个字节的流——真实 InputStream 允许短读，`fill` 必须循环补齐。 */
    private class TricklingStream(private val data: ByteArray) : InputStream() {
        private var pos = 0
        override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xff else -1
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= data.size) return -1
            b[off] = data[pos++]
            return 1
        }
    }

    @Test
    fun `identical multi-chunk content compares equal`() {
        val data = bytes(StreamCompare.CHUNK_BYTES * 2 + 1024)
        assertTrue(StreamCompare.contentEquals(stream(data), stream(data.copyOf())))
    }

    @Test
    fun `both empty streams compare equal`() {
        assertTrue(StreamCompare.contentEquals(stream(ByteArray(0)), stream(ByteArray(0))))
    }

    @Test
    fun `a difference in the very last byte is caught`() {
        val a = bytes(StreamCompare.CHUNK_BYTES * 2 + 1024)
        val b = a.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        assertFalse(StreamCompare.contentEquals(stream(a), stream(b)), "比对不能在中途认定相等就提前收工")
    }

    @Test
    fun `a difference at the first byte of the second chunk is caught`() {
        val a = bytes(StreamCompare.CHUNK_BYTES * 2)
        val b = a.copyOf().also { it[StreamCompare.CHUNK_BYTES] = (it[StreamCompare.CHUNK_BYTES] + 1).toByte() }
        assertFalse(StreamCompare.contentEquals(stream(a), stream(b)), "块边界后的差异同样要发现")
    }

    @Test
    fun `a prefix is not equal to the longer stream, in either argument order`() {
        val long = bytes(StreamCompare.CHUNK_BYTES + 100)
        val prefix = long.copyOf(StreamCompare.CHUNK_BYTES)
        assertFalse(StreamCompare.contentEquals(stream(prefix), stream(long)))
        assertFalse(StreamCompare.contentEquals(stream(long), stream(prefix)))
    }

    @Test
    fun `a stream that only yields one byte per read still compares equal`() {
        // 短读被当成流尾时，两侧读出的块长不同 → 会被误判为"内容不同"，于是一次幂等重写会被拒。
        val data = bytes(StreamCompare.CHUNK_BYTES + 7)
        assertTrue(StreamCompare.contentEquals(TricklingStream(data), stream(data.copyOf())))
    }
}
