package nz.myinspection.core.backup.format

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 往返 = 卡片 DoD 的主干：构造数据集 -> 加密归档 -> 解密展开 -> 逐文件 SHA-256 与 manifest 全对。
 * 同时钉住两条形态契约：**按物业过滤只带该物业资产**，以及**全程按块流动**（照片总量 GB 级，禁整包入内存）。
 */
class BackupRoundTripTest {

    private val createdAt = 1_755_400_000_000L

    private val db = bytesOf(4096, 3)
    private val photoA = bytesOf(1 shl 20, 11) // 1 MiB：足以证明分块
    private val photoB = bytesOf(128, 17)
    private val audioA = bytesOf(300, 23)
    private val configs = bytesOf(64, 29)

    private fun dataset() = listOf(
        sourceFile("db.sqlite", db),
        sourceFile("photos/2026/prop-A/kitchen.jpg", photoA, owner = "prop-A"),
        sourceFile("photos/2026/prop-B/lounge.jpg", photoB, owner = "prop-B"),
        sourceFile("audio/2026/prop-A/note.m4a", audioA, owner = "prop-A"),
        sourceFile("configs/compliance/nz-rules.json", configs),
    )

    private fun writeArchive(
        scope: BackupScope = BackupScope.Full,
        files: List<BackupSourceFile> = dataset(),
        passphrase: CharArray = TEST_PASSPHRASE,
    ): Pair<ByteArray, BackupManifest> {
        val out = ByteArrayOutputStream()
        val manifest = BackupWriter.writeWith(
            out = out,
            passphrase = passphrase,
            scope = scope,
            createdAtMs = createdAt,
            appVersion = "1.4.2",
            files = files,
            kdfIterations = TEST_ITERATIONS,
        )
        return out.toByteArray() to manifest
    }

    @Test
    fun `full round trip restores every file and every hash matches the manifest`() {
        val (archive, written) = writeArchive()
        val sink = RecordingSink()
        val read = BackupReader.read(ByteArrayInputStream(archive), TEST_PASSPHRASE, sink)

        assertTrue(
            archive.size > BackupFormat.HEADER_BYTES + 8 * (BackupFormat.CHUNK_PLAINTEXT_BYTES + BackupFormat.GCM_TAG_BYTES),
            "这份包必须跨很多密文块，否则往返测试根本没走到分块路径（实得 ${archive.size} 字节）",
        )
        assertEquals(written.canonicalJson, read.canonicalJson, "读回的 manifest 必须与写出的逐字节相同")
        assertEquals(read.canonicalJson, sink.manifest?.canonicalJson)
        assertEquals(1, sink.manifestCalls, "manifest 恰好交付一次，且早于任何文件字节")
        assertEquals(BackupScope.Full, read.scope)
        assertEquals(createdAt, read.createdAtMs)
        assertEquals("1.4.2", read.appVersion)

        assertContentEquals(db, sink.files["db.sqlite"])
        assertContentEquals(photoA, sink.files["photos/2026/prop-A/kitchen.jpg"])
        assertContentEquals(photoB, sink.files["photos/2026/prop-B/lounge.jpg"])
        assertContentEquals(audioA, sink.files["audio/2026/prop-A/note.m4a"])
        assertContentEquals(configs, sink.files["configs/compliance/nz-rules.json"])
        assertEquals(5, sink.files.size)
        assertEquals(read.files.map { it.relPath }.toSet(), sink.closed.toSet(), "每个落盘流都必须被关闭")
        for (file in read.files) {
            assertEquals(file.sha256, sha256Of(sink.files.getValue(file.relPath)), file.relPath)
            assertEquals(file.sizeBytes, sink.files.getValue(file.relPath).size.toLong(), file.relPath)
        }
    }

    @Test
    fun `property scoped export carries that property's assets plus library files only`() {
        val (archive, written) = writeArchive(scope = BackupScope.Property("prop-A"))
        val sink = RecordingSink()
        val read = BackupReader.read(ByteArrayInputStream(archive), TEST_PASSPHRASE, sink)

        assertEquals(BackupScope.Property("prop-A"), read.scope, "读取器据 scope 决定恢复语义，不能把按物业包当全量恢复")
        assertEquals(
            setOf(
                "audio/2026/prop-A/note.m4a",
                "configs/compliance/nz-rules.json",
                "db.sqlite",
                "photos/2026/prop-A/kitchen.jpg",
            ),
            sink.files.keys.toSet(),
        )
        assertFalse(written.files.any { it.relPath.contains("prop-B") }, "别的物业的资产不得进包")
        assertContentEquals(db, sink.files["db.sqlite"], "v1 简化：DB 快照仍整库，范围由 manifest.scope 标记")
    }

    @Test
    fun `an archive written with a composed passphrase opens with the decomposed form`() {
        val composed = ("caf" + 0x00E9.toChar() + " " + 0x623F.toChar() + 0x4EA7.toChar()).toCharArray()
        val decomposed = ("cafe" + 0x0301.toChar() + " " + 0x623F.toChar() + 0x4EA7.toChar()).toCharArray()
        val (archive, _) = writeArchive(passphrase = composed)
        val sink = RecordingSink()
        BackupReader.read(ByteArrayInputStream(archive), decomposed, sink)
        assertEquals(5, sink.files.size)
    }

    @Test
    fun `both sides move bytes in bounded chunks instead of whole files`() {
        // 「恒定内存」的可机检投影：写入端按块读源、读取端按块喂 sink，谁都不许一次性吞下 1 MiB。
        val counted = CountingInputStream(ByteArrayInputStream(photoA))
        val files = listOf(
            sourceFile("db.sqlite", db),
            BackupSourceFile(
                entry = BackupFileEntry("photos/2026/prop-A/kitchen.jpg", photoA.size.toLong(), sha256Of(photoA)),
                ownerPropertyId = "prop-A",
                openStream = { counted },
            ),
        )
        val (archive, _) = writeArchive(files = files)
        assertTrue(counted.readCalls > 8, "写入端读了 ${counted.readCalls} 次——一次读完就不是流式")
        assertTrue(
            counted.maxRequested <= BackupFormat.COPY_BUFFER_BYTES,
            "单次请求 ${counted.maxRequested} 字节超过缓冲上限",
        )

        val sink = RecordingSink()
        BackupReader.read(ByteArrayInputStream(archive), TEST_PASSPHRASE, sink)
        val chunks = sink.chunkSizes.getValue("photos/2026/prop-A/kitchen.jpg")
        assertTrue(chunks.size > 8, "读取端只写了 ${chunks.size} 块——整份文件被攒在内存里了")
        assertTrue(chunks.max() <= BackupFormat.COPY_BUFFER_BYTES, "单块 ${chunks.max()} 字节超过缓冲上限")
    }

    @Test
    fun `a null sink verifies the whole archive without writing anything`() {
        // ADR-0002 的「先试跑后落刀」：恢复前先整包校验一遍，不落任何文件。
        val (archive, _) = writeArchive()
        val dryRun = RecordingSink(skipFiles = dataset().map { it.entry.relPath }.toSet())
        val read = BackupReader.read(ByteArrayInputStream(archive), TEST_PASSPHRASE, dryRun)
        assertEquals(5, read.files.size)
        assertTrue(dryRun.files.isEmpty(), "试跑不落盘")
        assertTrue(dryRun.closed.isEmpty())

        val broken = archive.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte() }
        assertFailsWith<BackupCorruptException> {
            BackupReader.read(ByteArrayInputStream(broken), TEST_PASSPHRASE, RecordingSink(skipFiles = setOf("db.sqlite")))
        }
    }

    @Test
    fun `every archive gets a fresh salt and nonce`() {
        // 同一密钥重用 nonce 会摧毁 GCM 的安全性；salt 复用会让口令预算被跨包摊薄。
        val (first, _) = writeArchive()
        val (second, _) = writeArchive()
        val h1 = BackupHeader.decode(first.copyOf(BackupFormat.HEADER_BYTES))
        val h2 = BackupHeader.decode(second.copyOf(BackupFormat.HEADER_BYTES))
        assertNotEquals(toHexLower(h1.salt), toHexLower(h2.salt))
        assertNotEquals(toHexLower(h1.noncePrefix), toHexLower(h2.noncePrefix))
    }

    @Test
    fun `neither side closes the caller's stream`() {
        // SAF 的 URI 流归调用方管（T5-BACKUP-IO）：本层提前关掉就写不出后续内容、也拿不到 fd 归还时机。
        val out = object : ByteArrayOutputStream() {
            var closed = false
            override fun close() {
                closed = true
                super.close()
            }
        }
        BackupWriter.writeWith(out, TEST_PASSPHRASE, BackupScope.Full, createdAt, "1.4.2", dataset(), TEST_ITERATIONS)
        assertFalse(out.closed, "写入器不得关闭调用方的输出流")

        val input = object : ByteArrayInputStream(out.toByteArray()) {
            var closed = false
            override fun close() {
                closed = true
                super.close()
            }
        }
        BackupReader.read(input, TEST_PASSPHRASE, RecordingSink())
        assertFalse(input.closed, "读取器不得关闭调用方的输入流")
    }

    // 固定种子的伪随机字节：确定性（java.util.Random 的算法由 JDK 规范钉死）且**不可压缩**——
    // 可压缩的测试数据会让 1 MiB 照片 deflate 成几百字节，密文体缩到一块，多块路径就悄悄没被测到。
    private fun bytesOf(size: Int, seed: Int) = ByteArray(size).also { java.util.Random(seed.toLong()).nextBytes(it) }
}
