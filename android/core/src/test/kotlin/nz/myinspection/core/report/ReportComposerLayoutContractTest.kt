package nz.myinspection.core.report

import nz.myinspection.core.model.PhotoSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Contracts the renderer relies on but the golden tree alone cannot express: the *geometry* of inline
 * evidence, the exact text that actually gets drawn, and the projection inputs that must be refused.
 *
 * Each of these was previously asserted only indirectly - by block order, by a metadata field, or by a
 * "field is non-blank" check the constructor already guaranteed. An assertion that inspects metadata while
 * the renderer draws something else is not covering the renderer.
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
        assertEquals(ReportComposer.INLINE_THUMB_MM, thumbnail.widthMm)
        assertEquals(ReportComposer.THUMB_COLUMN_X_MM, thumbnail.xMm)
        assertEquals(0, thumbnail.yMm)
        assertTrue(thumbnail.xMm + thumbnail.widthMm <= ReportComposer.BODY_WIDTH_MM)
        assertTrue(thumbnail.yMm + thumbnail.heightMm <= row.heightMm, "thumbnail does not fit inside its row")
        // The text column stops before the picture column instead of running under it.
        assertTrue(
            block.textRuns.all { it.xMm + it.widthMm <= ReportComposer.THUMB_COLUMN_X_MM },
            "item text overlaps the thumbnail column",
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
            block.textRuns.any { it.widthMm == ReportComposer.BODY_WIDTH_MM },
            "a photo-less item should not be narrowed by an absent picture column",
        )
    }

    @Test
    fun `a very long reference cannot split an image slot or duplicate the photo`() {
        val longReference = "evidence/" + "segment-".repeat(400) + "end.jpg"
        val plan = composer.compose(reportWithItemReference(longReference), Audience.LANDLORD)

        ImagePurpose.entries.forEach { purpose ->
            val forPhoto = plan.slots().filter { it.photoId == "photo-item" && it.purpose == purpose }
            assertEquals(1, forPhoto.size, "the long-caption photo produced ${forPhoto.size} $purpose slots")
        }
        plan.slots().filter { it.photoId == "photo-item" }.forEach { slot ->
            // Structural fields keep the whole reference even though the caption is elided.
            assertEquals(longReference, slot.reference)
            assertTrue(
                slot.textRuns.size <= ReportComposer.MAX_CAPTION_LINES,
                "caption ran to ${slot.textRuns.size} lines; image slots must stay bounded",
            )
            assertTrue(
                slot.textRuns.last().text.endsWith(ReportComposer.CAPTION_ELISION),
                "an elided caption must say so",
            )
            assertTrue(slot.heightMm <= ReportComposer.BODY_HEIGHT_MM, "an image slot must fit one page")
        }
        // Footers sit on the bottom margin by design, so the body-overflow check excludes them.
        plan.pages.forEach { page ->
            page.blocks.filterNot { it.content is FooterBlock }.forEach {
                assertTrue(it.yMm + it.heightMm <= BODY_BOTTOM_MM, "page ${page.number} overflow")
            }
        }
    }

    @Test
    fun `the footer that gets drawn carries the short hash, not the full digest`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val expectedShort = ReportTestFixtures.DATA_HASH.take(ReportComposer.SHORT_HASH_LENGTH)

        plan.pages.forEach { page ->
            val footer = page.blocks.single { it.content is FooterBlock }.content as FooterBlock
            assertEquals(
                "$expectedShort · ${page.number}/${plan.pages.size}",
                footer.textRuns.joinToString("") { it.text },
                "the drawn footer text must be the short hash",
            )
            assertTrue(
                footer.textRuns.none { it.text.contains(ReportTestFixtures.DATA_HASH) },
                "the full 64-character digest must not be drawn",
            )
            assertEquals(ReportTestFixtures.DATA_HASH, footer.dataHash, "the full digest stays available")
        }
    }

    @Test
    fun `the cover draws labelled totals and ISO-8601 times rather than epoch milliseconds`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val cover = plan.pages.flatMap { it.blocks }.map { it.content }.filterIsInstance<CoverBlock>().single()
        val drawn = cover.textRuns.joinToString("|") { it.text }

        assertTrue(drawn.contains("Adverse items / 不利项：1"), "cover does not draw the adverse total: $drawn")
        assertTrue(drawn.contains("Pending remediation / 待处理：1"), "cover does not draw the pending total: $drawn")
        // scheduledAt 1_755_302_400_000 == 2025-08-16T00:00:00Z, fixed and locale-independent.
        assertTrue(drawn.contains("ROUTINE · 2025-08-16T00:00:00Z"), "cover draws a raw epoch value: $drawn")
        assertTrue(!drawn.contains("1755302400000"), "epoch milliseconds reached the rendered cover")
        assertEquals(1, cover.adverseItemCount)
        assertEquals(1, cover.pendingItemCount)
    }

    @Test
    fun `photo captions render a fixed ISO-8601 instant`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val thumb = plan.slots().single { it.photoId == "photo-item" && it.purpose == ImagePurpose.INLINE }

        // exifTimeMs 1_755_303_000_000 == 2025-08-16T00:10:00Z; the capture time wins over the import time.
        assertEquals("1.2.1 · camera · 2025-08-16T00:10:00Z", thumb.textRuns.joinToString("") { it.text })
        assertEquals(1_755_303_000_000L, thumb.capturedAt)
    }

    @Test
    fun `an empty room keeps its heading with its first photo and is refused when it has nothing to show`() {
        val withPhoto = composer.compose(reportWithEmptyRoom(photoCount = 1), Audience.LANDLORD)
        val page = withPhoto.pages.single { page ->
            page.blocks.any { (it.content as? RoomTitleBlock)?.roomId == "room-empty" }
        }
        val headingIndex = page.blocks.indexOfFirst { (it.content as? RoomTitleBlock)?.roomId == "room-empty" }
        assertTrue(
            page.blocks.drop(headingIndex + 1).any { it.content is ImageSlotBlock },
            "the empty room's heading was separated from its only content",
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            composer.compose(reportWithEmptyRoom(photoCount = 0), Audience.LANDLORD)
        }
        assertTrue(
            failure.message!!.contains("orphan heading"),
            "expected the orphan-heading refusal, got: ${failure.message}",
        )
    }

    /**
     * Each case asserts the *specific* refusal, not merely that something was thrown: the projection has
     * several guards and a case that trips the wrong one proves nothing about the guard it is named for.
     */
    @Test
    fun `projection identifiers and photo nesting are validated instead of rendering broken references`() {
        val cases = listOf(
            Triple(
                "blank room id",
                "report rooms require a non-blank id",
                { r: ReportSnapshot -> r.copy(rooms = r.rooms.map { it.copy(id = " ") }) },
            ),
            Triple(
                "blank item id",
                "report items require a non-blank id",
                // Blank exactly one id: blanking every item would make them duplicates and trip that guard first.
                { r: ReportSnapshot ->
                    r.copy(
                        rooms = r.rooms.map { room ->
                            room.copy(
                                items = room.items.mapIndexed { index, item ->
                                    if (index == 0) item.copy(id = "") else item
                                },
                            )
                        },
                    )
                },
            ),
            Triple(
                "duplicate photo reference",
                "duplicate report photo reference",
                { r: ReportSnapshot ->
                    r.copy(
                        rooms = r.rooms.map { room ->
                            room.copy(
                                photos = room.photos.map { it.copy(reference = "same.jpg") },
                                items = room.items.map { item ->
                                    item.copy(photos = item.photos.map { it.copy(reference = "same.jpg") })
                                },
                            )
                        },
                    )
                },
            ),
            Triple(
                "room slot holding an item-level photo",
                "isRoomLevel is false",
                { r: ReportSnapshot -> r.withRoomPhotoLevel(false) },
            ),
            Triple(
                "item slot holding a room-level photo",
                "isRoomLevel is true",
                { r: ReportSnapshot -> r.withItemPhotoLevel(true) },
            ),
        )

        cases.forEach { (label, expected, mutate) ->
            val failure = assertFailsWith<IllegalArgumentException>(message = "projection accepted: $label") {
                composer.compose(mutate(ReportTestFixtures.report()), Audience.LANDLORD)
            }
            assertTrue(
                failure.message!!.contains(expected),
                "'$label' was refused for the wrong reason: ${failure.message}",
            )
        }
    }

    // --- fixtures ---

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

    /** The canonical photo list has to grow with the projection, or the multiset guard refuses first. */
    private fun reportWithEmptyRoom(photoCount: Int): ReportSnapshot {
        val base = ReportTestFixtures.report()
        val photos = (0 until photoCount).map { index ->
            ReportPhoto(
                id = "photo-empty-$index",
                snapshot = PhotoSnapshot("ph-empty-$index", "camera", 1_755_400_000_000L, isRoomLevel = true),
                privacy = false,
                reference = "R.E.$index",
                capturedAt = 1_755_400_000_000L + index,
            )
        }
        return base.copy(
            canonical = base.canonical.copy(photos = base.canonical.photos + photos.map { it.snapshot }),
            rooms = base.rooms + ReportRoom(
                id = "room-empty",
                label = BilingualText("Hallway", "走廊"),
                items = emptyList(),
                photos = photos,
            ),
        )
    }

    /**
     * Flipping isRoomLevel changes the canonical photo too, so the multiset guard stays satisfied and the
     * nesting guard is the one under test.
     */
    private fun ReportSnapshot.withRoomPhotoLevel(isRoomLevel: Boolean): ReportSnapshot {
        val flipped = rooms.map { room ->
            room.copy(photos = room.photos.map { it.copy(snapshot = it.snapshot.copy(isRoomLevel = isRoomLevel)) })
        }
        return copy(rooms = flipped, canonical = canonical.copy(photos = flipped.canonicalPhotos(this)))
    }

    private fun ReportSnapshot.withItemPhotoLevel(isRoomLevel: Boolean): ReportSnapshot {
        val flipped = rooms.map { room ->
            room.copy(
                items = room.items.map { item ->
                    item.copy(
                        photos = item.photos.map { it.copy(snapshot = it.snapshot.copy(isRoomLevel = isRoomLevel)) },
                    )
                },
            )
        }
        return copy(rooms = flipped, canonical = canonical.copy(photos = flipped.canonicalPhotos(this)))
    }

    private fun List<ReportRoom>.canonicalPhotos(original: ReportSnapshot): List<PhotoSnapshot> {
        val updated = flatMap { room -> room.photos + room.items.flatMap { it.photos } }.map { it.snapshot }
        require(updated.size == original.canonical.photos.size) { "fixture changed the photo count" }
        return updated
    }
}
