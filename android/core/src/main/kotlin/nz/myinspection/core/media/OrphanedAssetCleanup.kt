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

/**
 * 孤儿资产清理用例：读 `photo.orphanedAssets()`，对每条待删路径调用注入的 [OrphanFileDeleter] 真正执行。
 * FINALIZED 巡检证据永不出现在待删列表——由 schema 侧 `photo.softDelete` 的 finalize 只读守卫结构性保证
 * （见 `Photo.sq` 该查询注释），本用例不重复该判定。
 */
class OrphanedAssetCleanup(private val db: MyInspectionDatabase, private val deleter: OrphanFileDeleter) {
    /** 待删清单（不执行，供只读展示/日志用）。 */
    fun pendingDeletions(): List<String> = db.photoQueries.orphanedAssets().executeAsList()

    /** 执行清理：对 [pendingDeletions] 逐条调用 [deleter]，返回真正删除成功的路径清单。 */
    fun run(): List<String> = pendingDeletions().filter { deleter.delete(it) }
}
