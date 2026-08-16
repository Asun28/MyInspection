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
}
