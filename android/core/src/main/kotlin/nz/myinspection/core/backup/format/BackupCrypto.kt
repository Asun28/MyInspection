package nz.myinspection.core.backup.format

import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.CharBuffer
import java.security.GeneralSecurityException
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 口令派生 + 分块 AEAD。**只组合 javax.crypto 标准件**（AES/GCM/NoPadding、PBKDF2WithHmacSHA256、
 * SecureRandom），不自研密码学原语，不新增依赖（卡片 forbid）。分块的理由见 [BackupFormat] 顶部。
 */
internal class DerivedSecrets(val key: ByteArray, val verifier: ByteArray) {
    fun wipe() {
        key.fill(0)
        verifier.fill(0)
    }
}

/**
 * PBKDF2WithHmacSHA256（Android API 26 起可用，minSdk 26）一次派生 40 字节：
 * 前 32 字节 = AES-256 密钥，尾 8 字节 = 口令校验值（WinZip AES 同款做法）。
 * 校验值让「口令错」能在开始解密**之前**就被干净地判出来，而不是等到最后一块 tag 失败时与
 * 「包被改过」混为一谈——两种诊断对用户的意义完全不同（一个重输口令，一个去找别的备份）。
 * 攻击者本就能拿一次 GCM 验证当口令预言机，代价同样是一次 PBKDF2，故校验值不额外削弱强度。
 *
 * 口令先做 NFC 归一（RFC 8265 OpaqueString 的归一化规则那一条；不做大小写/空格映射）：
 * 同一串字符在不同输入法/系统下可能是 NFC 或 NFD，不归一就会出现「口令明明没错却打不开」，
 * 而本格式**无口令找回**（ADR-0002）。
 */
internal fun deriveKeyAndVerifier(passphrase: CharArray, salt: ByteArray, iterations: Int): DerivedSecrets {
    if (passphrase.isEmpty()) {
        throw BackupFormatException("口令不能为空：空口令写出的包等于没加密，而包里有租客照片与联系方式（Privacy Act 2020）")
    }
    checkIterations(iterations)
    val normalized = Normalizer.normalize(CharBuffer.wrap(passphrase), Normalizer.Form.NFC).toCharArray()
    val spec = PBEKeySpec(normalized, salt, iterations, (BackupFormat.KEY_BYTES + BackupFormat.VERIFIER_BYTES) * 8)
    var derived: ByteArray? = null
    try {
        derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return DerivedSecrets(
            key = derived.copyOfRange(0, BackupFormat.KEY_BYTES),
            verifier = derived.copyOfRange(BackupFormat.KEY_BYTES, BackupFormat.KEY_BYTES + BackupFormat.VERIFIER_BYTES),
        )
    } catch (e: GeneralSecurityException) {
        throw BackupFormatException("口令派生失败（PBKDF2WithHmacSHA256 不可用）", e)
    } finally {
        derived?.fill(0)
        spec.clearPassword()
        normalized.fill(0.toChar())
    }
}

internal fun gcmCipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(BackupFormat.GCM_TAG_BITS, nonce))
    cipher.updateAAD(aad)
    return cipher
}

/**
 * 每块的 nonce = 前缀(7) || 块序号(4，大端) || final 标志(1)。
 * 序号进 nonce ⇒ 块被重排/丢失即 tag 失败；final 标志进 nonce ⇒ 在最后一块之前截断也当场失败。
 */
internal fun chunkNonce(prefix: ByteArray, index: Long, last: Boolean): ByteArray {
    if (prefix.size != BackupFormat.NONCE_PREFIX_BYTES) {
        throw BackupFormatException("nonce 前缀必须是 ${BackupFormat.NONCE_PREFIX_BYTES} 字节，实得 ${prefix.size}")
    }
    if (index < 0 || index > BackupFormat.MAX_CHUNK_INDEX) {
        throw BackupFormatException("密文块序号越界：$index（序号回绕 = 同密钥重用 nonce，GCM 会被整体攻破）")
    }
    val nonce = ByteArray(BackupFormat.GCM_NONCE_BYTES)
    prefix.copyInto(nonce, 0)
    nonce[7] = (index ushr 24).toByte()
    nonce[8] = (index ushr 16).toByte()
    nonce[9] = (index ushr 8).toByte()
    nonce[10] = index.toByte()
    nonce[11] = if (last) 1 else 0
    return nonce
}

/**
 * 把写进来的字节流切成定长明文块，逐块 AES-256-GCM 加密后吐给 [sink]。恒定内存：只持有一块。
 *
 * **不拥有 [sink]**：[close] 只吐出最后一块并 flush，绝不关闭调用方的流（SAF 的 URI 流归 T5-BACKUP-IO 管）。
 *
 * **满块要攥在手里、等下一批字节到来才吐**（同一次 [write] 调用内也照此办理）：于是 [close] 时手里必然
 * 还有一块，它才能被打上 final 标志。反过来说也成立——**非末块必然是满块**，读取器据此判定末块。
 * 若改成「一满就吐」，末块就可能已经被当成普通块吐出去了，「最后一块是哪块」便无从认证，截断也就无从察觉。
 * 唯一的空块出现在明文本身为空时（那时它同时是首块与末块）。
 */
internal class ChunkedGcmOutputStream(
    private val sink: OutputStream,
    private val key: ByteArray,
    private val noncePrefix: ByteArray,
    private val aad: ByteArray,
) : OutputStream() {
    private val plain = ByteArray(BackupFormat.CHUNK_PLAINTEXT_BYTES)
    private val cipherText = ByteArray(BackupFormat.CHUNK_PLAINTEXT_BYTES + BackupFormat.GCM_TAG_BYTES)
    private var filled = 0
    private var index = 0L
    private var closed = false

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        check(!closed) { "流已关闭" }
        var offset = off
        var remaining = len
        while (remaining > 0) {
            if (filled == plain.size) emit(last = false)
            val take = minOf(remaining, plain.size - filled)
            b.copyInto(plain, filled, offset, offset + take)
            filled += take
            offset += take
            remaining -= take
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        emit(last = true)
        plain.fill(0)
        sink.flush()
    }

    private fun emit(last: Boolean) {
        val cipher = gcmCipher(Cipher.ENCRYPT_MODE, key, chunkNonce(noncePrefix, index, last), aad)
        val produced = cipher.doFinal(plain, 0, filled, cipherText, 0)
        sink.write(cipherText, 0, produced)
        filled = 0
        index++
    }
}

/**
 * [ChunkedGcmOutputStream] 的对侧：逐块读、逐块验 tag、逐块交出明文。恒定内存：只持有一块。
 *
 * 「这块是不是最后一块」靠两件事判定：**没读满一块**（只可能因为到了流尾——非末块必然是满块，
 * 见 [ChunkedGcmOutputStream] 的攥块规则），**或**读满了但**向前偷看一个字节**发现后面没有了。
 * 于是：整块被砍掉 ⇒ 剩下的末块按 final 解密而它是按非 final 加密的 ⇒ tag 失败；
 * 尾部追加垃圾 ⇒ 真末块被判成非 final ⇒ tag 失败。**不拥有源流**，[close] 不关闭它。
 */
internal class ChunkedGcmInputStream(
    source: InputStream,
    private val key: ByteArray,
    private val noncePrefix: ByteArray,
    private val aad: ByteArray,
) : InputStream() {
    private val source = PushbackInputStream(source, 1)
    private val cipherText = ByteArray(BackupFormat.CHUNK_PLAINTEXT_BYTES + BackupFormat.GCM_TAG_BYTES)
    private val plain = ByteArray(BackupFormat.CHUNK_PLAINTEXT_BYTES)
    private var start = 0
    private var end = 0
    private var index = 0L
    private var finished = false

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (start == end) {
            if (finished) return -1
            readChunk()
        }
        val take = minOf(len, end - start)
        plain.copyInto(b, off, start, start + take)
        start += take
        return take
    }

    private fun readChunk() {
        val read = readUpTo(source, cipherText)
        if (read < BackupFormat.GCM_TAG_BYTES) {
            throw BackupCorruptException("密文块只有 $read 字节，装不下一个 GCM tag：备份包被截断")
        }
        val last = read < cipherText.size || !hasMore()
        val cipher = gcmCipher(Cipher.DECRYPT_MODE, key, chunkNonce(noncePrefix, index, last), aad)
        end = cipher.doFinal(cipherText, 0, read, plain, 0)
        start = 0
        index++
        finished = last
    }

    private fun hasMore(): Boolean {
        val next = source.read()
        if (next < 0) return false
        source.unread(next)
        return true
    }
}
