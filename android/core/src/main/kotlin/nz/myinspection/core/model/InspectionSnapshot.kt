package nz.myinspection.core.model

/**
 * `T1-CANON-HASH` 的 `canonicalJson(snapshot: InspectionSnapshot): String` 输入形状。字段严格对齐
 * 该卡「哈希域」清单——一个不多一个不少（ADR-0003 明确排除 updated_at/deleted_at、rel_path、UI 态、
 * PDF 元数据、LLM 建议、supplement——supplement 走独立的哈希链锚定，见 [SupplementSnapshot]）：
 *
 * - inspection：id/type/tenancyId/scheduledAt/finalizedAt/previousInspectionId/baselineInspectionId
 * - property 快照：id/address/kind/isBoardingHouse
 * - tenancy 快照：id/start/end（**不含**租客联系方式——保留期清理不得破坏哈希可复验性）
 * - template：id/type/version/contentHash
 * - items[]：stableId/status/note/wearOrDamage，按 **(check_item_def.sort, room_instance_id, stable_id)**
 *   这个**全序**排列。`sort` 单独用不够——同一 stable_id 可合法落在多个 room_instance 上，模板里两个
 *   stable_id 也允许共用一个 sort；并列项顺序一旦无定义，同样的数据会算出不同的 data_hash。
 *   排序由 `inspection_item.selectByInspectionInTemplateOrder` 提供，调用方直接按其返回顺序投影即可
 *   （排序键本身不进快照，同 photos[]/audios[] 用外部 UUID 定序的做法）。
 * - photos[]：contentHash/source/exifTimeMs/是否房间级，按 UUID 序（同上，调用方自行排序）
 * - audios[]：contentHash，按 UUID 序
 *
 * 本卡（T1-SCHEMA-CORE）只定义这份不可变数据形状，不实现序列化/哈希算法本身——那是 T1-CANON-HASH 的
 * 产出，本卡非目标明文排除。`core/model/` 不在 FrozenPaths 里（只有 `sqldelight/` 冻结），T1-CANON-HASH
 * 落地时如发现形状需要微调，这里可以改。
 */
data class InspectionSnapshot(
    val id: String,
    val type: String,
    val tenancyId: String?,
    val scheduledAt: Long,
    val finalizedAt: Long?,
    val previousInspectionId: String?,
    val baselineInspectionId: String?,
    val property: PropertySnapshot,
    val tenancy: TenancySnapshot?,
    val template: TemplateSnapshot,
    val items: List<InspectionItemSnapshot>,
    val photos: List<PhotoSnapshot>,
    val audios: List<AudioSnapshot>,
)

data class PropertySnapshot(
    val id: String,
    val address: String,
    val kind: String,
    val isBoardingHouse: Boolean,
)

/** 租客联系方式（tenant_name/contact）故意不在这里——见 [InspectionSnapshot] 顶部说明。 */
data class TenancySnapshot(
    val id: String,
    val startMs: Long,
    val endMs: Long?,
)

data class TemplateSnapshot(
    val id: String,
    val type: String,
    val version: Long,
    val contentHash: String,
)

data class InspectionItemSnapshot(
    val stableId: String,
    val status: String,
    val note: String?,
    val wearOrDamage: String?,
)

/** `isRoomLevel` = 该照片是否为房间级全景（对应 `photo.inspection_item_id IS NULL`），而非绑定某具体检查项。 */
data class PhotoSnapshot(
    val contentHash: String,
    val source: String,
    val exifTimeMs: Long?,
    val isRoomLevel: Boolean,
)

data class AudioSnapshot(
    val contentHash: String,
)
