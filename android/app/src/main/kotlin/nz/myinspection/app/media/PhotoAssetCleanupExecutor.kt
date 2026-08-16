package nz.myinspection.app.media

import java.io.File
import nz.myinspection.core.media.OrphanFileDeleter

/**
 * [OrphanFileDeleter] 的真实文件系统实现：:core [nz.myinspection.core.media.OrphanedAssetCleanup] 只判定
 * 该删哪些相对路径（FINALIZED 巡检证据结构性永不入选，见其 KDoc），这里把判定结果真正落成磁盘删除。
 * 目标本就不存在也算成功（幂等——两次清理跑到同一条孤儿记录不该报错）。
 */
class PhotoAssetCleanupExecutor(private val mediaRoot: File) : OrphanFileDeleter {
    override fun delete(relPath: String): Boolean {
        val target = MediaFileStore.resolve(mediaRoot, relPath)
        return !target.exists() || target.delete()
    }
}
