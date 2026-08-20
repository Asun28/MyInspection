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
                listOf("section:status-glossary@15:10", "status:GOOD@25:20", "status:POOR@45:20", "footer@272:10"),
                listOf("section:summary@15:10", "summary:item-poor@25:16", "footer@272:10"),
                listOf(
                    "room:room-kitchen@15:12", "image:photo-room:inline@27:48",
                    "item:item-good@75:18", "item:item-poor@93:18", "image:photo-item:inline@111:48",
                    "footer@272:10",
                ),
                listOf(
                    "section:photo-appendix@15:10", "image:photo-room:appendix@25:120",
                    "image:photo-item:appendix@145:120", "footer@272:10",
                ),
                listOf(
                    "section:closing@15:10", "remediation:item-poor:HIGH@25:20",
                    "supplement:S1@45:14", "disclaimer@59:24", "footer@272:10",
                ),
            ),
            plan.pages.map { page -> page.blocks.map(::signature) },
        )
        assertEquals((1..6).toList(), plan.pages.map { it.number })
        plan.pages.forEach { page ->
            val footer = page.blocks.single { it.content is FooterBlock }.content as FooterBlock
            assertEquals(ReportTestFixtures.DATA_HASH, footer.dataHash)
            assertEquals(ReportTestFixtures.DATA_HASH.take(12), footer.shortHash)
            assertEquals(page.number, footer.pageNumber)
            assertEquals(6, footer.totalPages)
        }
    }

    @Test
    fun `audience types prevent tenant remediation leakage and retain tenant agreement`() {
        val landlord = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val tenant = composer.compose(ReportTestFixtures.report(), Audience.TENANT)

        assertTrue(landlord.contents().any { it is RemediationBlock && it.urgency == Urgency.HIGH })
        assertFalse(landlord.contents().any { it is TenantAgreementBlock })
        assertFalse(tenant.contents().any { it is RemediationBlock })
        assertTrue(tenant.contents().any { it is TenantAgreementBlock })
        assertTrue(tenant.contents().any { it is DisclaimerBlock })
    }

    @Test
    fun `bilingual text is one indivisible block rather than separate language rows`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val bilingual = plan.contents().flatMap { it.bilingualText() }

        assertTrue(bilingual.isNotEmpty())
        assertTrue(bilingual.all { it.en.isNotBlank() && it.zh.isNotBlank() })
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
