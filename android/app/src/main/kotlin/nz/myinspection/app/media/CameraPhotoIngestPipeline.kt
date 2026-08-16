package nz.myinspection.app.media

import android.graphics.Bitmap
import java.io.File
import nz.myinspection.core.media.ContentHash
import nz.myinspection.core.media.PhotoIngest
import nz.myinspection.core.media.PhotoIngestPlan

/**
 * 相机路径编排（卡片上下文包「拍摄保存」）：CameraX 出片（已解码的 [capturedBitmap]，其 EXIF orientation
 * 由调用方读出并传入——CameraX 输出的字节流读取属相机 UI 层，非本卡范围）→ 转正烘焙 → JPEG q92 重编码 →
 * 对**烘焙后字节**求 SHA-256（ADR-0003：此后 overlay/PDF 永不再考虑旋转）→ 去重决策 → 新内容才落盘。
 *
 * [existingActiveRelPaths] 来自调用方对 `photo.selectActiveAssetsByContentHash` 的查询——本卡不含仓储层
 * （DB 读写属 T2-CAPTURE-CORE），这里只接查询结果做去重判定，与 [PhotoIngest.plan] 的既有约定一致。
 */
object CameraPhotoIngestPipeline {
    fun ingest(
        capturedBitmap: Bitmap,
        exifOrientation: Int,
        propertyId: String,
        inspectionId: String,
        photoId: String,
        mediaRoot: File,
        existingActiveRelPaths: List<String>,
    ): PhotoIngestPlan {
        val baked = PhotoOrientationBaker.bake(capturedBitmap, exifOrientation)
        val bytes = PhotoJpegEncoder.encode(baked)
        val contentHash = ContentHash.sha256Hex(bytes)
        val plan = PhotoIngest.plan(propertyId, inspectionId, photoId, contentHash, existingActiveRelPaths)
        if (plan is PhotoIngestPlan.WriteNewAsset) {
            MediaFileStore.writeNewAsset(mediaRoot, plan.relPath, bytes)
        }
        return plan
    }
}
