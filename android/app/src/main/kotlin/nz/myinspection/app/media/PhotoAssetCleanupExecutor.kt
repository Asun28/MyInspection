package nz.myinspection.app.media

import java.io.File
import nz.myinspection.core.media.OrphanFileDeleter

/**
 * [OrphanFileDeleter] 的真实文件系统实现：把 :core 判定出的待删相对路径落成磁盘删除（幂等，见
 * [MediaFileStore.deleteIfPresent]）。异常一律不在此捕获——按 [OrphanFileDeleter] 的契约，环境性异常由
 * `OrphanedAssetCleanup.run` 统一记录原因，契约违反（`resolve` 的包含性校验）该冒泡就冒泡。
 */
class PhotoAssetCleanupExecutor(private val mediaRoot: File) : OrphanFileDeleter {
    override fun delete(relPath: String): Boolean = MediaFileStore.deleteIfPresent(MediaFileStore.resolve(mediaRoot, relPath))
}
