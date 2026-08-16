package nz.myinspection.app.media

import java.io.File
import java.io.InputStream

/**
 * 文件 IO 薄壳：:core 只产出/消费相对路径（[nz.myinspection.core.media.MediaPaths]），根路径由
 * :app 运行时注入（app 私有外部存储根）。本层只做「相对路径 + 根 → 绝对 File」与字节落盘，不判定
 * 该不该落哪、该不该复用——那是 [nz.myinspection.core.media.PhotoIngest] 的决定。
 */
object MediaFileStore {
    fun resolve(root: File, relPath: String): File = File(root, relPath)

    /** 落一份新资产：目录不存在则先建，再整体写入字节（转正烘焙后的 JPEG）。 */
    fun writeNewAsset(root: File, relPath: String, bytes: ByteArray): File {
        val target = resolve(root, relPath)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        return target
    }

    /**
     * 导入=复制，不移动用户原始文件（硬边界）：从来源流读到目标文件，来源保持不动。
     * 调用方负责关闭 [source]（典型来自 ContentResolver.openInputStream，其生命周期由调用方管理）。
     */
    fun copyInto(source: InputStream, root: File, relPath: String): File {
        val target = resolve(root, relPath)
        target.parentFile?.mkdirs()
        target.outputStream().use { out -> source.copyTo(out) }
        return target
    }
}
