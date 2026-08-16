package nz.myinspection.app.media

import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 文件 IO 薄壳：:core 只产出/消费相对路径（[nz.myinspection.core.media.MediaPaths]），根路径由
 * :app 运行时注入（app 私有外部存储根）。本层只做「相对路径 + 根 → 绝对 File」与字节落盘，不判定
 * 该不该落哪、该不该复用——那是 [nz.myinspection.core.media.PhotoIngest] 的决定。
 *
 * [MediaPaths] 已在派生源头拒绝分隔符/`..`；这里再加一道**根包含性**校验（防的是万一 relPath 不经
 * [nz.myinspection.core.media.MediaPaths] 就直接传进来——如已有 DB 行的 rel_path、或未来某个调用点
 * 手滑绕过派生点），两道闸独立生效。
 */
object MediaFileStore {
    /** @throws IllegalArgumentException 若 [relPath] 解析后逃出了 [root]（路径穿越/绝对路径注入）。 */
    fun resolve(root: File, relPath: String): File {
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, relPath).canonicalFile
        require(candidate == canonicalRoot || candidate.path.startsWith(canonicalRoot.path + File.separator)) {
            "relPath escapes the media root: $relPath"
        }
        return candidate
    }

    /** 落一份新资产：先写到同目录的临时文件，再交给 [publish] 发布到最终路径。 */
    fun writeNewAsset(root: File, relPath: String, bytes: ByteArray): File {
        val target = resolve(root, relPath)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp-${System.nanoTime()}")
        temp.writeBytes(bytes)
        return publish(temp, target)
    }

    /**
     * 导入=复制，不移动用户原始文件（硬边界）：从来源流读到临时文件、再交给 [publish]；来源保持不动。
     * 调用方负责关闭 [source]（典型来自 ContentResolver.openInputStream，其生命周期由调用方管理）。
     */
    fun copyInto(source: InputStream, root: File, relPath: String): File {
        val target = resolve(root, relPath)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp-${System.nanoTime()}")
        temp.outputStream().use { out -> source.copyTo(out) }
        return publish(temp, target)
    }

    /**
     * 把写完的临时文件发布到最终路径：
     *  - 目标不存在 → 尽量原子改名（`ATOMIC_MOVE` 不受支持的文件系统上显式退化为普通 move——退化后仍是
     *    「先写临时文件再改名」，只是不再保证跨文件系统的原子性，不静默吞掉这个降级）。
     *  - 目标已存在且字节与临时文件**完全相同** → 判定为同一次写入的幂等重试，直接采用既有文件。
     *  - 目标已存在但字节不同 → **拒绝覆盖并报错**：`photoId` 全局唯一（UUIDv7），新资产写入本不该撞到
     *    已有文件；一旦撞上，静默覆盖就可能改写仍被巡检引用、甚至已 FINALIZED 的证据（关键不变量：
     *    finalize 后原始条目只读）。
     *  - 任何一支失败都清掉临时文件，不留孤儿（`finally`：成功改名后临时文件已不在原路径，delete() 是
     *    安全的空操作；未改名成功时则真正需要清掉）。
     */
    private fun publish(temp: File, target: File): File {
        try {
            if (target.exists()) {
                check(target.readBytes().contentEquals(temp.readBytes())) {
                    "refusing to overwrite existing file with different content: ${target.path}"
                }
                return target
            }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath())
            }
            return target
        } finally {
            temp.delete()
        }
    }
}
