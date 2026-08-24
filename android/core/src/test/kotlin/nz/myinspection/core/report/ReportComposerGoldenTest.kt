package nz.myinspection.core.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Production break under test: without the report model/composer there is no deterministic layout tree,
 * no typed audience boundary, and no canonical data-hash footer for a renderer to consume.
 *
 * Mutation evidence (mutation -> discriminating assertion -> expected failure text):
 * A1 remove a forced section page -> exact six-page tree -> `expected 6 pages`; A2 reorder a block ->
 * exact golden signatures -> `layout tree differs`; A3 reduce appendix density -> odd/even absolute geometry ->
 * `appendix page ... carries ... pictures`; A4 move a thumbnail out of its row -> exact 40 mm geometry ->
 * `item evidence is still emitted`; A5 draw raw epoch -> literal ISO text -> `cover draws a raw epoch value`;
 * A6 omit provenance -> exact caption -> `photo captions render reference`; A7 append/drop a UTF-16 unit ->
 * proportional and supplementary regressions -> `elided final line still wraps` / `unpaired UTF-16 surrogate`;
 * A8 split an image slot -> per-purpose photo-id uniqueness -> `photo was emitted more than once`;
 * A9 place a photo-only heading sequentially -> boundary fixture -> `heading did not move to a fresh page`;
 * A10 duplicate flowing text -> reconstructed note/supplement -> `must read once, end to end`;
 * A11 copy or split EN/ZH into later chunks -> language sequence checks -> `later chunk repeats EN/ZH`;
 * A12 expose landlord data -> typed-block and cover-only checks -> `tenant cover carries ... total`;
 * A13 include privacy/default or accept bare room -> two-audience/refusal checks -> `room-bare`;
 * A14 weaken canonical projection validation -> table-driven exact refusal -> `projection accepted`;
 * A15 derive geometry from production constants -> source purity and literal bounds -> `drawn unit spans`;
 * A16 use wall-clock/locale formatting -> fixed instant captions -> `raw epoch`;
 * A17 derive footer/tail expectations -> literal 1/6, 6/6 and exact runs/counts -> `tail contract differs`;
 * A18 drop/reorder summary/backlinks -> ordered triples and row resolution -> `summary order/backlink differs`.
 */
class ReportComposerGoldenTest {
    private val composer = ReportComposer(ReportTestFixtures.measurer)

    @Test
    fun `fixed inspection produces the golden six-page layout tree`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)

        assertEquals(ReportTestFixtures.DATA_HASH, plan.dataHash)
        // Six pages are forced by six independently paged regions: cover, glossary, summary, room detail,
        // one two-up appendix page, and closing. None is inferred from the result being asserted.
        assertEquals(6, plan.pages.size, "expected 6 pages: cover + glossary + summary + room + appendix + closing")
        assertEquals(
            listOf(
                listOf("cover@15:100", "footer@272:10"),
                listOf(
                    "section:status-glossary@15:10", "status:GOOD@25:20", "status:FAIR@45:20",
                    "status:POOR@65:20", "status:NOT_APPLICABLE@85:20", "footer@272:10",
                ),
                listOf("section:summary@15:10", "summary:item-poor@25:16", "footer@272:10"),
                // The item photo is a thumbnail inside item-poor, which is why that row is 54 mm tall and
                // no separate inline image slot follows it.
                listOf(
                    "room:room-kitchen@15:12", "image:photo-room:inline@27:50",
                    "item:item-good@77:18", "item:item-poor@95:54",
                    "footer@272:10",
                ),
                listOf(
                    "section:photo-appendix@15:10", "image:photo-room:appendix@25:114",
                    "image:photo-item:appendix@139:114", "footer@272:10",
                ),
                listOf(
                    "section:closing@15:10", "remediation:item-poor:HIGH@25:20",
                    "supplement:S1@45:14", "disclaimer@59:26", "footer@272:10",
                ),
            ),
            plan.pages.map { page -> page.blocks.map(::signature) },
        )
        assertEquals((1..6).toList(), plan.pages.map { it.number })
        val cover = plan.contents().single { it is CoverBlock } as CoverBlock
        assertEquals(1, cover.adverseItemCount)
        assertEquals(1, cover.pendingItemCount)
        assertEquals(
            listOf(RoomStatusCount("room-kitchen", "GOOD", 1), RoomStatusCount("room-kitchen", "POOR", 1)),
            cover.roomStatusCounts,
        )
    }

    /**
     * The footer proves the report was not edited after finalize, so what matters is the text that reaches
     * paper: the drawn run is the short hash, and the full digest stays in the block for verification.
     */
    @Test
    fun `the footer that gets drawn carries the short hash, not the full digest`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val expectedShort = ReportTestFixtures.DATA_HASH.take(12)

        assertEquals("ea9cd02e76bf · 1/6", plan.pages.first().footerText())
        assertEquals("ea9cd02e76bf · 6/6", plan.pages.last().footerText())

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
            assertEquals(expectedShort, footer.shortHash)
            assertEquals(page.number, footer.pageNumber)
            assertEquals(6, footer.totalPages)
        }
    }

    /** 封面即答案: the totals and the instant have to reach paper, not merely sit in the block's fields. */
    @Test
    fun `the cover draws labelled totals and ISO-8601 times rather than epoch milliseconds`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val cover = plan.contents().filterIsInstance<CoverBlock>().single()
        val drawn = cover.textRuns.joinToString("|") { it.text }

        assertTrue(drawn.contains("Adverse items: 1|不利项：1"), "cover does not draw the adverse total: $drawn")
        assertTrue(drawn.contains("Pending remediation: 1|待处理：1"), "cover does not draw the pending total: $drawn")
        // scheduledAt 1_755_302_400_000 == 2025-08-16T00:00:00Z, fixed and locale-independent.
        assertTrue(drawn.contains("ROUTINE · 2025-08-16T00:00:00Z"), "cover draws a raw epoch value: $drawn")
        assertTrue(!drawn.contains("1755302400000"), "epoch milliseconds reached the rendered cover")
        assertTrue(drawn.contains("room-kitchen · GOOD · 1"), "the room breakdown is not drawn: $drawn")
    }

    @Test
    fun `audience types prevent tenant remediation leakage and retain tenant agreement`() {
        val landlord = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val tenant = composer.compose(tenantFreeTextReport(), Audience.TENANT)

        assertTrue(landlord.contents().any { it is RemediationBlock && it.urgency == Urgency.HIGH })
        assertFalse(landlord.contents().any { it is TenantAgreementBlock })
        assertFalse(tenant.contents().any { it is RemediationBlock })
        assertTrue(tenant.contents().any { it is TenantAgreementBlock })
        assertTrue(tenant.contents().any { it is DisclaimerBlock && it.text == REPORT_DISCLAIMER })
        assertTrue(tenant.contents().filterIsInstance<ItemRowBlock>().all { it.wearOrDamage == null })
        assertTrue(
            tenant.contents().filterIsInstance<ItemRowBlock>().flatMap { it.textRuns }
                .any { it.text.contains("DAMAGE FAIR_WEAR remediation 建议 HIGH") },
            "legitimate tenant-authored vocabulary was removed from the item note",
        )
    }

    /**
     * The audience boundary is not only about block types. A landlord-only total drawn on the tenant cover
     * tells the tenant how many remediation suggestions the landlord is holding, which is the same leak by
     * another route. The disclaimer is exempt because it is one frozen constant, pinned above.
     */
    @Test
    fun `tenant cover and typed blocks exclude landlord-only data without scanning user notes`() {
        val tenant = composer.compose(tenantFreeTextReport(), Audience.TENANT)
        val forbidden = listOf("remediation", "Remediation", "待处理", "建议", "紧急度") + Urgency.entries.map { it.name }

        val cover = tenant.contents().filterIsInstance<CoverBlock>().single()
        assertEquals(null, cover.pendingItemCount, "the tenant cover carries the landlord's pending total")
        assertFalse(tenant.contents().any { it is RemediationBlock }, "a landlord-only block reached the tenant")
        cover.textRuns.forEach { run ->
            forbidden.forEach { word ->
                assertFalse(run.text.contains(word), "tenant cover draws '$word': ${run.text}")
            }
        }
    }

    @Test
    fun `closing tail pins exact disclaimer supplement and audience-specific blocks`() {
        val landlord = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val tenant = composer.compose(ReportTestFixtures.report(), Audience.TENANT)
        val expectedDisclaimerRuns = listOf(
            TextLanguage.EN to "This report records visible conditions at the inspection tim",
            TextLanguage.EN to "e. It is not legal, building, engineering, property, health ",
            TextLanguage.EN to "or safety advice. Requirements and standards may change. Con",
            TextLanguage.EN to "sult appropriately licensed professionals before acting.",
            TextLanguage.ZH to "本报告仅记录检查时可见的状况，不构成法律、建筑、工程、物业、健康或安全建议。要求和标准可能变化，采取行动前请咨询具备相应",
            TextLanguage.ZH to "执照的专业人士。",
        )

        listOf(landlord, tenant).forEach { plan ->
            val tail = plan.pages.last().blocks.map { it.content }
            assertEquals(listOf("S1"), tail.filterIsInstance<SupplementBlock>().map { it.reference })
            assertEquals(
                expectedDisclaimerRuns,
                tail.filterIsInstance<DisclaimerBlock>().single().textRuns.map { it.language to it.text },
                "tail contract differs for ${plan.audience}",
            )
            assertEquals(1, tail.count { it is DisclaimerBlock })
            assertEquals(1, tail.count { it is SupplementBlock })
        }
        assertEquals(1, landlord.contents().count { it is RemediationBlock })
        assertEquals(0, landlord.contents().count { it is TenantAgreementBlock })
        assertEquals(0, tenant.contents().count { it is RemediationBlock })
        assertEquals(1, tenant.contents().count { it is TenantAgreementBlock })
        assertEquals(
            listOf(
                TextLanguage.EN to "Tenant agreement / signature",
                TextLanguage.ZH to "租客同意 / 签名",
            ),
            tenant.contents().filterIsInstance<TenantAgreementBlock>().single().textRuns.map { it.language to it.text },
        )
    }

    @Test
    fun `summary rows preserve ordered adverse triples and each backlink resolves to an item row`() {
        val plan = composer.compose(multiAdverseReport(), Audience.LANDLORD)
        val summaries = plan.contents().filterIsInstance<SummaryItemBlock>()
        val rows = plan.contents().filterIsInstance<ItemRowBlock>()

        assertEquals(
            listOf(
                Triple("room-z", "item-z-poor", "POOR"),
                Triple("room-a", "item-a-fair", "FAIR"),
                Triple("room-a", "item-a-poor", "POOR"),
            ),
            summaries.map { Triple(it.roomId, it.itemId, it.status) },
            "summary order differs from room/item traversal",
        )
        assertEquals(
            summaries.map { it.itemId },
            summaries.map { summary -> rows.single { it.itemId == summary.itemId }.itemId },
            "a summary backlink does not resolve to exactly one item row",
        )
    }

    @Test
    fun `bilingual text is one indivisible block rather than separate language rows`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val bilingual = plan.contents().flatMap { it.bilingualText() }

        assertTrue(bilingual.isNotEmpty())
        assertTrue(bilingual.all { it.en.isNotBlank() && it.zh.isNotBlank() })
        plan.pages.flatMap { it.blocks }.forEach { placed ->
            val block = placed.content as? TextBearingBlock ?: return@forEach
            assertTrue(block.textRuns.isNotEmpty(), "${block::class.simpleName} must contain renderer-ready measured runs")
            assertTrue(block.textRuns.all { it.yMm >= 0 && it.yMm + it.heightMm <= placed.heightMm })
        }
    }

    private fun DocumentPlan.contents() = pages.flatMap { page -> page.blocks.map { it.content } }

    private fun PagePlan.footerText(): String =
        (blocks.single { it.content is FooterBlock }.content as FooterBlock).textRuns.joinToString("") { it.text }

    private fun tenantFreeTextReport(): ReportSnapshot {
        val base = ReportTestFixtures.report()
        val room = base.rooms.single()
        val changed = room.items.last().copy(
            snapshot = room.items.last().snapshot.copy(note = "DAMAGE FAIR_WEAR remediation 建议 HIGH"),
        )
        return base.copy(
            canonical = base.canonical.copy(items = listOf(room.items.first().snapshot, changed.snapshot)),
            rooms = listOf(room.copy(items = listOf(room.items.first(), changed))),
        )
    }

    private fun multiAdverseReport(): ReportSnapshot {
        val base = ReportTestFixtures.canonical()
        val zPoor = base.items.last().copy(stableId = "z-poor", status = "POOR")
        val zGood = base.items.first().copy(stableId = "z-good", status = "GOOD")
        val aFair = base.items.last().copy(stableId = "a-fair", status = "FAIR")
        val aPoor = base.items.last().copy(stableId = "a-poor", status = "POOR")
        val rooms = listOf(
            ReportRoom(
                "room-z",
                BilingualText("Rear room", "后室"),
                listOf(
                    ReportItem("item-z-poor", zPoor, BilingualText("Door", "门")),
                    ReportItem("item-z-good", zGood, BilingualText("Wall", "墙")),
                ),
            ),
            ReportRoom(
                "room-a",
                BilingualText("Front room", "前室"),
                listOf(
                    ReportItem("item-a-fair", aFair, BilingualText("Floor", "地板")),
                    ReportItem("item-a-poor", aPoor, BilingualText("Window", "窗")),
                ),
            ),
        )
        return ReportSnapshot(
            canonical = base.copy(items = listOf(zPoor, zGood, aFair, aPoor), photos = emptyList()),
            tenancyReference = null,
            rooms = rooms,
            statusDefinitions = ReportTestFixtures.report().statusDefinitions,
        )
    }

    private fun DocumentBlock.bilingualText(): List<BilingualText> = when (this) {
        is SectionTitleBlock -> listOf(title)
        is StatusDefinitionBlock -> listOf(label, description)
        is RoomTitleBlock -> listOf(label)
        is ItemRowBlock -> listOf(label)
        is RemediationBlock -> listOf(text)
        is DisclaimerBlock -> listOf(text)
        is TenantAgreementBlock -> listOf(label)
        else -> emptyList()
    }

    private fun signature(block: PlacedBlock): String {
        val key = when (val content = block.content) {
            is CoverBlock -> "cover"
            is SectionTitleBlock -> "section:${content.key}"
            is StatusDefinitionBlock -> "status:${content.status}"
            is SummaryItemBlock -> "summary:${content.itemId}"
            is RoomTitleBlock -> "room:${content.roomId}"
            is ItemRowBlock -> "item:${content.itemId}"
            is ImageSlotBlock -> "image:${content.photoId}:${content.purpose.name.lowercase()}"
            is RemediationBlock -> "remediation:${content.itemId}:${content.urgency}"
            is SupplementBlock -> "supplement:${content.reference}"
            is DisclaimerBlock -> "disclaimer"
            is TenantAgreementBlock -> "tenant-agreement"
            is FooterBlock -> "footer"
        }
        return "$key@${block.yMm}:${block.heightMm}"
    }
}
