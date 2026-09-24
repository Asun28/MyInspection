package nz.myinspection.core.report

import nz.myinspection.core.model.AudioSnapshot
import nz.myinspection.core.model.InspectionItemSnapshot
import nz.myinspection.core.model.InspectionSnapshot
import nz.myinspection.core.model.PhotoSnapshot
import nz.myinspection.core.model.PropertySnapshot
import nz.myinspection.core.model.TemplateSnapshot
import nz.myinspection.core.model.TenancySnapshot

internal object ReportTestFixtures {
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
            StatusDefinition("GOOD", BilingualText("Good", "良好"), BilingualText("No issue observed", "未观察到问题")),
            StatusDefinition("FAIR", BilingualText("Fair", "一般"), BilingualText("Wear is visible", "可见正常损耗")),
            StatusDefinition("POOR", BilingualText("Poor", "较差"), BilingualText("Attention is needed", "需要处理")),
            StatusDefinition(
                "NOT_APPLICABLE",
                BilingualText("Not applicable", "不适用"),
                BilingualText("This item does not apply", "本检查项不适用"),
            ),
        ),
        supplements = listOf(ReportSupplement("S1", "Follow-up inspection requested.")),
        remediations = listOf(
            ReportRemediation("item-poor", Urgency.HIGH, BilingualText("Repair carpet", "修复地毯")),
        ),
    )

    /** The fake measurer's per-line budget. Tests assert against the same number the measurer wraps at. */
    fun charBudget(widthMm: Int): Int = (widthMm / 3).coerceAtLeast(1)

    const val LINE_HEIGHT_MM = 4

    val typography = typographyOf(LINE_HEIGHT_MM, LINE_HEIGHT_MM, LINE_HEIGHT_MM)

    fun typographyOf(titleMm: Int, bodyMm: Int, captionMm: Int): ReportTypography = ReportTypography(
        title = TextStyleProfile(fontSizePt = 2.0, lineHeightMm = titleMm),
        body = TextStyleProfile(fontSizePt = 2.0, lineHeightMm = bodyMm),
        caption = TextStyleProfile(fontSizePt = 2.0, lineHeightMm = captionMm),
    )

    fun measurerOf(lineHeightMm: Int): TextMeasurer = measurerOf(typographyOf(lineHeightMm, lineHeightMm, lineHeightMm))

    fun measurerOf(typography: ReportTypography): TextMeasurer = TextMeasurer { text, language, style, widthMm ->
        measured(text, language, style, widthMm, typography)
    }

    /**
     * A measurer whose line height depends on the style, which is the ordinary case for a Paint-backed
     * measurer: a heading line is taller than a small-print caption line. The uniform measurer above cannot
     * express that difference at all, so a composer precondition that mixes the two styles is invisible to it.
     */
    fun measurerOf(titleMm: Int, bodyMm: Int, captionMm: Int): TextMeasurer =
        measurerOf(typographyOf(titleMm, bodyMm, captionMm))

    fun measured(
        text: String,
        language: TextLanguage,
        style: TextStyle,
        widthMm: Int,
        typography: ReportTypography = this.typography,
    ): MeasuredText {
        return measuredLines(text.chunked(charBudget(widthMm)).ifEmpty { listOf(" ") }, language, style, typography)
    }

    fun measuredLines(
        lines: List<String>,
        language: TextLanguage,
        style: TextStyle,
        typography: ReportTypography = this.typography,
    ): MeasuredText {
        val profile = typography.profileFor(style)
        return MeasuredText(
            lines,
            profile.lineHeightMm,
            TextMetricSnapshot(
                style = style,
                language = language,
                fontRole = typography.roleFor(language),
                fontSizePt = profile.fontSizePt,
                baselineOffsetPt = 8.0,
                glyphTopPt = -8.0,
                glyphBottomPt = 3.0,
            ),
        )
    }

    val measurer = measurerOf(LINE_HEIGHT_MM)
}
