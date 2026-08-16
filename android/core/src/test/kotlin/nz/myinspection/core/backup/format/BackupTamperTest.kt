package nz.myinspection.core.backup.format

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 防篡改与错口令：**任何**单字节改动、任何截断/追加都必须被拒（GCM tag 或 manifest 校验），
 * 而错口令必须是一条干净的 [WrongPassphraseException]、不是崩溃、也不与「包被改过」混为一谈。
 */
class BackupTamperTest {

    private val createdAt = 1_755_400_000_000L
    private val db = ByteArray(48) { (it * 7 + 1).toByte() }
    private val photo = ByteArray(32) { (it * 13 + 5).toByte() }

    private fun archive(): ByteArray {
        val out = ByteArrayOutputStream()
        BackupWriter.write(
            out = out,
            passphrase = TEST_PASSPHRASE,
            scope = BackupScope.Full,
            createdAtMs = createdAt,
            appVersion = "1.4.2",
            files = listOf(sourceFile("db.sqlite", db), sourceFile("photos/a.jpg", photo, owner = "prop-A")),
            kdfIterations = TEST_ITERATIONS,
        )
        return out.toByteArray()
    }

    @Test
    fun `the untampered archive reads back cleanly`() {
        // 正对照：没有它，下面「一律被拒」的断言可能只是因为读取器永远失败。
        val sink = RecordingSink()
        BackupReader.read(ByteArrayInputStream(archive()), TEST_PASSPHRASE, sink)
        assertEquals(setOf("db.sqlite", "photos/a.jpg"), sink.files.keys.toSet())
    }

    @Test
    fun `a wrong passphrase is refused cleanly before any content is handed over`() {
        val sink = RecordingSink()
        assertFailsWith<WrongPassphraseException> {
            BackupReader.read(ByteArrayInputStream(archive()), "not-the-passphrase".toCharArray(), sink)
        }
        assertEquals(0, sink.manifestCalls, "口令不对时连 manifest 都不该交付")
        assertTrue(sink.files.isEmpty())
    }

    @Test
    fun `flipping any single byte anywhere in the archive is rejected`() {
        val original = archive()
        for (offset in original.indices) {
            val tampered = original.copyOf()
            tampered[offset] = (tampered[offset].toInt() xor 0x01).toByte()
            assertFailsWith<BackupException>("偏移 $offset 的单比特翻转被静默接受了") {
                BackupReader.read(ByteArrayInputStream(tampered), TEST_PASSPHRASE, RecordingSink())
            }
        }
        assertTrue(original.size > BackupFormat.HEADER_BYTES + 16, "样本须同时覆盖头、密文与 GCM tag")
    }

    @Test
    fun `truncating or padding the archive is rejected`() {
        // 截掉尾部 16 字节 = 摘掉整个 GCM tag：只有「读到密文 EOF 才校验 tag」的读取器会发现；
        // 半途放手的实现会把一份被削尾的包当成完好。
        val original = archive()
        val truncations = listOf(original.size - 1, original.size - 8, original.size - 16, original.size - 17, BackupFormat.HEADER_BYTES, 0)
        for (size in truncations) {
            assertFailsWith<BackupException>("截断到 $size 字节仍被接受") {
                BackupReader.read(ByteArrayInputStream(original.copyOf(size)), TEST_PASSPHRASE, RecordingSink())
            }
        }
        assertFailsWith<BackupException>("尾部追加垃圾仍被接受") {
            BackupReader.read(ByteArrayInputStream(original + ByteArray(8)), TEST_PASSPHRASE, RecordingSink())
        }
    }

    @Test
    fun `tampering the tail is caught even when the zip parser never reads it`() {
        // 文件一多，zip 的中央目录就跨过密文块边界；而 ZipInputStream 读到目录起点就停手——
        // 末尾那些块它一个字节都不碰。读取器若不把密文读到流尾，那段尾巴就永远没人验 tag。
        val files = mutableListOf(sourceFile("db.sqlite", db))
        repeat(1500) { i ->
            files += sourceFile("photos/2026/p" + i.toString().padStart(4, '0') + ".jpg", byteArrayOf(i.toByte()), owner = "prop-A")
        }
        val out = ByteArrayOutputStream()
        BackupWriter.write(out, TEST_PASSPHRASE, BackupScope.Full, createdAt, "1.4.2", files, TEST_ITERATIONS)
        val archive = out.toByteArray()

        val sink = RecordingSink()
        assertEquals(1501, BackupReader.read(ByteArrayInputStream(archive), TEST_PASSPHRASE, sink).files.size)
        val tampered = archive.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte() }
        assertFailsWith<BackupException>("包尾的改动必须被发现，哪怕 zip 解析器压根没读到那里") {
            BackupReader.read(ByteArrayInputStream(tampered), TEST_PASSPHRASE, RecordingSink())
        }
    }

    @Test
    fun `the plaintext header is authenticated as GCM additional data`() {
        // 头是明文（读取器需要盐/迭代数才能派生密钥），所以它必须被 tag 覆盖：
        // 不喂 AAD 就解不开，正是「头参与认证」的直接证据。本样本足够小，密文体恰好只有一块。
        val original = archive()
        val header = BackupHeader.decode(original.copyOf(BackupFormat.HEADER_BYTES))
        val body = original.copyOfRange(BackupFormat.HEADER_BYTES, original.size)
        val derived = deriveKeyAndVerifier(TEST_PASSPHRASE, header.salt, header.kdfIterations)
        val finalChunkNonce = chunkNonce(header.noncePrefix, 0, true)

        val withoutAad = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(derived.key, "AES"), GCMParameterSpec(BackupFormat.GCM_TAG_BITS, finalChunkNonce))
        }
        assertFailsWith<AEADBadTagException>("不喂 AAD 也能解开 = 头没有被认证") { withoutAad.doFinal(body) }
        assertFailsWith<AEADBadTagException>("final 标志必须进 nonce") {
            gcmCipher(Cipher.DECRYPT_MODE, derived.key, chunkNonce(header.noncePrefix, 0, false), header.encode()).doFinal(body)
        }

        val zipBytes = gcmCipher(Cipher.DECRYPT_MODE, derived.key, finalChunkNonce, header.encode()).doFinal(body)
        assertEquals(zipBytes.size + BackupFormat.GCM_TAG_BYTES, body.size, "tag 长度 = 16 字节")
        derived.wipe()

        // 顺带钉住包内布局：manifest.json 必须是首条目，其余按 rel_path 全序。
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names.add(entry.name)
            }
        }
        assertEquals(listOf("manifest.json", "db.sqlite", "photos/a.jpg"), names)
    }

    @Test
    fun `a failing sink is reported as a sink error not as a corrupt archive`() {
        // 磁盘满 / SAF 撤权是调用方的故障：把它报成「备份包损坏」会让用户去删一份其实完好的备份。
        val failure = assertFailsWith<BackupSinkException> {
            BackupReader.read(
                ByteArrayInputStream(archive()),
                TEST_PASSPHRASE,
                RecordingSink(failOn = "db.sqlite"),
            )
        }
        assertTrue(failure.cause is IOException)
    }
}
