package nz.myinspection.core.backup.format

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * 备份包的展开去处。
 *
 * **本层是流式的**：[BackupReader.read] 抛异常之前，sink 可能已经收到了部分文件——所以调用方必须先落
 * staging、只有 read() 正常返回才提交（ADR-0002「先试跑后落刀」）。
 * [openFile] 返回 `null` 即「只校验不落盘」，整包照样逐字节验哈希——那就是试跑。
 */
interface BackupSink {
    /**
     * 收到 manifest 时调用，**早于任何文件字节**，且整个读取过程只调用一次。
     * `manifest.scope` 决定恢复语义：按物业的包绝不能被当成全量恢复（ADR-0002 v1 = 整包替换）。
     */
    fun onManifest(manifest: BackupManifest)

    /** 返回该文件的写入目标（由读取器负责关闭），或 `null` 表示只校验不落盘。 */
    fun openFile(file: BackupFileEntry): OutputStream?
}

/**
 * ★ 备份包读取器：验口令 → 逐块解密验 tag → 按 manifest 逐文件核对 SHA-256 与字节数。
 * 全程按块流动，恒定内存；不拥有调用方的输入流（不 close）。
 *
 * 拒收的每一种形态都有明确异常：口令错 [WrongPassphraseException]、结构非法 [BackupFormatException]、
 * 被改过/被截断/与 manifest 不符 [BackupCorruptException]、调用方自己 IO 失败 [BackupSinkException]。
 */
object BackupReader {

    fun read(input: InputStream, passphrase: CharArray, sink: BackupSink): BackupManifest {
        try {
            val header = BackupHeader.readFrom(input)
            val derived = deriveKeyAndVerifier(passphrase, header.salt, header.kdfIterations)
            try {
                // 口令校验值先判：这样「口令错」不必等到最后一块 tag 失败才知道，
                // 也不会与「包被改过」混成同一句报错。
                if (!MessageDigest.isEqual(derived.verifier, header.passphraseVerifier)) {
                    throw WrongPassphraseException("口令错：无法用该口令打开此备份包（本格式无口令找回，ADR-0002）")
                }
                return expand(input, header, derived.key, sink)
            } finally {
                derived.wipe()
            }
        } catch (e: BackupSinkException) {
            throw e // 调用方的故障，不是包的问题
        } catch (e: BackupException) {
            throw e
        } catch (e: IOException) {
            throw BackupCorruptException("备份包读取失败（截断 / 损坏 / 完整性校验未过）", e)
        } catch (e: GeneralSecurityException) {
            throw BackupCorruptException("备份包完整性校验未过：GCM tag 不匹配（内容被改动过，或口令对应的是另一份包）", e)
        } catch (e: RuntimeException) {
            // 解压链对损坏输入抛的是未受检异常（如 ZipInputStream 的 IllegalArgumentException("MALFORMED")）。
            // 敌意输入绝不允许以原始形态冒到 UI（DoD：不崩溃）——一律归为「包损坏」，cause 保留供诊断。
            throw BackupCorruptException("备份包损坏（解析时抛出 ${e.javaClass.simpleName}）", e)
        }
    }

    private fun expand(input: InputStream, header: BackupHeader, key: ByteArray, sink: BackupSink): BackupManifest {
        val chunked = ChunkedGcmInputStream(input, key, header.noncePrefix, header.encode())
        val zip = ZipInputStream(chunked)

        val first = zip.nextEntry ?: throw BackupCorruptException("备份包是空的：首条目应当是 ${BackupFormat.MANIFEST_ENTRY}")
        if (first.name != BackupFormat.MANIFEST_ENTRY) {
            throw BackupCorruptException("首条目必须是 ${BackupFormat.MANIFEST_ENTRY}，实得 ${safeShow(first.name)}")
        }
        val manifest = BackupManifest.parse(readAtMost(zip, BackupFormat.MAX_MANIFEST_BYTES))
        sinkCall("接收 manifest") { sink.onManifest(manifest) }

        val seen = LinkedHashSet<String>()
        while (true) {
            val name = (zip.nextEntry ?: break).name
            if (name == BackupFormat.MANIFEST_ENTRY) {
                throw BackupCorruptException("${BackupFormat.MANIFEST_ENTRY} 在包里出现了不止一次")
            }
            // manifest 是路径安全的**唯一**闸门：条目名必须逐字对上一条已校验过的声明，因此读取器交给
            // 调用方的路径不可能逃出归档根（zip-slip 在这里就走不通），目录条目（名字以 / 结尾）也一样进不来
            // ——`checkRelPath` 不许 manifest 里出现那种名字。别再加一道语法预检：它拦不下任何这道闸放过的东西
            // （变异实验证实无论删哪一句都没有测试变红），只会让「路径安全在哪判」出现第二个说法。
            val file = manifest.file(name)
                ?: throw BackupCorruptException("包内文件未在 manifest 声明（未被哈希覆盖）：${safeShow(name)}")
            if (!seen.add(name)) throw BackupCorruptException("同一 rel_path 在包里出现了不止一次：${safeShow(name)}")
            extract(zip, file, sink)
        }

        val missing = manifest.files.map { it.relPath }.filter { it !in seen }
        if (missing.isNotEmpty()) {
            throw BackupCorruptException("manifest 声明的文件在包里缺失（共 ${missing.size} 个）：${missing.take(5)}")
        }

        // ★ 必须把密文读到尾。每块的 tag 在该块被解密的那一刻就验过了——问题在于**尾部的块可能根本没被解密过**：
        // ZipInputStream 读到中央目录起点就停手，而文件一多，中央目录本身就跨过好几个密文块（1500 个条目
        // 约 100 KB > 一块 64 KiB）。半途放手 = 那几块的篡改与截断全部漏网，最后一块的 final 标志也没人验。
        drain(chunked)
        return manifest
    }

    private fun extract(zip: ZipInputStream, file: BackupFileEntry, sink: BackupSink) {
        val target = sinkCall("打开 ${file.relPath}") { sink.openFile(file) }
        var verified = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BackupFormat.COPY_BUFFER_BYTES)
            var total = 0L
            while (true) {
                val read = zip.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > file.sizeBytes) {
                    // 越界的字节一个都不交给调用方：声明 4 字节、实塞 4 MiB 的解压炸弹必须在这里停手，
                    // 而不是先写满磁盘再说哈希不对。
                    throw BackupCorruptException("包内文件比 manifest 声明的大：${file.relPath} 声明 ${file.sizeBytes} 字节")
                }
                digest.update(buffer, 0, read)
                if (target != null) sinkCall("写入 ${file.relPath}") { target.write(buffer, 0, read) }
            }
            if (total != file.sizeBytes) {
                throw BackupCorruptException("包内文件字节数与 manifest 不符：${file.relPath} 声明 ${file.sizeBytes}，实得 $total")
            }
            val actual = toHexLower(digest.digest())
            if (actual != file.sha256) {
                throw BackupCorruptException("包内文件 SHA-256 与 manifest 不符：${file.relPath}")
            }
            verified = true
        } finally {
            if (target != null) {
                // 成功路径上的 close 失败必须冒出去（没 flush 成功 = 数据没落地）；失败路径上只求释放资源。
                if (verified) sinkCall("关闭 ${file.relPath}") { target.close() } else runCatching { target.close() }
            }
        }
    }

    private fun drain(input: InputStream) {
        val buffer = ByteArray(BackupFormat.COPY_BUFFER_BYTES)
        while (input.read(buffer) >= 0) {
            // 只为把剩下的密文块读进来验 tag，内容本身不再需要（zip 目录已被 ZipInputStream 用过）。
        }
    }

    /** 调用方回调的边界：它抛出的任何异常都包成 [BackupSinkException]，绝不误判成「包损坏」。 */
    private inline fun <T> sinkCall(what: String, block: () -> T): T = try {
        block()
    } catch (e: BackupSinkException) {
        throw e
    } catch (e: Exception) {
        throw BackupSinkException("调用方在「$what」时失败（不是备份包的问题）", e)
    }
}
