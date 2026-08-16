package nz.myinspection.core.canon

import java.security.MessageDigest
import java.util.HexFormat
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nz.myinspection.core.model.AudioSnapshot
import nz.myinspection.core.model.InspectionItemSnapshot
import nz.myinspection.core.model.InspectionSnapshot
import nz.myinspection.core.model.PhotoSnapshot
import nz.myinspection.core.model.PropertySnapshot
import nz.myinspection.core.model.SupplementSnapshot
import nz.myinspection.core.model.TemplateSnapshot
import nz.myinspection.core.model.TenancySnapshot

/**
 * 巡检快照 → canonical JSON 的纯函数面（T1-CANON-HASH 卡定的 API 形态）。
 * 输入是 model 层不可变数据类，不依赖 DB；哈希域与排除域见 [InspectionSnapshot] 顶部说明与 ADR-0003。
 * 投影键名一律 snake_case、与任务卡「哈希域」清单逐字对应；可空字段显式序列化为 null
 * （省略 null 键会让「字段缺席」与「字段为空」不可区分，哈希域形状必须唯一）。
 * kotlinx 的可空 put 重载天然给出该语义（null → JsonNull）。
 */
fun canonicalJson(snapshot: InspectionSnapshot): String = CanonicalJson.serialize(snapshot.toJson())

/** SHA-256(UTF-8 bytes) 的小写十六进制——data_hash 进 PDF 页脚自证报告未被事后修改。 */
fun sha256Hex(s: String): String =
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)))

private val SHA256_HEX = Regex("[0-9a-f]{64}")

/**
 * Supplement 哈希链：`chain_hash(n) = SHA-256(canonical(supplement_n) + prev_hash)`，
 * `prev_hash(1) = inspection.data_hash`。写库时机归 T3-FINALIZE，本卡只提供纯函数。
 * `prev` 必须是 64 位小写十六进制——空串/截断/大写在这里 fail-fast，
 * 否则链会静默锚定在错误锚点上，多年后对着 PDF 复验才发现。
 */
fun supplementChainHash(prev: String, s: SupplementSnapshot): String {
    require(SHA256_HEX.matches(prev)) {
        "supplement 链的 prev 必须是 64 位小写十六进制 SHA-256（prev(1) = inspection.data_hash），实得：\"$prev\""
    }
    return sha256Hex(CanonicalJson.serialize(s.toJson()) + prev)
}

private fun InspectionSnapshot.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("type", type)
    put("tenancy_id", tenancyId)
    put("scheduled_at", scheduledAt)
    put("finalized_at", finalizedAt)
    put("previous_inspection_id", previousInspectionId)
    put("baseline_inspection_id", baselineInspectionId)
    put("property", property.toJson())
    put("tenancy", tenancy?.toJson() ?: JsonNull)
    put("template", template.toJson())
    put("items", buildJsonArray { for (item in items) add(item.toJson()) })
    put("photos", buildJsonArray { for (photo in photos) add(photo.toJson()) })
    put("audios", buildJsonArray { for (audio in audios) add(audio.toJson()) })
}

private fun PropertySnapshot.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("address", address)
    put("kind", kind)
    put("is_boarding_house", isBoardingHouse)
}

private fun TenancySnapshot.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("start", startMs)
    put("end", endMs)
}

private fun TemplateSnapshot.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("type", type)
    put("version", version)
    put("content_hash", contentHash)
}

private fun InspectionItemSnapshot.toJson(): JsonObject = buildJsonObject {
    put("stable_id", stableId)
    put("status", status)
    put("note", note)
    put("wear_or_damage", wearOrDamage)
}

private fun PhotoSnapshot.toJson(): JsonObject = buildJsonObject {
    put("content_hash", contentHash)
    put("source", source)
    put("exif_time_ms", exifTimeMs)
    put("is_room_level", isRoomLevel)
}

private fun AudioSnapshot.toJson(): JsonObject = buildJsonObject {
    put("content_hash", contentHash)
}

private fun SupplementSnapshot.toJson(): JsonObject = buildJsonObject {
    put("created_at", createdAt)
    put("text", text)
}
