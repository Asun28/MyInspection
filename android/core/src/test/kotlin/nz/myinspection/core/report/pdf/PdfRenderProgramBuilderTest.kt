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
 * A1, A2 and A3 for the translation itself. The builder is the only place in this card that reads a plan,
 * so every clause about "the plan decides, the renderer draws" is proved or lost here.
 *
 * R4 mutation receipt (2026-09-02). Each mutation was applied alone, the whole `nz.myinspection.core.report.*`
 * suite was rerun with --rerun-tasks --no-build-cache, and the file was restored and re-hashed before the
 * next. 26 of 26 killed, each by a named failing test rather than a compiler error. Pinned production SHA-256:
 *   PdfExportQuality.kt        2a229201f42bd073e8295fb209b8d802edbef1403167b2a38f6765f0a7e859a7
 *   PdfImageSampling.kt        45ee06fb21e623e577f56ee4172bd7ab4fb9203b2e5c87656c16c61e0f882456
 *   PdfRenderProgram.kt        a088665ba1a8342f8c6ada67e38ba06c1d91f8a32158ee6fc218a3d8e5c5e3be
 *   PdfRenderProgramBuilder.kt 7ace4c385df63213f1974911be4ec587600c8ecbc00c2f6035ec209749d1b5a6
 *
 * | # | A | Mutation | Discriminating failure |
 * | --- | --- | --- | --- |
 * | M1 | A1 | Medium inline dpi 120 -> 128 | `(medium, 120, 160)` vs `(medium, 128, 160)` |
 * | M2 | A1 | DEFAULT = MEDIUM -> HIGH | fallback stopped resolving to Medium |
 * | M3 | A1 | dpiFor(APPENDIX) returns inlineDpi | `expected [120] but found [96]` |
 * | M4 | A1 | fromStoredValue falls back to LOW | an unknown stored value stopped resolving to Medium |
 * | M5 | A1 | High inline dpi 150 -> 100 | the profiles stopped being ordered by density |
 * | M6 | A1 | identity swaps dataHash and fingerprint | digest lands in the fingerprint slot |
 * | M7 | A2 | mmToPt drops its rounding term | 297 mm gives 841 not 842; every box shifts |
 * | M8 | A2 | widthPt from the length, not the two edges | `did not land on the page edge expected 595 found 596` |
 * | M9 | A2 | delete the page-containment guard | overhanging block completed instead of throwing |
 * | M10 | A2 | delete the block-level containment call | a block overhanging with fitting runs passed |
 * | M11 | A2 | drop the negative-coordinate clause | a run at x = -5 mm was converted, not refused |
 * | M12 | A2 | far edges summed in Int, not Long | a wrapped coordinate sum read as inside the page |
 * | M13 | A3 | page bound sums instead of maximising | `expected [12000000] but found [12188000]` |
 * | M14 | A3 | page bound minimises instead of maximising | `expected [12000000] but found [188000]` |
 * | M15 | A3 | missing source dimensions default to 1x1 | an unbounded picture returned a number, no throw |
 * | M16 | A3 | drop nested thumbnail captions | the row's caption op is missing from the page |
 * | M17 | A3 | drop nested thumbnails entirely | thumbnail op missing; composed-plan coverage also red |
 * | M18 | A3 | picture box uses slot height, not image height | the caption-inclusive height reached the picture |
 * | M19 | A3 | every slot sampled at the inline dpi | the appendix plate asked for 851x567, not 1134x756 |
 * | M20 | A3 | inSampleSize compares with `>` not `>=` | an Int.MAX source stopped resolving to 2^30 |
 * | M21 | A3 | inSampleSize starts at 2 | an 800x600 source was subsampled below its target |
 * | M22 | A3 | targetPixels floors instead of ceiling | `expected [378] but found [377]` |
 * | M23 | A3 | targetPixels bounds relaxed to `> 0` | a 10001 mm box was accepted instead of refused |
 * | M24 | A3 | decodedBytes floors the decoded width | `4 * 501 * 376` vs `4 * 500 * 376` |
 * | M25 | A3 | drop the byte-cost saturation guard | the bound wrapped negative instead of saturating |
 * | M26 | A3 | drop PdfSourcePixels' positive check | a zero source dimension was accepted |
 *
 * Five of these exist because writing the batch, or reading R3's first round, exposed a gap rather than
 * confirming coverage. M19: nothing pinned that an appendix plate uses the appendix density, and the two
 * densities round to the same decode parameter on most fixtures. M10: nothing distinguished a block that
 * overhangs from a run that overhangs. M12, M23, M25: the arithmetic could wrap for accepted inputs, which
 * R3 caught. M4 and M2 come from an earlier batch where M2 left the fallback test green - that test named
 * DEFAULT on both sides, so it followed the mutation instead of failing it; the values are written out now.
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

    /**
     * Two positive coordinates whose sum overflows an `Int` wrap to a negative number, and a bound written
     * as `xMm + widthMm <= A4_WIDTH_MM` then reads a block far off the page as comfortably inside it. The
     * far edges are summed in `Long` for exactly this case.
     */
    @Test
    fun `a coordinate pair that overflows an Int sum is still refused`() {
        val plan = DocumentPlan(
            Audience.LANDLORD,
            DATA_HASH,
            listOf(PagePlan(1, listOf(placedText("wrapped", xMm = Int.MAX_VALUE - 10, yMm = 10, widthMm = 100)))),
        )
        assertFailsWith<IllegalArgumentException> {
            builder.build(plan, "insp-0001", fingerprint, PdfExportQuality.MEDIUM)
        }
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

    private fun twoPicturePlan(): DocumentPlan = DocumentPlan(
        Audience.LANDLORD,
        DATA_HASH,
        listOf(
            PagePlan(
                1,
                listOf(
                    itemRowWithThumbnail(photoId = "thumb", xMm = 10, yMm = 20),
                    PlacedBlock(15, 60, 180, 130, appendixSlot("plate")),
                ),
            ),
        ),
    )

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
