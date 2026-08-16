package nz.myinspection.app.media

import java.io.File
import java.io.InputStream
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

    /**
     * 落一份新资产：先写到同目录的临时文件、再原子改名到最终路径——中途被杀掉的写入永远不会以最终文件名
     * 现身（不会有巡检证据行指着一份被截断的 JPEG）。
     */
    fun writeNewAsset(root: File, relPath: String, bytes: ByteArray): File {
        val target = resolve(root, relPath)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp-${System.nanoTime()}")
        temp.writeBytes(bytes)
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return target
    }

    /**
     * 导入=复制，不移动用户原始文件（硬边界）：从来源流读到目标文件（同样走临时文件+改名，防半份文件），
     * 来源保持不动。调用方负责关闭 [source]（典型来自 ContentResolver.openInputStream，其生命周期由调用方管理）。
     */
    fun copyInto(source: InputStream, root: File, relPath: String): File {
        val target = resolve(root, relPath)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp-${System.nanoTime()}")
        temp.outputStream().use { out -> source.copyTo(out) }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return target
    }
}
