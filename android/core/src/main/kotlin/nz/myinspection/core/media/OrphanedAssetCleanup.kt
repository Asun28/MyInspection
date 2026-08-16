package nz.myinspection.core.media

import java.io.IOException
import nz.myinspection.core.db.MyInspectionDatabase

/**
 * 单条相对路径的物理删除动作，由调用方注入——:core 不摸文件系统，但注入点让本用例仍能在 JVM 测试里
 * 验证「该删的都删了、不该删的一次都没碰」，同 [nz.myinspection.core.db.ClockMs] / `Uuid7RandomSource`
 * 的注入纪律。返回是否真删除成功（目标本就不存在也算成功，幂等）。
 *
 * **契约**：实现允许抛 [IOException]（环境性失败，如磁盘错误——[OrphanedAssetCleanup.run] 会捕获并记入
 * [FailedDeletion.cause]）或 [SecurityException]（权限拒绝，同样被捕获记录）；除此之外的异常
 * （含 `IllegalArgumentException` 这类契约违反、以及 `Error`）一律不捕获、原样冒泡——那不是"这一条删除
 * 失败"，是调用链本身有 bug，静默吞掉只会把真问题埋起来。
 */
fun interface OrphanFileDeleter {
    fun delete(relPath: String): Boolean
}

/**
 * 一条删除失败的详情：[cause] 为 `null` 表示 [OrphanFileDeleter] 干净地返回了 `false`（正常的"没删成"
 * 信号，如目标仍被别处引用）；非 `null` 表示 deleter 抛出了 [OrphanFileDeleter] 契约允许的异常之一——
 * 保留原因，供调用方判断可否重试（`IOException` 通常瞬时/环境性、值得重试；`SecurityException` 是权限
 * 问题，重试大概率无用，见 CLAUDE.md「错误分 retryable/non-retryable」）。
 */
data class FailedDeletion(val relPath: String, val cause: Throwable?)

/**
 * [OrphanedAssetCleanup.run] 的结果，三桶**分开**报告——没有哪一桶允许悄悄吞并到另一桶：
 *  - [deleted]：真删除成功。
 *  - [failed]：过了命名空间闸、但删除未成功——[FailedDeletion.cause] 说明是"deleter 报告 false"还是
 *    "deleter 抛出了已知异常类型"，以及具体是哪个异常。
 *  - [rejected]：**从未尝试删除**——rel_path 形状不符 [MediaPaths.isPhotoRelPathShape]，判定为损坏/串表
 *    数据，直接拒绝物理触碰。
 */
data class CleanupResult(val deleted: List<String>, val failed: List<FailedDeletion>, val rejected: List<String>)

/**
 * 孤儿资产清理用例：读 `photo.orphanedAssets()`，对每条待删路径先过命名空间形状闸
 * （[MediaPaths.isPhotoRelPathShape]），过闸的才调用注入的 [OrphanFileDeleter] 真正执行。
 * FINALIZED 巡检证据永不出现在待删列表——由 schema 侧 `photo.softDelete` 的 finalize 只读守卫结构性保证
 * （见 `Photo.sq` 该查询注释），本用例不重复该判定。
 *
 * **形状闸存在的理由**：`photo.rel_path` 列（schema 冻结、不能事后补 CHECK）不保证内容落在
 * `photos/{propertyId}/{inspectionId}/{photoId}.jpg` 这个形状——一行数据损坏或误把别的表的行当 photo
 * 插入，都可能带来一个不属于照片命名空间的路径（如 `audio/x.m4a`，甚至 `.` 直指媒体根）。这道闸独立于
 * [nz.myinspection.app.media.MediaFileStore] 的根包含性校验——那道防"逃出根目录"，这道防"落在根目录内
 * 但不该被清理链路碰"，两道闸各管各的、互不替代。
 */
class OrphanedAssetCleanup(private val db: MyInspectionDatabase, private val deleter: OrphanFileDeleter) {
    /**
     * 待删清单（不执行删除，供只读展示/日志用；不过滤命名空间形状——过滤发生在 [run]）。
     *
     * **按 rel_path 排序（L222）**：`photo.orphanedAssets` 这条冻结 SQL 没有 `ORDER BY`（见 `Photo.sq`
     * 该查询），SQLite 的实际返回序是查询计划的副产品、不是契约，不能假设它稳定。这里在内存里排序，
     * 让调用方（日志/断言/[run]）拿到的清单跨次运行确定性一致——不碰冻结的 `.sq` 文件本身。
     */
    fun pendingDeletions(): List<String> = db.photoQueries.orphanedAssets().executeAsList().sorted()

    /**
     * 执行清理：对 [pendingDeletions]（已排序）逐条先判形状、再调用 [deleter]，三种结果分别归入
     * [CleanupResult]——见 [OrphanFileDeleter] 的异常契约：只捕获 `IOException`/`SecurityException`
     * 并保留原因，其余异常原样冒泡。
     */
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
