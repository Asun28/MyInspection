package nz.myinspection.core.backup.format

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * ★ 分块 AEAD 是密文体的骨架（为什么不是一路 CipherOutputStream，见 [BackupFormat] 顶部：
 * JCE/Conscrypt 的 AEAD 会把整份密文缓冲在内存里，GB 级照片必 OOM）。
 *
 * 分块必须同时挡住四件事，否则「密文完整」就是假的：
 * 块被改（tag）· 块被重排（序号进 nonce）· 块被丢弃/截断（final 标志进 nonce）· 尾部被追加。
 */
class BackupChunkedGcmTest {

    private val chunk = BackupFormat.CHUNK_PLAINTEXT_BYTES
    private val tag = BackupFormat.GCM_TAG_BYTES
    private val key = ByteArray(BackupFormat.KEY_BYTES) { (it * 7 + 1).toByte() }
    private val prefix = ByteArray(BackupFormat.NONCE_PREFIX_BYTES) { (it + 100).toByte() }
    private val aad = "MYINSPBK-header".toByteArray(Charsets.US_ASCII)

    private fun seal(plain: ByteArray, prefix: ByteArray = this.prefix, aad: ByteArray = this.aad): ByteArray {
        val out = ByteArrayOutputStream()
        ChunkedGcmOutputStream(out, key, prefix, aad).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun open(body: ByteArray, prefix: ByteArray = this.prefix, aad: ByteArray = this.aad): ByteArray =
        readAtMost(ChunkedGcmInputStream(ByteArrayInputStream(body), key, prefix, aad), 8 * 1024 * 1024)

    private fun bytes(size: Int) = ByteArray(size) { ((it * 31 + 11) and 0xFF).toByte() }

    @Test
    fun `round trips at every chunk boundary with the expected framing`() {
        // 满块**不立即**吐出（等下一批字节才吐），于是最后一块总是非空——除非明文本身为空。
        for (size in listOf(0, 1, chunk - 1, chunk, chunk + 1, 3 * chunk, 3 * chunk + 7)) {
            val plain = bytes(size)
            val body = seal(plain)
            val expectedChunks = if (size == 0) 1 else (size + chunk - 1) / chunk
            assertEquals(size + expectedChunks * tag, body.size, "明文 $size 字节应切成 $expectedChunks 块")
            assertContentEquals(plain, open(body), "明文 $size 字节未能原样还原")
        }
    }

    @Test
    fun `single byte writes and reads go through the same framing`() {
        val out = ByteArrayOutputStream()
        ChunkedGcmOutputStream(out, key, prefix, aad).use { stream ->
            for (b in listOf(1, 2, 3)) stream.write(b)
        }
        val input = ChunkedGcmInputStream(ByteArrayInputStream(out.toByteArray()), key, prefix, aad)
        assertEquals(listOf(1, 2, 3, -1), listOf(input.read(), input.read(), input.read(), input.read()))
    }

    @Test
    fun `flipping a byte in any chunk is rejected`() {
        val body = seal(bytes(2 * chunk + 5))
        for (offset in listOf(0, chunk, body.size - 1)) {
            val tampered = body.copyOf().also { it[offset] = (it[offset].toInt() xor 0x01).toByte() }
            assertFailsWith<AEADBadTagException>("偏移 $offset") { open(tampered) }
        }
    }

    @Test
    fun `dropping the final chunk is rejected`() {
        // 最后一块被整块砍掉后，倒数第二块就成了「最后一块」——但它是按非 final 加密的，tag 立刻不认。
        // 没有 final 标志的分块方案在这里会一声不吭地交出一份被削尾的备份。
        val body = seal(bytes(3 * chunk))
        assertFailsWith<AEADBadTagException> { open(body.copyOf(body.size - (chunk + tag))) }
    }

    @Test
    fun `swapping two chunks is rejected`() {
        // 块序号进了 nonce，所以重排 = 用错 nonce = tag 失败。
        val body = seal(bytes(3 * chunk))
        val block = chunk + tag
        val swapped = body.copyOf()
        body.copyInto(swapped, 0, block, 2 * block)
        body.copyInto(swapped, block, 0, block)
        assertFailsWith<AEADBadTagException> { open(swapped) }
    }

    @Test
    fun `appending bytes after the final chunk is rejected`() {
        val body = seal(bytes(chunk))
        assertFailsWith<AEADBadTagException> { open(body + ByteArray(4)) }
    }

    @Test
    fun `a body too short to hold a tag is rejected`() {
        assertFailsWith<BackupCorruptException> { open(ByteArray(tag - 1)) }
        assertFailsWith<BackupCorruptException> { open(ByteArray(0)) }
    }

    @Test
    fun `a different nonce prefix or aad does not open the body`() {
        val body = seal(bytes(100))
        assertFailsWith<AEADBadTagException> {
            open(body, prefix = ByteArray(BackupFormat.NONCE_PREFIX_BYTES) { (it + 101).toByte() })
        }
        assertFailsWith<AEADBadTagException> { open(body, aad = "other-header".toByteArray(Charsets.US_ASCII)) }
    }

    @Test
    fun `the chunk nonce is prefix then big endian index then the final flag`() {
        // nonce 布局也是格式契约：读取器必须能算出与写入器完全相同的 nonce，否则跨版本读不回。
        assertEquals("6465666768696a0000000100", toHexLower(chunkNonce(prefix, 1, false)))
        assertEquals("6465666768696a7fffffff01", toHexLower(chunkNonce(prefix, 0x7FFFFFFFL, true)))
        assertEquals("6465666768696affffffff01", toHexLower(chunkNonce(prefix, BackupFormat.MAX_CHUNK_INDEX, true)))
        assertFailsWith<BackupFormatException> { chunkNonce(prefix, BackupFormat.MAX_CHUNK_INDEX + 1, false) }
        assertFailsWith<BackupFormatException> { chunkNonce(prefix, -1, false) }
        assertFailsWith<BackupFormatException> { chunkNonce(ByteArray(6), 0, false) }
    }
}
