package nz.myinspection.core.media

import java.io.IOException
import nz.myinspection.core.db.MyInspectionDatabase

/**
 * 单条相对路径的物理删除动作，由调用方注入——:core 不摸文件系统。目标本就不存在也算成功（幂等）。
 *
 * **异常契约**：允许抛 [IOException]（磁盘错误）与 [SecurityException]（权限拒绝），两者被
 * [OrphanedAssetCleanup.run] 捕获并记进 [FailedDeletion.cause]；其余异常（含契约违反与 `Error`）原样冒泡
 * ——那不是"这一条删除失败"，是调用链本身有 bug。
 */
fun interface OrphanFileDeleter {
    fun delete(relPath: String): Boolean
}

/** [cause] 为 `null` = deleter 干净地返回了 `false`；非 `null` = 保留原因，供调用方判断可否重试。 */
data class FailedDeletion(val relPath: String, val cause: Throwable?)

/**
 * [OrphanedAssetCleanup.run] 的结果，三桶分开报告：[deleted] 真删掉的；[failed] 试了没成的；
 * [rejected] **从未尝试**——rel_path 形状不符 [MediaPaths.isPhotoRelPathShape]，判为损坏数据，拒绝物理触碰。
 */
data class CleanupResult(val deleted: List<String>, val failed: List<FailedDeletion>, val rejected: List<String>)

/**
 * 孤儿资产清理用例：读 `photo.orphanedAssets()`，逐条先过命名空间形状闸，过闸的才交给注入的
 * [OrphanFileDeleter]。FINALIZED 巡检证据永不出现在待删列表——由 `photo.softDelete` 的 finalize 只读守卫
 * 结构性保证（见 `Photo.sq`），本用例不重复该判定。
 */
class OrphanedAssetCleanup(private val db: MyInspectionDatabase, private val deleter: OrphanFileDeleter) {
    /**
     * 待删清单（只读，不执行删除）。**在内存里按 rel_path 排序**（L222）：冻结的 `photo.orphanedAssets`
     * 没有 ORDER BY，SQLite 的返回序是查询计划的副产品、不是契约。
     */
    fun pendingDeletions(): List<String> = db.photoQueries.orphanedAssets().executeAsList().sorted()

    /** 执行清理：逐条先判形状、再调用 [deleter]，三种结果分别归入 [CleanupResult]。 */
    fun run(): CleanupResult {
        val deleted = mutableListOf<String>()
        val failed = mutableListOf<FailedDeletion>()
        val rejected = mutableListOf<String>()
        for (relPath in pendingDeletions()) {
            if (!MediaPaths.isPhotoRelPathShape(relPath)) {
                rejected += relPath
                continue
            }
            try {
                if (deleter.delete(relPath)) deleted += relPath else failed += FailedDeletion(relPath, cause = null)
            } catch (e: IOException) {
                failed += FailedDeletion(relPath, cause = e)
            } catch (e: SecurityException) {
                failed += FailedDeletion(relPath, cause = e)
            }
        }
        return CleanupResult(deleted, failed, rejected)
    }
}
