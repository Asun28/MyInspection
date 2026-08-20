package nz.myinspection.core.report

import nz.myinspection.core.model.AudioSnapshot
import nz.myinspection.core.model.InspectionItemSnapshot
import nz.myinspection.core.model.InspectionSnapshot
import nz.myinspection.core.model.PhotoSnapshot
import nz.myinspection.core.model.PropertySnapshot
import nz.myinspection.core.model.TemplateSnapshot
import nz.myinspection.core.model.TenancySnapshot

internal object ReportTestFixtures {
    const val DATA_HASH = "ea9cd02e76bf79ac320df5795e51433b3200eb28900ab8837479a0c15eaf452d"

    private val goodItem = InspectionItemSnapshot(
        stableId = "kitchen.wall.paint",
        status = "GOOD",
        note = null,
        wearOrDamage = null,
    )
    private val poorItem = InspectionItemSnapshot(
        stableId = "lounge.carpet",
        status = "POOR",
        note = "墙面有刮痕，需重新粉刷",
        wearOrDamage = "DAMAGE",
    )
    private val itemPhoto = PhotoSnapshot(
        contentHash = "ph-hash-1",
        source = "camera",
        exifTimeMs = 1_755_303_000_000L,
        isRoomLevel = false,
    )
    private val roomPhoto = PhotoSnapshot(
        contentHash = "ph-hash-2",
        source = "imported",
        exifTimeMs = null,
        isRoomLevel = true,
    )

    /** Same frozen canonical vector as T1-CANON-HASH golden vector 1. */
    fun canonical() = InspectionSnapshot(
        id = "insp-0001",
        type = "ROUTINE",
        tenancyId = "ten-0001",
        scheduledAt = 1_755_302_400_000L,
        finalizedAt = 1_755_309_600_000L,
        previousInspectionId = "insp-0000",
        baselineInspectionId = "insp-base",
        property = PropertySnapshot(
            id = "prop-0001",
            address = "12 Aroha Ave, Auckland",
            kind = "RENTAL",
            isBoardingHouse = false,
        ),
        tenancy = TenancySnapshot(id = "ten-0001", startMs = 1_704_067_200_000L, endMs = null),
        template = TemplateSnapshot(
            id = "tpl-routine-v3",
            type = "ROUTINE",
            version = 3,
            contentHash = "template-hash-1",
        ),
        items = listOf(goodItem, poorItem),
        photos = listOf(itemPhoto, roomPhoto),
        audios = listOf(AudioSnapshot(contentHash = "au-hash-1")),
    )

    fun report(
        itemPhotos: List<ReportPhoto> = listOf(
            ReportPhoto("photo-item", itemPhoto, privacy = false, reference = "1.2.1", capturedAt = 1_755_303_100_000L),
        ),
        roomPhotos: List<ReportPhoto> = listOf(
            ReportPhoto("photo-room", roomPhoto, privacy = false, reference = "1.R.1", capturedAt = 1_755_303_200_000L),
        ),
        canonical: InspectionSnapshot = canonical(),
    ) = ReportSnapshot(
        canonical = canonical,
        tenancyReference = "TENANCY-42",
        rooms = listOf(
            ReportRoom(
                id = "room-kitchen",
                label = BilingualText("Kitchen / Lounge", "厨房 / 客厅"),
                items = listOf(
                    ReportItem("item-good", goodItem, BilingualText("Wall paint", "墙面油漆")),
                    ReportItem(
                        "item-poor",
                        poorItem,
                        BilingualText("Carpet", "地毯"),
                        photos = itemPhotos,
                    ),
                ),
                photos = roomPhotos,
            ),
        ),
        statusDefinitions = listOf(
            StatusDefinition(
                "GOOD",
                BilingualText("Good", "良好"),
                BilingualText("No material issue observed", "未观察到重大问题"),
                adverse = false,
            ),
            StatusDefinition(
                "POOR",
                BilingualText("Poor", "较差"),
                BilingualText("Material attention is needed", "需要重点处理"),
                adverse = true,
            ),
        ),
        supplements = listOf(ReportSupplement("S1", "Follow-up inspection requested.")),
        remediations = listOf(
            ReportRemediation("item-poor", Urgency.HIGH, BilingualText("Repair carpet", "修复地毯")),
        ),
    )

    val measurer = TextMeasurer { text, _, widthMm ->
        val charsPerLine = (widthMm / 3).coerceAtLeast(1)
        ((text.length + charsPerLine - 1) / charsPerLine).coerceAtLeast(1) * 4
    }
}
