package nz.myinspection.app.media

import java.io.File
import nz.myinspection.core.media.OrphanFileDeleter

/**
 * [OrphanFileDeleter] 的真实文件系统实现：:core [nz.myinspection.core.media.OrphanedAssetCleanup] 只判定
 * 该删哪些相对路径（FINALIZED 巡检证据结构性永不入选，见其 KDoc），这里把判定结果真正落成磁盘删除。
 * 目标本就不存在也算成功（幂等——两次清理跑到同一条孤儿记录不该报错）。
 *
 * 不在这里吞异常：[OrphanFileDeleter] 的契约允许实现抛 `IOException`/`SecurityException`（`resolve`
 * 的 `canonicalFile` 在极端环境下可能触发前者；`exists`/`delete` 在受限环境下可能触发后者），由
 * [OrphanedAssetCleanup.run] 统一捕获、保留原因写进 `FailedDeletion.cause`——异常处理只在一处，不重复、
 * 不吞掉调用方本该看到的原因。`resolve` 的包含性校验若失败会抛 `IllegalArgumentException`：对一条已过
 * [nz.myinspection.core.media.MediaPaths.isPhotoRelPathShape] 命名空间闸的路径理论上不可达，属契约违反
 * 而非环境性失败，同样不在此处捕获（该冒泡就冒泡，别把真 bug 悄悄埋成一次"删除失败"）。
 */
class PhotoAssetCleanupExecutor(private val mediaRoot: File) : OrphanFileDeleter {
    override fun delete(relPath: String): Boolean {
        val target = MediaFileStore.resolve(mediaRoot, relPath)
        return !target.exists() || target.delete()
    }
}
