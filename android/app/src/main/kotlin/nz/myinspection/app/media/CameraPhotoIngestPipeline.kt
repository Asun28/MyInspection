package nz.myinspection.app.media

import android.graphics.Bitmap
import java.io.File
import nz.myinspection.core.media.ContentHash
import nz.myinspection.core.media.PhotoIngest
import nz.myinspection.core.media.PhotoIngestPlan

/**
 * 相机路径编排（卡片上下文包「拍摄保存」）：CameraX 出片（已解码的 [capturedBitmap]，其 EXIF orientation
 * 由调用方读出并传入——CameraX 输出的字节流读取属相机 UI 层，非本卡范围）→ 转正烘焙 → JPEG q92 重编码 →
 * 对**烘焙后字节**求 SHA-256（ADR-0003：此后 overlay/PDF 永不再考虑旋转）→ 去重决策（含复用候选存在性
 * 校验）→ 新内容才落盘。位图解码本身不在本函数控制范围（[capturedBitmap] 已是调用方解码好的产物，其
 * 尺寸限幅属相机/采集 UI 层），本函数只负责回收自己新分配出来的中间位图。
 *
 * [activeAssetLookup] 是对 `photo.selectActiveAssetsByContentHash` 的查询函数，**按本函数内部算出的
 * contentHash 调用**——不能反过来让调用方预先查好再传一份 List：调用方那时还不知道烘焙后字节的哈希是
 * 什么（哈希只在烘焙+编码之后才算得出来），预查等于逼调用方自己重实现一遍烘焙/编码来猜哈希，猜错了
 * `PhotoIngest.plan` 就会拿着牛头不对马嘴的复用候选去判定。本卡不含仓储层（DB 读写属 T2-CAPTURE-CORE），
 * 这里只接一个查询函数，不接查询结果。
 */
object CameraPhotoIngestPipeline {
    fun ingest(
        capturedBitmap: Bitmap,
        exifOrientation: Int,
        propertyId: String,
        inspectionId: String,
        photoId: String,
        mediaRoot: File,
        activeAssetLookup: (contentHash: String) -> List<String>,
    ): PhotoIngestPlan {
        val baked = PhotoOrientationBaker.bake(capturedBitmap, exifOrientation)
        val bytes = PhotoJpegEncoder.encode(baked)
        // baked 若是 bake() 新分配的位图（非原样返回 capturedBitmap），编码完字节即可回收——
        // capturedBitmap 本身不是本函数分配的，调用方仍可能持有它做别的用途（如预览），不在此回收。
        if (baked !== capturedBitmap) baked.recycle()
        val contentHash = ContentHash.sha256Hex(bytes)
        val candidatePlan = PhotoIngest.plan(propertyId, inspectionId, photoId, contentHash, activeAssetLookup(contentHash))
        val plan = PhotoIngest.verifyReuseExists(candidatePlan, propertyId, inspectionId, photoId) { relPath ->
            MediaFileStore.resolve(mediaRoot, relPath).exists()
        }
        if (plan is PhotoIngestPlan.WriteNewAsset) {
            MediaFileStore.writeNewAsset(mediaRoot, plan.relPath, bytes)
        }
        return plan
    }
}
