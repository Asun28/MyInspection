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

/** 新资产的存储路径上下文，唯一来源是 [PhotoAssociationRecorder.resolvePathContext]（不由调用方自带）。 */
data class PhotoPathContext(val propertyId: String, val inspectionId: String)

/**
 * 撤销本次 ingest 刚落下的那份字节（补偿动作），由 :app 注入——:core 不摸文件系统。
 * **契约**：目标已不在也算撤销成功（幂等）；撤不掉返回 `false`。允许抛文件系统异常
 * （`canonicalFile`/`delete` 的 IOException/SecurityException），[PhotoAssociationRecorder] 一律按
 * "撤销失败"处理——补偿路径不得成为 record 的异常出口。
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
     * 派生存储路径要用的物业/巡检 id，**从 [roomInstanceId] 反查 DB 得到**，不收调用方另给一份。
     *
     * 路径由 (propertyId, inspectionId, photoId) 决定、关联行由 [PhotoTarget] 决定，两者若各自由调用方
     * 传入，一次传错就能把 B 房间的照片写进 A 巡检的目录——文件位置与 DB 归属从此对不上，而两边各自看着
     * 都合法。让唯一权威落在 DB 这一侧，这条错配就不再可表达（同 T1-TEMPLATE-ENGINE 把不变量做进类型的改法）。
     */
    fun resolvePathContext(roomInstanceId: String): PhotoPathContext {
        val room = checkNotNull(db.roomInstanceQueries.selectById(roomInstanceId).executeAsOneOrNull()) {
            "no such room_instance: $roomInstanceId"
        }
        val inspection = checkNotNull(db.inspectionQueries.selectById(room.inspection_id).executeAsOneOrNull()) {
            "room_instance $roomInstanceId references a missing inspection ${room.inspection_id}"
        }
        return PhotoPathContext(propertyId = inspection.property_id, inspectionId = inspection.id)
    }

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
        val affected = try {
            // clock.nowMs() 在 try 内：本函数是"落盘之后的入库半步"（KDoc），字节在调用前已落定——时钟本身
            // 抛出也不能跳过下面的补偿，否则一次时钟故障就留下一份 orphanedAssets() 永远发现不了的文件。
            val now = clock.nowMs()
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
            if (!compensate(plan, photoId, discard)) {
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
            orphanedFileRemains = !compensate(plan, photoId, discard),
        )
    }

    /**
     * 撤销本次新落的字节，返回"没留下无主文件"。三种情况**不删**：
     *  - 复用既有资产：本次没写任何字节，那份文件属于别的关联；
     *  - **[photoId] 自己那行仍活跃、且指向这个 rel_path**：同 photoId 重试时，先到的那次已把行写成、字节
     *    也已发布到同一个路径——rel_path 由 `(propertyId, inspectionId, photoId)` 决定，**不看内容哈希**，
     *    两次不同哈希的重试算出的是**同一个**路径。判据直接 `selectById(photoId)`，**不按内容哈希判活**：
     *    schema 不保证同一 rel_path 下所有行哈希相同，按哈希查会在哈希不同的重试里漏判，把仍被赢家引用的
     *    路径当成"没人挡着"删掉（赢家若已 FINALIZED，删的就是巡检证据）。**安全性不对称、故意偏向漏删**：
     *    查不到活跃同 id 行才视为"未被引用"去尝试撤销，留下的孤儿文件仍可被 `orphanedAssets()` 回收，
     *    误删的 FINALIZED 证据不可恢复；
     *  - **liveness 查询或 [discard] 本身抛出异常**：两者共享同一个 try/catch——文件系统/DB 层的失败都不能
     *    变成 `record` 的异常出口（会顶掉真正的主异常，见 [record] 的 catch 块），一律记为"撤销失败"。
     */
    private fun compensate(plan: PhotoIngestPlan, photoId: String, discard: NewAssetDiscard): Boolean = when (plan) {
        is PhotoIngestPlan.ReuseExistingAsset -> true
        is PhotoIngestPlan.WriteNewAsset -> try {
            val winner = db.photoQueries.selectById(photoId).executeAsOneOrNull()
            if (winner != null && winner.deleted_at == null && winner.rel_path == plan.relPath) {
                true
            } else {
                discard.discard(plan.relPath)
            }
        } catch (e: Exception) {
            false
        }
    }
}
