package nz.myinspection.app.media

import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import nz.myinspection.core.media.ContentHash
import nz.myinspection.core.media.PhotoIngest
import nz.myinspection.core.media.PhotoIngestPlan

/** [PhotoImportPipeline.ingest] 结果：去重/落盘决策 + 导入照片各自独立的拍摄时间（需求 §5）。 */
data class PhotoImportResult(val plan: PhotoIngestPlan, val exifTimeMs: Long?)

/**
 * 导入路径编排（卡片上下文包「导入」）：复制原字节到临时私有文件（硬边界：导入=复制，不移动用户原始
 * 文件）、对**原始字节**求 SHA-256（去重按源内容判定，与相机路径喂烘焙后字节不同）→ 去重命中则丢弃
 * 临时副本、只建新关联；未命中则按临时副本自身的 EXIF orientation 转正烘焙、编码后落到派生路径——
 * **最终存储的永远是转正后的版本，无论来源**，临时副本用后即删。
 *
 * SAF 选文件本身（把用户挑的 Uri 打开成 [sourceStream]）属相机/采集 UI 层，非本卡范围
 * （见卡片 `non_goals`：批量导入分配界面）；本函数只接一个已打开的输入流。
 */
object PhotoImportPipeline {
    fun ingest(
        sourceStream: InputStream,
        tempDir: File,
        propertyId: String,
        inspectionId: String,
        photoId: String,
        mediaRoot: File,
        existingActiveRelPaths: List<String>,
    ): PhotoImportResult {
        val tempFile = MediaFileStore.copyInto(sourceStream, tempDir, "$photoId.import.tmp")
        try {
            val originalBytes = tempFile.readBytes()
            val contentHash = ContentHash.sha256Hex(originalBytes)
            val exifTimeMs = PhotoExifReader.readExifTimeMs(tempFile)
            val plan = PhotoIngest.plan(propertyId, inspectionId, photoId, contentHash, existingActiveRelPaths)
            if (plan is PhotoIngestPlan.WriteNewAsset) {
                val orientation = PhotoExifReader.readOrientation(tempFile)
                val decoded = BitmapFactory.decodeFile(tempFile.path)
                    ?: error("failed to decode imported image at ${tempFile.path}")
                val baked = PhotoOrientationBaker.bake(decoded, orientation)
                val bytes = PhotoJpegEncoder.encode(baked)
                MediaFileStore.writeNewAsset(mediaRoot, plan.relPath, bytes)
            }
            return PhotoImportResult(plan, exifTimeMs)
        } finally {
            tempFile.delete()
        }
    }
}
