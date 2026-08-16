package nz.myinspection.app.media

import java.io.File
import nz.myinspection.core.media.OrphanFileDeleter

/**
 * [OrphanFileDeleter] 的真实文件系统实现：:core [nz.myinspection.core.media.OrphanedAssetCleanup] 只判定
 * 该删哪些相对路径（FINALIZED 巡检证据结构性永不入选，见其 KDoc），这里把判定结果真正落成磁盘删除。
 * 目标本就不存在也算成功（幂等——两次清理跑到同一条孤儿记录不该报错）。
 *
 * [MediaFileStore.resolve] 会在包含性校验不过时抛 `IllegalArgumentException`（也可能在极端环境下因
 * `canonicalFile` 触发 `IOException`）——本类是一个良好公民的 [OrphanFileDeleter] 实现，自己把这类异常
 * 转成 `false` 而不是让它冒泡出去（[OrphanedAssetCleanup.run] 对注入的 deleter 也有一层兜底，两道防线
 * 独立生效，同本卡「命名空间闸 + 根包含性闸各管各的」一贯做法）。
 */
class PhotoAssetCleanupExecutor(private val mediaRoot: File) : OrphanFileDeleter {
    override fun delete(relPath: String): Boolean = runCatching {
        val target = MediaFileStore.resolve(mediaRoot, relPath)
        !target.exists() || target.delete()
    }.getOrDefault(false)
}
