package nz.myinspection.app.media

import android.util.Log
import java.io.File
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

/**
 * 文件 IO 薄壳：:core 只产出/消费相对路径（[nz.myinspection.core.media.MediaPaths]），根路径由
 * :app 运行时注入（app 私有外部存储根）。本层只做「相对路径 + 根 → 绝对 File」与字节落盘，不判定
 * 该不该落哪、该不该复用——那是 [nz.myinspection.core.media.PhotoIngest] 的决定。
 *
 * [MediaPaths][nz.myinspection.core.media.MediaPaths] 已在派生源头拒绝分隔符/`..`；这里再加一道
 * **根包含性**校验（防的是万一 relPath 不经派生点就直接传进来——如已有 DB 行的 rel_path、或未来某个
 * 调用点手滑绕过派生点），两道闸独立生效。
 */
object MediaFileStore {
    private const val TAG = "MediaFileStore"

    /** @throws IllegalArgumentException 若 [relPath] 解析后逃出了 [root]（路径穿越/绝对路径注入）。 */
    fun resolve(root: File, relPath: String): File {
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, relPath).canonicalFile
        require(candidate == canonicalRoot || candidate.path.startsWith(canonicalRoot.path + File.separator)) {
            "relPath escapes the media root: $relPath"
        }
        return candidate
    }

    /**
     * 落一份新资产：create→write→publish 全程一个 try/finally 管到底——任何一步失败都清掉临时文件，
     * 绝不留孤儿；发布成功后临时文件已不在原路径，[deleteTemp] 是安全的空操作。
     */
    fun writeNewAsset(root: File, relPath: String, bytes: ByteArray): File {
        val target = resolve(root, relPath)
        target.parentFile?.mkdirs()
        val temp = createTempSibling(target)
        try {
            temp.writeBytes(bytes)
            return publish(temp, target)
        } finally {
            deleteTemp(temp)
        }
    }

    /**
     * 导入=复制，不移动用户原始文件（硬边界）：来源保持不动，同样是「一个 try/finally 管到底」的临时
     * 文件生命周期。调用方负责关闭 [source]（典型来自 ContentResolver.openInputStream）。
     */
    fun copyInto(source: InputStream, root: File, relPath: String): File {
        val target = resolve(root, relPath)
        target.parentFile?.mkdirs()
        val temp = createTempSibling(target)
        try {
            temp.outputStream().use { out -> source.copyTo(out) }
            return publish(temp, target)
        } finally {
            deleteTemp(temp)
        }
    }

    /** 临时文件名靠 [File.createTempFile] 取真随机唯一性——不用 `System.nanoTime()`：两次调用理论上可能
     * 落在同一纳秒（分辨率非保证严格递增），`createTempFile` 内部用真随机数兜底、并原子性地新建该文件。 */
    private fun createTempSibling(target: File): File = File.createTempFile("${target.name}-", ".tmp", target.parentFile)

    /**
     * 发布：直接尝试**不带任何 CopyOption** 的 `Files.move`。
     *
     * 这里刻意不用 `StandardCopyOption.ATOMIC_MOVE`——**已用 JDK 17 + Windows/NTFS（本项目唯一
     * 开发/CI 平台）实测验证**：`Files.move(temp, target, ATOMIC_MOVE)` 在目标已存在时**不抛异常、
     * 静默替换**（javadoc 把这一情形标注为「implementation specific」，实测踩坑正是这一支）——那正是
     * 本函数要杜绝的静默覆盖本身，用它反而重新制造问题。而**不带任何选项**的 `Files.move` 在实测中于
     * Windows 上目标已存在时可靠抛出 [FileAlreadyExistsException]（`MoveFileEx` 不带
     * `MOVEFILE_REPLACE_EXISTING` 本身就是一次原子失败，检查与移动之间没有独立窗口——同名文件系统调用，
     * 不是"先 exists() 再 move()"两步）。
     *
     * 目标已存在时捕获该异常，才去比对字节：完全相同 = 同一次写入的幂等重试，直接采用既有文件；不同 =
     * 拒绝覆盖并报错（关键不变量：finalize 后原始条目只读，证据不可被静默替换）。
     */
    private fun publish(temp: File, target: File): File {
        try {
            Files.move(temp.toPath(), target.toPath())
            return target
        } catch (e: FileAlreadyExistsException) {
            check(target.readBytes().contentEquals(temp.readBytes())) {
                "refusing to overwrite existing file with different content: ${target.path}"
            }
            return target
        }
    }

    /** 临时文件清理失败不是证据丢失（缓存目录里多一份 `.tmp`），但也不能悄悄看不见——写 logcat 警告
     * （:app 尚无常驻结构化日志框架，`Log.w` 是零新依赖的最小可见性手段）。 */
    private fun deleteTemp(file: File) {
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "failed to delete temp file ${file.path}")
        }
    }
}
