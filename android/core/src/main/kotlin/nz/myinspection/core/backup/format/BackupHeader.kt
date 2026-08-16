package nz.myinspection.core.backup.format

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ★ 明文头：读取器在拿到口令之前唯一能读的部分（它需要盐与迭代数才能派生密钥）。
 *
 * 布局（大端，共 [BackupFormat.HEADER_BYTES] = 47 字节）：
 * ```
 * off 0  len 8   magic "MYINSPBK"
 * off 8  len 2   format_version      (u16)
 * off 10 len 2   kdf_id              (u16；1 = PBKDF2WithHmacSHA256)
 * off 12 len 4   kdf_iterations      (u32)
 * off 16 len 16  salt
 * off 32 len 7   nonce_prefix
 * off 39 len 8   passphrase_verifier
 * ```
 * **整个头是每个密文块的 GCM AAD**：头虽是明文，却被 tag 认证，改一个字节整包就打不开。
 * 布局由 [BackupHeaderTest] 的黄金十六进制串逐字节钉死，合并即冻结。
 */
class BackupHeader(
    val formatVersion: Int,
    val kdfId: Int,
    val kdfIterations: Int,
    salt: ByteArray,
    noncePrefix: ByteArray,
    passphraseVerifier: ByteArray,
) {
    // 入参一律拷贝存放：别名化的 salt/nonce 会让「头里写的参数」与「实际派生用的参数」悄悄分家，
    // 包从此再也打不开——而且是几个月后恢复时才发现。
    private val saltStore: ByteArray = salt.copyOf()
    private val noncePrefixStore: ByteArray = noncePrefix.copyOf()
    private val verifierStore: ByteArray = passphraseVerifier.copyOf()

    val salt: ByteArray get() = saltStore.copyOf()
    val noncePrefix: ByteArray get() = noncePrefixStore.copyOf()
    val passphraseVerifier: ByteArray get() = verifierStore.copyOf()

    init {
        if (formatVersion != BackupFormat.FORMAT_VERSION) {
            throw BackupFormatException(
                "备份包 format_version=$formatVersion，本版只能读 ${BackupFormat.FORMAT_VERSION}（请用更新的 app 打开）",
            )
        }
        if (kdfId != BackupFormat.KDF_PBKDF2_HMAC_SHA256) {
            throw BackupFormatException("备份包 kdf_id=$kdfId，本版只支持 ${BackupFormat.KDF_PBKDF2_HMAC_SHA256}（PBKDF2WithHmacSHA256）")
        }
        checkIterations(kdfIterations)
        requireSize(saltStore, BackupFormat.SALT_BYTES, "salt")
        requireSize(noncePrefixStore, BackupFormat.NONCE_PREFIX_BYTES, "nonce_prefix")
        requireSize(verifierStore, BackupFormat.VERIFIER_BYTES, "passphrase_verifier")
    }

    fun encode(): ByteArray = ByteBuffer.allocate(BackupFormat.HEADER_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .put(BackupFormat.MAGIC_BYTES)
        .putShort(formatVersion.toShort())
        .putShort(kdfId.toShort())
        .putInt(kdfIterations)
        .put(saltStore)
        .put(noncePrefixStore)
        .put(verifierStore)
        .array()

    private fun requireSize(bytes: ByteArray, expected: Int, field: String) {
        if (bytes.size != expected) throw BackupFormatException("头字段 $field 必须是 $expected 字节，实得 ${bytes.size}")
    }

    companion object {
        fun decode(bytes: ByteArray): BackupHeader {
            if (bytes.size != BackupFormat.HEADER_BYTES) {
                throw BackupFormatException("备份包头必须是 ${BackupFormat.HEADER_BYTES} 字节，实得 ${bytes.size}")
            }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val magic = ByteArray(BackupFormat.MAGIC_BYTES.size)
            buffer.get(magic)
            if (!magic.contentEquals(BackupFormat.MAGIC_BYTES)) {
                throw BackupFormatException("不是 MyInspection 备份包（magic 不匹配）：${toHexLower(magic)}")
            }
            val formatVersion = buffer.short.toInt() and 0xFFFF
            val kdfId = buffer.short.toInt() and 0xFFFF
            val iterations = buffer.int
            val salt = ByteArray(BackupFormat.SALT_BYTES).also { buffer.get(it) }
            val noncePrefix = ByteArray(BackupFormat.NONCE_PREFIX_BYTES).also { buffer.get(it) }
            val verifier = ByteArray(BackupFormat.VERIFIER_BYTES).also { buffer.get(it) }
            return BackupHeader(formatVersion, kdfId, iterations, salt, noncePrefix, verifier)
        }

        /** 从流里**恰好**读走头那么多字节：多读一个就吃掉第一个密文块的开头。 */
        fun readFrom(input: InputStream): BackupHeader {
            val bytes = ByteArray(BackupFormat.HEADER_BYTES)
            val read = readUpTo(input, bytes)
            if (read < bytes.size) {
                throw BackupFormatException("流只有 $read 字节，不足一个 ${BackupFormat.HEADER_BYTES} 字节的包头：不是备份包，或文件已被截断")
            }
            return decode(bytes)
        }
    }
}
