package nz.myinspection.core.backup.format

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 备份格式测试夹具。**冻结点卡的测试即契约本体**——这里的构件（尤其 [buildArchive]）是「第二个写入方」，
 * 它按格式规范（明文头 + AAD=头 + GCM 密文体包 zip）独立拼包，用来喂读取器那些正规写入器**造不出来**的
 * 恶意/损坏形态（manifest 不在首位、包内多出文件、重复条目、大小/哈希撒谎）。
 */
internal const val TEST_ITERATIONS = 1024

internal val TEST_PASSPHRASE: CharArray get() = "kia-ora-2026".toCharArray()

internal fun sha256Of(bytes: ByteArray): String =
    toHexLower(MessageDigest.getInstance("SHA-256").digest(bytes))

/** 十六进制 -> 字节（黄金向量只以十六进制字面量书写：不可见码位永不进源码，L193）。 */
internal fun fromHex(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "十六进制串长度必须是偶数：${hex.length}" }
    return ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

internal fun sourceFile(relPath: String, bytes: ByteArray, owner: String? = null): BackupSourceFile =
    BackupSourceFile(
        entry = BackupFileEntry(relPath, bytes.size.toLong(), sha256Of(bytes)),
        ownerPropertyId = owner,
        openStream = { ByteArrayInputStream(bytes) },
    )

/** 记录式 sink：留下 manifest、每个文件的字节、**每次 write 调用的块长**（流式的可机检证据）与关闭记录。 */
internal class RecordingSink(
    private val skipFiles: Set<String> = emptySet(),
    private val failOn: String? = null,
) : BackupSink {
    var manifest: BackupManifest? = null
    var manifestCalls = 0
    val files = LinkedHashMap<String, ByteArray>()
    val chunkSizes = LinkedHashMap<String, MutableList<Int>>()
    val closed = mutableListOf<String>()

    override fun onManifest(manifest: BackupManifest) {
        this.manifest = manifest
        manifestCalls++
    }

    override fun openFile(file: BackupFileEntry): OutputStream? {
        if (file.relPath in skipFiles) return null
        val bytes = ByteArrayOutputStream()
        val chunks = chunkSizes.getOrPut(file.relPath) { mutableListOf() }
        val relPath = file.relPath
        return object : OutputStream() {
            override fun write(b: Int) {
                write(byteArrayOf(b.toByte()), 0, 1)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                if (relPath == failOn) throw java.io.IOException("磁盘写入失败（模拟调用方 IO 故障）")
                chunks.add(len)
                bytes.write(b, off, len)
            }

            override fun close() {
                closed.add(relPath)
                files[relPath] = bytes.toByteArray()
            }
        }
    }
}

/** 计数式源流：证明写入器是按块读源、而不是把整份文件读进内存。 */
internal class CountingInputStream(private val delegate: InputStream) : InputStream() {
    var readCalls = 0
    var maxRequested = 0

    override fun read(): Int {
        readCalls++
        maxRequested = maxOf(maxRequested, 1)
        return delegate.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        readCalls++
        maxRequested = maxOf(maxRequested, len)
        return delegate.read(b, off, len)
    }
}

/**
 * 独立拼包器（测试专用的第二写入方）：按格式规范直接铺字节，可产出正规 [BackupWriter] 拒绝产出的形态。
 * 复用 main 的 [deriveKeyAndVerifier]/[gcmCipher]/[BackupHeader] 是**刻意的**——本夹具要检验的是读取器的
 * 结构校验，不是重造一遍密码学；口令派生的正确性由 [BackupKdfTest] 的公开 RFC 向量独立钉死。
 */
internal fun buildArchive(
    manifestBytes: ByteArray?,
    entries: List<Pair<String, ByteArray>>,
    patchZip: (ByteArray) -> ByteArray = { it },
): ByteArray {
    val all = if (manifestBytes == null) entries else listOf(BackupFormat.MANIFEST_ENTRY to manifestBytes) + entries
    return encryptArchive(patchZip(zipOf(all)))
}

/** 明文 zip 字节。单独暴露是为了让测试能在加密**之前**做字节级手术（如造出 zip 允许、API 不允许的重名条目）。 */
internal fun zipOf(entries: List<Pair<String, ByteArray>>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, body) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(body)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/** 把 ASCII 名字整体替换成等长的另一个名字（本地头与中央目录里都要换）。 */
internal fun patchEntryName(zipBytes: ByteArray, from: String, to: String): ByteArray {
    require(from.length == to.length) { "只能等长替换，否则 zip 的偏移量全乱" }
    val needle = from.toByteArray(Charsets.US_ASCII)
    val replacement = to.toByteArray(Charsets.US_ASCII)
    val patched = zipBytes.copyOf()
    var hits = 0
    outer@ for (start in 0..patched.size - needle.size) {
        for (i in needle.indices) if (patched[start + i] != needle[i]) continue@outer
        replacement.copyInto(patched, start)
        hits++
    }
    require(hits > 0) { "zip 里没找到条目名 $from" }
    return patched
}

internal fun encryptArchive(zipBytes: ByteArray): ByteArray {
    val passphrase = TEST_PASSPHRASE
    val iterations = TEST_ITERATIONS
    val salt = ByteArray(BackupFormat.SALT_BYTES) { it.toByte() }
    val noncePrefix = ByteArray(BackupFormat.NONCE_PREFIX_BYTES) { (it + 32).toByte() }
    val derived = deriveKeyAndVerifier(passphrase, salt, iterations)
    val header = BackupHeader(
        formatVersion = BackupFormat.FORMAT_VERSION,
        kdfId = BackupFormat.KDF_PBKDF2_HMAC_SHA256,
        kdfIterations = iterations,
        salt = salt,
        noncePrefix = noncePrefix,
        passphraseVerifier = derived.verifier,
    )
    val headerBytes = header.encode()
    val out = ByteArrayOutputStream()
    out.write(headerBytes)
    ChunkedGcmOutputStream(out, derived.key, noncePrefix, headerBytes).use { it.write(zipBytes) }
    derived.wipe()
    return out.toByteArray()
}
