package nz.myinspection.app.media

import android.graphics.Bitmap
import java.io.File
import nz.myinspection.core.media.ImportBounds
import nz.myinspection.core.media.MediaPaths
import nz.myinspection.core.media.PhotoAssociationRecorder
import nz.myinspection.core.media.PhotoIngest
import nz.myinspection.core.media.PhotoIngestPlan
import nz.myinspection.core.media.PhotoQualityProfileSource
import nz.myinspection.core.media.PhotoSource
import nz.myinspection.core.media.PhotoTarget
import nz.myinspection.core.media.VerifiedAssetWorkflow

/**
 * 相机路径（卡片上下文包「拍摄保存」）：预算闸 → 转正烘焙 → 按冻结档位缩放并 JPEG 重编码 → 对最终 JPEG 求
 * SHA-256（ADR-0003：此后 overlay/PDF 永不再考虑旋转）→ 去重决策 → 新内容才落盘 → 入库 photo 关联
 * （source=CAMERA）。[capturedBitmap] 由调用方解码并持有，本函数只回收自己新分配的中间位图。
 *
 * **预算闸对相机路径同样生效**：源位图始终由调用方持有；非 identity EXIF 烘焙和实际缩小时的 scale 分别可能
 * 分配第二、第三份位图；JPEG 只经过有界输出流。传感器分辨率是硬件上限，但设备当下的堆余量不是常数——高像素
 * 模式 + 内存吃紧时照样会把进程拖垮，故与导入路径同一套判定。
 *
 * [activeAssetLookup] 按本函数内部算出的 contentHash 调用：哈希只在烘焙+编码之后才算得出来，调用方无从预查。
 * [capturedAtMs] 是 CameraX 报告的拍摄时刻（与巡检时间分开入库，需求 §5），无从取得时传 null，不伪造。
 */
object CameraPhotoIngestPipeline {
    fun ingest(
        capturedBitmap: Bitmap,
        exifOrientation: Int,
        photoId: String,
        mediaRoot: File,
        target: PhotoTarget,
        recorder: PhotoAssociationRecorder,
        capturedAtMs: Long?,
        activeAssetLookup: (contentHash: String) -> List<String>,
        qualityProfileSource: PhotoQualityProfileSource,
        budgetBytes: Long = PhotoMemoryBudget.transientBytes(),
    ): PhotoIngestOutcome {
        val qualityProfile = qualityProfileSource.snapshotForNewPhoto()
        ImportBounds.check(
            width = capturedBitmap.width,
            height = capturedBitmap.height,
            budgetBytes = budgetBytes,
            profile = qualityProfile,
            exifOrientation = exifOrientation,
        )
            .rejectionOrNull()
            ?.let { return it }

        // 存储路径的物业/巡检上下文从 [target] 反查 DB，不由调用方另传——见 resolvePathContext。
        val (propertyId, inspectionId) = recorder.resolvePathContext(target.roomInstanceId)

        var baked: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
            val bakedBitmap = PhotoOrientationBaker.bake(capturedBitmap, exifOrientation)
            baked = bakedBitmap
            val scaledBitmap = PhotoBitmapScaler.scaleDown(bakedBitmap, qualityProfile)
            scaled = scaledBitmap
            VerifiedAssetWorkflow.encodeStagePublishRecord(
                target = MediaFileStore.resolve(
                    mediaRoot,
                    MediaPaths.photoRelPath(propertyId, inspectionId, photoId),
                ),
                input = scaledBitmap,
                encoder = PhotoJpegEncoder(qualityProfile),
                plan = { staged ->
                    // 相机路径的 content_hash 是最终缩放 JPEG，不是源 Bitmap 的像素摘要。
                    val contentHash = staged.digest.sha256
                    PhotoIngest.verifyReuseExists(
                        PhotoIngest.plan(propertyId, inspectionId, photoId, contentHash, activeAssetLookup(contentHash)),
                        propertyId,
                        inspectionId,
                        photoId,
                    ) { relPath -> MediaFileStore.resolve(mediaRoot, relPath).exists() }
                },
                shouldPublish = { plan -> plan is PhotoIngestPlan.WriteNewAsset },
                publish = { staged, plan -> MediaFileStore.publishStaged(staged, mediaRoot, plan.relPath) },
                record = { plan ->
                    recorder.recordLanded(plan, photoId, target, PhotoSource.CAMERA, capturedAtMs, mediaRoot)
                },
            )
        } finally {
            if (scaled != null && scaled !== baked) scaled.recycle()
            // [capturedBitmap] 属调用方（可能还要拿去做预览），只回收 bake() 新分配的那份。
            if (baked != null && baked !== capturedBitmap) baked.recycle()
        }
    }
}
