package nz.myinspection.app.media

import java.io.File
import nz.myinspection.core.media.ImportBoundsResult
import nz.myinspection.core.media.PhotoAssociationRecorder
import nz.myinspection.core.media.PhotoAssociationResult
import nz.myinspection.core.media.PhotoIngestPlan
import nz.myinspection.core.media.PhotoSource
import nz.myinspection.core.media.PhotoTarget

/**
 * 两条 ingest 管线（[CameraPhotoIngestPipeline] / [PhotoImportPipeline]）的共同结果域：要么字节落定 +
 * 关联入库，要么给出一个具名的拒绝。**拒绝分两类可重试性**（CLAUDE.md「错误分 retryable/non-retryable」），
 * 见各成员的 [retryable]；拒绝态的 UX 属 T2-CAPTURE-UI 的导入流程，本层只把决策点显式暴露出来。
 */
sealed interface PhotoIngestOutcome {
    /** 同样的输入重跑是否可能得到不同结果。 */
    val retryable: Boolean

    /** 字节已落定（[reusedExistingAsset] 为真表示复用既有物理文件）且 `photo` 行已入库。 */
    data class Recorded(
        val photoId: String,
        val relPath: String,
        val reusedExistingAsset: Boolean,
        val exifTimeMs: Long?,
    ) : PhotoIngestOutcome {
        override val retryable: Boolean get() = false
    }

    /**
     * 解码+烘焙+编码的峰值内存超出本次预算。**可重试**：预算取自当下的堆余量（`PhotoMemoryBudget`），
     * 内存宽松时同一张图会被接受——这是环境性拒绝，不是这份文件本身的性质。
     */
    data class RejectedTooLarge(val width: Int, val height: Int, val requiredBytes: Long, val budgetBytes: Long) : PhotoIngestOutcome {
        override val retryable: Boolean get() = true
    }

    /** 取不到正的图像边界：非图片、损坏文件，或零尺寸位图。同一份文件重跑结论不变。 */
    data class RejectedUndecodable(val width: Int, val height: Int) : PhotoIngestOutcome {
        override val retryable: Boolean get() = false
    }

    /**
     * 冻结守卫拒绝入库（巡检已 FINALIZED / item 不属于该房间）；本次新落的字节已撤销。
     * 不可重试：巡检定型是单向的，重试同样会被拒。
     */
    data class RejectedByGuard(val relPath: String, val orphanedFileRemains: Boolean) : PhotoIngestOutcome {
        override val retryable: Boolean get() = false
    }
}

/** 边界判定 → 拒绝结果；[ImportBoundsResult.Accepted] 返回 `null`（继续走）。两条管线共用同一套映射。 */
internal fun ImportBoundsResult.rejectionOrNull(): PhotoIngestOutcome? = when (this) {
    ImportBoundsResult.Accepted -> null
    is ImportBoundsResult.Rejected -> PhotoIngestOutcome.RejectedTooLarge(width, height, requiredBytes, budgetBytes)
    is ImportBoundsResult.Undecodable -> PhotoIngestOutcome.RejectedUndecodable(width, height)
}

private fun PhotoAssociationResult.toOutcome(exifTimeMs: Long?): PhotoIngestOutcome = when (this) {
    is PhotoAssociationResult.Recorded -> PhotoIngestOutcome.Recorded(photoId, relPath, reusedExistingAsset, exifTimeMs)
    is PhotoAssociationResult.RejectedByGuard -> PhotoIngestOutcome.RejectedByGuard(relPath, orphanedFileRemains)
}

/**
 * 入库这次 ingest 的 photo 关联并映射成结果。**只在字节已落定之后调用**（顺序契约见
 * `PhotoAssociationRecorder`），补偿动作统一取 [MediaFileStore.discardIn]——两条管线不各写一份。
 */
internal fun PhotoAssociationRecorder.recordLanded(
    plan: PhotoIngestPlan,
    photoId: String,
    target: PhotoTarget,
    source: PhotoSource,
    exifTimeMs: Long?,
    mediaRoot: File,
): PhotoIngestOutcome =
    record(plan, photoId, target, source, exifTimeMs, MediaFileStore.discardIn(mediaRoot)).toOutcome(exifTimeMs)
