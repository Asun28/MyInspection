package nz.myinspection.core.backup.format

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 一个待写入的源文件。[entry] 里的字节数与 SHA-256 由调用方声明（DB 里本就存着照片的内容哈希，
 * CLAUDE.md 关键不变量），写入器在流过每个字节时**当场复核**声明——对不上就当场炸，
 * 绝不写出一份「manifest 说的」与「包里装的」不一致的备份。
 *
 * [ownerPropertyId] = 该资产属于哪个物业；`null` 表示库级资产（db.sqlite、configs），任何范围都收。
 * [openStream] 只会被调用一次，返回的流由写入器负责关闭。
 */
class BackupSourceFile(
    val entry: BackupFileEntry,
    val ownerPropertyId: String?,
    val openStream: () -> InputStream,
)

/**
 * ★ 备份包写入器：明文头 + 分块 AES-256-GCM 密文体（内含 zip 流）。全程按块流动，恒定内存。
 * 不拥有调用方的输出流：写完只 flush，不 close（SAF 的 URI 流归 T5-BACKUP-IO 管）。
 */
object BackupWriter {

    /**
     * 写出一份备份包，返回实际写进包里的 manifest。
     *
     * **迭代数与熵源在生产入口上不可配置**：能被传进 1 次迭代或一个可预测的 `SecureRandom` 的公开 API，
     * 等于把「租客数据必须被真正加密」这条要求交给每个调用点自觉。要弱化只能走 internal 的 [writeWith]
     * 测试缝（卡片允许测试用小迭代数——迭代数写在头里，故读侧不受影响）。
     *
     * @param scope 数据集范围；按物业导出时只有该物业的资产 + 库级资产进包。
     */
    fun write(
        out: OutputStream,
        passphrase: CharArray,
        scope: BackupScope,
        createdAtMs: Long,
        appVersion: String,
        files: List<BackupSourceFile>,
    ): BackupManifest = writeWith(
        out = out,
        passphrase = passphrase,
        scope = scope,
        createdAtMs = createdAtMs,
        appVersion = appVersion,
        files = files,
        kdfIterations = BackupFormat.DEFAULT_KDF_ITERATIONS,
        random = SecureRandom(),
    )

    /** 测试缝：只有 :core 模块内部（含测试源集）能调，用来把迭代数压到小值、或钉住熵源。 */
    internal fun writeWith(
        out: OutputStream,
        passphrase: CharArray,
        scope: BackupScope,
        createdAtMs: Long,
        appVersion: String,
        files: List<BackupSourceFile>,
        kdfIterations: Int,
        random: SecureRandom = SecureRandom(),
    ): BackupManifest {
        checkSources(files)
        val selected = files.filter { scope.includes(it.ownerPropertyId) }
        val manifest = BackupManifest.create(createdAtMs, appVersion, scope, selected.map { it.entry })
        val bySource = selected.associateBy { it.entry.relPath }

        val salt = ByteArray(BackupFormat.SALT_BYTES).also(random::nextBytes)
        val noncePrefix = ByteArray(BackupFormat.NONCE_PREFIX_BYTES).also(random::nextBytes)
        val derived = deriveKeyAndVerifier(passphrase, salt, kdfIterations)
        try {
            val header = BackupHeader(
                formatVersion = BackupFormat.FORMAT_VERSION,
                kdfId = BackupFormat.KDF_PBKDF2_HMAC_SHA256,
                kdfIterations = kdfIterations,
                salt = salt,
                noncePrefix = noncePrefix,
                passphraseVerifier = derived.verifier,
            )
            val headerBytes = header.encode()
            out.write(headerBytes)
            val zip = ZipOutputStream(ChunkedGcmOutputStream(out, derived.key, noncePrefix, headerBytes))
            zip.putNextEntry(entryOf(BackupFormat.MANIFEST_ENTRY, createdAtMs))
            zip.write(manifest.toBytes())
            zip.closeEntry()
            for (file in manifest.files) {
                zip.putNextEntry(entryOf(file.relPath, createdAtMs))
                copyVerified(bySource.getValue(file.relPath).openStream(), zip, file)
                zip.closeEntry()
            }
            // 只有一路无异常走到这里才收尾。中途失败时**刻意不 close**：close 会补齐 zip 目录并写出合法
            // GCM tag，把半包伪装成完整包。本层不持有 OS 资源（out 归调用方，Deflater 由 Cleaner 回收）。
            zip.close()
            out.flush()
            return manifest
        } finally {
            derived.wipe()
        }
    }

    private fun checkSources(files: List<BackupSourceFile>) {
        val seen = HashSet<String>()
        for (file in files) {
            val relPath = file.entry.relPath
            if (file.ownerPropertyId != null && file.ownerPropertyId.isEmpty()) {
                throw BackupFormatException("库级资产请用 null 表示，不要用空串（$relPath）")
            }
            if (relPath == BackupFormat.DB_ENTRY && file.ownerPropertyId != null) {
                throw BackupFormatException(
                    "${BackupFormat.DB_ENTRY} 必须是库级资产（owner=null）：v1 的 DB 快照恒为整库，范围只由 manifest.scope 标记",
                )
            }
            if (!seen.add(relPath)) throw BackupFormatException("源清单里有重复 rel_path：$relPath")
        }
    }

    private fun entryOf(name: String, timeMs: Long) = ZipEntry(name).apply { time = timeMs }

    /**
     * 边流边算：字节数与 SHA-256 必须与 manifest 的声明一致。
     * 压缩方式不属于格式契约（读取器用 ZipInputStream，DEFLATED/STORED 都能读）。
     */
    private fun copyVerified(source: InputStream, target: OutputStream, entry: BackupFileEntry) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BackupFormat.COPY_BUFFER_BYTES)
        var total = 0L
        source.use {
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > entry.sizeBytes) {
                    throw BackupFormatException("源文件比 manifest 声明的大：${entry.relPath} 声明 ${entry.sizeBytes} 字节")
                }
                digest.update(buffer, 0, read)
                target.write(buffer, 0, read)
            }
        }
        if (total != entry.sizeBytes) {
            throw BackupFormatException("源文件字节数与 manifest 声明不符：${entry.relPath} 声明 ${entry.sizeBytes}，实得 $total")
        }
        val actual = toHexLower(digest.digest())
        if (actual != entry.sha256) {
            throw BackupFormatException("源文件 SHA-256 与 manifest 声明不符：${entry.relPath} 声明 ${entry.sha256}，实得 $actual")
        }
    }
}
