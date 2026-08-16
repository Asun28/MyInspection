package nz.myinspection.core.backup.format

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 读取器面对**正规写入器造不出来**的包：条目与 manifest 不符、重复条目、大小/哈希撒谎、manifest 不在首位。
 * 这些包都带合法 GCM tag（[buildArchive] 用正确口令拼），所以只有结构校验能拦下它们——
 * 密码学不负责回答「这份 zip 里装的是不是 manifest 说的东西」。
 */
class BackupReaderHostileTest {

    private val createdAt = 1_755_400_000_000L
    private val db = ByteArray(64) { (it * 5 + 1).toByte() }
    private val photo = ByteArray(32) { (it * 9 + 2).toByte() }

    private fun manifestOf(vararg files: BackupFileEntry): ByteArray =
        BackupManifest.create(createdAt, "1.4.2", BackupScope.Full, files.toList()).toBytes()

    private fun entry(relPath: String, bytes: ByteArray) = BackupFileEntry(relPath, bytes.size.toLong(), sha256Of(bytes))

    private fun read(archive: ByteArray, sink: BackupSink = RecordingSink()) =
        BackupReader.read(ByteArrayInputStream(archive), TEST_PASSPHRASE, sink)

    @Test
    fun `the crafted baseline archive is accepted`() {
        // 正对照：夹具拼出的「规规矩矩」的包必须能读——否则下面每条拒收都可能是夹具坏了。
        val sink = RecordingSink()
        read(buildArchive(manifestOf(entry("db.sqlite", db)), listOf("db.sqlite" to db)), sink)
        assertEquals(setOf("db.sqlite"), sink.files.keys.toSet())
    }

    @Test
    fun `an archive whose first entry is not the manifest is rejected`() {
        val manifest = manifestOf(entry("db.sqlite", db))
        val archive = buildArchive(null, listOf("db.sqlite" to db, BackupFormat.MANIFEST_ENTRY to manifest))
        assertFailsWith<BackupCorruptException> { read(archive) }
    }

    @Test
    fun `an archive with no manifest or no entries at all is rejected`() {
        assertFailsWith<BackupCorruptException> { read(buildArchive(null, listOf("db.sqlite" to db))) }
        assertFailsWith<BackupCorruptException> { read(buildArchive(null, emptyList())) }
    }

    @Test
    fun `a second manifest entry is rejected`() {
        // ZipOutputStream 自己就不肯写重名条目，所以重名要在加密前对明文 zip 做等长改名手术造出来。
        val manifest = manifestOf(entry("db.sqlite", db))
        val archive = buildArchive(
            manifest,
            listOf("db.sqlite" to db, "manifest.jsoX" to manifest),
            patchZip = { patchEntryName(it, "manifest.jsoX", "manifest.json") },
        )
        assertFailsWith<BackupCorruptException> { read(archive) }
    }

    @Test
    fun `a file that the manifest never declared is rejected`() {
        // 未声明 = 未被哈希覆盖：放过它等于允许往备份里夹带任意文件，恢复时原样落到用户设备上。
        val archive = buildArchive(
            manifestOf(entry("db.sqlite", db)),
            listOf("db.sqlite" to db, "photos/a.jpg" to photo),
        )
        assertFailsWith<BackupCorruptException> { read(archive) }
    }

    @Test
    fun `a path escaping the archive root is rejected`() {
        val archive = buildArchive(manifestOf(entry("db.sqlite", db)), listOf("db.sqlite" to db, "../escape.jpg" to photo))
        assertFailsWith<BackupCorruptException> { read(archive) }
    }

    @Test
    fun `a declared file missing from the archive is rejected`() {
        // 少一张照片的包不是「部分成功」——恢复语义是整包替换，缺件就是数据丢失。
        val archive = buildArchive(
            manifestOf(entry("db.sqlite", db), entry("photos/a.jpg", photo)),
            listOf("db.sqlite" to db),
        )
        assertFailsWith<BackupCorruptException> { read(archive) }
    }

    @Test
    fun `two entries for the same rel path are rejected`() {
        // zip 允许重名条目，恢复时「哪一份说了算」是未定义的。两份**内容相同**是最刁的构造：
        // 逐文件哈希校验对它毫无意见（两份都对得上 manifest），只有「同名不得出现两次」这条能拦下它。
        val archive = buildArchive(
            manifestOf(entry("db.sqlite", db)),
            listOf("db.sqlite" to db, "db.sqlitX" to db),
            patchZip = { patchEntryName(it, "db.sqlitX", "db.sqlite") },
        )
        assertFailsWith<BackupCorruptException> { read(archive) }
    }

    @Test
    fun `content that does not match the declared hash is rejected`() {
        val lying = manifestOf(BackupFileEntry("db.sqlite", db.size.toLong(), sha256Of(ByteArray(64) { 7 })))
        assertFailsWith<BackupCorruptException> { read(buildArchive(lying, listOf("db.sqlite" to db))) }
    }

    @Test
    fun `an entry longer than its declared size is cut off and rejected`() {
        // 解压炸弹：声明 4 字节、实际塞 4 MiB。必须在越界的那一刻停手，而不是先写满磁盘再说哈希不对。
        val bomb = ByteArray(4 shl 20)
        val lying = manifestOf(BackupFileEntry("db.sqlite", 4, sha256Of(ByteArray(4))))
        val sink = RecordingSink()
        assertFailsWith<BackupCorruptException> { read(buildArchive(lying, listOf("db.sqlite" to bomb)), sink) }
        val written = sink.chunkSizes["db.sqlite"]?.sum() ?: 0
        assertTrue(written <= 4, "越界的字节不得再写给调用方（实写 $written）")
    }

    @Test
    fun `a close failure during a failed extraction is kept as a suppressed diagnostic`() {
        // 出错时也得把 sink 关掉，但 close 自己再炸时不能盖掉首因——两条线索都要留给诊断，
        // 否则「为什么恢复失败」只剩下后发生的那一个。
        val lying = manifestOf(BackupFileEntry("db.sqlite", db.size.toLong(), sha256Of(ByteArray(64) { 7 })))
        val failure = assertFailsWith<BackupCorruptException> {
            read(buildArchive(lying, listOf("db.sqlite" to db)), RecordingSink(failOnClose = true))
        }
        assertEquals(1, failure.suppressed.size, "close 的失败必须挂成 suppressed，不得被吞")
        assertTrue(failure.suppressed[0] is java.io.IOException)
    }

    @Test
    fun `an entry shorter than its declared size is rejected`() {
        val lying = manifestOf(BackupFileEntry("db.sqlite", 64, sha256Of(db)))
        assertFailsWith<BackupCorruptException> { read(buildArchive(lying, listOf("db.sqlite" to db.copyOf(32)))) }
    }

    @Test
    fun `a directory entry inside the archive is rejected`() {
        // 目录条目（名字以 / 结尾）进不了 manifest（checkRelPath 拒收），于是它在包里也就无处容身。
        val archive = buildArchive(manifestOf(entry("db.sqlite", db)), listOf("db.sqlite" to db, "photos/" to ByteArray(0)))
        assertFailsWith<BackupCorruptException> { read(archive) }
    }
}
