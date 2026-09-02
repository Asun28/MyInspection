package nz.myinspection.core.report.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nz.myinspection.core.report.Audience
import nz.myinspection.core.report.BilingualText
import nz.myinspection.core.report.DocumentPlan
import nz.myinspection.core.report.ImagePurpose
import nz.myinspection.core.report.ImageSlotBlock
import nz.myinspection.core.report.ItemRowBlock
import nz.myinspection.core.report.PagePlan
import nz.myinspection.core.report.PlacedBlock
import nz.myinspection.core.report.ReportComposer
import nz.myinspection.core.report.ReportTestFixtures
import nz.myinspection.core.report.TextLanguage
import nz.myinspection.core.report.TextRun
import nz.myinspection.core.report.TextStyle

/**
 * A1, A2 and A4 for the translation itself. The builder is the only place in this card that reads a plan,
 * so every clause about "the plan decides, the renderer draws" is proved or lost here.
 *
 * R4 mutation receipt (2026-09-02). Each mutation was applied alone to the production files below, the whole
 * `nz.myinspection.core.report.*` suite was rerun with --rerun-tasks --no-build-cache, and the file was
 * restored and re-hashed before the next one. 25 of 25 killed, every one by a named failing test rather than
 * by a compiler error. Production SHA-256 this receipt is pinned to:
 *   PdfArtifactPaths.kt        804b5cd4f7b6d09abe5175b6f515cddb7e907b98d35ef42d80a8c1ea3a6ba159
 *   PdfExportQuality.kt        2a229201f42bd073e8295fb209b8d802edbef1403167b2a38f6765f0a7e859a7
 *   PdfImageSampling.kt        83beb7f94c972a1492969da5e72ad1190a2cdc68ba8d0b5c2583da7144267959
 *   PdfRenderProgram.kt        402057b802eb3d6eeb3fccc215aea07a1c4e2b7478d762f3817bf2bac68d1d16
 *   PdfRenderProgramBuilder.kt 79491e2ca6e42d3b0a33da317adcd9520896d2e570fa5655f6343017780201f0
 *
 * | # | A | Mutation | Discriminating failure |
 * | --- | --- | --- | --- |
 * | M1 | A1 | Medium inline dpi 120 -> 128 | `(medium, 120, 160)` vs `(medium, 128, 160)` |
 * | M2 | A1 | DEFAULT = MEDIUM -> HIGH | `expected [MEDIUM] but found [HIGH]` |
 * | M3 | A1 | dpiFor(APPENDIX) returns inlineDpi | `expected [120] but found [96]` |
 * | M4 | A1 | identity swaps dataHash and fingerprint | digest lands in the fingerprint slot |
 * | M5 | A2 | mmToPt drops its rounding term | 297 mm gives 841 not 842; every box shifts |
 * | M6 | A2 | widthPt from the length, not the two edges | `did not land on the page edge expected 595 found 596` |
 * | M7 | A2 | delete the page-containment guard | overhanging block completed instead of throwing |
 * | M8 | A3 | drop the quality from the filename | `insp-0001-landlord.pdf` vs `-landlord-medium.pdf` |
 * | M9 | A3 | drop `!= ".."` from the segment guard | `property segment .. was accepted` |
 * | M10 | A4 | page bound sums instead of maximising | `expected [12000000] but found [12188000]` |
 * | M11 | A4 | page bound minimises instead of maximising | `expected [12000000] but found [188000]` |
 * | M12 | A4 | missing source dimensions default to 1x1 | unbounded picture returned 188000, no throw |
 * | M13 | A4 | drop nested thumbnail captions | the row's caption op is missing from the page |
 * | M14 | A4 | drop nested thumbnails entirely | thumbnail op missing; composed-plan coverage also red |
 * | M15 | A4 | picture box uses slot height, not image height | `heightPt=68` becomes the caption-inclusive height |
 * | M16 | A4 | every slot sampled at the inline dpi | appendix plate bound 12000000 -> 3000000 |
 * | M17 | A4 | inSampleSize compares with `>` not `>=` | `expected [4] but found [2]` |
 * | M18 | A4 | targetPixels floors instead of ceiling | `expected [378] but found [377]` |
 * | M19 | A2 | delete the block-level containment call | a block overhanging with fitting runs passed |
 * | M20 | A2 | drop the negative-coordinate clause | a run at x = -5 mm was converted, not refused |
 * | M21 | A4 | drop targetPixels' positive-length guard | a 0 mm box returned 0 pixels instead of throwing |
 * | M22 | A4 | inSampleSize starts at 2 | an 800x600 source was subsampled below its target |
 * | M23 | A4 | decodedBytes floors the decoded width | `4 * 501 * 376` vs `4 * 500 * 376` |
 * | M24 | A1 | fromStoredValue falls back to LOW | an unknown stored value stopped resolving to Medium |
 * | M25 | A1 | High inline dpi 150 -> 100 | the profiles stopped being ordered by density |
 *
 * Four of these exist because writing the batch, rather than running it, exposed the gaps. M16: nothing
 * pinned that an appendix plate is sampled at the appendix density, and the two densities round to the same
 * decode parameter on most fixtures, so a renderer reading the wrong one passed everything else. M19: nothing
 * distinguished a block that overhangs the page from a run that overhangs it. M24 and M25 came from M2, which
 * in an earlier batch left `an unknown or missing stored value falls back to the default` green - that test
 * named DEFAULT on both sides of the comparison, so it followed the mutation instead of failing it. The
 * expected values are written out now, which is why M2 fails two tests here and one there.
 */
class PdfRenderProgramBuilderTest {
    private val builder = PdfRenderProgramBuilder()
    private val fingerprint = "f".repeat(64)

    // --- A1 -----------------------------------------------------------------------------------------

    /**
     * The clause the four profiles exist to satisfy: a reader of the Low file and a reader of the Extra High
     * file are looking at the same report. Identity and every drawn position, string, style and picture are
     * compared as whole structures, so a field added later is covered without anyone remembering to add it.
     */
    @Test
    fun `all four qualities agree on document identity and on every drawable operation`() {
        val plan = goldenPlan()
        val programs = PdfExportQuality.entries.map { builder.build(plan, "insp-0001", fingerprint, it) }

        assertEquals(1, programs.map { it.identity }.toSet().size, "the four qualities disagree about identity")
        assertEquals(
            1,
            programs.map { it.drawableSemantics() }.toSet().size,
            "quality changed something a reader can see",
        )
        assertEquals(PdfExportQuality.entries, programs.map { it.quality })
    }

    /** The complement: if nothing at all differed, the profiles would be decorative. */
    @Test
    fun `a higher quality asks the decoder for a larger picture than a lower one`() {
        val plan = goldenPlan()
        val low = builder.build(plan, "insp-0001", fingerprint, PdfExportQuality.LOW).imageOps()
        val extraHigh = builder.build(plan, "insp-0001", fingerprint, PdfExportQuality.EXTRA_HIGH).imageOps()

        assertEquals(low.size, extraHigh.size)
        assertTrue(low.isNotEmpty(), "the golden plan must carry pictures for this comparison to mean anything")
        low.zip(extraHigh).forEach { (lowOp, highOp) ->
            assertEquals(lowOp.placement, highOp.placement, "quality moved or resized a picture on the page")
            assertTrue(
                highOp.targetWidthPx > lowOp.targetWidthPx && highOp.targetHeightPx > lowOp.targetHeightPx,
                "Extra High did not request more pixels than Low for ${lowOp.placement.photoId}",
            )
        }
    }

    /**
     * The identity restates what the plan and the projection already decided. No caller can hand the builder
     * a hash or an audience that disagrees with the plan it is rendering, because neither is a parameter.
     */
    @Test
    fun `identity carries the plan's own hash and audience alongside the semantic fingerprint`() {
        val plan = goldenPlan()
        val program = builder.build(plan, "insp-0001", fingerprint, PdfExportQuality.MEDIUM)

        assertEquals(
            PdfDocumentIdentity("insp-0001", Audience.LANDLORD, plan.dataHash, fingerprint),
            program.identity,
        )
    }

    // --- A2 -----------------------------------------------------------------------------------------

    @Test
    fun `an A4 page is 595 by 842 points`() {
        assertEquals(595, PdfGeometry.mmToPt(210))
        assertEquals(842, PdfGeometry.mmToPt(297))
        assertEquals(0, PdfGeometry.mmToPt(0))
        assertEquals(43, PdfGeometry.mmToPt(15))

        val page = builder.build(goldenPlan(), "insp-0001", fingerprint, PdfExportQuality.MEDIUM).pages.first()
        assertEquals(595, page.widthPt)
        assertEquals(842, page.heightPt)
    }

    /**
     * Boxes are converted edge to edge rather than as an origin plus an independently rounded length. Two
     * millimetre boxes that touch therefore still touch in points, and a box that ends exactly at the page
     * edge lands exactly on it instead of one rounded point past it.
     */
    @Test
    fun `a box is converted by its edges, so adjacent boxes still meet and the last one lands on the edge`() {
        val ops = builder.build(edgeToEdgePlan(), "insp-0001", fingerprint, PdfExportQuality.MEDIUM)
            .pages.single().ops.filterIsInstance<PdfTextOp>()

        assertEquals(listOf("left", "right"), ops.map { it.text })
        assertEquals(ops[0].xPt + ops[0].widthPt, ops[1].xPt, "a seam opened between two touching boxes")
        assertEquals(595, ops[1].xPt + ops[1].widthPt, "the right-hand box did not land on the page edge")
    }

    /**
     * Geometry outside the page is a composer defect, and the renderer's job is to say so by name rather
     * than to clip it away where nobody will ever see that a block went missing.
     */
    @Test
    fun `a block reaching past the page is refused by name instead of clipped`() {
        val overhanging = DocumentPlan(
            Audience.LANDLORD,
            DATA_HASH,
            listOf(PagePlan(1, listOf(placedText("tenant's own words", xMm = 200, yMm = 10, widthMm = 20)))),
        )
        val failure = assertFailsWith<IllegalArgumentException> {
            builder.build(overhanging, "insp-0001", fingerprint, PdfExportQuality.MEDIUM)
        }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("ItemRowBlock"), "the message must name the block that escaped: $message")
        assertTrue(message.contains("page 1"), "the message must name the page it escaped from: $message")
        assertFalse(
            message.contains("tenant's own words"),
            "a diagnostic must not quote report content back into a log: $message",
        )
    }

    /**
     * A block can overhang the page while every run inside it still fits. That is still a defect, because
     * the paginator placed the block by its own box, so checking only what gets drawn would let it through.
     */
    @Test
    fun `a block whose own box overhangs is refused even though everything it draws fits`() {
        val plan = DocumentPlan(
            Audience.LANDLORD,
            DATA_HASH,
            listOf(PagePlan(1, listOf(placedText("inside", xMm = 200, yMm = 10, widthMm = 20, runWidthMm = 5)))),
        )
        val failure = assertFailsWith<IllegalArgumentException> {
            builder.build(plan, "insp-0001", fingerprint, PdfExportQuality.MEDIUM)
        }
        assertTrue(failure.message.orEmpty().contains("20x4 mm"), "the block's own box must be the one reported")
    }

    /** Negative geometry is refused for the same reason and at the same place, rather than rounded into place. */
    @Test
    fun `a negative coordinate is refused rather than converted`() {
        val plan = DocumentPlan(
            Audience.LANDLORD,
            DATA_HASH,
            listOf(PagePlan(1, listOf(placedText("left of the page", xMm = 0, yMm = 10, widthMm = 20, runXMm = -5)))),
        )
        val failure = assertFailsWith<IllegalArgumentException> {
            builder.build(plan, "insp-0001", fingerprint, PdfExportQuality.MEDIUM)
        }
        assertTrue(failure.message.orEmpty().contains("negative"), "the diagnostic must say what was wrong")
    }

    // --- A4 -----------------------------------------------------------------------------------------

    /**
     * A thumbnail lives inside an item row, in that row's coordinate space, and its caption is expressed in
     * the same space. Both have to be lifted onto the page, and the caption is the easy one to lose: it is
     * not among the row's own text runs, so a builder that only walks `textRuns` drops it silently.
     */
    @Test
    fun `a nested thumbnail and its caption are both lifted into page coordinates`() {
        val program = builder.build(thumbnailPlan(), "insp-0001", fingerprint, PdfExportQuality.MEDIUM)
        val ops = program.pages.single().ops

        assertEquals(
            listOf(
                PdfTextOp("row", TextLanguage.EN, TextStyle.BODY, xPt = 28, yPt = 57, widthPt = 170, heightPt = 11),
                PdfTextOp(
                    "cap",
                    TextLanguage.NEUTRAL,
                    TextStyle.CAPTION,
                    xPt = 227,
                    yPt = 125,
                    widthPt = 85,
                    heightPt = 11,
                ),
                // 30 x 24 mm is the picture box; the slot is 30 mm tall in total because its caption follows.
                PdfImageOp(
                    PdfImagePlacement("p1", ImagePurpose.INLINE, xPt = 227, yPt = 57, widthPt = 85, heightPt = 68),
                    targetWidthPx = 142,
                    targetHeightPx = 114,
                ),
            ),
            ops,
        )
    }

    /**
     * An appendix plate is the evidence copy and is sampled harder than anything inline at the same profile.
     * Without this, a renderer that read the inline density for every slot would still satisfy every other
     * assertion here, because the two densities usually round to the same decode parameter anyway.
     */
    @Test
    fun `an appendix plate is sampled at the appendix density, not the inline one`() {
        val plate = builder.build(twoPicturePlan(), "insp-0001", fingerprint, PdfExportQuality.MEDIUM)
            .imageOps().single { it.placement.purpose == ImagePurpose.APPENDIX }

        // 180 x 120 mm at the Medium appendix density of 160 dpi. The inline density of 120 dpi would ask
        // for 851 x 567 instead.
        assertEquals(1134, plate.targetWidthPx)
        assertEquals(756, plate.targetHeightPx)
    }

    /**
     * Over a real composed plan: the room panorama, the item thumbnail and both appendix plates. Every
     * picture is drawn once for each purpose it was placed under, and none is drawn twice or dropped.
     */
    @Test
    fun `every placed picture in a composed plan is drawn exactly once`() {
        val plan = ReportComposer(ReportTestFixtures.measurer).compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val drawn = builder.build(plan, "insp-0001", fingerprint, PdfExportQuality.MEDIUM)
            .imageOps().map { it.placement.photoId to it.placement.purpose }

        assertEquals(
            listOf(
                "photo-room" to ImagePurpose.INLINE,
                "photo-item" to ImagePurpose.INLINE,
                "photo-room" to ImagePurpose.APPENDIX,
                "photo-item" to ImagePurpose.APPENDIX,
            ),
            drawn,
        )
        assertEquals(drawn.size, drawn.toSet().size, "a picture was drawn twice under one purpose")
    }

    /**
     * The whole point of starting one page at a time and recycling each bitmap after it is drawn: the page
     * costs the largest single picture, not the total. A bound written as a sum would be describing a
     * renderer that keeps every decoded bitmap alive, which is the behaviour this contract exists to forbid.
     */
    @Test
    fun `a page's decoded byte bound is its largest picture, not the sum of its pictures`() {
        // Both pictures come from one 4000x3000 source. The 30x24 mm thumbnail wants 142x114 px, so it
        // samples at 16 and decodes 250x188 = 188,000 bytes; the 180x120 mm plate wants 1134x756 px, so it
        // samples at 2 and decodes 2000x1500 = 12,000,000 bytes. Their sum, 12,188,000, is the answer a
        // renderer that never recycles would give, and it is the value this assertion exists to reject.
        val sources = mapOf("thumb" to PdfSourcePixels(4000, 3000), "plate" to PdfSourcePixels(4000, 3000))

        listOf(false, true).forEach { plateFirst ->
            val plan = twoPicturePlan(plateFirst)
            val page = builder.build(plan, "insp-0001", fingerprint, PdfExportQuality.MEDIUM).pages.single()
            assertEquals(12_000_000L, page.decodedByteBound(sources), "plateFirst=$plateFirst")
        }
    }

    @Test
    fun `a page with no pictures needs no decode budget`() {
        val page = builder.build(edgeToEdgePlan(), "insp-0001", fingerprint, PdfExportQuality.MEDIUM).pages.single()
        assertEquals(0L, page.decodedByteBound(emptyMap()))
    }

    /** A picture whose source dimensions are unknown cannot be bounded, so it is refused rather than skipped. */
    @Test
    fun `a picture missing from the source dimensions is refused, not treated as free`() {
        val page = builder.build(twoPicturePlan(), "insp-0001", fingerprint, PdfExportQuality.MEDIUM).pages.single()
        val failure = assertFailsWith<IllegalArgumentException> {
            page.decodedByteBound(mapOf("thumb" to PdfSourcePixels(4000, 3000)))
        }
        assertTrue(failure.message.orEmpty().contains("plate"), "the message must name the unbounded picture")
    }

    // --- fixtures -----------------------------------------------------------------------------------

    private fun goldenPlan(): DocumentPlan =
        ReportComposer(ReportTestFixtures.measurer).compose(ReportTestFixtures.report(), Audience.LANDLORD)

    private fun placedText(
        text: String,
        xMm: Int,
        yMm: Int,
        widthMm: Int,
        runWidthMm: Int = widthMm,
        runXMm: Int = 0,
    ): PlacedBlock = PlacedBlock(
        xMm,
        yMm,
        widthMm,
        4,
        ItemRowBlock(
            "item-$text",
            BilingualText(text, text),
            "GOOD",
            null,
            null,
            listOf(TextRun(text, TextLanguage.EN, TextStyle.BODY, runXMm, 0, runWidthMm, 4)),
        ),
    )

    private fun edgeToEdgePlan(): DocumentPlan = DocumentPlan(
        Audience.LANDLORD,
        DATA_HASH,
        listOf(
            PagePlan(
                1,
                listOf(
                    placedText("left", xMm = 0, yMm = 0, widthMm = 105),
                    placedText("right", xMm = 105, yMm = 0, widthMm = 105),
                ),
            ),
        ),
    )

    private fun thumbnailPlan(): DocumentPlan = DocumentPlan(
        Audience.LANDLORD,
        DATA_HASH,
        listOf(PagePlan(1, listOf(itemRowWithThumbnail(photoId = "p1", xMm = 10, yMm = 20)))),
    )

    /** [plateFirst] reverses the block order, so neither "the first picture" nor "the last" can pass as a bound. */
    private fun twoPicturePlan(plateFirst: Boolean = false): DocumentPlan {
        val thumb = itemRowWithThumbnail(photoId = "thumb", xMm = 10, yMm = 20)
        val plate = PlacedBlock(15, 60, 180, 130, appendixSlot("plate"))
        val blocks = if (plateFirst) listOf(plate, thumb) else listOf(thumb, plate)
        return DocumentPlan(Audience.LANDLORD, DATA_HASH, listOf(PagePlan(1, blocks)))
    }

    private fun itemRowWithThumbnail(photoId: String, xMm: Int, yMm: Int): PlacedBlock = PlacedBlock(
        xMm,
        yMm,
        100,
        50,
        ItemRowBlock(
            "item-1",
            BilingualText("Carpet", "地毯"),
            "POOR",
            null,
            null,
            listOf(TextRun("row", TextLanguage.EN, TextStyle.BODY, 0, 0, 60, 4)),
            listOf(
                ImageSlotBlock(
                    photoId = photoId,
                    purpose = ImagePurpose.INLINE,
                    reference = "1.2.1",
                    source = "camera",
                    capturedAt = 1_755_303_100_000L,
                    textRuns = listOf(TextRun("cap", TextLanguage.NEUTRAL, TextStyle.CAPTION, 70, 24, 30, 4)),
                    xMm = 70,
                    yMm = 0,
                    widthMm = 30,
                    imageHeightMm = 24,
                    heightMm = 30,
                ),
            ),
        ),
    )

    private fun appendixSlot(photoId: String): ImageSlotBlock = ImageSlotBlock(
        photoId = photoId,
        purpose = ImagePurpose.APPENDIX,
        reference = "A.1",
        source = "camera",
        capturedAt = 1_755_303_200_000L,
        textRuns = emptyList(),
        xMm = 0,
        yMm = 0,
        widthMm = 180,
        imageHeightMm = 120,
        heightMm = 130,
    )

    private companion object {
        val DATA_HASH = "a".repeat(64)
    }
}
