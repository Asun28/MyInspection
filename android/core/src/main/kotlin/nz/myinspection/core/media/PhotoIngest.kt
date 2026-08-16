package nz.myinspection.core.media

/**
 * 一份物理照片资产的落盘/关联决策：给定内容哈希与该哈希当下的活跃资产路径（
 * `photo.selectActiveAssetsByContentHash` 的返回，已按 rel_path 升序去重——同一哈希可能有多个活跃路径，
 * 调用方恒取第一条，见 `Photo.sq` 该查询注释），判定是「复用既有物理文件、只建新关联」还是
 * 「新内容、需要落一份新文件」。新文件路径经 [MediaPaths.photoRelPath] 派生（唯一出口），调用方不再手拼。
 *
 * **复用候选先过命名空间形状闸**（[MediaPaths.isPhotoRelPathShape]）：`photo.rel_path` 列没有 schema
 * 约束保证它长 `photos/{propertyId}/{inspectionId}/{photoId}.jpg` 的样子，一行数据损坏/串表会让不该被
 * 复用的路径混进候选列表——形状不符的候选直接跳过（不当作可复用资产），退化到「新内容」分支，不静默
 * 复用一个来路不明的路径。
 */
sealed interface PhotoIngestPlan {
    /** 内容已存在：不写字节，只在 [relPath] 上新建一条 photo 关联。 */
    data class ReuseExistingAsset(val relPath: String, val contentHash: String) : PhotoIngestPlan

    /** 新内容：调用方把（已烘焙/已复制的）字节写到 [relPath]，再插入 photo 行。 */
    data class WriteNewAsset(val relPath: String, val contentHash: String) : PhotoIngestPlan
}

object PhotoIngest {
    fun plan(
        propertyId: String,
        inspectionId: String,
        photoId: String,
        contentHash: String,
        existingActiveRelPaths: List<String>,
    ): PhotoIngestPlan {
        val reuseTarget = existingActiveRelPaths.firstOrNull(MediaPaths::isPhotoRelPathShape)
        return if (reuseTarget != null) {
            PhotoIngestPlan.ReuseExistingAsset(relPath = reuseTarget, contentHash = contentHash)
        } else {
            PhotoIngestPlan.WriteNewAsset(
                relPath = MediaPaths.photoRelPath(propertyId, inspectionId, photoId),
                contentHash = contentHash,
            )
        }
    }

    /**
     * [plan] 只判定"该不该复用"，不摸文件系统——真正的物理文件可能已经不在了（被孤儿清理回收、或从未
     * 真正落地过的半成品记录），这种情况下 DB 行说了谎，继续当作可复用资产会让这次 ingest 悄悄丢掉字节
     * （关联指向一个不存在的文件，photo 行看着正常，报告/UI 却打不开图）。
     *
     * 本函数在 [plan] 已判定复用之后再补一道存在性校验：[assetExists] 由调用方注入（:core 不摸文件系统，
     * 同 [OrphanFileDeleter] 的注入纪律），只在 [plan] 判定为 [PhotoIngestPlan.ReuseExistingAsset] 时才会
     * 被调用——已经是 [PhotoIngestPlan.WriteNewAsset] 的分支不需要探测任何文件是否存在，不做多余调用。
     * 校验不过时退化为「新内容」，路径仍走 [MediaPaths.photoRelPath] 这唯一派生点重新算，不手拼。
     */
    fun verifyReuseExists(
        plan: PhotoIngestPlan,
        propertyId: String,
        inspectionId: String,
        photoId: String,
        assetExists: (relPath: String) -> Boolean,
    ): PhotoIngestPlan =
        if (plan is PhotoIngestPlan.ReuseExistingAsset && !assetExists(plan.relPath)) {
            PhotoIngestPlan.WriteNewAsset(
                relPath = MediaPaths.photoRelPath(propertyId, inspectionId, photoId),
                contentHash = plan.contentHash,
            )
        } else {
            plan
        }
}
