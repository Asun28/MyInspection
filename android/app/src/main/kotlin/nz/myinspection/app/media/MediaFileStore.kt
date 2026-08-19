package nz.myinspection.app.media

import android.util.Log
import java.io.File
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import nz.myinspection.core.media.NewAssetDiscard
import nz.myinspection.core.media.StagedFile
import nz.myinspection.core.media.StreamCompare

/**
 * 文件 IO 薄壳：:core 只产出/消费相对路径，根路径由 :app 运行时注入。本层只做「相对路径 + 根 → 绝对
 * File」与字节落盘，不判定该落哪、该不该复用（那是 `PhotoIngest` 的决定）。
 */
object MediaFileStore {
    private const val TAG = "MediaFileStore"

    /**
     * @throws IllegalArgumentException 若 [relPath] 解析后逃出了 [root]（路径穿越/绝对路径注入）。
     * 与 `MediaPaths` 的命名空间形状闸是两道独立闸：那道防"在根内但不该被碰"，这道防"逃出根"。
     */
    fun resolve(root: File, relPath: String): File {
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, relPath).canonicalFile
        require(candidate == canonicalRoot || candidate.path.startsWith(canonicalRoot.path + File.separator)) {
            "relPath escapes the media root: $relPath"
        }
        return candidate
    }

    /** Publishes a previously closed and verified staged file through the unchanged no-overwrite move policy. */
    internal fun publishStaged(staged: StagedFile, root: File, relPath: String): File =
        publish(staged.file, resolve(root, relPath))

    /** 导入=复制，不移动用户原始文件（硬边界）。调用方负责关闭 [source]。 */
    fun copyInto(source: InputStream, root: File, relPath: String): File {
        val target = resolve(root, relPath)
        target.parentFile?.mkdirs()
        val temp = createTempSibling(target)
        var copyPrimary: Throwable? = null
        try {
            temp.outputStream().use { out -> source.copyTo(out) }
            return publish(temp, target)
        } catch (failure: Throwable) {
            copyPrimary = failure
            throw failure
        } finally {
            try {
                deleteTemp(temp)
            } catch (cleanupFailure: Throwable) {
                val failure = copyPrimary
                if (failure == null) throw cleanupFailure
                failure.addSuppressed(cleanupFailure)
            }
        }
    }

    /**
     * 删除一个文件；**目标在调用后不存在即算成功**。先删、再看在不在——`exists()` 再 `delete()` 的两步写法
     * 会把"另一条清理链路抢先删掉了"这种并发情形误报成失败。
     */
    fun deleteIfPresent(file: File): Boolean = file.delete() || !file.exists()

    /** `PhotoAssociationRecorder` 的补偿动作实现：撤销本次刚落在 [root] 下的那份资产字节。 */
    fun discardIn(root: File): NewAssetDiscard = NewAssetDiscard { relPath -> deleteIfPresent(resolve(root, relPath)) }

    /** 临时文件名靠 [File.createTempFile] 取真随机唯一性并原子性建档，不用可能撞值的 `System.nanoTime()`。 */
    private fun createTempSibling(target: File): File = File.createTempFile("${target.name}-", ".tmp", target.parentFile)

    /**
     * 发布：用**不带任何 CopyOption** 的 `Files.move`。刻意不用 `ATOMIC_MOVE`——JDK 17 + Windows/NTFS
     * （本项目唯一开发/CI 平台）实测它在目标已存在时**静默替换**（javadoc 标为 implementation specific），
     * 正是本函数要杜绝的事；不带选项的 `Files.move` 实测可靠抛 [FileAlreadyExistsException]，且检查与移动
     * 之间没有独立窗口。目标已存在时比对内容：相同 = 幂等重试，采用既有文件；不同 = 拒绝覆盖并报错
     * （关键不变量：证据不可被静默替换）。
     */
    private fun publish(temp: File, target: File): File {
        try {
            Files.move(temp.toPath(), target.toPath())
            return target
        } catch (e: FileAlreadyExistsException) {
            check(sameContent(target, temp)) {
                "refusing to overwrite existing file with different content: ${target.path}"
            }
            return target
        }
    }

    /** 长度先挡一道（廉价），再逐块流式比对——两份文件都不整体读进内存，判定逻辑在 :core 有向量测试钉住。 */
    private fun sameContent(a: File, b: File): Boolean {
        if (a.length() != b.length()) return false
        return a.inputStream().use { left -> b.inputStream().use { right -> StreamCompare.contentEquals(left, right) } }
    }

    /** 临时文件清理失败不是证据丢失，但也不能悄悄看不见。`op=`/`path=`/`result=` 键值对格式固定、可 grep。 */
    private fun deleteTemp(file: File) {
        if (!deleteIfPresent(file)) {
            Log.w(TAG, "op=deleteTemp path=${file.path} result=failed")
        }
    }
}
