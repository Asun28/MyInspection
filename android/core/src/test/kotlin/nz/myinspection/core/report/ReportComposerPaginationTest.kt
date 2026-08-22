package nz.myinspection.core.report

import nz.myinspection.core.model.PhotoSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Production breaks under test: a greedy block-by-block paginator can orphan a room heading, split a
 * bilingual row or image slot, overflow A4, leak privacy photos, or silently lay out a projection that
 * no longer matches the canonical hash input.
 */
class ReportComposerPaginationTest {
    private val composer = ReportComposer(ReportTestFixtures.measurer)

    @Test
    fun `eighty photos paginate without overflow orphan heading or split image`() {
        val canonicalPhotos = (1..80).map { index ->
            PhotoSnapshot("hash-$index", "camera", index.toLong(), isRoomLevel = false)
        }
        val photos = canonicalPhotos.mapIndexed { index, photo ->
            ReportPhoto(
                "photo-${index + 1}",
                photo,
                privacy = false,
                reference = "1.1.${index + 1}",
                capturedAt = index.toLong() + 100,
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
            statusDefinitions = listOf(
                StatusDefinition("GOOD", BilingualText("Good", "良好"), BilingualText("No issue", "无问题")),
                StatusDefinition("FAIR", BilingualText("Fair", "一般"), BilingualText("Wear", "损耗")),
                StatusDefinition("POOR", BilingualText("Poor", "较差"), BilingualText("Attention", "需处理")),
                StatusDefinition(
                    "NOT_APPLICABLE",
                    BilingualText("Not applicable", "不适用"),
                    BilingualText("Does not apply", "不适用"),
                ),
            ),
        )

        val plan = composer.compose(report, Audience.TENANT)

        assertTrue(plan.pages.size <= 65, "80-photo report unexpectedly expanded to ${plan.pages.size} pages")
        for (page in plan.pages) {
            val body = page.blocks.filterNot { it.content is FooterBlock }
            assertTrue(body.all { it.yMm >= 15 && it.yMm + it.heightMm <= 272 }, "page ${page.number} overflow")
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
        assertTrue(plan.imageSlots().all { it.source.isNotBlank() && it.reference.isNotBlank() && it.capturedAt > 0 })
    }

    @Test
    fun `privacy photos are excluded by default and included only by explicit option`() {
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

        assertTrue(composer.compose(report, Audience.LANDLORD).imageSlots().isEmpty())
        val included = composer.compose(report, Audience.LANDLORD, ReportOptions(includePrivacyPhotos = true))
        assertEquals(listOf("private-photo", "private-photo"), included.imageSlots().map { it.photoId })
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

        assertFailsWith<IllegalArgumentException> { composer.compose(wrongItems, Audience.LANDLORD) }
        assertFailsWith<IllegalArgumentException> { composer.compose(missingPhoto, Audience.LANDLORD) }
    }

    @Test
    fun `injected text measurement expands a wrapped item row without overflowing the page`() {
        val report = ReportTestFixtures.report()
        val longItem = report.rooms.single().items.last().let { item ->
            item.copy(snapshot = item.snapshot.copy(note = "wrapped ".repeat(50)))
        }
        val changed = report.copy(
            canonical = report.canonical.copy(items = listOf(report.canonical.items.first(), longItem.snapshot)),
            rooms = listOf(report.rooms.single().copy(items = listOf(report.rooms.single().items.first(), longItem))),
        )

        val row = composer.compose(changed, Audience.LANDLORD).pages
            .flatMap { it.blocks }
            .single { (it.content as? ItemRowBlock)?.itemId == "item-poor" }
        assertTrue(row.heightMm > 18)
        assertTrue(row.yMm + row.heightMm <= BODY_BOTTOM_MM)
        assertTrue((row.content as ItemRowBlock).textRuns.any { it.language == TextLanguage.ORIGINAL })
    }

    @Test
    fun `rental and annual glossaries must exactly match their authoritative status domains`() {
        val rental = ReportTestFixtures.report()
        assertFailsWith<IllegalArgumentException> {
            composer.compose(rental.copy(statusDefinitions = rental.statusDefinitions.dropLast(1)), Audience.LANDLORD)
        }

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

    @Test
    fun `large adverse collection and long original text continue across bounded pages`() {
        val base = ReportTestFixtures.canonical()
        val items = (1..20).map { index ->
            base.items.last().copy(stableId = "item-$index", note = if (index == 1) "long note ".repeat(2_000) else "issue")
        }
        val reportItems = items.mapIndexed { index, item ->
            ReportItem("report-item-${index + 1}", item, BilingualText("Item ${index + 1}", "检查项 ${index + 1}"))
        }
        val report = ReportSnapshot(
            canonical = base.copy(items = items, photos = emptyList()),
            tenancyReference = null,
            rooms = listOf(ReportRoom("room-many", BilingualText("Many items", "多个检查项"), reportItems)),
            statusDefinitions = ReportTestFixtures.report().statusDefinitions,
            supplements = listOf(ReportSupplement("LONG", "supplement ".repeat(2_000))),
        )

        val plan = composer.compose(report, Audience.LANDLORD)
        assertEquals(20, plan.pages.flatMap { it.blocks }.count { it.content is SummaryItemBlock })
        assertTrue(plan.pages.flatMap { it.blocks }.count { it.content is ItemRowBlock } > 20)
        assertTrue(plan.pages.flatMap { it.blocks }.count { it.content is SupplementBlock } > 1)
        assertTrue(
            plan.pages.flatMap { it.blocks }
                .filterNot { it.content is FooterBlock }
                .all { it.yMm >= PAGE_MARGIN_MM && it.yMm + it.heightMm <= BODY_BOTTOM_MM },
        )
    }

    @Test
    fun `room heading and first row move together when remaining space is too small`() {
        val base = ReportTestFixtures.canonical()
        val thirdItem = base.items.first().copy(stableId = "bedroom.window")
        val roomPhotos = (1..4).map { index ->
            PhotoSnapshot("room-photo-$index", "camera", index.toLong(), isRoomLevel = true)
        }
        val presentationPhotos = roomPhotos.mapIndexed { index, photo ->
            ReportPhoto(
                "room-photo-${index + 1}",
                photo,
                privacy = false,
                reference = "1.R.${index + 1}",
                capturedAt = index.toLong() + 100,
            )
        }
        val report = ReportSnapshot(
            canonical = base.copy(items = base.items + thirdItem, photos = roomPhotos),
            tenancyReference = null,
            rooms = listOf(
                ReportRoom(
                    "room-1",
                    BilingualText("Lounge", "客厅"),
                    listOf(
                        ReportItem("item-1", base.items[0], BilingualText("Wall", "墙面")),
                        ReportItem("item-2", base.items[1], BilingualText("Carpet", "地毯")),
                    ),
                    presentationPhotos,
                ),
                ReportRoom(
                    "room-2",
                    BilingualText("Bedroom", "卧室"),
                    listOf(ReportItem("item-3", thirdItem, BilingualText("Window", "窗户"))),
                ),
            ),
            statusDefinitions = listOf(
                StatusDefinition(
                    "GOOD",
                    BilingualText("Good", "良好"),
                    BilingualText("No issue", "无问题"),
                ),
                StatusDefinition("FAIR", BilingualText("Fair", "一般"), BilingualText("Wear", "损耗")),
                StatusDefinition(
                    "POOR",
                    BilingualText("Poor", "较差"),
                    BilingualText("Needs attention", "需要处理"),
                ),
                StatusDefinition(
                    "NOT_APPLICABLE",
                    BilingualText("Not applicable", "不适用"),
                    BilingualText("Does not apply", "不适用"),
                ),
            ),
        )
        val plan = composer.compose(report, Audience.TENANT)

        val headingPage = plan.pages.single { page ->
            page.blocks.any { (it.content as? RoomTitleBlock)?.roomId == "room-2" }
        }
        assertTrue(headingPage.blocks.any { (it.content as? ItemRowBlock)?.itemId == "item-3" })
        val heading = headingPage.blocks.single { (it.content as? RoomTitleBlock)?.roomId == "room-2" }
        val runs = (heading.content as RoomTitleBlock).textRuns
        assertTrue(runs.any { it.language == TextLanguage.EN } && runs.any { it.language == TextLanguage.ZH })
        assertTrue(heading.yMm + heading.heightMm <= BODY_BOTTOM_MM)
    }

    /** Item evidence now lives inside its row, so collecting slots has to descend into item thumbnails. */
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
}
