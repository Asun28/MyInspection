package nz.myinspection.core.backup.format

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

/**
 * ★ 明文头 = 跨年契约的最外层。本文件的黄金布局**就是格式规范本体**：偏移/宽度/字节序/字段顺序一旦
 * 合并即冻结，未来版本必须照此读回今年的包。任何改动 = 新 format_version + 版本评审，不是「调参」。
 *
 * 布局（大端，共 47 字节）：
 * ```
 * off 0  len 8   magic "MYINSPBK"
 * off 8  len 2   format_version  (u16)
 * off 10 len 2   kdf_id          (u16；1 = PBKDF2WithHmacSHA256，2 起留给 Argon2 等升级)
 * off 12 len 4   kdf_iterations  (u32；写进头 => 迭代数可随年份上调而不破旧包兼容)
 * off 16 len 16  salt
 * off 32 len 7   nonce_prefix（每块 nonce = 前缀 || 块序号(4) || final 标志(1)）
 * off 39 len 8   passphrase_verifier（PBKDF2 输出的尾 8 字节）
 * ```
 * 头全 47 字节同时是每个密文块的 GCM AAD（见 [BackupTamperTest] 的 AAD 用例）。
 */
class BackupHeaderTest {

    private val goldenHex =
        "4d59494e5350424b" + // magic MYINSPBK
            "0001" + // format_version = 1
            "0001" + // kdf_id = 1 (PBKDF2WithHmacSHA256)
            "00001000" + // kdf_iterations = 4096
            "000102030405060708090a0b0c0d0e0f" + // salt
            "10111213141516" + // nonce_prefix
            "ef0ecb0d8bec18c1" // passphrase_verifier

    private fun golden() = BackupHeader(
        formatVersion = 1,
        kdfId = 1,
        kdfIterations = 4096,
        salt = fromHex("000102030405060708090a0b0c0d0e0f"),
        noncePrefix = fromHex("10111213141516"),
        passphraseVerifier = fromHex("ef0ecb0d8bec18c1"),
    )

    @Test
    fun `golden header pins field order widths and big-endian encoding`() {
        assertEquals(47, BackupFormat.HEADER_BYTES)
        assertEquals(94, goldenHex.length, "47 字节 = 94 个十六进制字符")
        assertEquals(goldenHex, toHexLower(golden().encode()))
    }

    @Test
    fun `decode restores every field from the golden bytes`() {
        val header = BackupHeader.decode(fromHex(goldenHex))
        assertEquals(1, header.formatVersion)
        assertEquals(1, header.kdfId)
        assertEquals(4096, header.kdfIterations)
        assertEquals("000102030405060708090a0b0c0d0e0f", toHexLower(header.salt))
        assertEquals("10111213141516", toHexLower(header.noncePrefix))
        assertEquals("ef0ecb0d8bec18c1", toHexLower(header.passphraseVerifier))
    }

    @Test
    fun `header copies its byte fields on the way in and on the way out`() {
        // 别名化的 salt/nonce 会让「头里写的参数」与「实际派生用的参数」悄悄分家——包从此无法解密。
        val salt = fromHex("000102030405060708090a0b0c0d0e0f")
        val header = BackupHeader(1, 1, 4096, salt, fromHex("10111213141516"), fromHex("ef0ecb0d8bec18c1"))
        salt.fill(0)
        assertEquals(goldenHex, toHexLower(header.encode()), "构造时须拷贝入参")
        val exposed = header.salt
        assertNotSame(exposed, header.salt)
        exposed.fill(0)
        assertEquals(goldenHex, toHexLower(header.encode()), "读取属性须返回拷贝")
    }

    @Test
    fun `decode rejects a foreign magic`() {
        val alien = fromHex(goldenHex).also { it[0] = 'X'.code.toByte() }
        assertFailsWith<BackupFormatException> { BackupHeader.decode(alien) }
    }

    @Test
    fun `decode rejects a format version this build cannot read`() {
        for (version in listOf(0, 2, 0xFFFF)) {
            val bytes = fromHex(goldenHex)
            bytes[8] = (version shr 8).toByte()
            bytes[9] = version.toByte()
            assertFailsWith<BackupFormatException>("format_version=$version 必须被拒") { BackupHeader.decode(bytes) }
        }
    }

    @Test
    fun `decode rejects an unknown kdf id`() {
        // kdf_id=2 是 Argon2 之类升级的预留位；本版读不懂就必须明说，绝不当成 PBKDF2 硬算。
        val bytes = fromHex(goldenHex).also { it[11] = 2 }
        assertFailsWith<BackupFormatException> { BackupHeader.decode(bytes) }
    }

    @Test
    fun `decode bounds the iteration count so a hostile header cannot burn the cpu`() {
        // u32 的高位一置，迭代数就是十亿级——没有上界的话，一个被改过的头能让解包挂死。
        val cases = mapOf(
            "00000000" to "0 次",
            "80000000" to "最高位置一（int 溢出成负数）",
            "ffffffff" to "全 1",
            "7fffffff" to "int 最大值",
        )
        for ((hex, why) in cases) {
            val bytes = fromHex(goldenHex)
            fromHex(hex).copyInto(bytes, 12)
            assertFailsWith<BackupFormatException>("迭代数 $why 必须被拒") { BackupHeader.decode(bytes) }
        }
        assertEquals(
            BackupFormat.MAX_KDF_ITERATIONS,
            BackupHeader.decode(
                fromHex(goldenHex).also { encodeInt(BackupFormat.MAX_KDF_ITERATIONS).copyInto(it, 12) },
            ).kdfIterations,
            "上界本身合法",
        )
        val overMax = fromHex(goldenHex).also { encodeInt(BackupFormat.MAX_KDF_ITERATIONS + 1).copyInto(it, 12) }
        assertFailsWith<BackupFormatException> { BackupHeader.decode(overMax) }
    }

    @Test
    fun `decode rejects a header of the wrong length`() {
        assertFailsWith<BackupFormatException> { BackupHeader.decode(fromHex(goldenHex).copyOf(46)) }
        assertFailsWith<BackupFormatException> { BackupHeader.decode(fromHex(goldenHex).copyOf(48)) }
    }

    @Test
    fun `readFrom consumes exactly the header and leaves the ciphertext untouched`() {
        // 多读一个字节就吃掉第一个密文块的开头——流式读取的边界必须精确到字节。
        val tail = "TAIL".toByteArray(Charsets.US_ASCII)
        val input = ByteArrayInputStream(fromHex(goldenHex) + tail)
        assertEquals(4096, BackupHeader.readFrom(input).kdfIterations)
        val rest = ByteArray(8)
        val read = input.read(rest)
        assertEquals(4, read)
        assertEquals("TAIL", String(rest, 0, 4, Charsets.US_ASCII))
    }

    @Test
    fun `readFrom rejects a stream too short to hold a header`() {
        for (size in listOf(0, 1, 46)) {
            val input = ByteArrayInputStream(fromHex(goldenHex).copyOf(size))
            assertFailsWith<BackupFormatException>("$size 字节不足以构成头") { BackupHeader.readFrom(input) }
        }
    }

    @Test
    fun `constructor rejects byte fields of the wrong size`() {
        val salt = fromHex("000102030405060708090a0b0c0d0e0f")
        val noncePrefix = fromHex("10111213141516")
        val verifier = fromHex("ef0ecb0d8bec18c1")
        assertFailsWith<BackupFormatException> { BackupHeader(1, 1, 4096, salt.copyOf(15), noncePrefix, verifier) }
        assertFailsWith<BackupFormatException> { BackupHeader(1, 1, 4096, salt, noncePrefix.copyOf(6), verifier) }
        assertFailsWith<BackupFormatException> { BackupHeader(1, 1, 4096, salt, noncePrefix, verifier.copyOf(7)) }
    }

    private fun encodeInt(value: Int) = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte(),
    )
}
