package nz.myinspection.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import nz.myinspection.core.media.ContentHash
import nz.myinspection.core.media.ImportBounds
import nz.myinspection.core.media.PhotoAssociationRecorder
import nz.myinspection.core.media.PhotoIngest
import nz.myinspection.core.media.PhotoIngestPlan
import nz.myinspection.core.media.PhotoSource
import nz.myinspection.core.media.PhotoTarget

/**
 * 导入路径（卡片上下文包「导入」）：复制原字节到临时私有文件（硬边界：导入=复制，不移动用户原始文件）、
 * **边拷边对原始字节求 SHA-256**（单次读流完成拷贝+摘要，不把整份文件读进内存）→ 去重命中且物理文件仍在
 * 则丢弃临时副本、只建新关联 → 否则先只读边界过预算闸（`inJustDecodeBounds` 不分配像素内存），过闸才
 * 原分辨率解码、按临时副本自身的 EXIF orientation 转正烘焙、编码落到派生路径 → 入库 photo 关联
 * （source=IMPORTED，exif_time_ms 与巡检时间分开存，需求 §5）。临时副本用后即删，中间位图恒在 finally 回收。
 *
 * **不做有损降采样**：证据分辨率决策属消费端（卡片上下文包「缩略图交给消费端按需降采样」）；装不下的
 * 文件出路是具名拒绝，不是静默压小。
 *
 * SAF 选文件（把用户挑的 Uri 打开成 [sourceStream]）属采集 UI 层；本函数只接一个已打开的流，并负责关闭它。
 */
object PhotoImportPipeline {
    private const val TAG = "PhotoImportPipeline"

    fun ingest(
        sourceStream: InputStream,
        tempDir: File,
        photoId: String,
        mediaRoot: File,
        target: PhotoTarget,
        recorder: PhotoAssociationRecorder,
        activeAssetLookup: (contentHash: String) -> List<String>,
        budgetBytes: Long = PhotoMemoryBudget.transientBytes(),
    ): PhotoIngestOutcome {
        // 存储路径的物业/巡检上下文从 [target] 反查 DB，不由调用方另传——见 resolvePathContext。
        val (propertyId, inspectionId) = recorder.resolvePathContext(target.roomInstanceId)
        // tempFile 的路径在进 try 之前就算好（纯函数、不摸磁盘）：若 use{} 关流时抛异常（此时文件早已
        // 写完并发布到这个路径），finally 仍认得它、能清掉；若改成"use{} 的返回值"，那份已落盘的临时
        // 文件就此永久孤儿。
        val tempFile = MediaFileStore.resolve(tempDir, "$photoId.import.tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(sourceStream, digest).use { digesting ->
                MediaFileStore.copyInto(digesting, tempDir, "$photoId.import.tmp")
            }
            val contentHash = ContentHash.hex(digest.digest())
            val exifTimeMs = PhotoExifReader.readExifTimeMs(tempFile)
            val plan = PhotoIngest.verifyReuseExists(
                PhotoIngest.plan(propertyId, inspectionId, photoId, contentHash, activeAssetLookup(contentHash)),
                propertyId,
                inspectionId,
                photoId,
            ) { relPath -> MediaFileStore.resolve(mediaRoot, relPath).exists() }

            if (plan is PhotoIngestPlan.WriteNewAsset) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(tempFile.path, bounds)
                ImportBounds.check(bounds.outWidth, bounds.outHeight, budgetBytes)
                    .rejectionOrNull()
                    ?.let { return it }

                val orientation = PhotoExifReader.readOrientation(tempFile)
                var decoded: Bitmap? = null
                var baked: Bitmap? = null
                try {
                    // 边界读得出、真解码却回 null：文件在两次读之间被改坏，或格式只有边界可读——具名拒绝，不崩。
                    decoded = BitmapFactory.decodeFile(tempFile.path)
                        ?: return PhotoIngestOutcome.RejectedUndecodable(bounds.outWidth, bounds.outHeight)
                    baked = PhotoOrientationBaker.bake(decoded, orientation)
                    MediaFileStore.writeNewAsset(mediaRoot, plan.relPath, PhotoJpegEncoder.encode(baked))
                } finally {
                    if (baked != null && baked !== decoded) baked.recycle()
                    decoded?.recycle()
                }
            }
            return recorder.recordLanded(plan, photoId, target, PhotoSource.IMPORTED, exifTimeMs, mediaRoot)
        } finally {
            if (!MediaFileStore.deleteIfPresent(tempFile)) {
                Log.w(TAG, "op=deleteImportTemp photoId=$photoId path=${tempFile.path} result=failed")
            }
        }
    }
}
