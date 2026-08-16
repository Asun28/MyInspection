package nz.myinspection.core.media

import nz.myinspection.core.db.MyInspectionDatabase

/**
 * 孤儿资产清理用例：读 `photo.orphanedAssets()`，返回待物理删除的相对路径清单。
 * FINALIZED 巡检证据永不出现在此列表——由 schema 侧 `photo.softDelete` 的 finalize 只读守卫结构性保证
 * （见 `Photo.sq` 该查询注释），本用例不重复该判定。真正的文件系统删除动作留给 :app（:core 不摸文件系统）。
 */
class OrphanedAssetCleanup(private val db: MyInspectionDatabase) {
    fun pendingDeletions(): List<String> = db.photoQueries.orphanedAssets().executeAsList()
}
