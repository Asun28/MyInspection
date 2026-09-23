package nz.myinspection.app.export.pdf

import java.io.File
import java.util.Collections
import java.util.UUID
import nz.myinspection.core.model.InspectionItemSnapshot
import nz.myinspection.core.model.InspectionSnapshot
import nz.myinspection.core.model.PhotoSnapshot
import nz.myinspection.core.model.PropertySnapshot
import nz.myinspection.core.model.TemplateSnapshot
import nz.myinspection.core.report.Audience
import nz.myinspection.core.report.BilingualText
import nz.myinspection.core.report.ReportContentAdapter
import nz.myinspection.core.report.ReportItem
import nz.myinspection.core.report.ReportOptions
import nz.myinspection.core.report.ReportPhoto
import nz.myinspection.core.report.ReportRoom
import nz.myinspection.core.report.ReportSnapshot
import nz.myinspection.core.report.StatusDefinition
import nz.myinspection.core.report.content.ReportContent

/** The two independently derived digests of the fixed input; they label different objects and never cross-check. */
data class FixedDigests(val nativeDataHash: String, val semanticFingerprint: String)

/** One placed photo: its fixture UUID, composer reference, room/item slot and the verified source file. */
data class FixturePhotoDescriptor(
    val manifestOrdinal: Int,
    val photoId: String,
    val reference: String,
    val roomId: String,
    val itemId: String?,
    val contentHash: String,
    val file: File,
)

/** The fixed report input: the source snapshot, its LANDLORD/default-privacy projection and the photo bindings. */
class FixedReportInput internal constructor(
    val report: ReportSnapshot,
    val content: ReportContent,
    descriptors: List<FixturePhotoDescriptor>,
) {
    /** A read-only copy, so a consumer cannot swap a binding inside the returned input. */
    val descriptors: List<FixturePhotoDescriptor> = Collections.unmodifiableList(ArrayList(descriptors))
}

/**
 * Deterministic real80 report input. Every identity is a fixture-local UUIDv7 minted from one anchor timestamp
 * and a counter (photo counter = 0x1000 + manifest ordinal), so nothing here claims to be a database row.
 * Photos are grouped by manifest category into four fixed rooms: panoramas are room-level in the first room,
 * the other three categories hang off one real Routine v2 item each. Capture times are fixed fallbacks, not EXIF.
 */
object PdfDeviceFixture {
    val EXPECTED = FixedDigests(
        nativeDataHash = "f8573b3252196b7ac36201755a2e6dbb9fcd91b2485899679aa5230c32751023",
        semanticFingerprint = "af17258a955afaa0dc73bab853db8ed9fa2424b043274f31bafa01c974a7287c",
    )
    /** Raw-byte SHA-256 of data/templates/routine-v2.json, the TemplateLoader contentHash definition. */
    const val TEMPLATE_CONTENT_HASH = "fd06639f0b7117b1b3cbbf74a71b19cf0f0cc2ee4e374256ad967ad2bdcfe078"
    private const val ANCHOR_MS = 1789603200000L
    private const val CAPTURE_BASE_MS = 1789599600000L
    private const val FIXTURE_ADDRESS = "PDF fixture only / 仅用于 PDF 测试"

    private class Group(val ordinal: Int, val category: String, val room: BilingualText, val item: Item?) {
        val roomId = uuid(0x100L + ordinal)
        val itemId = item?.let { uuid(0x200L + ordinal) }
    }

    private class Item(val stableId: String, val templateSort: Int, val status: String, val label: BilingualText, val note: String)

    private val GROUPS = listOf(
        Group(1, "room_panorama", BilingualText("Panoramas", "房间全景"), null),
        Group(2, "low_light", BilingualText("Low light", "低光场景"),
            Item("BED-LIGHT-01", 52, "GOOD", BilingualText("Lighting samples", "灯光样本"), "Test fixture only / 仅供测试")),
        Group(3, "high_texture", BilingualText("Fine textures", "精细纹理"),
            Item("HAL-WALL-01", 83, "FAIR", BilingualText("Texture samples", "纹理样本"), "Test texture detail / 测试纹理细节")),
        Group(4, "nameplate", BilingualText("Nameplates", "设备铭牌"),
            Item("GEN-METER-01", 74, "POOR", BilingualText("Nameplate samples", "铭牌样本"), "Test small print / 测试铭牌小字")),
    )

    private val STATUS_DEFINITIONS = listOf(
        StatusDefinition("GOOD", BilingualText("Good", "良好"), BilingualText("No issue observed", "未观察到问题")),
        StatusDefinition("FAIR", BilingualText("Fair", "一般"), BilingualText("Wear is visible", "可见正常损耗")),
        StatusDefinition("POOR", BilingualText("Poor", "较差"), BilingualText("Attention is needed", "需要处理")),
        StatusDefinition("NOT_APPLICABLE", BilingualText("Not applicable", "不适用"), BilingualText("This item does not apply", "本检查项不适用")),
    )

    /** The checked entry: the fixed input must reproduce both frozen digests or it is refused. */
    fun build(fixture: AuthorizedFixture): FixedReportInput =
        buildUnverified(fixture).also { verifyFixedDigests(it.content, EXPECTED) }

    internal fun verifyFixedDigests(content: ReportContent, expected: FixedDigests) {
        PdfFixtureManifest.refuseUnless(content.nativeIntegrity.dataHash == expected.nativeDataHash, "NATIVE-DRIFT",
            "native data_hash ${content.nativeIntegrity.dataHash} is not the fixed ${expected.nativeDataHash}")
        PdfFixtureManifest.refuseUnless(content.semanticFingerprint == expected.semanticFingerprint, "SEMANTIC-DRIFT",
            "semantic fingerprint ${content.semanticFingerprint} is not the fixed ${expected.semanticFingerprint}")
    }

    internal fun buildUnverified(fixture: AuthorizedFixture): FixedReportInput {
        val byCategory = GROUPS.associateBy { it.category }
        val withinGroup = mutableMapOf<Int, Int>()
        val placed = fixture.photos.map { photo ->
            val group = byCategory.getValue(photo.row.category)
            val index = (withinGroup[group.ordinal] ?: 0) + 1
            withinGroup[group.ordinal] = index
            val slot = if (group.item == null) "R" else "1"
            val reference = "${group.ordinal}.$slot." + index.toString().padStart(2, '0')
            val descriptor = FixturePhotoDescriptor(
                photo.ordinal, uuid(0x1000L + photo.ordinal), reference, group.roomId, group.itemId, photo.row.sha256, photo.file,
            )
            val snapshot = PhotoSnapshot(photo.row.sha256, "imported", null, isRoomLevel = group.item == null)
            descriptor to ReportPhoto(descriptor.photoId, snapshot, false, reference, CAPTURE_BASE_MS + photo.ordinal * 1000L)
        }
        val rooms = GROUPS.map { group ->
            val photos = placed.filter { it.first.roomId == group.roomId }.map { it.second }
            val item = group.item
            if (item == null) {
                ReportRoom(group.roomId, group.room, emptyList(), photos)
            } else {
                val snapshot = InspectionItemSnapshot(item.stableId, item.status, item.note, null)
                ReportRoom(group.roomId, group.room, listOf(ReportItem(group.itemId!!, snapshot, item.label, photos)))
            }
        }
        val canonicalItems = GROUPS.mapNotNull { group -> group.item?.let { Triple(it.templateSort, group.roomId, it) } }
            .sortedWith(compareBy({ it.first }, { it.second }, { it.third.stableId }))
            .map { InspectionItemSnapshot(it.third.stableId, it.third.status, it.third.note, null) }
        val canonical = InspectionSnapshot(
            id = uuid(1), type = "ROUTINE", tenancyId = null, scheduledAt = ANCHOR_MS, finalizedAt = ANCHOR_MS + 60_000L,
            previousInspectionId = null, baselineInspectionId = null,
            property = PropertySnapshot(uuid(2), FIXTURE_ADDRESS, "RENTAL", false),
            tenancy = null,
            template = TemplateSnapshot(uuid(3), "ROUTINE", 2L, TEMPLATE_CONTENT_HASH),
            items = canonicalItems,
            photos = placed.map { it.second.snapshot },
            audios = emptyList(),
        )
        val report = ReportSnapshot(canonical, tenancyReference = null, rooms = rooms, statusDefinitions = STATUS_DEFINITIONS)
        val content = ReportContentAdapter().adapt(report, Audience.LANDLORD, ReportOptions(includePrivacyPhotos = false))
        return FixedReportInput(report, content, placed.map { it.first })
    }

    /** UUIDv7 with rand_a = 0 and the counter in rand_b: (anchor << 80) | (7 << 76) | (2 << 62) | counter. */
    private fun uuid(counter: Long): String = UUID((ANCHOR_MS shl 16) or (7L shl 12), (2L shl 62) or counter).toString()
}
