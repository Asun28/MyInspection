package nz.myinspection.core.media

import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.SystemClockMs

/** `photo.source` 的封闭域，与 `Photo.sq` 的 `CHECK (source IN ('CAMERA','IMPORTED'))` 同集。 */
enum class PhotoSource { CAMERA, IMPORTED }

/**
 * 这次关联挂到哪里：[roomInstanceId] 必填（room 级全景照只挂房间），[inspectionItemId] 为空表示全景照。
 * [privacyFlag] = 含租客物品，报告据此排除（需求 §10 / `Photo.sq` 该列注释）。
 */
data class PhotoTarget(
    val roomInstanceId: String,
    val inspectionItemId: String? = null,
    val privacyFlag: Boolean = false,
)

/**
 * 撤销本次 ingest 刚落下的那份字节（补偿动作），由 :app 注入——:core 不摸文件系统。
 * **契约**：目标已不在也算撤销成功（幂等）；撤不掉返回 `false`，不抛异常。
 */
fun interface NewAssetDiscard {
    fun discard(relPath: String): Boolean
}

sealed interface PhotoAssociationResult {
    /** 关联行已落库。[reusedExistingAsset] 为真表示这条关联指向既有物理文件，本次没写新字节。 */
    data class Recorded(val photoId: String, val relPath: String, val reusedExistingAsset: Boolean) : PhotoAssociationResult

    /**
     * 冻结守卫拒绝入库（父巡检已 FINALIZED，或 [PhotoTarget.inspectionItemId] 不属于该 room_instance），
     * 无行落库、本次新落的字节已撤销。[orphanedFileRemains] 为真 = 撤销本身失败，磁盘上留着一份没有任何
     * photo 行引用的文件——`photo.orphanedAssets()` 只找被软删的行，看不见它，只能靠这个返回值告知调用方。
     */
    data class RejectedByGuard(val relPath: String, val orphanedFileRemains: Boolean) : PhotoAssociationResult
}

/**
 * 落盘之后的入库半步（卡片上下文包「落盘 + 入库」）：把一个已执行的 [PhotoIngestPlan] 变成一条 `photo` 行。
 *
 * **调用顺序是契约**：字节先落定、行后写。反过来会让一条指向不存在文件的行短暂可见，而报告/UI 打不开它；
 * 本顺序的代价是"行没写成时字节已在磁盘上"，故写行失败一律触发 [NewAssetDiscard] 补偿。
 *
 * **补偿只针对 [PhotoIngestPlan.WriteNewAsset]**：复用既有资产时那份文件属于**别的**关联，删掉即删掉别人
 * 的证据；那条路径本次没写任何字节，也就没有要撤销的东西。
 */
class PhotoAssociationRecorder(
    private val db: MyInspectionDatabase,
    private val clock: ClockMs = SystemClockMs,
) {
    /**
     * [photoId] 同时是新行主键与 [PhotoIngestPlan.WriteNewAsset] 路径里的文件名段（调用方须传新 UUIDv7）。
     * 守卫之外的异常（主键/唯一索引冲突等）同样先补偿再原样冒泡——那是调用链有 bug 或撞上并发写入，
     * 不该被折叠成一种正常结果；补偿若也失败，用 `addSuppressed` 附上，不静默丢。
     */
    fun record(
        plan: PhotoIngestPlan,
        photoId: String,
        target: PhotoTarget,
        source: PhotoSource,
        exifTimeMs: Long?,
        discard: NewAssetDiscard,
    ): PhotoAssociationResult {
        val now = clock.nowMs()
        val affected = try {
            db.photoQueries.insert(
                id = photoId,
                inspection_item_id = target.inspectionItemId,
                room_instance_id = target.roomInstanceId,
                rel_path = plan.relPath,
                content_hash = plan.contentHash,
                exif_time_ms = exifTimeMs,
                source = source.name,
                privacy_flag = if (target.privacyFlag) 1L else 0L,
                created_at = now,
                updated_at = now,
            ).value
        } catch (e: Exception) {
            if (!compensate(plan, discard)) {
                e.addSuppressed(IllegalStateException("compensation failed, ${plan.relPath} is now untracked"))
            }
            throw e
        }
        if (affected == 1L) {
            return PhotoAssociationResult.Recorded(
                photoId = photoId,
                relPath = plan.relPath,
                reusedExistingAsset = plan is PhotoIngestPlan.ReuseExistingAsset,
            )
        }
        return PhotoAssociationResult.RejectedByGuard(
            relPath = plan.relPath,
            orphanedFileRemains = !compensate(plan, discard),
        )
    }

    /** 撤销本次新落的字节；复用路径没有可撤销的东西，恒报"没留下垃圾"、且**绝不调用** [discard]。 */
    private fun compensate(plan: PhotoIngestPlan, discard: NewAssetDiscard): Boolean = when (plan) {
        is PhotoIngestPlan.WriteNewAsset -> discard.discard(plan.relPath)
        is PhotoIngestPlan.ReuseExistingAsset -> true
    }
}
