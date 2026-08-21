package nz.myinspection.core.media

/**
 * 一份物理照片资产的落盘/关联决策：复用既有物理文件、还是落一份新文件。两支都带落点 [relPath] 与
 * [contentHash]，故调用方（[PhotoAssociationRecorder]）无需分支即可入库。
 */
sealed interface PhotoIngestPlan {
    val relPath: String
    val contentHash: String

    /** 内容已存在：不写字节，只在 [relPath] 上新建一条 photo 关联。 */
    data class ReuseExistingAsset(override val relPath: String, override val contentHash: String) : PhotoIngestPlan

    /** 新内容：调用方把（已烘焙/已复制的）字节写到 [relPath]，再入库。 */
    data class WriteNewAsset(override val relPath: String, override val contentHash: String) : PhotoIngestPlan
}

object PhotoIngest {
    /**
     * [existingActiveRelPaths] = `photo.selectActiveAssetsByPropertyAndContentHash` 的返回（已按 rel_path 升序去重），
     * 取第一条**形状合法且属于 [propertyId]** 的作复用目标。物业路径闸不可省：它让调用契约即使收到错误的
     * 跨物业候选也会退化为新文件，而不是制造无法按单一物业完整备份的共享物理资产。
     * 无可用候选时经 [MediaPaths.photoRelPath] 这唯一派生点算新路径，不手拼。
     */
    fun plan(
        propertyId: String,
        inspectionId: String,
        photoId: String,
        contentHash: String,
        existingActiveRelPaths: List<String>,
    ): PhotoIngestPlan {
        val reuseTarget = existingActiveRelPaths.firstOrNull { MediaPaths.isPhotoRelPathForProperty(it, propertyId) }
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
     * [plan] 只判"该不该复用"、不摸文件系统——物理文件可能已被孤儿清理回收，这种情况下 DB 行说了谎，
     * 继续复用会让这次 ingest 悄悄丢掉字节（关联指向不存在的文件）。[assetExists] 由调用方注入，
     * 只在复用分支被调用；校验不过即退化为新内容。
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
