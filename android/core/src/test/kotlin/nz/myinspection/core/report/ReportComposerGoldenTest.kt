package nz.myinspection.core.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Production break under test: without the report model/composer there is no deterministic layout tree,
 * no typed audience boundary, and no canonical data-hash footer for a renderer to consume.
 */
class ReportComposerGoldenTest {
    private val composer = ReportComposer(ReportTestFixtures.measurer)

    @Test
    fun `fixed inspection produces the golden six-page layout tree`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)

        assertEquals(ReportTestFixtures.DATA_HASH, plan.dataHash)
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
        val tenant = composer.compose(ReportTestFixtures.report(), Audience.TENANT)

        assertTrue(landlord.contents().any { it is RemediationBlock && it.urgency == Urgency.HIGH })
        assertFalse(landlord.contents().any { it is TenantAgreementBlock })
        assertFalse(tenant.contents().any { it is RemediationBlock })
        assertTrue(tenant.contents().any { it is TenantAgreementBlock })
        assertTrue(tenant.contents().any { it is DisclaimerBlock && it.text == REPORT_DISCLAIMER })
        assertTrue(tenant.contents().filterIsInstance<ItemRowBlock>().all { it.wearOrDamage == null })
        assertFalse(
            tenant.contents().filterIsInstance<TextBearingBlock>()
                .flatMap { it.textRuns }
                .any { it.text.contains("DAMAGE") || it.text.contains("FAIR_WEAR") },
        )
    }

    /**
     * The audience boundary is not only about block types. A landlord-only total drawn on the tenant cover
     * tells the tenant how many remediation suggestions the landlord is holding, which is the same leak by
     * another route. The disclaimer is exempt because it is one frozen constant, pinned above.
     */
    @Test
    fun `no landlord-only word reaches any tenant page`() {
        val tenant = composer.compose(ReportTestFixtures.report(), Audience.TENANT)
        val forbidden = listOf("remediation", "Remediation", "待处理", "建议", "紧急度") + Urgency.entries.map { it.name }

        val cover = tenant.contents().filterIsInstance<CoverBlock>().single()
        assertEquals(null, cover.pendingItemCount, "the tenant cover carries the landlord's pending total")
        tenant.contents().filterIsInstance<TextBearingBlock>()
            .filterNot { it is DisclaimerBlock }
            .flatMap { block -> block.textRuns.map { block to it.text } }
            .forEach { (block, drawn) ->
                forbidden.forEach { word ->
                    assertFalse(drawn.contains(word), "${block::class.simpleName} draws '$word' to the tenant: $drawn")
                }
            }
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
