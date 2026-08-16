package nz.myinspection.core.media

import nz.myinspection.core.db.MyInspectionDatabase

/**
 * 单条相对路径的物理删除动作，由调用方注入——:core 不摸文件系统，但注入点让本用例仍能在 JVM 测试里
 * 验证「该删的都删了、不该删的一次都没碰」，同 [nz.myinspection.core.db.ClockMs] / `Uuid7RandomSource`
 * 的注入纪律。返回是否真删除成功（目标本就不存在也算成功，幂等）。
 */
fun interface OrphanFileDeleter {
    fun delete(relPath: String): Boolean
}

/** [OrphanedAssetCleanup.run] 的结果：成功删除的路径与删除失败的路径**分开**报告——失败绝不能被
 * 悄悄折进"反正不在成功列表里"，否则调用方无从区分"本来就没删"与"删了但失败了"，也没法针对失败的那批重试/告警。 */
data class CleanupResult(val deleted: List<String>, val failed: List<String>)

/**
 * 孤儿资产清理用例：读 `photo.orphanedAssets()`，对每条待删路径调用注入的 [OrphanFileDeleter] 真正执行。
 * FINALIZED 巡检证据永不出现在待删列表——由 schema 侧 `photo.softDelete` 的 finalize 只读守卫结构性保证
 * （见 `Photo.sq` 该查询注释），本用例不重复该判定。
 */
class OrphanedAssetCleanup(private val db: MyInspectionDatabase, private val deleter: OrphanFileDeleter) {
    /** 待删清单（不执行，供只读展示/日志用）。 */
    fun pendingDeletions(): List<String> = db.photoQueries.orphanedAssets().executeAsList()

    /** 执行清理：对 [pendingDeletions] 逐条调用 [deleter]，成功/失败的路径分别归入 [CleanupResult] 两侧。 */
    fun run(): CleanupResult {
        val deleted = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (relPath in pendingDeletions()) {
            if (deleter.delete(relPath)) deleted += relPath else failed += relPath
        }
        return CleanupResult(deleted, failed)
    }
}
