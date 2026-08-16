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
 * [OrphanedAssetCleanup.run] 的结果，三桶**分开**报告——没有哪一桶允许悄悄吞并到另一桶：
 *  - [deleted]：真删除成功。
 *  - [failed]：过了命名空间闸、但 [OrphanFileDeleter] 报告删除失败（磁盘错误/权限等）。
 *  - [rejected]：**从未尝试删除**——rel_path 形状不符 [MediaPaths.isPhotoRelPathShape]，判定为损坏/串表
 *    数据，直接拒绝物理触碰。调用方据此能区分"本来就没删"/"删了但失败了"/"这条数据本身可疑"三种情形，
 *    分别决定重试、告警还是人工核查。
 */
data class CleanupResult(val deleted: List<String>, val failed: List<String>, val rejected: List<String>)

/**
 * 孤儿资产清理用例：读 `photo.orphanedAssets()`，对每条待删路径先过命名空间形状闸
 * （[MediaPaths.isPhotoRelPathShape]），过闸的才调用注入的 [OrphanFileDeleter] 真正执行。
 * FINALIZED 巡检证据永不出现在待删列表——由 schema 侧 `photo.softDelete` 的 finalize 只读守卫结构性保证
 * （见 `Photo.sq` 该查询注释），本用例不重复该判定。
 *
 * **形状闸存在的理由**：`photo.rel_path` 列（schema 冻结、不能事后补 CHECK）不保证内容落在
 * `photos/{propertyId}/{inspectionId}/{photoId}.jpg` 这个形状——一行数据损坏或误把别的表的行当 photo
 * 插入，都可能带来一个不属于照片命名空间的路径（如 `audio/x.m4a`，甚至 `.` 直指媒体根）。物理删除动作
 * 一旦真的对着这类路径执行，删掉的可能是仍在用的别的资产。这道闸独立于
 * [nz.myinspection.app.media.MediaFileStore] 的根包含性校验——那道防"逃出根目录"，这道防"落在根目录内
 * 但不该被清理链路碰"，两道闸各管各的、互不替代。
 */
class OrphanedAssetCleanup(private val db: MyInspectionDatabase, private val deleter: OrphanFileDeleter) {
    /** 待删清单（不执行，供只读展示/日志用；未过滤命名空间形状——过滤发生在 [run]）。 */
    fun pendingDeletions(): List<String> = db.photoQueries.orphanedAssets().executeAsList()

    /**
     * 执行清理：对 [pendingDeletions] 逐条先判形状、再调用 [deleter]，三种结果分别归入 [CleanupResult]。
     *
     * **单条 [deleter] 调用抛异常不得中断整批**：[OrphanFileDeleter] 的契约是「返回 Boolean」，但注入的
     * 实现终究是外部代码（真实文件系统 IO），一条路径触发的 IO 异常（权限/损坏路径/磁盘错误）不该让
     * 同一批里排在它后面的、本可正常处理的孤儿全部陪葬——那样 `run()` 会整体抛出而不是如实返回
     * `CleanupResult`，调用方连"已经删了哪些"都读不到，比只报告这一条失败更糟。异常按 `failed` 归类
     * （它确实被尝试过，只是没成功——区别于 `rejected` 那种"压根没尝试"）。
     */
    fun run(): CleanupResult {
        val deleted = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        for (relPath in pendingDeletions()) {
            when {
                !MediaPaths.isPhotoRelPathShape(relPath) -> rejected += relPath
                runCatching { deleter.delete(relPath) }.getOrDefault(false) -> deleted += relPath
                else -> failed += relPath
            }
        }
        return CleanupResult(deleted, failed, rejected)
    }
}
