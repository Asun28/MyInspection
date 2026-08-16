package nz.myinspection.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import nz.myinspection.core.media.ContentHash
import nz.myinspection.core.media.PhotoIngest
import nz.myinspection.core.media.PhotoIngestPlan

/** [PhotoImportPipeline.ingest] 结果：去重/落盘决策 + 导入照片各自独立的拍摄时间（需求 §5）。 */
data class PhotoImportResult(val plan: PhotoIngestPlan, val exifTimeMs: Long?)

/**
 * 导入路径编排（卡片上下文包「导入」）：复制原字节到临时私有文件（硬边界：导入=复制，不移动用户原始
 * 文件）、**边拷贝边对原始字节求 SHA-256**（`DigestInputStream` 包一层，单次读流即完成拷贝+摘要，不再
 * 额外把整份文件读进内存二次求哈希——大图片也不会因此撑爆内存）→ 去重命中且物理文件仍在则丢弃临时
 * 副本、只建新关联；未命中（或复用候选已不在磁盘上）则按临时副本自身的 EXIF orientation **限幅解码**、
 * 转正烘焙、编码后落到派生路径，中间位图用完即回收。**最终存储的永远是转正后的版本，无论来源**，
 * 临时副本用后即删。
 *
 * [activeAssetLookup] 同 [CameraPhotoIngestPipeline]：按本函数内部算出的（原始字节）contentHash 调用，
 * 不接调用方预查的结果——原因同上，调用方在拿到本函数产出的哈希前不可能知道该查哪个哈希。
 * SAF 选文件本身（把用户挑的 Uri 打开成 [sourceStream]）属相机/采集 UI 层，非本卡范围
 * （见卡片 `non_goals`：批量导入分配界面）；本函数只接一个已打开的输入流，并负责关闭它。
 */
object PhotoImportPipeline {
    private const val TAG = "PhotoImportPipeline"

    /** 单张巡检证据照片的解码上限（长边像素）：够report/UI 展示与放大查看，不需要保留手机相机原生
     * 4000万像素级分辨率——限幅只影响解码时的内存占用，不是最终存储质量（存储质量由 JPEG q92 决定）。 */
    private const val MAX_DECODE_DIMENSION = 4096

    fun ingest(
        sourceStream: InputStream,
        tempDir: File,
        propertyId: String,
        inspectionId: String,
        photoId: String,
        mediaRoot: File,
        activeAssetLookup: (contentHash: String) -> List<String>,
    ): PhotoImportResult {
        // tempFile 的路径在进 try 之前就算好（纯函数、不摸磁盘）——不依赖 DigestInputStream.use{} 的
        // 返回值。这样即便 use{} 内部关闭 sourceStream 时抛异常（此时 copyInto 早已把文件写完/发布到
        // 这个路径），下面的 finally 仍认得这个路径、能把它清掉；若改成"tempFile = use{...}的返回值"，
        // 关流异常会让整条赋值语句失败，try/finally 都进不去，已落盘的临时文件就此永久孤儿。
        val tempFile = MediaFileStore.resolve(tempDir, "$photoId.import.tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(sourceStream, digest).use { digesting ->
                MediaFileStore.copyInto(digesting, tempDir, "$photoId.import.tmp")
            }
            val contentHash = ContentHash.hex(digest.digest())
            val exifTimeMs = PhotoExifReader.readExifTimeMs(tempFile)
            val candidatePlan = PhotoIngest.plan(propertyId, inspectionId, photoId, contentHash, activeAssetLookup(contentHash))
            val plan = PhotoIngest.verifyReuseExists(candidatePlan, propertyId, inspectionId, photoId) { relPath ->
                MediaFileStore.resolve(mediaRoot, relPath).exists()
            }
            if (plan is PhotoIngestPlan.WriteNewAsset) {
                val orientation = PhotoExifReader.readOrientation(tempFile)
                val decoded = decodeBounded(tempFile)
                val baked = PhotoOrientationBaker.bake(decoded, orientation)
                val bytes = PhotoJpegEncoder.encode(baked)
                // bake() 在 orientation 已正的情况下原样返回 decoded（不新分配）——只回收真正新建的那份，
                // 且不重复回收同一个对象。
                if (baked !== decoded) decoded.recycle()
                baked.recycle()
                MediaFileStore.writeNewAsset(mediaRoot, plan.relPath, bytes)
            }
            return PhotoImportResult(plan, exifTimeMs)
        } finally {
            if (tempFile.exists() && !tempFile.delete()) {
                Log.w(TAG, "op=deleteImportTemp photoId=$photoId path=${tempFile.path} result=failed")
            }
        }
    }

    /**
     * 两遍解码：第一遍只读边界（`inJustDecodeBounds`，不分配像素内存）算出需要的 `inSampleSize`，第二遍
     * 按该采样率真正解码——避免一张远超 [MAX_DECODE_DIMENSION] 的照片在解码那一刻就把全尺寸位图撑进内存
     * （标准 Android 大图解码模式，developer.android.com "Loading Large Bitmaps Efficiently"）。
     */
    private fun decodeBounded(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= MAX_DECODE_DIMENSION || bounds.outHeight / (sampleSize * 2) >= MAX_DECODE_DIMENSION) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.path, decodeOptions)
            ?: error("failed to decode imported image at ${file.path}")
    }
}
