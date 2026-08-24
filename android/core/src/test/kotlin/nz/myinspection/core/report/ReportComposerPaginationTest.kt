package nz.myinspection.core.report

import nz.myinspection.core.model.PhotoSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Production breaks under test: a greedy block-by-block paginator can orphan a room heading, split a
 * bilingual row or image slot, duplicate or lose evidence when a row outgrows a page, overflow A4, leak
 * privacy photos, or silently lay out a projection that no longer matches the canonical hash input.
 *
 * Page geometry is written out rather than read back from the production constants: A4 210x297 mm at a
 * 15 mm margin with a 10 mm footer strip gives a body from y=15 to y=272, 257 mm tall and 180 mm wide.
 * An assertion phrased in terms of `BODY_BOTTOM_MM` stays green when `BODY_BOTTOM_MM` becomes 9999.
 */
class ReportComposerPaginationTest {
    private val composer = ReportComposer(ReportTestFixtures.measurer)

    @Test
    fun `eighty photos paginate without overflow orphan heading or split image`() {
        // Real capture instants: an EXIF time of a few milliseconds past the epoch renders every caption as
        // 1970-01-01T00:00:00Z, which no assertion about capturedAt can see.
        val canonicalPhotos = (1..80).map { index ->
            PhotoSnapshot("hash-$index", "camera", 1_755_303_000_000L + index * 60_000L, isRoomLevel = false)
        }
        val photos = canonicalPhotos.mapIndexed { index, photo ->
            ReportPhoto(
                "photo-${index + 1}",
                photo,
                privacy = false,
                reference = "1.1.${index + 1}",
                capturedAt = photo.exifTimeMs!! + 500L,
            )
        }
        val base = ReportTestFixtures.canonical()
        val oneItem = base.items.first()
        val report = ReportSnapshot(
            canonical = base.copy(items = listOf(oneItem), photos = canonicalPhotos),
            tenancyReference = "TENANCY-80",
            rooms = listOf(
                ReportRoom(
                    "room-1",
                    BilingualText("Bedroom", "卧室"),
                    listOf(ReportItem("item-1", oneItem, BilingualText("Wall", "墙面"), photos)),
                ),
            ),
            statusDefinitions = ReportTestFixtures.report().statusDefinitions,
        )

        val plan = composer.compose(report, Audience.TENANT)

        plan.assertNothingOverflows()
        // 1 cover + 1 glossary + 1 (empty) summary + 20 item-row pages + 40 two-up appendix pages + 1 closing.
        // A 54 mm thumbnail on a 56 mm pitch puts 4 pictures in the 257 mm body, so 80 photos need 20 rows.
        assertEquals(64, plan.pages.size)
        assertEquals(
            (1..80).associate { "photo-$it" to 1 },
            plan.imageSlots(ImagePurpose.INLINE).groupingBy { it.photoId }.eachCount(),
            "each item photo must be drawn exactly once",
        )
        for (page in plan.pages) {
            val body = page.blocks.filterNot { it.content is FooterBlock }
            body.filter { it.content is ImageSlotBlock }.forEach {
                val slot = it.content as ImageSlotBlock
                assertEquals(slot.heightMm, it.heightMm, "image slot geometry must match its placed height")
            }
            val headingIndex = body.indexOfFirst { it.content is RoomTitleBlock }
            if (headingIndex >= 0) {
                assertTrue(body.drop(headingIndex + 1).any { it.content is ItemRowBlock }, "orphan room heading")
            }
        }
        assertEquals(80, plan.imageSlots(ImagePurpose.INLINE).size)
        assertEquals(80, plan.imageSlots(ImagePurpose.APPENDIX).size)
        // Splitting a slot would show up as one photoId appearing twice for the same purpose.
        ImagePurpose.entries.forEach { purpose ->
            val ids = plan.imageSlots(purpose).map { it.photoId }
            assertEquals(ids.size, ids.toSet().size, "a $purpose photo was emitted more than once")
        }
    }

    /**
     * An item's thumbnail column is 56n - 2 mm tall, so any item with five or more photos outgrows the body
     * and is split. Thumbnails are drawn, so the split has to partition them: a chunk that kept the whole
     * list would print the same photograph under every fragment of the note, and a chunk sized from its
     * text alone would stack the pictures off the bottom of the sheet.
     */
    @Test
    fun `an item with more photos than fit a page draws each photo exactly once`() {
        val plan = composer.compose(photoHeavyItemReport(photoCount = 6, note = null), Audience.LANDLORD)
        val chunks = plan.itemChunks("item-big")

        assertTrue(chunks.size > 1, "a 334mm thumbnail column should not fit one 257mm body")
        assertEquals(
            (1..6).associate { "p-$it" to 1 },
            chunks.flatMap { it.thumbnails }.groupingBy { it.photoId }.eachCount(),
        )
        plan.assertNothingOverflows()
    }

    /**
     * A continuation chunk re-bases the thumbnails it owns to the top of its own row, and a caption belongs
     * to its picture, not to the row it was first measured in. Moving the box without its caption runs is
     * invisible to any assertion that walks the box alone: the photograph appears in the right place and
     * its provenance line - reference, source, capture instant, the evidence the appendix exists to carry -
     * is drawn hundreds of millimetres below, off the sheet.
     *
     * Six photos split 4 + 2, so the second chunk is the one whose pictures actually move, and it holds
     * more than one of them: a single-thumbnail chunk moves nothing (its slot is already at y=0) and cannot
     * see the defect at all.
     */
    @Test
    fun `each caption follows its own picture into the continuation chunk`() {
        val plan = composer.compose(photoHeavyItemReport(photoCount = 6, note = null), Audience.LANDLORD)
        val placed = plan.pages.flatMap { page -> page.blocks.filter { it.content is ItemRowBlock } }

        assertEquals(
            listOf(4, 2),
            placed.map { (it.content as ItemRowBlock).thumbnails.size },
            "the fixture must produce a continuation chunk carrying more than one moved thumbnail",
        )
        assertEquals(
            listOf(listOf("p-1", "p-2", "p-3", "p-4"), listOf("p-5", "p-6")),
            placed.map { row -> (row.content as ItemRowBlock).thumbnails.map { it.photoId } },
        )
        placed.forEach { row ->
            (row.content as ItemRowBlock).thumbnails.forEach { slot ->
                val pictureBottom = row.yMm + slot.yMm + slot.imageHeightMm
                slot.textRuns.forEachIndexed { line, run ->
                    assertEquals(
                        pictureBottom + line * run.heightMm,
                        row.yMm + run.yMm,
                        "caption line $line of ${slot.photoId} is drawn at ${row.yMm + run.yMm}mm, " +
                            "not under its picture box ending at ${pictureBottom}mm",
                    )
                }
            }
        }
    }

    /**
     * The picture column stacks on a fixed 2 mm gap. At gap 0 each provenance line is printed hard against
     * the next photograph and the evidence column stops being readable in print, which no assertion about
     * how many photos a chunk holds can see: four thumbnails fit the body either way.
     */
    @Test
    fun `thumbnails stack under one another on a two millimetre gap`() {
        val plan = composer.compose(photoHeavyItemReport(photoCount = 6, note = null), Audience.LANDLORD)
        val chunks = plan.itemChunks("item-big")

        // The closed form below holds only while every caption is the same height, so pin that first.
        assertEquals(
            setOf(3),
            chunks.flatMap { it.thumbnails }.map { it.textRuns.size }.toSet(),
            "the fixture must give every thumbnail a three-line caption",
        )
        assertEquals(setOf(54), chunks.flatMap { it.thumbnails }.map { it.heightMm }.toSet())
        chunks.forEach { chunk ->
            assertEquals(
                chunk.thumbnails.indices.map { it * 56 },
                chunk.thumbnails.map { it.yMm },
                "a 54mm slot on a 2mm gap puts the nth picture at (n-1) x 56mm",
            )
            chunk.thumbnails.zipWithNext().forEach { (above, below) ->
                assertEquals(
                    above.yMm + above.heightMm + 2,
                    below.yMm,
                    "${below.photoId} does not sit 2mm under ${above.photoId}",
                )
            }
        }
    }

    /**
     * The field case behind the split: a POOR carpet with six photos and a long dictated note. Both columns
     * have to be partitioned at once - the note must read once end to end, and each photograph must appear
     * once. The bilingual label repeats on every chunk, because a continuation row still has to say which
     * item its pictures belong to.
     */
    @Test
    fun `a long dictated note and six photos are each drawn once across the chunks`() {
        val note = "刮痕 scratch ".repeat(2_000)
        val plan = composer.compose(photoHeavyItemReport(photoCount = 6, note = note), Audience.LANDLORD)
        val chunks = plan.itemChunks("item-big")

        assertTrue(note.length > 20_000, "the fixture must exceed 20,000 characters, got ${note.length}")
        assertEquals(
            (1..6).associate { "p-$it" to 1 },
            chunks.flatMap { it.thumbnails }.groupingBy { it.photoId }.eachCount(),
        )
        assertEquals(
            note,
            chunks.flatMap { chunk -> chunk.textRuns.filter { it.language == TextLanguage.ORIGINAL } }
                .joinToString("") { it.text },
            "the note must read once, end to end, across the chunks",
        )
        chunks.forEachIndexed { index, chunk ->
            assertEquals(
                listOf("Carpet", "地毯"),
                chunk.textRuns.filter { it.language == TextLanguage.EN || it.language == TextLanguage.ZH }
                    .map { it.text },
                "chunk $index does not carry the item's bilingual label",
            )
        }
        plan.assertNothingOverflows()
    }

    /**
     * 双语成对不拆页. Fixed text is emitted as an en run followed by a zh run, and the pair is kept in one
     * chunk. When the pair alone is taller than a page there is no honest layout, so the composer refuses
     * instead of laying the two halves on different pages.
     */
    @Test
    fun `a bilingual pair taller than the body is refused rather than split across pages`() {
        val base = ReportTestFixtures.report()
        val huge = BilingualText("Repair the carpet. ".repeat(260), "修复地毯。".repeat(260))
        val report = base.copy(remediations = listOf(ReportRemediation("item-poor", Urgency.HIGH, huge)))

        val failure = assertFailsWith<IllegalArgumentException> { composer.compose(report, Audience.LANDLORD) }
        assertTrue(
            failure.message!!.contains("an en/zh pair is never split across pages"),
            "expected the bilingual-pair refusal, got: ${failure.message}",
        )
        assertTrue(
            failure.message!!.contains("remediation for item-poor"),
            "the refusal must name the block, got: ${failure.message}",
        )
    }

    /**
     * The measurer is an injected seam, so its line height is an input like any other. A height that cannot
     * hold the composer's own fixed disclaimer has to be refused at the door, with a message naming the
     * style and the measurement: a refusal raised deep inside pagination names neither.
     */
    @Test
    fun `a line height that cannot hold the fixed disclaimer is refused, naming the style and the value`() {
        val tall = ReportComposer(ReportTestFixtures.measurerOf(lineHeightMm = 60))

        val failure = assertFailsWith<IllegalArgumentException> {
            tall.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        }
        listOf("60mm", "CAPTION", "disclaimer").forEach {
            assertTrue(failure.message!!.contains(it), "the refusal never mentions '$it': ${failure.message}")
        }
        // The same report composes at the ordinary line height, so the fixture is not otherwise broken.
        composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
    }

    /**
     * The appendix is two large pictures per page because the Tribunal takes two printed copies and the
     * evidence has to stay legible in print. A caption long enough to push the pair over the body must not
     * demote the second picture to a page of its own - a page that would also carry no section title, the
     * title having been spent on the pair before it. Halving the density silently doubles the printed page
     * count, so the fixture below uses the longest caption the cap allows.
     */
    @Test
    fun `odd and even multi-page appendices retain titles two-up density and absolute bounds`() {
        val threeLineReference = "evidence/" + "x".repeat(91)
        listOf(3 to listOf(2, 1), 4 to listOf(2, 2)).forEach { (photoCount, expectedDensity) ->
            val plan = composer.compose(
                reportWithAppendixPhotos(photoCount, threeLineReference),
                Audience.LANDLORD,
            )
            val appendixPages = plan.pages.filter { page ->
                page.blocks.any { (it.content as? ImageSlotBlock)?.purpose == ImagePurpose.APPENDIX }
            }

            assertEquals(2, appendixPages.size, "$photoCount photos must span two appendix pages")
            assertEquals(expectedDensity, appendixPages.map { page -> page.appendixSlots().size })
            appendixPages.forEachIndexed { pageIndex, page ->
                val title = page.blocks.single { (it.content as? SectionTitleBlock)?.key == "photo-appendix" }
                val slots = page.appendixSlots()
                assertEquals(15, title.yMm, "appendix page ${page.number} title does not start at the body top")
                assertEquals(listOf(25, 147).take(slots.size), slots.map { it.first.yMm })
                assertEquals(listOf(147, 269).take(slots.size), slots.map { it.first.yMm + it.first.heightMm })
                slots.forEach { (_, slot) ->
                    assertEquals(3, slot.textRuns.size, "photo ${slot.photoId} did not spend the caption cap")
                }
                assertEquals(
                    expectedDensity[pageIndex],
                    slots.size,
                    "appendix page ${page.number} carries the wrong picture density",
                )
            }
            plan.assertNothingOverflows()
        }
    }

    /**
     * The appendix picture box is a fixed number, so how many millimetres a title line and a caption line
     * cost is what decides whether a section title plus two full-caption slots still fit the body. Those
     * are properties of the injected measurer, so the composer has to refuse a measurer it cannot draw an
     * appendix page with - at the door, where the message can name both styles and both measured values.
     *
     * A title line taller than a caption line is the ordinary case for a Paint-backed measurer and is the
     * shape the uniform fake cannot express: at TITLE 6 / CAPTION 4 an appendix page measures 258 mm, one
     * millimetre over the body, and every report with a photograph in it becomes ungenerable.
     */
    @Test
    fun `a measurer that cannot draw an appendix page is refused, naming both styles and both values`() {
        val cases = listOf(
            // 14mm title + 2 x (108 + 3 x 4 + 2) = 258mm.
            Triple(
                ReportTestFixtures.measurerOf(titleMm = 6, bodyMm = 4, captionMm = 4),
                listOf("6mm TITLE", "4mm CAPTION", "258mm", "257mm"),
                "a taller heading than caption",
            ),
            // 18mm title + 2 x (108 + 3 x 8 + 2) = 286mm.
            Triple(
                ReportTestFixtures.measurerOf(lineHeightMm = 8),
                listOf("8mm TITLE", "8mm CAPTION", "286mm", "257mm"),
                "a uniformly taller line",
            ),
        )

        cases.forEach { (measurer, expected, label) ->
            val failure = assertFailsWith<IllegalArgumentException>(message = "$label was accepted") {
                ReportComposer(measurer).compose(ReportTestFixtures.report(), Audience.LANDLORD)
            }
            expected.forEach {
                assertTrue(
                    failure.message!!.contains(it),
                    "the refusal for $label never mentions '$it': ${failure.message}",
                )
            }
        }
        // One millimetre either side of the bound: 12mm title + 244mm = 256mm still composes, so the guard
        // is measuring the appendix page rather than rejecting every non-uniform measurer it is handed.
        ReportComposer(ReportTestFixtures.measurerOf(titleMm = 5, bodyMm = 4, captionMm = 4))
            .compose(ReportTestFixtures.report(), Audience.LANDLORD)
    }

    /**
     * The blocks a page must carry together are placed as one indivisible group, so a room opening that
     * cannot fit one page fails loudly instead of stranding the heading. A 2,760-character label makes the
     * heading 190 mm on its own: with a 50 mm panorama that leaves 17 mm, under the 18 mm an item row can
     * ever shrink to, so the group is 258 mm and cannot be placed at all.
     */
    @Test
    fun `a room opening too tall for one page fails loudly instead of stranding its heading`() {
        val base = ReportTestFixtures.report()
        val huge = BilingualText("e".repeat(2_760), "房")
        val report = base.copy(rooms = base.rooms.map { it.copy(label = huge) })

        val failure = assertFailsWith<IllegalArgumentException>(message = "the oversized room opening was placed") {
            composer.compose(report, Audience.LANDLORD)
        }
        listOf(
            "room title room-kitchen",
            "image slot photo-room",
            "item item-good",
            "must be placed together but measure 258mm",
        ).forEach {
            assertTrue(failure.message!!.contains(it), "the refusal never mentions '$it': ${failure.message}")
        }
    }

    /** One slot per photo per purpose, whatever the caption does. */
    @Test
    fun `a very long reference cannot split an image slot or duplicate the photo`() {
        val longReference = "evidence/" + "segment-".repeat(400) + "end.jpg"
        val base = ReportTestFixtures.report()
        val report = base.copy(
            rooms = base.rooms.map { room ->
                room.copy(
                    items = room.items.map { item ->
                        item.copy(photos = item.photos.map { it.copy(reference = longReference) })
                    },
                )
            },
        )

        val plan = composer.compose(report, Audience.LANDLORD)

        ImagePurpose.entries.forEach { purpose ->
            val forPhoto = plan.imageSlots(purpose).filter { it.photoId == "photo-item" }
            assertEquals(1, forPhoto.size, "the long-caption photo produced ${forPhoto.size} $purpose slots")
        }
        plan.assertNothingOverflows()
    }

    /**
     * The cover's room-by-status breakdown grows with the property. A cover that flows onto a second page
     * stops being a cover: the reader gets two pages that both claim to be the answer sheet, and the
     * structural payload every chunk carries would be drawn twice if a renderer trusted it.
     */
    @Test
    fun `a sixty-room property still has exactly one cover on exactly one page`() {
        val plan = composer.compose(manyRoomReport(rooms = 60), Audience.LANDLORD)
        val covers = plan.pages.flatMap { page -> page.blocks.map { page.number to it } }
            .filter { it.second.content is CoverBlock }

        assertEquals(1, covers.size, "the cover was split across pages ${covers.map { it.first }}")
        assertEquals(1, covers.single().first, "the cover must be page 1")
        val cover = covers.single().second.content as CoverBlock
        assertEquals(60, cover.roomStatusCounts.size, "the full breakdown stays available")
        assertTrue(covers.single().second.yMm + covers.single().second.heightMm <= 272, "the cover overflows")
        // What is left out says so, rather than vanishing.
        val drawn = cover.textRuns.joinToString("|") { it.text }
        val drawnRows = cover.textRuns.count { it.text.startsWith("room-") }
        assertTrue(drawnRows in 1 until 60, "the cover drew $drawnRows of 60 rows")
        assertTrue(drawn.contains("… ${60 - drawnRows} more rows"), "the cover drops rows silently: $drawn")
    }

    /**
     * One cover or no report - never two. The header is admitted against the page budget and the elision
     * marker is then appended whenever a row is left out, so a header that fills the budget leaves the
     * marker nowhere to go and the block outgrows the body, which splits it onto a second page that carries
     * the full address and both totals again.
     *
     * A single address length cannot pin that: the window where it happens is a few dozen characters wide
     * and sits between an address that composes and one that is refused. Sweeping across the whole window
     * asserts the invariant instead - every length either draws exactly one cover or is refused for being
     * unable to fit the cover, and the sweep exercises both outcomes.
     */
    @Test
    fun `no address length can split the cover onto a second page`() {
        val outcomes = (3_300..3_600 step 10).map { length ->
            val report = manyRoomReport(rooms = 60, address = "a".repeat(length))
            try {
                val covers = composer.compose(report, Audience.LANDLORD).pages
                    .flatMap { page -> page.blocks.map { page.number to it } }
                    .filter { it.second.content is CoverBlock }
                assertEquals(
                    1,
                    covers.size,
                    "an address of $length characters drew ${covers.size} covers, on pages ${covers.map { it.first }}",
                )
                "composed"
            } catch (refused: IllegalArgumentException) {
                assertTrue(
                    refused.message!!.contains("the cover header"),
                    "an address of $length characters was refused for an unrelated reason: ${refused.message}",
                )
                "refused"
            }
        }

        // Neither branch may be empty, or the loop above asserts nothing about the outcome it never saw.
        assertEquals(setOf("composed", "refused"), outcomes.toSet(), "the sweep must cross the refusal boundary")
    }

    @Test
    fun `privacy photos are excluded by default for both audiences and included only by explicit option`() {
        val basePhoto = ReportTestFixtures.canonical().photos.first()
        val privatePhoto = ReportPhoto(
            "private-photo",
            basePhoto,
            privacy = true,
            reference = "1.2.1",
            capturedAt = 1_755_303_100_000L,
        )
        val canonical = ReportTestFixtures.canonical().copy(photos = listOf(basePhoto))
        val report = ReportTestFixtures.report(
            itemPhotos = listOf(privatePhoto),
            roomPhotos = emptyList(),
            canonical = canonical,
        )

        Audience.entries.forEach { audience ->
            assertTrue(
                composer.compose(report, audience).imageSlots().isEmpty(),
                "a private photo reached the $audience default report",
            )
        }
        val included = composer.compose(report, Audience.LANDLORD, ReportOptions(includePrivacyPhotos = true))
        assertEquals(listOf("private-photo", "private-photo"), included.imageSlots().map { it.photoId })
    }

    /**
     * Excluding privacy photos is the default, so a room whose whole content is excluded is an ordinary
     * projection, not a malformed one. Refusing it would mean the only generable report is the one that
     * prints the tenant's private photographs.
     */
    @Test
    fun `a room emptied by the privacy filter is skipped independently for both audiences`() {
        Audience.entries.forEach { audience ->
            val plan = composer.compose(reportWithPhotoOnlyRoom(privacy = true), audience)
            assertTrue(
                plan.pages.flatMap { it.blocks }.none { (it.content as? RoomTitleBlock)?.roomId == "room-private" },
                "a fully filtered room still emitted its heading for $audience",
            )
            assertTrue(plan.imageSlots().none { it.photoId == "photo-private" })
        }
    }

    @Test
    fun `a truly bare room is refused by name`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            composer.compose(reportWithBareRoom(), Audience.LANDLORD)
        }
        assertTrue(
            failure.message!!.contains("room-bare") && failure.message!!.contains("orphan heading"),
            "expected the room-bare orphan-heading refusal, got: ${failure.message}",
        )
    }

    /** A photo-only room's heading travels with its first picture, so it can never end a page alone. */
    @Test
    fun `a room with no items keeps its heading with its first photo`() {
        val plan = composer.compose(reportWithBoundaryPhotoOnlyRoom(), Audience.LANDLORD)
        val page = plan.pages.single { page ->
            page.blocks.any { (it.content as? RoomTitleBlock)?.roomId == "room-private" }
        }
        val headingIndex = page.blocks.indexOfFirst { (it.content as? RoomTitleBlock)?.roomId == "room-private" }
        val heading = page.blocks[headingIndex]
        val photo = page.blocks[headingIndex + 1]

        assertTrue(photo.content is ImageSlotBlock, "the photo-only heading is not immediately followed by its photo")
        assertEquals(15, heading.yMm, "the heading did not move to a fresh page with its photo")
        assertEquals(heading.yMm + heading.heightMm, photo.yMm)
        val previous = plan.pages[page.number - 2]
        val previousEnd = previous.blocks.filterNot { it.content is FooterBlock }.maxOf { it.yMm + it.heightMm }
        assertEquals(249, previousEnd, "the boundary fixture must end the preceding page at 249mm")
        assertTrue(previousEnd + heading.heightMm <= 272, "the heading alone must fit the preceding page")
        assertTrue(previousEnd + heading.heightMm + photo.heightMm > 272, "heading plus photo must not fit")
    }

    @Test
    fun `presentation item and photo multisets must exactly match canonical hash input`() {
        val report = ReportTestFixtures.report()
        val wrongItem = report.rooms.single().items.first().copy(
            snapshot = report.rooms.single().items.first().snapshot.copy(status = "POOR"),
        )
        val wrongItems = report.copy(
            rooms = listOf(report.rooms.single().copy(items = listOf(wrongItem) + report.rooms.single().items.drop(1))),
        )
        val missingPhoto = report.copy(
            rooms = listOf(report.rooms.single().copy(photos = emptyList())),
        )

        // Each of the two multisets is guarded separately, so each needs its own message: a bare "it threw"
        // is satisfied by whichever guard happens to fire, including one neither case is named for.
        assertEquals(
            "report items do not match canonical snapshot",
            assertFailsWith<IllegalArgumentException> { composer.compose(wrongItems, Audience.LANDLORD) }.message,
        )
        assertEquals(
            "report photos do not match canonical snapshot",
            assertFailsWith<IllegalArgumentException> { composer.compose(missingPhoto, Audience.LANDLORD) }.message,
        )
    }

    /**
     * Each case asserts the *specific* refusal, not merely that something was thrown: the projection has
     * several guards and a case that trips the wrong one proves nothing about the guard it is named for.
     */
    @Test
    fun `projection identifiers, photo nesting and rendered capture times are validated`() {
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
            // The caption renders exifTimeMs when present and capturedAt only otherwise, so a guard that
            // checks capturedAt alone reports success while every caption for that photo reads 1970.
            Triple(
                "non-positive exif capture time",
                "would render capture time 0",
                { r: ReportSnapshot -> r.withItemPhotoExif(0L) },
            ),
            Triple(
                "duplicate room id",
                "duplicate room id",
                { r: ReportSnapshot -> r.copy(rooms = r.rooms + r.rooms) },
            ),
            // Give one item its sibling's id rather than inventing a new one for both: a fresh id would
            // also orphan the remediation that points at the old one, and that guard would fire first.
            Triple(
                "duplicate item id",
                "duplicate report item id",
                { r: ReportSnapshot ->
                    r.copy(
                        rooms = r.rooms.map { room ->
                            room.copy(items = room.items.map { it.copy(id = room.items.last().id) })
                        },
                    )
                },
            ),
            // Two slots holding one id render the same photograph as two pieces of evidence, and the
            // reference guard below cannot see it: distinct references on a repeated id satisfy it.
            Triple(
                "duplicate photo id",
                "duplicate report photo id",
                { r: ReportSnapshot ->
                    r.copy(
                        rooms = r.rooms.map { room ->
                            room.copy(photos = room.photos.map { it.copy(id = "photo-item") })
                        },
                    )
                },
            ),
            Triple(
                "remediation pointing at an unknown item",
                "remediation references unknown item",
                { r: ReportSnapshot ->
                    r.copy(remediations = listOf(ReportRemediation("gone", Urgency.HIGH, BilingualText("Fix", "修复"))))
                },
            ),
            // A repeated status renders two glossary entries for one rating; the set comparison below is
            // satisfied by the duplicate, because a set of the same statuses still equals the domain.
            Triple(
                "duplicate status definition",
                "duplicate status definition",
                { r: ReportSnapshot -> r.copy(statusDefinitions = r.statusDefinitions + r.statusDefinitions.last()) },
            ),
            // The glossary is set-equal to the template domain, so a status outside it has no entry: the
            // report would print a rating the reader has no definition for, and the adverse classification
            // would silently treat it as benign. Changing the canonical item too keeps the multiset guard
            // satisfied, so the guard under test is the one that fires.
            Triple(
                "item status outside the template domain",
                "item status is outside the template domain",
                { r: ReportSnapshot ->
                    val room = r.rooms.single()
                    val wrecked = room.items.first().let { it.copy(snapshot = it.snapshot.copy(status = "WRECKED")) }
                    r.copy(
                        canonical = r.canonical.copy(items = listOf(wrecked.snapshot) + r.canonical.items.drop(1)),
                        rooms = listOf(room.copy(items = listOf(wrecked) + room.items.drop(1))),
                    )
                },
            ),
        )

        // Two guards sharing one message make each other untestable: delete either and the sibling branch
        // refuses the same input with the same text, so no case can tell which one is gone.
        assertEquals(
            cases.size,
            cases.map { it.second }.toSet().size,
            "two cases expect the same refusal text: ${cases.map { it.second }}",
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

    /**
     * The injected measurer decides how tall a row is. A 1,600-character note wraps to 36 lines in the
     * 135 mm text column, which with the label and status lines is 39 runs of 4 mm: 158 mm, taller than
     * both the 18 mm minimum and the 54 mm thumbnail column beside it.
     */
    @Test
    fun `injected text measurement expands a wrapped item row without overflowing the page`() {
        val report = ReportTestFixtures.report()
        val longItem = report.rooms.single().items.last().let { item ->
            item.copy(snapshot = item.snapshot.copy(note = "wrapped ".repeat(200)))
        }
        val changed = report.copy(
            canonical = report.canonical.copy(items = listOf(report.canonical.items.first(), longItem.snapshot)),
            rooms = listOf(report.rooms.single().copy(items = listOf(report.rooms.single().items.first(), longItem))),
        )

        val row = composer.compose(changed, Audience.LANDLORD).pages
            .flatMap { it.blocks }
            .single { (it.content as? ItemRowBlock)?.itemId == "item-poor" }
        val block = row.content as ItemRowBlock

        assertEquals(158, row.heightMm)
        assertEquals(36, block.textRuns.count { it.language == TextLanguage.ORIGINAL })
        assertEquals(54, block.thumbnails.single().heightMm, "the row is taller than its picture column")
        assertTrue(row.yMm + row.heightMm <= 272)
    }

    @Test
    fun `rental and annual glossaries must exactly match their authoritative status domains`() {
        val rental = ReportTestFixtures.report()
        // The glossary is what tells the reader what a rating means, so an incomplete one has to be refused
        // by name: a bare "it threw" is equally satisfied by the duplicate-definition guard beside it.
        assertEquals(
            "report glossary must exactly cover the ROUTINE status domain",
            assertFailsWith<IllegalArgumentException> {
                composer.compose(rental.copy(statusDefinitions = rental.statusDefinitions.dropLast(1)), Audience.LANDLORD)
            }.message,
        )

        val annualItem = rental.canonical.items.first().copy(status = "SIGNIFICANT_DEFECT")
        val annual = ReportSnapshot(
            canonical = rental.canonical.copy(
                type = "ANNUAL",
                template = rental.canonical.template.copy(type = "ANNUAL"),
                items = listOf(annualItem),
                photos = emptyList(),
            ),
            tenancyReference = null,
            rooms = listOf(
                ReportRoom(
                    "annual-room",
                    BilingualText("Exterior", "室外"),
                    listOf(ReportItem("annual-item", annualItem, BilingualText("Cladding", "外墙"))),
                ),
            ),
            statusDefinitions = listOf(
                StatusDefinition("NO_ISSUE", BilingualText("No issue", "无问题"), BilingualText("Clear", "正常")),
                StatusDefinition("MONITOR", BilingualText("Monitor", "观察"), BilingualText("Review later", "后续复查")),
                StatusDefinition(
                    "MAINTENANCE_ITEM",
                    BilingualText("Maintenance item", "维护项"),
                    BilingualText("Maintenance needed", "需要维护"),
                ),
                StatusDefinition(
                    "SIGNIFICANT_DEFECT",
                    BilingualText("Significant defect", "重大缺陷"),
                    BilingualText("Prompt attention", "应尽快处理"),
                ),
                StatusDefinition(
                    "NOT_APPLICABLE",
                    BilingualText("Not applicable", "不适用"),
                    BilingualText("Does not apply", "不适用"),
                ),
            ),
        )
        val cover = composer.compose(annual, Audience.LANDLORD).pages
            .flatMap { it.blocks }
            .map { it.content }
            .filterIsInstance<CoverBlock>()
            .single()
        assertEquals(1, cover.adverseItemCount)
    }

    /**
     * Free text continues across pages without being duplicated or lost, and each continuation is sized
     * from the runs it actually holds. The item row is split once, against the budget its room heading
     * leaves (245 mm), so every full chunk is 242 mm and only the last is short; the supplement is split
     * against the budget its section title leaves (247 mm) and runs to 246 mm a chunk.
     */
    @Test
    fun `large adverse collection and long original text continue across bounded pages`() {
        val base = ReportTestFixtures.canonical()
        val longNote = "long note ".repeat(2_000)
        val items = (1..20).map { index ->
            base.items.last().copy(stableId = "item-$index", note = if (index == 1) longNote else "issue")
        }
        val reportItems = items.mapIndexed { index, item ->
            ReportItem("report-item-${index + 1}", item, BilingualText("Item ${index + 1}", "检查项 ${index + 1}"))
        }
        val supplement = "supplement ".repeat(2_000)
        val report = ReportSnapshot(
            canonical = base.copy(items = items, photos = emptyList()),
            tenancyReference = null,
            rooms = listOf(ReportRoom("room-many", BilingualText("Many items", "多个检查项"), reportItems)),
            statusDefinitions = ReportTestFixtures.report().statusDefinitions,
            supplements = listOf(ReportSupplement("LONG", supplement)),
        )

        val plan = composer.compose(report, Audience.LANDLORD)
        val rows = plan.pages.flatMap { it.blocks }.filter { it.content is ItemRowBlock }
        val supplements = plan.pages.flatMap { it.blocks }.filter { it.content is SupplementBlock }

        assertEquals(20, plan.pages.flatMap { it.blocks }.count { it.content is SummaryItemBlock })
        // 6 chunks for the wrapped item plus one row each for the other 19 items.
        assertEquals(25, rows.size)
        assertEquals(
            listOf(242, 242, 242, 242, 242, 190),
            rows.filter { (it.content as ItemRowBlock).itemId == "report-item-1" }.map { it.heightMm },
        )
        assertEquals(listOf(246, 246, 246, 246, 246, 246, 14), supplements.map { it.heightMm })
        assertEquals(
            longNote,
            plan.itemChunks("report-item-1")
                .flatMap { chunk -> chunk.textRuns.filter { it.language == TextLanguage.ORIGINAL } }
                .joinToString("") { it.text },
            "the note must read once, end to end",
        )
        assertEquals(
            supplement,
            supplements.flatMap { placed ->
                (placed.content as SupplementBlock).textRuns.filter { it.language == TextLanguage.ORIGINAL }
            }.joinToString("") { it.text },
            "the supplement must read once, end to end",
        )
        plan.assertNothingOverflows()
    }

    /**
     * A room heading is never the last thing on a page. The fixture leaves 23 mm below room-1: enough for
     * the 12 mm heading on its own, not enough for the heading plus its 18 mm first row. Replacing the
     * grouped placement with two independent placements strands the heading at y=249 and puts item-5 on
     * the next page, which is exactly the orphan this constraint exists to prevent.
     */
    @Test
    fun `room heading and first row move together when remaining space is too small`() {
        val plan = composer.compose(twoRoomReport(), Audience.TENANT)

        val headingPage = plan.pages.single { page ->
            page.blocks.any { (it.content as? RoomTitleBlock)?.roomId == "room-2" }
        }
        val heading = headingPage.blocks.single { (it.content as? RoomTitleBlock)?.roomId == "room-2" }
        assertTrue(
            headingPage.blocks.any { (it.content as? ItemRowBlock)?.itemId == "item-5" },
            "room-2's heading is alone on page ${headingPage.number}; its first row went elsewhere",
        )
        val firstRow = headingPage.blocks.single { (it.content as? ItemRowBlock)?.itemId == "item-5" }

        assertEquals(15, heading.yMm, "the heading did not move to a fresh page with its row")
        assertEquals(heading.yMm + heading.heightMm, firstRow.yMm, "the row must follow its heading immediately")
        val previous = plan.pages[headingPage.number - 2]
        val previousEnd = previous.blocks.filterNot { it.content is FooterBlock }.maxOf { it.yMm + it.heightMm }
        assertEquals(249, previousEnd, "the fixture must leave a residual between one heading and heading+row")
        assertTrue(
            previousEnd + heading.heightMm <= 272,
            "the heading alone would not have fitted, so grouping is not what moved it",
        )
        assertTrue(
            previousEnd + heading.heightMm + firstRow.heightMm > 272,
            "heading and row both fitted, so grouping is not what moved them",
        )
        val runs = (heading.content as RoomTitleBlock).textRuns
        assertTrue(runs.any { it.language == TextLanguage.EN } && runs.any { it.language == TextLanguage.ZH })
    }

    /**
     * A section title is never left alone either. The title reduces the budget its first block is split to,
     * so the first chunk always fits beside it; splitting to the full body instead produces a first chunk
     * of 250 mm that no longer fits under the 10 mm title, and the placement fails outright.
     */
    @Test
    fun `a section title is grouped with the first chunk of an oversized first block`() {
        // 61 bilingual runs of 4 mm = 244 mm: under the 247 mm a title leaves, over it once the urgency
        // line is added, so the block splits into exactly two chunks.
        val base = ReportTestFixtures.report()
        val long = BilingualText("e".repeat(1_860), "修".repeat(1_800))
        val report = base.copy(remediations = listOf(ReportRemediation("item-poor", Urgency.HIGH, long)))

        val plan = composer.compose(report, Audience.LANDLORD)
        val titlePage = plan.pages.single { page ->
            page.blocks.any { (it.content as? SectionTitleBlock)?.key == "closing" }
        }
        val title = titlePage.blocks.single { (it.content as? SectionTitleBlock)?.key == "closing" }
        val chunks = plan.pages.flatMap { it.blocks }.filter { it.content is RemediationBlock }

        assertEquals(2, chunks.size, "the fixture must split the first closing block in two")
        assertEquals(listOf(246, 20), chunks.map { it.heightMm })
        val firstLanguages = (chunks.first().content as RemediationBlock).textRuns.map { it.language }
        assertTrue(TextLanguage.EN in firstLanguages && TextLanguage.ZH in firstLanguages)
        chunks.drop(1).forEachIndexed { index, chunk ->
            val languages = (chunk.content as RemediationBlock).textRuns.map { it.language }
            assertTrue(
                TextLanguage.EN !in languages && TextLanguage.ZH !in languages,
                "later chunk ${index + 2} repeats EN/ZH instead of keeping the pair in the first chunk: $languages",
            )
        }
        assertTrue(
            titlePage.blocks.any { it.content is RemediationBlock },
            "the section title was left alone on page ${titlePage.number}",
        )
        assertEquals(title.yMm + title.heightMm, chunks.first().yMm, "the first chunk must follow its title")
        plan.assertNothingOverflows()
    }

    // --- fixtures ---

    /** One item carrying more evidence than a single page can hold. */
    private fun photoHeavyItemReport(photoCount: Int, note: String?): ReportSnapshot {
        val base = ReportTestFixtures.canonical()
        val item = base.items.last().copy(note = note)
        val snapshots = (1..photoCount).map {
            PhotoSnapshot("heavy-$it", "camera", 1_755_303_000_000L + it * 60_000L, isRoomLevel = false)
        }
        val photos = snapshots.mapIndexed { index, snapshot ->
            ReportPhoto("p-${index + 1}", snapshot, privacy = false, "2.1.${index + 1}", snapshot.exifTimeMs!! + 500L)
        }
        return ReportSnapshot(
            canonical = base.copy(items = listOf(item), photos = snapshots),
            tenancyReference = null,
            rooms = listOf(
                ReportRoom(
                    "room-1",
                    BilingualText("Lounge", "客厅"),
                    listOf(ReportItem("item-big", item, BilingualText("Carpet", "地毯"), photos)),
                ),
            ),
            statusDefinitions = ReportTestFixtures.report().statusDefinitions,
        )
    }

    /**
     * Room 1 ends its page at y=249: a 12 mm heading, a 50 mm panorama and a first 18 mm row placed as one
     * group, then two more panoramas and three more rows. Room 2 then has 23 mm to land in.
     */
    private fun twoRoomReport(): ReportSnapshot {
        val base = ReportTestFixtures.canonical()
        val item = base.items.first()
        val snapshots = (1..3).map {
            PhotoSnapshot("room-photo-$it", "camera", 1_755_303_000_000L + it * 60_000L, isRoomLevel = true)
        }
        val photos = snapshots.mapIndexed { index, snapshot ->
            ReportPhoto("room-photo-${index + 1}", snapshot, privacy = false, "1.R.${index + 1}", snapshot.exifTimeMs!!)
        }
        return ReportSnapshot(
            canonical = base.copy(items = List(5) { item }, photos = snapshots),
            tenancyReference = null,
            rooms = listOf(
                ReportRoom(
                    "room-1",
                    BilingualText("Lounge", "客厅"),
                    (1..4).map { ReportItem("item-$it", item, BilingualText("Wall $it", "墙面 $it")) },
                    photos,
                ),
                ReportRoom(
                    "room-2",
                    BilingualText("Bedroom", "卧室"),
                    listOf(ReportItem("item-5", item, BilingualText("Window", "窗户"))),
                ),
            ),
            statusDefinitions = ReportTestFixtures.report().statusDefinitions,
        )
    }

    /** `reference` is presentation-only and outside the canonical hash domain, so caption length can vary. */
    private fun reportWithPhotoReference(reference: String): ReportSnapshot {
        val base = ReportTestFixtures.report()
        return base.copy(
            rooms = base.rooms.map { room ->
                room.copy(
                    photos = room.photos.map { it.copy(reference = "$reference.room") },
                    items = room.items.map { item -> item.copy(photos = item.photos.map { it.copy(reference = reference) }) },
                )
            },
        )
    }

    private fun reportWithAppendixPhotos(photoCount: Int, reference: String): ReportSnapshot {
        val base = ReportTestFixtures.canonical()
        val item = base.items.first()
        val snapshots = (1..photoCount).map { index ->
            PhotoSnapshot("appendix-$index", "camera", 1_755_500_000_000L + index, isRoomLevel = false)
        }
        val photos = snapshots.mapIndexed { index, snapshot ->
            ReportPhoto("appendix-${index + 1}", snapshot, false, "$reference.${index + 1}", snapshot.exifTimeMs!!)
        }
        return ReportSnapshot(
            canonical = base.copy(items = listOf(item), photos = snapshots),
            tenancyReference = null,
            rooms = listOf(
                ReportRoom(
                    "appendix-room",
                    BilingualText("Evidence room", "证据房间"),
                    listOf(ReportItem("appendix-item", item, BilingualText("Wall", "墙"), photos)),
                ),
            ),
            statusDefinitions = ReportTestFixtures.report().statusDefinitions,
        )
    }

    /** A room whose only content is one room-level photograph, visible or privacy-flagged. */
    private fun reportWithPhotoOnlyRoom(privacy: Boolean): ReportSnapshot {
        val base = ReportTestFixtures.report()
        val snapshot = PhotoSnapshot("ph-private", "camera", 1_755_400_000_000L, isRoomLevel = true)
        val photo = ReportPhoto("photo-private", snapshot, privacy, "R.P.1", 1_755_400_000_000L)
        return base.copy(
            canonical = base.canonical.copy(photos = base.canonical.photos + snapshot),
            rooms = base.rooms + ReportRoom("room-private", BilingualText("Ensuite", "套间"), emptyList(), listOf(photo)),
        )
    }

    private fun reportWithBoundaryPhotoOnlyRoom(): ReportSnapshot {
        val base = reportWithPhotoOnlyRoom(privacy = false)
        val fillerSnapshots = (1..2).map { index ->
            PhotoSnapshot("ph-fill-$index", "camera", 1_755_410_000_000L + index, isRoomLevel = true)
        }
        val fillerPhotos = fillerSnapshots.mapIndexed { index, snapshot ->
            ReportPhoto("photo-fill-${index + 1}", snapshot, false, "1.R.${index + 2}", snapshot.exifTimeMs!!)
        }
        return base.copy(
            canonical = base.canonical.copy(photos = base.canonical.photos + fillerSnapshots),
            rooms = base.rooms.mapIndexed { index, room ->
                if (index == 0) room.copy(photos = room.photos + fillerPhotos) else room
            },
        )
    }

    private fun reportWithBareRoom(): ReportSnapshot {
        val base = ReportTestFixtures.report()
        return base.copy(
            rooms = base.rooms + ReportRoom("room-bare", BilingualText("Garage", "车库"), emptyList(), emptyList()),
        )
    }

    /** One GOOD item per room, so the cover's room-by-status breakdown has exactly one line per room. */
    private fun manyRoomReport(rooms: Int, address: String? = null): ReportSnapshot {
        val base = ReportTestFixtures.canonical()
        val item = base.items.first()
        return ReportSnapshot(
            canonical = base.copy(
                items = List(rooms) { item },
                photos = emptyList(),
                property = base.property.copy(address = address ?: base.property.address),
            ),
            tenancyReference = null,
            rooms = (1..rooms).map { index ->
                ReportRoom(
                    "room-$index",
                    BilingualText("Room $index", "房间 $index"),
                    listOf(ReportItem("item-$index", item, BilingualText("Wall", "墙面"))),
                )
            },
            statusDefinitions = ReportTestFixtures.report().statusDefinitions,
        )
    }

    /**
     * Flipping a canonical field changes the canonical photo too, so the multiset guard stays satisfied and
     * the guard under test is the one that fires.
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

    private fun ReportSnapshot.withItemPhotoExif(exifTimeMs: Long?): ReportSnapshot {
        val flipped = rooms.map { room ->
            room.copy(
                items = room.items.map { item ->
                    item.copy(photos = item.photos.map { it.copy(snapshot = it.snapshot.copy(exifTimeMs = exifTimeMs)) })
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

    // --- assertions over drawn geometry ---

    /**
     * A unit a renderer actually puts on the page, in absolute page coordinates. Placed blocks are not the
     * whole story: an item row's thumbnails are drawn at the row's origin plus their own offset, so walking
     * only [PlacedBlock] geometry cannot see a picture column that runs off the bottom of the sheet.
     */
    private data class DrawnUnit(val tag: String, val topMm: Int, val heightMm: Int)

    private fun PagePlan.drawn(): List<DrawnUnit> = blocks
        .filterNot { it.content is FooterBlock }
        .flatMap { placed ->
            val slots = when (val content = placed.content) {
                is ItemRowBlock -> content.thumbnails
                is ImageSlotBlock -> listOf(content)
                else -> emptyList()
            }
            // A caption is drawn from its own run and not from the box above it, so a slot moved without its
            // runs puts the picture on the page and its provenance line off the bottom of the sheet. Walking
            // the box alone reports that page as clean.
            val nested = slots.flatMap { slot ->
                listOf(DrawnUnit("slot:${slot.photoId}", placed.yMm + slot.yMm, slot.heightMm)) +
                    slot.textRuns.mapIndexed { line, run ->
                        DrawnUnit("caption:${slot.photoId}:$line", placed.yMm + run.yMm, run.heightMm)
                    }
            }
            listOf(DrawnUnit(placed.content::class.simpleName.orEmpty(), placed.yMm, placed.heightMm)) + nested
        }

    private fun DocumentPlan.assertNothingOverflows() = pages.forEach { page ->
        page.drawn().forEach { unit ->
            assertTrue(
                unit.topMm >= 15 && unit.topMm + unit.heightMm <= 272,
                "page ${page.number}: ${unit.tag} spans ${unit.topMm}..${unit.topMm + unit.heightMm}mm",
            )
        }
    }

    private fun DocumentPlan.itemChunks(itemId: String): List<ItemRowBlock> = pages
        .flatMap { it.blocks }
        .mapNotNull { it.content as? ItemRowBlock }
        .filter { it.itemId == itemId }

    /** Item evidence lives inside its row, so collecting slots has to descend into item thumbnails. */
    private fun DocumentPlan.imageSlots(purpose: ImagePurpose? = null): List<ImageSlotBlock> = pages
        .flatMap { it.blocks }
        .flatMap { placed ->
            when (val content = placed.content) {
                is ImageSlotBlock -> listOf(content)
                is ItemRowBlock -> content.thumbnails
                else -> emptyList()
            }
        }
        .filter { purpose == null || it.purpose == purpose }

    private fun PagePlan.appendixSlots(): List<Pair<PlacedBlock, ImageSlotBlock>> = blocks.mapNotNull { placed ->
        (placed.content as? ImageSlotBlock)?.takeIf { it.purpose == ImagePurpose.APPENDIX }?.let { placed to it }
    }
}
