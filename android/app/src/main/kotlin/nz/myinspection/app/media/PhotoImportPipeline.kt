package nz.myinspection.app.media

import android.graphics.BitmapFactory
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
 * 额外把整份文件读进内存二次求哈希——大图片也不会因此撑爆内存）→ 去重命中则丢弃临时副本、只建新关联；
 * 未命中则按临时副本自身的 EXIF orientation 转正烘焙、编码后落到派生路径。**最终存储的永远是转正后的
 * 版本，无论来源**，临时副本用后即删（best-effort：删除失败只留一份多余的临时缓存文件，不是证据丢失，
 * 不值得为此把整条导入流程判失败）。
 *
 * [activeAssetLookup] 同 [CameraPhotoIngestPipeline]：按本函数内部算出的（原始字节）contentHash 调用，
 * 不接调用方预查的结果——原因同上，调用方在拿到本函数产出的哈希前不可能知道该查哪个哈希。
 * SAF 选文件本身（把用户挑的 Uri 打开成 [sourceStream]）属相机/采集 UI 层，非本卡范围
 * （见卡片 `non_goals`：批量导入分配界面）；本函数只接一个已打开的输入流，并负责关闭它。
 */
object PhotoImportPipeline {
    fun ingest(
        sourceStream: InputStream,
        tempDir: File,
        propertyId: String,
        inspectionId: String,
        photoId: String,
        mediaRoot: File,
        activeAssetLookup: (contentHash: String) -> List<String>,
    ): PhotoImportResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val tempFile = DigestInputStream(sourceStream, digest).use { digesting ->
            MediaFileStore.copyInto(digesting, tempDir, "$photoId.import.tmp")
        }
        try {
            val contentHash = ContentHash.hex(digest.digest())
            val exifTimeMs = PhotoExifReader.readExifTimeMs(tempFile)
            val plan = PhotoIngest.plan(propertyId, inspectionId, photoId, contentHash, activeAssetLookup(contentHash))
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
