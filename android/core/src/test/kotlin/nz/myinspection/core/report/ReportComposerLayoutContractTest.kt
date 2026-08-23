package nz.myinspection.core.report

import kotlin.test.Test
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
    private val composer = ReportComposer(ReportTestFixtures.measurer)

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
}
