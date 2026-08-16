package nz.myinspection.core.backup.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * ★ 口令派生 = 备份包能否被未来版本打开的根。这里的向量钉死**组合方式**（PBKDF2 输出的前 32 字节做
 * AES 密钥、尾 8 字节做口令校验值），而原语本身是 JDK 标准件（卡片 forbid：不自研密码学）。
 *
 * 向量一来自公开标准（RFC 7914 §11 的 PBKDF2-HMAC-SHA256 用例），与本实现完全独立；
 * 向量二由独立实现（Python hashlib.pbkdf2_hmac）预算，覆盖非 ASCII 口令与 NFC 归一。
 */
class BackupKdfTest {

    @Test
    fun `derivation matches the published RFC 7914 PBKDF2-HMAC-SHA256 vector`() {
        // RFC 7914 §11: P="passwd", S="salt", c=1, dkLen=64 —— PBKDF2 输出是前缀稳定的字节流，
        // 故本实现取的 40 字节必须等于该 64 字节向量的前 40 字节。
        val derived = deriveKeyAndVerifier("passwd".toCharArray(), "salt".toByteArray(Charsets.US_ASCII), 1)
        assertEquals("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc", toHexLower(derived.key))
        assertEquals("49ca9cccf179b645", toHexLower(derived.verifier))
        assertEquals(BackupFormat.KEY_BYTES, derived.key.size)
        assertEquals(BackupFormat.VERIFIER_BYTES, derived.verifier.size)
    }

    @Test
    fun `derivation pins utf8 passphrase bytes and normalizes to NFC`() {
        // 口令 "café 房产"：NFC 与 NFD 两种输入（不同 IME / 不同系统粘贴）必须派生出同一密钥，
        // 否则用户「明明口令没错」却永远打不开自己的备份，而本格式无口令找回（ADR-0002）。
        val nfc = "caf" + 0x00E9.toChar() + " " + 0x623F.toChar() + 0x4EA7.toChar()
        val nfd = "cafe" + 0x0301.toChar() + " " + 0x623F.toChar() + 0x4EA7.toChar()
        assertEquals("636166c3a920e688bfe4baa7", toHexLower(nfc.toByteArray(Charsets.UTF_8)), "向量输入的字节形态")
        val salt = fromHex("000102030405060708090a0b0c0d0e0f")
        val fromNfc = deriveKeyAndVerifier(nfc.toCharArray(), salt, 4096)
        val fromNfd = deriveKeyAndVerifier(nfd.toCharArray(), salt, 4096)
        assertEquals("dfd6fa9ed15aa85715a49b143672d1266cf88667b089b4db4d47ed164a73f49e", toHexLower(fromNfc.key))
        assertEquals("ef0ecb0d8bec18c1", toHexLower(fromNfc.verifier))
        assertEquals(toHexLower(fromNfc.key), toHexLower(fromNfd.key))
        assertEquals(toHexLower(fromNfc.verifier), toHexLower(fromNfd.verifier))
    }

    @Test
    fun `the iteration count really feeds the derivation`() {
        val salt = fromHex("000102030405060708090a0b0c0d0e0f")
        assertNotEquals(
            toHexLower(deriveKeyAndVerifier("pw".toCharArray(), salt, 1024).key),
            toHexLower(deriveKeyAndVerifier("pw".toCharArray(), salt, 1025).key),
        )
        assertEquals(210_000, BackupFormat.DEFAULT_KDF_ITERATIONS, "本版默认迭代数（可随年份上调，头里带数所以不破旧包）")
    }

    @Test
    fun `an empty passphrase is refused`() {
        // 空口令写出的包等于没加密，而包里有租客照片与联系方式（Privacy Act 2020）。
        assertFailsWith<BackupFormatException> { deriveKeyAndVerifier(CharArray(0), ByteArray(16), 1024) }
    }

    @Test
    fun `wiping clears both halves of the derived material`() {
        val derived = deriveKeyAndVerifier("pw".toCharArray(), ByteArray(16), 1024)
        derived.wipe()
        assertEquals("0".repeat(64), toHexLower(derived.key))
        assertEquals("0".repeat(16), toHexLower(derived.verifier))
    }
}
