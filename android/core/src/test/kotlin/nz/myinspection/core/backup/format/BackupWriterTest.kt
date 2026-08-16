package nz.myinspection.core.backup.format

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 写入器的守卫：**绝不产出自相矛盾的包**。备份的价值全押在「manifest 说什么，包里就是什么」上——
 * 声明与字节对不上时必须当场炸，而不是写出一份看着完好、恢复时才发现哈希不符的包。
 */
class BackupWriterTest {

    private val createdAt = 1_755_400_000_000L
    private val db = ByteArray(64) { (it * 3 + 1).toByte() }
    private val photo = ByteArray(32) { (it * 7 + 2).toByte() }

    private fun write(
        files: List<BackupSourceFile>,
        scope: BackupScope = BackupScope.Full,
        passphrase: CharArray = TEST_PASSPHRASE,
        iterations: Int = TEST_ITERATIONS,
        out: ByteArrayOutputStream = ByteArrayOutputStream(),
    ): ByteArray {
        BackupWriter.writeWith(out, passphrase, scope, createdAt, "1.4.2", files, iterations)
        return out.toByteArray()
    }

    @Test
    fun `a source whose bytes disagree with the declared size is refused`() {
        val tooShort = BackupSourceFile(BackupFileEntry("photos/a.jpg", 999, sha256Of(photo)), "prop-A") { ByteArrayInputStream(photo) }
        val tooLong = BackupSourceFile(BackupFileEntry("photos/a.jpg", 4, sha256Of(photo)), "prop-A") { ByteArrayInputStream(photo) }
        assertFailsWith<BackupFormatException> { write(listOf(sourceFile("db.sqlite", db), tooShort)) }
        assertFailsWith<BackupFormatException> { write(listOf(sourceFile("db.sqlite", db), tooLong)) }
    }

    @Test
    fun `a source whose bytes disagree with the declared hash is refused`() {
        val lying = BackupSourceFile(
            BackupFileEntry("photos/a.jpg", photo.size.toLong(), sha256Of(ByteArray(32))),
            "prop-A",
        ) { ByteArrayInputStream(photo) }
        assertFailsWith<BackupFormatException> { write(listOf(sourceFile("db.sqlite", db), lying)) }
    }

    @Test
    fun `a half written archive is never readable`() {
        // 中途失败时不得把 zip 收尾、不得写出合法的 final 块——否则半包会伪装成完整包。
        // 数据库快照取得足够大（几个密文块），确保失败发生时**确实已有密文落地**，而不是只写了个头。
        val bigDb = ByteArray(200 * 1024).also { java.util.Random(7).nextBytes(it) } // 不可压缩，确保真有密文块落地
        val lying = BackupSourceFile(BackupFileEntry("photos/a.jpg", 999, sha256Of(photo)), "prop-A") { ByteArrayInputStream(photo) }
        val out = ByteArrayOutputStream()
        assertFailsWith<BackupFormatException> { write(listOf(sourceFile("db.sqlite", bigDb), lying), out = out) }
        assertTrue(
            out.size() > BackupFormat.HEADER_BYTES + BackupFormat.CHUNK_PLAINTEXT_BYTES,
            "确实已经写出了不止一个密文块（实得 ${out.size()} 字节）",
        )
        assertFailsWith<BackupException> {
            BackupReader.read(ByteArrayInputStream(out.toByteArray()), TEST_PASSPHRASE, RecordingSink())
        }
    }

    @Test
    fun `the public writer cannot be configured below the approved kdf strength`() {
        // 公开入口刻意没有迭代数/熵源参数（可配置版是 internal 测试缝）。这条断言钉住它写出来的头
        // 确实带着批准的强度——否则「加密备份」这句承诺就落在每个调用点的自觉上。
        val out = ByteArrayOutputStream()
        BackupWriter.write(out, TEST_PASSPHRASE, BackupScope.Full, createdAt, "1.4.2", listOf(sourceFile("db.sqlite", db)))
        val header = BackupHeader.decode(out.toByteArray().copyOf(BackupFormat.HEADER_BYTES))
        assertEquals(BackupFormat.DEFAULT_KDF_ITERATIONS, header.kdfIterations)
        assertEquals(210_000, BackupFormat.DEFAULT_KDF_ITERATIONS)
    }

    @Test
    fun `an empty passphrase is refused`() {
        assertFailsWith<BackupFormatException> { write(listOf(sourceFile("db.sqlite", db)), passphrase = CharArray(0)) }
    }

    @Test
    fun `duplicate rel paths among the sources are refused`() {
        assertFailsWith<BackupFormatException> {
            write(listOf(sourceFile("db.sqlite", db), sourceFile("photos/a.jpg", photo, "prop-A"), sourceFile("photos/a.jpg", photo, "prop-B")))
        }
    }

    @Test
    fun `ownership must match the area for every area`() {
        // 归属不能靠调用方随手填：一张 owner=null 的照片会混进**每一个**按物业包，
        // 而一份带 owner 的 configs 会从别的物业包里凭空消失；带 owner 的 db.sqlite 更会被过滤掉，
        // 写出一份没有数据库的包（拿去恢复就是清库）。四个区域逐一钉死。
        assertFailsWith<BackupFormatException>("db.sqlite 带 owner") {
            write(listOf(BackupSourceFile(BackupFileEntry("db.sqlite", db.size.toLong(), sha256Of(db)), "prop-A") { ByteArrayInputStream(db) }))
        }
        assertFailsWith<BackupFormatException>("configs 带 owner") {
            write(
                listOf(
                    sourceFile("db.sqlite", db),
                    BackupSourceFile(BackupFileEntry("configs/compliance/nz.json", photo.size.toLong(), sha256Of(photo)), "prop-A") { ByteArrayInputStream(photo) },
                ),
            )
        }
        for (relPath in listOf("photos/a.jpg", "audio/a.m4a")) {
            assertFailsWith<BackupFormatException>(relPath) {
                write(listOf(sourceFile("db.sqlite", db), sourceFile(relPath, photo, owner = null)))
            }
        }
        // 正对照：区域与归属一致时照常写出。
        val archive = write(
            listOf(
                sourceFile("db.sqlite", db),
                sourceFile("configs/compliance/nz.json", photo),
                sourceFile("photos/a.jpg", photo, "prop-A"),
                sourceFile("audio/a.m4a", photo, "prop-A"),
            ),
        )
        val sink = RecordingSink()
        assertEquals(4, BackupReader.read(ByteArrayInputStream(archive), TEST_PASSPHRASE, sink).files.size)
    }

    @Test
    fun `the writer refuses a manifest the reader would reject`() {
        // 每条目都合法、合起来越界，就会产出一份「本版自己读不回来」的包。写侧必须先自查同一个上限。
        assertEquals(BackupFormat.MAX_MANIFEST_BYTES, checkManifestSize(ByteArray(BackupFormat.MAX_MANIFEST_BYTES)).size)
        assertFailsWith<BackupFormatException> { checkManifestSize(ByteArray(BackupFormat.MAX_MANIFEST_BYTES + 1)) }
    }

    @Test
    fun `an empty owner string is refused`() {
        // "" 与 null 是两种语义（某物业 vs 库级），混用会让过滤结果随口径漂移。
        assertFailsWith<BackupFormatException> { write(listOf(sourceFile("db.sqlite", db), sourceFile("photos/a.jpg", photo, ""))) }
    }

    @Test
    fun `an out of range iteration count is refused`() {
        for (iterations in listOf(0, -1, BackupFormat.MAX_KDF_ITERATIONS + 1)) {
            assertFailsWith<BackupFormatException>("迭代数 $iterations") {
                write(listOf(sourceFile("db.sqlite", db)), iterations = iterations)
            }
        }
    }

    @Test
    fun `a property with no assets still produces a valid library only archive`() {
        val archive = write(
            listOf(sourceFile("db.sqlite", db), sourceFile("photos/a.jpg", photo, "prop-A")),
            scope = BackupScope.Property("prop-Z"),
        )
        val sink = RecordingSink()
        val manifest = BackupReader.read(ByteArrayInputStream(archive), TEST_PASSPHRASE, sink)
        assertEquals(BackupScope.Property("prop-Z"), manifest.scope)
        assertEquals(setOf("db.sqlite"), sink.files.keys.toSet())
    }
}
