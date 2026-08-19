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
import nz.myinspection.core.media.VerifiedAssetWorkflow

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
 * SAF 选文件（把用户挑的 Uri 打开成 [sourceStream]）属采集 UI 层；本函数只接一个已打开的流，并负责关闭
 * 它——**从函数入口就建立所有权**（最外层 `finally` 显式 `close()`，覆盖 [resolvePathContext] 与临时路径
 * 解析这些发生在任何真正读流之前、也可能失败的步骤），不是等到真正开始拷贝时才用 `use{}` 包一层——
 * 那样如果更早的步骤先失败，函数会在流从未进入任何 `use{}`/`try` 的情况下直接抛出，流永远不会被关闭。
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
        var primary: Throwable? = null
        try {
            // 存储路径的物业/巡检上下文从 [target] 反查 DB，不由调用方另传——见 resolvePathContext。
            val (propertyId, inspectionId) = recorder.resolvePathContext(target.roomInstanceId)
            // 每次调用一个独立命名的临时文件（同 MediaFileStore 内部临时文件的唯一性来源
            // File.createTempFile）——同一 photoId 并发重试若共用一个确定性文件名，一次调用的 finally
            // 删除会把另一次仍在读的文件从磁盘上抽走。
            val scratchRelPath = uniqueScratchRelPath(tempDir, photoId)
            val tempFile = MediaFileStore.resolve(tempDir, scratchRelPath)
            var scratchPrimary: Throwable? = null
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                MediaFileStore.copyInto(DigestInputStream(sourceStream, digest), tempDir, scratchRelPath)
                val contentHash = ContentHash.hex(digest.digest())
                val exifTimeMs = PhotoExifReader.readExifTimeMs(tempFile)
                val plan = PhotoIngest.verifyReuseExists(
                    PhotoIngest.plan(propertyId, inspectionId, photoId, contentHash, activeAssetLookup(contentHash)),
                    propertyId,
                    inspectionId,
                    photoId,
                ) { relPath -> MediaFileStore.resolve(mediaRoot, relPath).exists() }

                if (plan is PhotoIngestPlan.WriteNewAsset) {
                    val newAssetPlan = plan
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
                        val decodedBitmap = BitmapFactory.decodeFile(tempFile.path)
                            ?: return PhotoIngestOutcome.RejectedUndecodable(bounds.outWidth, bounds.outHeight)
                        decoded = decodedBitmap
                        val bakedBitmap = PhotoOrientationBaker.bake(decodedBitmap, orientation)
                        baked = bakedBitmap
                        return VerifiedAssetWorkflow.encodeStagePublishRecord(
                            target = MediaFileStore.resolve(mediaRoot, newAssetPlan.relPath),
                            input = bakedBitmap,
                            encoder = PhotoJpegEncoder,
                            // Import dedupe/DB semantics intentionally keep the source-byte hash computed above.
                            plan = { newAssetPlan },
                            shouldPublish = { true },
                            publish = { staged, planned ->
                                MediaFileStore.publishStaged(staged, mediaRoot, planned.relPath)
                            },
                            record = { planned ->
                                recorder.recordLanded(
                                    planned,
                                    photoId,
                                    target,
                                    PhotoSource.IMPORTED,
                                    exifTimeMs,
                                    mediaRoot,
                                )
                            },
                        )
                    } finally {
                        if (baked != null && baked !== decoded) baked.recycle()
                        decoded?.recycle()
                    }
                }
                return recorder.recordLanded(plan, photoId, target, PhotoSource.IMPORTED, exifTimeMs, mediaRoot)
            } catch (failure: Throwable) {
                scratchPrimary = failure
                throw failure
            } finally {
                try {
                    if (!MediaFileStore.deleteIfPresent(tempFile)) {
                        Log.w(TAG, "op=deleteImportTemp photoId=$photoId path=${tempFile.path} result=failed")
                    }
                } catch (cleanupFailure: Throwable) {
                    val failure = scratchPrimary
                    if (failure == null) throw cleanupFailure
                    failure.addSuppressed(cleanupFailure)
                }
            }
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try {
                sourceStream.close()
            } catch (closeFailure: Throwable) {
                val failure = primary
                if (failure == null) throw closeFailure
                failure.addSuppressed(closeFailure)
            }
        }
    }

    /**
     * 借 [File.createTempFile] 的真随机唯一性铸一个**名字**（不是长期持有的文件）：立即建档又立即删除，
     * 只借它的唯一性保证，真正的写入交给 [MediaFileStore.copyInto] 自己的临时文件+发布流程。
     */
    private fun uniqueScratchRelPath(tempDir: File, photoId: String): String {
        tempDir.mkdirs()
        val reserved = File.createTempFile("$photoId-", ".import.tmp", tempDir)
        reserved.delete()
        return reserved.name
    }
}
