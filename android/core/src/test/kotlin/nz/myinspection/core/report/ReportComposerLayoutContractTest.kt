package nz.myinspection.core.report

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The *geometry* of inline evidence and the exact caption text that gets drawn. The golden tree pins block
 * order and placed heights; neither can see where inside a row a picture sits, how tall the picture box is,
 * or what the caption says.
 *
 * Every number here is written out. An assertion whose expected value is one of the composer's own layout
 * constants compares the production value with itself and stays green when that constant is changed to
 * anything at all; `ReportSourcePurityTest` is what keeps such an expected value out of these files, the
 * comments included - a number restated in prose drifts exactly as silently as one restated in code.
 * A4 210x297 mm at a 15 mm margin leaves a 180 mm body; the picture column is the rightmost 40 mm of it.
 */
class ReportComposerLayoutContractTest {
    private val composer = ReportComposer(ReportTestFixtures.measurer, ReportTestFixtures.typography)

    @Test
    fun `item photos are 40mm thumbnails positioned inside the item row, not blocks that follow it`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val row = plan.pages.flatMap { it.blocks }
            .single { (it.content as? ItemRowBlock)?.itemId == "item-poor" }
        val block = row.content as ItemRowBlock

        val thumbnail = block.thumbnails.single()
        assertEquals(ImagePurpose.INLINE, thumbnail.purpose)
        assertEquals(40, thumbnail.widthMm)
        assertEquals(140, thumbnail.xMm)
        assertEquals(0, thumbnail.yMm)
        assertEquals(180, thumbnail.xMm + thumbnail.widthMm, "the picture column must end at the body's edge")
        assertTrue(thumbnail.yMm + thumbnail.heightMm <= row.heightMm, "thumbnail does not fit inside its row")
        // The text column stops 5mm short of the picture column instead of running up to or under it.
        assertEquals(setOf(135), block.textRuns.map { it.widthMm }.toSet(), "the text column is not 135mm wide")
        assertTrue(
            block.textRuns.all { it.xMm + it.widthMm < 140 },
            "item text reaches ${block.textRuns.maxOf { it.xMm + it.widthMm }}mm, into the picture column at 140mm",
        )
        // No page-level inline slot for this photo survives.
        assertTrue(
            plan.pages.flatMap { it.blocks }.none { placed ->
                (placed.content as? ImageSlotBlock)
                    ?.let { it.photoId == thumbnail.photoId && it.purpose == ImagePurpose.INLINE } == true
            },
            "item evidence is still emitted as a page-level block",
        )
    }

    @Test
    fun `an item without photos keeps the full text width`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val block = plan.pages.flatMap { it.blocks }
            .single { (it.content as? ItemRowBlock)?.itemId == "item-good" }.content as ItemRowBlock

        assertTrue(block.thumbnails.isEmpty())
        assertTrue(
            block.textRuns.all { it.widthMm == 180 },
            "a photo-less item is narrowed to ${block.textRuns.map { it.widthMm }.distinct()} by an absent column",
        )
    }

    /**
     * The picture box is a plan number, not something the renderer works out. Deriving it from the first
     * caption run's y works only while a slot has at least one caption line, and a renderer that guessed
     * wrong would paint the caption over the bottom of the photograph.
     *
     * Width and x are part of that box. A full-width slot that quietly narrowed, or a panorama indented
     * inside its own placed block, prints evidence smaller than the page allows without changing any
     * height these assertions already pin.
     */
    @Test
    fun `every slot states its own picture box`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val thumbnail = plan.slots().single { it.photoId == "photo-item" && it.purpose == ImagePurpose.INLINE }
        val panorama = plan.slots().single { it.photoId == "photo-room" && it.purpose == ImagePurpose.INLINE }
        val appendix = plan.slots().first { it.purpose == ImagePurpose.APPENDIX }

        assertEquals(40, thumbnail.imageHeightMm)
        assertEquals(44, panorama.imageHeightMm)
        assertEquals(108, appendix.imageHeightMm)
        // The full-width slots span the whole 180mm body, and the panorama starts at its block's own left
        // edge rather than being indented inside it.
        assertEquals(180, appendix.widthMm, "the appendix picture is narrower than the 180mm body")
        assertEquals(180, panorama.widthMm, "the room panorama is narrower than the 180mm body")
        assertEquals(0, panorama.xMm, "the room panorama is indented inside its own placed block")
        plan.slots().forEach { slot ->
            assertEquals(
                slot.yMm + slot.imageHeightMm,
                slot.textRuns.first().yMm,
                "the caption of ${slot.photoId} does not start where its picture ends",
            )
            assertTrue(slot.imageHeightMm < slot.heightMm, "${slot.photoId} reserves no room for its caption")
        }
    }

    /**
     * Every photo carries its own provenance line: reference, source and capture instant. The instant is
     * rendered from EXIF when the photo has one and from the collection time otherwise, and it is always a
     * fixed-offset ISO-8601 string - a renderer must never be handed raw epoch milliseconds to format.
     */
    @Test
    fun `photo captions render reference, source and a fixed ISO-8601 instant`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)

        // exifTimeMs 1_755_303_000_000 == 2025-08-16T00:10:00Z; the capture time wins over the import time.
        val thumbnail = plan.slots().single { it.photoId == "photo-item" && it.purpose == ImagePurpose.INLINE }
        assertEquals("1.2.1 · camera · 2025-08-16T00:10:00Z", thumbnail.caption())
        assertEquals(1_755_303_000_000L, thumbnail.capturedAt)

        // The room panorama has no EXIF, so its caption falls back to capturedAt 1_755_303_200_000.
        val panorama = plan.slots().single { it.photoId == "photo-room" && it.purpose == ImagePurpose.INLINE }
        assertEquals("1.R.1 · imported · 2025-08-16T00:13:20Z", panorama.caption())

        val appendix = plan.slots().single { it.photoId == "photo-item" && it.purpose == ImagePurpose.APPENDIX }
        assertEquals("1.2.1 · camera · 2025-08-16T00:10:00Z", appendix.caption())

        val expected = mapOf(
            "photo-item" to Triple("1.2.1", "camera", 1_755_303_000_000L),
            "photo-room" to Triple("1.R.1", "imported", 1_755_303_200_000L),
        )
        assertEquals(4, plan.slots().size)
        expected.forEach { (photoId, provenance) ->
            val slots = plan.slots().filter { it.photoId == photoId }
            assertEquals(setOf(ImagePurpose.INLINE, ImagePurpose.APPENDIX), slots.map { it.purpose }.toSet())
            slots.forEach { slot ->
                assertEquals(provenance.first, slot.reference, "$photoId ${slot.purpose} lost its reference")
                assertEquals(provenance.second, slot.source, "$photoId ${slot.purpose} lost its source")
                assertEquals(provenance.third, slot.capturedAt, "$photoId ${slot.purpose} lost its capture instant")
            }
        }
    }

    /**
     * A caption is capped at three measured lines with an explicit marker. The marker *replaces* the end of
     * the last line: appending it to a line the measurer already filled to budget pushes a glyph past the
     * column edge, and the 40 mm thumbnail column ends at the body's right edge, so the overflow lands in
     * the page margin.
     */
    @Test
    fun `an over-long caption is elided within the column it was measured for`() {
        val longReference = "evidence/" + "segment-".repeat(400) + "end.jpg"
        val plan = composer.compose(reportWithItemReference(longReference), Audience.LANDLORD)

        plan.slots().filter { it.photoId == "photo-item" }.forEach { slot ->
            // Structural fields keep the whole reference even though the caption is elided.
            assertEquals(longReference, slot.reference)
            assertEquals(3, slot.textRuns.size, "an elided caption is exactly the three lines the cap allows")
            assertTrue(slot.textRuns.last().text.endsWith("…"), "an elided caption must say so")
            slot.textRuns.forEach { run ->
                assertTrue(
                    run.text.length <= ReportTestFixtures.charBudget(run.widthMm),
                    "caption line '${run.text}' is ${run.text.length} chars in a " +
                        "${ReportTestFixtures.charBudget(run.widthMm)}-char column",
                )
            }
            assertTrue(slot.heightMm <= 257, "an image slot must fit the 257mm page body")
        }
    }

    @Test
    fun `caption elision remeasures a wide marker until the final line fits`() {
        val proportional = weightedMeasurer()
        val plan = ReportComposer(proportional, ReportTestFixtures.typography).compose(
            reportWithItemReference("i".repeat(200)),
            Audience.LANDLORD,
        )

        plan.slots().filter { it.photoId == "photo-item" }.forEach { slot ->
            val finalRun = slot.textRuns.last()
            assertTrue(finalRun.text.endsWith("…"), "the over-long caption has no elision marker")
            assertEquals(
                listOf(finalRun.text),
                proportional.measure(finalRun.text, finalRun.language, finalRun.style, finalRun.widthMm).lines,
                "the elided final line still wraps under the measurer that produced the plan",
            )
        }
    }

    @Test
    fun `caption elision never leaves half of a supplementary code point`() {
        val supplementary = TextMeasurer { text, language, style, _ ->
            when {
                text.contains("supplementary-reference") ->
                    ReportTestFixtures.measuredLines(listOf("first", "second", "third😀", "overflow"), language, style)
                text == "third😀…" -> ReportTestFixtures.measuredLines(listOf("third😀", "…"), language, style)
                text == "third…" -> ReportTestFixtures.measuredLines(listOf("THIRD…"), language, style)
                else -> ReportTestFixtures.measuredLines(listOf(text), language, style)
            }
        }
        val plan = ReportComposer(supplementary, ReportTestFixtures.typography).compose(
            reportWithItemReference("supplementary-reference"),
            Audience.LANDLORD,
        )

        plan.slots().filter { it.photoId == "photo-item" }.forEach { slot ->
            val finalText = slot.textRuns.last().text
            assertEquals("third…", finalText)
            assertTrue(
                finalText.codePoints().allMatch { Character.isValidCodePoint(it) } &&
                    finalText.none { Character.isSurrogate(it) },
                "caption elision left an unpaired UTF-16 surrogate: $finalText",
            )
        }
    }

    // --- fixtures ---

    private fun ImageSlotBlock.caption(): String = textRuns.joinToString("") { it.text }

    private fun DocumentPlan.slots(): List<ImageSlotBlock> = pages.flatMap { it.blocks }.flatMap { placed ->
        when (val content = placed.content) {
            is ImageSlotBlock -> listOf(content)
            is ItemRowBlock -> content.thumbnails
            else -> emptyList()
        }
    }

    /** `reference` is presentation-only and outside the canonical hash domain, so it can vary freely. */
    private fun reportWithItemReference(reference: String): ReportSnapshot {
        val base = ReportTestFixtures.report()
        return base.copy(
            rooms = base.rooms.map { room ->
                room.copy(
                    items = room.items.map { item ->
                        item.copy(photos = item.photos.map { it.copy(reference = reference) })
                    },
                )
            },
        )
    }

    private fun weightedMeasurer(): TextMeasurer = TextMeasurer { text, language, style, widthMm ->
        val capacity = (widthMm / 5).coerceAtLeast(8)
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var used = 0
        text.codePoints().forEach { codePoint ->
            val weight = if (codePoint == 'i'.code) 1 else if (codePoint == '…'.code) 6 else 2
            if (current.isNotEmpty() && used + weight > capacity) {
                lines += current.toString()
                current.clear()
                used = 0
            }
            current.appendCodePoint(codePoint)
            used += weight
        }
        if (current.isNotEmpty()) lines += current.toString()
        ReportTestFixtures.measuredLines(lines.ifEmpty { listOf(" ") }, language, style)
    }

    /* R4 verified 2026-09-18: all 26 compiling mutations killed and sources restored.
     * All 26 compiled (exit 0), then failed (exit 1): 29 named java.lang.AssertionError results in fresh XML.
     * Base: d53cec8c994189f98893f8ac25889bc00b281801.
     * Composer SHA256: C20D2D773EEFD25A2CD149134B45DF059EAB3518F07393CBB935B2C44E625527.
     * Model SHA256: CC121DBBC7AE9AB96CA4DCB0ECF122BFAB3AAAD0ECFEB8FA0BBE51EF28FDC086.
     * Evidence manifest SHA256: 2EF068A821BF48D0932124ECABEC710D32F61BD02DB05A27588EDB0BA666C8F1.
     * Manifest retains mutant bytes, native UTC/logs/XML, unchanged test pins and exact source restoration.
     * E01 raw runs failed these three methods separately with java.lang.AssertionError:
     *   initial disclaimer validates ZH after valid EN;
     *   initial appendix title validates after valid disclaimer and EN title;
     *   ordinary request validates its own metric.
     * E02 raw reserve: height reserve validates its own metric.
     * E03 raw original caption: appendix original caption validates after valid thumbnail and candidates.
     * E04 raw elision failed both methods separately:
     *   rejected elision candidate validates after valid original caption;
     *   successful elision candidate validates after valid rejected candidate.
     * E05 raw footer: footer validates after all body requests.
     * E06 no signed guard / E07 absolute-coordinate delta box: ordinary request validates its own metric.
     * E08 footer strip as line box: footer validates after all body requests.
     * E09 measured line replaces candidate: caption elision never leaves half of a supplementary code point.
     * B01 style / B02 language / B03 font role / B04 font size / B05 line height guard deletion:
     *   composer refuses every profile binding mismatch before emitting runs; each distinct case label was verified.
     * B06 explicit profile ignored: known fixture requests keep their independent language at every entry.
     * B07 wrong omitted default: omitted profile accepts default measurements.
     * L01 EN / L02 ZH / L03 address / L04 original note / L05 tenancy / L06 reserve /
     * L07 photo / L08 elision / L09 footer / L10 API forwarding language corruption:
     *   known fixture requests keep their independent language at every entry; all ten mutations individually verified.
     * E01/E04 include all five named entry results; all 26 before/mutant/restored SHA256 triples were verified.
     * Initial and restored full DoD: report266/e2e6, zero failures/errors/skips; restored fresh XML: 0/24 suites.
     * No compiler error, missed target, invalid control fixture or unobserved entry counts as a successful mutation kill.
     */

    private data class MeasureRequest(val text: String, val language: TextLanguage, val style: TextStyle, val widthMm: Int)

    private val longCaption = "R".repeat(300) + " · camera · 2025-08-16T00:10:00Z"
    private val roomCaption = "1.R.1 · imported · 2025-08-16T00:13:20Z"
    private val requestReport get() = reportWithItemReference("R".repeat(300))

    private val entryCases get() = mapOf(
        "initial disclaimer ZH" to MeasureRequest(REPORT_DISCLAIMER.zh, TextLanguage.ZH, TextStyle.CAPTION, 180),
        "initial appendix ZH" to MeasureRequest("照片附录", TextLanguage.ZH, TextStyle.TITLE, 180),
        "ordinary" to MeasureRequest("12 Aroha Ave, Auckland", TextLanguage.ORIGINAL, TextStyle.TITLE, 180),
        "reserve" to MeasureRequest("… 2 more rows / 另有 2 行见摘要", TextLanguage.NEUTRAL, TextStyle.BODY, 180),
        "thumbnail rejected candidate" to MeasureRequest("R".repeat(13) + "…", TextLanguage.NEUTRAL, TextStyle.CAPTION, 40),
        "thumbnail successful candidate" to MeasureRequest("R".repeat(12) + "…", TextLanguage.NEUTRAL, TextStyle.CAPTION, 40),
        "appendix item original" to MeasureRequest(longCaption, TextLanguage.NEUTRAL, TextStyle.CAPTION, 180),
        "footer" to MeasureRequest("ea9cd02e76bf · 1/6", TextLanguage.NEUTRAL, TextStyle.CAPTION, 180),
    )

    private fun requestComposer(
        trace: MutableList<MeasureRequest>,
        target: MeasureRequest? = null,
        change: (MeasuredText) -> MeasuredText = { it },
    ): ReportComposer = ReportComposer(
        TextMeasurer { text, language, style, width ->
            val request = MeasureRequest(text, language, style, width)
            trace += request
            val measured = ReportTestFixtures.measured(text, language, style, width)
            if (request == target && trace.count { it == request } == 1) {
                change(measured)
            } else measured
        },
        ReportTestFixtures.typography,
    )

    private fun successfulRequestTrace(): List<MeasureRequest> {
        val trace = mutableListOf<MeasureRequest>()
        val result = runCatching { requestComposer(trace).compose(requestReport, Audience.LANDLORD) }
        assertTrue(result.isSuccess, "legal control failed: ${result.exceptionOrNull()}")
        assertEquals(6, result.getOrThrow().pages.size, "the fixed request fixture must retain six pages")
        return trace
    }

    private fun assertRejectedAt(
        name: String,
        diagnostic: String? = "text metric glyph bottom escapes its line box",
        label: String = name,
        change: (MeasuredText) -> MeasuredText = {
            it.copy(metricSnapshot = it.metricSnapshot.copy(glyphBottomPt = 3.25))
        },
    ) {
        val target = entryCases.getValue(name)
        val control = successfulRequestTrace()
        val stop = control.indexOf(target)
        assertTrue(stop >= 0, "$label: fixed request is absent")
        val trace = mutableListOf<MeasureRequest>()
        var injections = 0
        val failure = assertFailsWith<IllegalArgumentException>(label) {
            requestComposer(trace, target) {
                injections++
                change(it)
            }.compose(requestReport, Audience.LANDLORD)
        }
        assertEquals(1, injections, "$label: poison must be returned exactly once")
        assertEquals(control.take(stop + 1), trace, "$label: rejection occurred at a different entry")
        if (diagnostic != null) {
            assertTrue(failure.message.orEmpty().contains(diagnostic), "$label: ${failure.message}")
        }
    }

    @Test
    fun `initial disclaimer validates ZH after valid EN`() = assertRejectedAt("initial disclaimer ZH")

    @Test
    fun `initial appendix title validates after valid disclaimer and EN title`() = assertRejectedAt("initial appendix ZH")

    @Test
    fun `ordinary request validates its own metric`() = assertRejectedAt("ordinary")

    @Test
    fun `height reserve validates its own metric`() = assertRejectedAt("reserve")

    @Test
    fun `appendix original caption validates after valid thumbnail and candidates`() = assertRejectedAt("appendix item original")

    @Test
    fun `rejected elision candidate validates after valid original caption`() = assertRejectedAt("thumbnail rejected candidate")

    @Test
    fun `successful elision candidate validates after valid rejected candidate`() = assertRejectedAt("thumbnail successful candidate")

    @Test
    fun `footer validates after all body requests`() = assertRejectedAt("footer")


    @Test
    fun `known fixture requests keep their independent language at every entry`() {
        val trace = successfulRequestTrace()
        val expected = entryCases.values + listOf(
            MeasureRequest(REPORT_DISCLAIMER.en, TextLanguage.EN, TextStyle.CAPTION, 180),
            MeasureRequest("Photo appendix", TextLanguage.EN, TextStyle.TITLE, 180),
            MeasureRequest("Wall paint", TextLanguage.EN, TextStyle.BODY, 180),
            MeasureRequest("墙面油漆", TextLanguage.ZH, TextStyle.BODY, 180),
            MeasureRequest("墙面有刮痕，需重新粉刷", TextLanguage.ORIGINAL, TextStyle.BODY, 135),
            MeasureRequest("TENANCY-42", TextLanguage.NEUTRAL, TextStyle.BODY, 180),
            MeasureRequest(roomCaption, TextLanguage.NEUTRAL, TextStyle.CAPTION, 180),
            MeasureRequest(longCaption, TextLanguage.NEUTRAL, TextStyle.CAPTION, 40),
        )
        expected.distinct().forEach { want ->
            val actual = trace.filter { it.text == want.text && it.style == want.style && it.widthMm == want.widthMm }
            assertTrue(actual.isNotEmpty(), "missing fixed request: $want")
            actual.forEach { assertEquals(want, it, "wrong request route for ${want.text}") }
        }
    }

    @Test
    fun `omitted profile accepts default measurements`() {
        val result = runCatching {
            ReportComposer(ReportTestFixtures.measurerOf(ReportTypography.DEFAULT))
                .compose(ReportTestFixtures.report(), Audience.LANDLORD)
        }
        assertTrue(result.isSuccess, "omitted profile rejected DEFAULT requests: ${result.exceptionOrNull()}")
    }

    @Test
    fun `composer refuses every profile binding mismatch before emitting runs`() {
        val cases = listOf(
            "style" to { metric: TextMetricSnapshot ->
                metric.copy(style = if (metric.style == TextStyle.CAPTION) TextStyle.BODY else TextStyle.CAPTION)
            },
            "language" to { metric: TextMetricSnapshot ->
                metric.copy(language = if (metric.language == TextLanguage.EN) TextLanguage.ZH else TextLanguage.EN)
            },
            "font role" to { metric: TextMetricSnapshot ->
                metric.copy(
                    fontRole = if (metric.fontRole == TextFontRole.LATIN_SANS) {
                        TextFontRole.CJK_FALLBACK
                    } else {
                        TextFontRole.LATIN_SANS
                    },
                )
            },
            "font size" to { metric: TextMetricSnapshot -> metric.copy(fontSizePt = 3.0) },
        )

        cases.forEach { (name, change) ->
            assertRejectedAt("ordinary", diagnostic = null, label = "$name mismatch was accepted") {
                it.copy(metricSnapshot = change(it.metricSnapshot))
            }
        }
        assertRejectedAt("ordinary", diagnostic = "line height", label = "line-height mismatch was accepted") {
            it.copy(lineHeightMm = 5)
        }
    }
}
