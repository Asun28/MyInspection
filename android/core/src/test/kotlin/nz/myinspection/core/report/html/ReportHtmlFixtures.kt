package nz.myinspection.core.report.html

import nz.myinspection.core.model.AudioSnapshot
import nz.myinspection.core.model.InspectionItemSnapshot
import nz.myinspection.core.model.InspectionSnapshot
import nz.myinspection.core.model.PhotoSnapshot
import nz.myinspection.core.model.PropertySnapshot
import nz.myinspection.core.model.TemplateSnapshot
import nz.myinspection.core.model.TenancySnapshot
import nz.myinspection.core.report.Audience
import nz.myinspection.core.report.BilingualText
import nz.myinspection.core.report.ReportItem
import nz.myinspection.core.report.ReportOptions
import nz.myinspection.core.report.ReportPhoto
import nz.myinspection.core.report.ReportRemediation
import nz.myinspection.core.report.ReportRoom
import nz.myinspection.core.report.ReportSnapshot
import nz.myinspection.core.report.ReportSupplement
import nz.myinspection.core.report.StatusDefinition
import nz.myinspection.core.report.Urgency
import nz.myinspection.core.report.content.LegacyImportProvenance
import nz.myinspection.core.report.content.ReportContent
import nz.myinspection.core.report.content.ReportContentProjector

/**
 * One report carrying, on purpose, every string this card has to keep out of the wrong document or out of
 * the wrong syntactic context. The sentinels are nonsense words rather than plausible English so that a
 * byte-level absence assertion cannot pass by accident on a report that happens not to use the word.
 */
internal object ReportHtmlFixtures {
    const val WEAR_SENTINEL = "WEARQ7SENTINEL"
    const val REMEDIATION_SENTINEL = "REMEDIATIONQ7SENTINEL"
    const val PRIVATE_PHOTO_SENTINEL = "PRIVATEQ7SENTINEL"

    /** A note as a hostile dictation would arrive: tag, attribute breakout, entity and both quote forms. */
    const val HOSTILE_NOTE = "</td><script>alert('x')</script> \"quoted\" & <img src=x onerror=y> 5 < 6"

    private val goodItem = InspectionItemSnapshot("kitchen.wall.paint", "GOOD", null, null)
    private val poorItem = InspectionItemSnapshot("lounge.carpet", "POOR", HOSTILE_NOTE, WEAR_SENTINEL)
    private val publicItemPhoto = PhotoSnapshot("ph-hash-1", "camera", 1_755_303_000_000L, false)
    private val privateItemPhoto = PhotoSnapshot("ph-hash-2", "imported", null, false)
    private val roomPhoto = PhotoSnapshot("ph-hash-3", "camera", 1_755_303_400_000L, true)

    fun content(
        audience: Audience = Audience.LANDLORD,
        includePrivacyPhotos: Boolean = false,
        provenance: LegacyImportProvenance? = null,
    ): ReportContent = ReportContentProjector()
        .project(report(), audience, ReportOptions(includePrivacyPhotos), provenance)

    fun provenance() = LegacyImportProvenance(
        sourceSha256 = "a".repeat(64),
        normalizedManifestSha256 = "b".repeat(64),
        mappingReceiptSha256 = "c".repeat(64),
        extractorVersion = "extractor-1",
        sourceReportDate = "2026-03-01",
    )

    private fun report() = ReportSnapshot(
        canonical = InspectionSnapshot(
            id = "insp-0001", type = "ROUTINE", tenancyId = "ten-0001",
            scheduledAt = 1_755_302_400_000L, finalizedAt = 1_755_309_600_000L,
            previousInspectionId = null, baselineInspectionId = null,
            property = PropertySnapshot("prop-0001", "12 Aroha Ave & Lane", "RENTAL", false),
            tenancy = TenancySnapshot("ten-0001", 1_704_067_200_000L, null),
            template = TemplateSnapshot("tpl-routine-v3", "ROUTINE", 3, "template-hash-1"),
            items = listOf(goodItem, poorItem),
            photos = listOf(publicItemPhoto, privateItemPhoto, roomPhoto),
            audios = listOf(AudioSnapshot("au-hash-1")),
        ),
        // Syntactic characters in identity and caption data too: without them the escape calls guarding
        // an identity field and a figure caption could both be deleted with every test still green.
        tenancyReference = "TENANCY-42 & <b>",
        rooms = listOf(
            ReportRoom(
                id = "room-kitchen",
                // Both an ampersand and a double quote, because the room label reaches an element and an
                // attribute (an image alternative). Without the quote the two escapers produce identical
                // bytes here and swapping them would go unnoticed.
                label = BilingualText("Kitchen \"A\" & Lounge", "厨房 A 客厅"),
                items = listOf(
                    ReportItem("item-good", goodItem, BilingualText("Wall paint", "墙面油漆")),
                    ReportItem(
                        "item-poor",
                        poorItem,
                        BilingualText("Carpet", "地毯"),
                        photos = listOf(
                            ReportPhoto("photo-public", publicItemPhoto, false, "1.2.1", 1_755_303_100_000L),
                            ReportPhoto("photo-private", privateItemPhoto, true, PRIVATE_PHOTO_SENTINEL, 1_755_303_200_000L),
                        ),
                    ),
                ),
                photos = listOf(ReportPhoto("photo-room", roomPhoto, false, "1.R.1 & <a>", 1_755_303_300_000L)),
            ),
        ),
        // Exactly the ROUTINE status domain; the projection refuses a glossary that does not cover it.
        statusDefinitions = listOf(
            StatusDefinition("GOOD", BilingualText("Good", "良好"), BilingualText("No issue observed", "未观察到问题")),
            StatusDefinition("FAIR", BilingualText("Fair", "一般"), BilingualText("Wear is visible", "可见正常损耗")),
            StatusDefinition("POOR", BilingualText("Poor", "较差"), BilingualText("Attention is needed", "需要处理")),
            StatusDefinition("NOT_APPLICABLE", BilingualText("Not applicable", "不适用"), BilingualText("N/A here", "此处不适用")),
        ),
        supplements = listOf(ReportSupplement("S1", "Follow-up inspection requested.")),
        remediations = listOf(
            ReportRemediation("item-poor", Urgency.HIGH, BilingualText(REMEDIATION_SENTINEL, "修复地毯")),
        ),
    )

    /** A valid JPEG start-of-image marker. The renderer never decodes evidence, it only encodes it. */
    val jpegBytes = byteArrayOf(-1, -40, -1, -32)

    val images = ReportImageSource { _, _ -> EmbeddedImage("image/jpeg", jpegBytes) }

    val noImages = ReportImageSource { _, _ -> null }
}
