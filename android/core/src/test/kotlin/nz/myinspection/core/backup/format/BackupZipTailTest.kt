package nz.myinspection.core.backup.format

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ★ 明文 zip 的**尾部**（中央目录 + EOCD）在本读取器眼里算什么——实测钉死，不靠推断。
 *
 * `ZipInputStream` 按**本地条目头**顺序读，读到不是本地头的签名就返回 null，**从不校验中央目录**；
 * 而且它在判定前已把 30 字节的头读进自己的缓冲、并把余料退回**内部**的 PushbackInputStream，
 * 尾部字节因此对上层不可见。于是本层的实际契约是：
 *
 * **包内容的权威是 manifest，不是 zip 的中央目录。** 每个交付给调用方的文件都逐字节核过
 * 大小与 SHA-256、且必须在 manifest 里声明过；每个密文块都过了 GCM tag（含末块标志）。
 * 尾部再怎么坏，坏的部分永远不会被交出去。
 *
 * 下面三种尾部形态**当前被接受**，这是本测试要写明的事实。**它们都带合法 GCM tag**——
 * 能造出来的只有握有口令的人。是否要收紧到「尾部必须是良构的中央目录 + EOCD」，
 * 需要替换容器读取器（自写最小 zip 解析器）或改容器分帧，属于冻结点上的格式决策，见 PR #9 的评审往复。
 */
class BackupZipTailTest {

    private val db = ByteArray(64) { (it * 5 + 1).toByte() }

    private fun manifestBytes() = BackupManifest.create(
        1_755_400_000_000L,
        "1.4.2",
        BackupScope.Full,
        listOf(BackupFileEntry("db.sqlite", db.size.toLong(), sha256Of(db))),
    ).toBytes()

    private fun readTailShape(patch: (ByteArray) -> ByteArray): Result<Set<String>> {
        val archive = buildArchive(manifestBytes(), listOf("db.sqlite" to db), patchZip = patch)
        val sink = RecordingSink()
        return runCatching {
            BackupReader.read(ByteArrayInputStream(archive), TEST_PASSPHRASE, sink)
            sink.files.keys.toSet()
        }
    }

    /** EOCD 里记的「中央目录起始偏移」= 尾部的起点。 */
    private fun centralDirectoryOffset(zipBytes: ByteArray): Int {
        val eocdSignature = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
        for (start in zipBytes.size - 22 downTo 0) {
            if ((0..3).all { zipBytes[start + it] == eocdSignature[it] }) {
                var offset = 0
                for (i in 3 downTo 0) offset = (offset shl 8) or (zipBytes[start + 16 + i].toInt() and 0xFF)
                return offset
            }
        }
        error("样本里找不到 EOCD，夹具坏了")
    }

    @Test
    fun `the intact archive is the control`() {
        assertEquals(setOf("db.sqlite"), readTailShape { it }.getOrThrow())
    }

    @Test
    fun `a damaged zip tail does not change what the reader delivers`() {
        // 三种形态都被接受，且**交付内容完全不受影响**——后半句才是本条真正守的东西：
        // 尾部的垃圾一个字节都不会变成交给调用方的文件。
        val shapes = linkedMapOf<String, (ByteArray) -> ByteArray>(
            "central directory removed" to { it.copyOf(centralDirectoryOffset(it)) },
            "junk inserted before the central directory" to {
                val cut = centralDirectoryOffset(it)
                it.copyOf(cut) + "JUNKJUNKJUNK".toByteArray(Charsets.US_ASCII) + it.copyOfRange(cut, it.size)
            },
            "plaintext appended after EOCD" to { it + "TRAILING".toByteArray(Charsets.US_ASCII) },
        )
        for ((shape, patch) in shapes) {
            assertEquals(setOf("db.sqlite"), readTailShape(patch).getOrThrow(), shape)
        }
    }
}
