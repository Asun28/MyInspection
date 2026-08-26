package nz.myinspection.core.e2e

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nz.myinspection.core.report.FooterBlock
import nz.myinspection.core.report.ItemRowBlock
import nz.myinspection.core.report.RemediationBlock
import nz.myinspection.core.report.TextBearingBlock

class GoldenEvidenceTenantRedactionE2ETest {
    @Test
    fun `tenant plan retains objective evidence without landlord remediation or private photo sentinels`() {
        GoldenEvidenceCoreHarness.execute().use { evidence ->
            val landlord = evidence.landlordPlan
            val tenant = evidence.tenantPlan
            val landlordCorpus = landlord.toString()
            val tenantCorpus = tenant.toString()
            val tenantDrawnText = tenant.pages.flatMap { page ->
                page.blocks.flatMap { placed ->
                    val direct = (placed.content as? TextBearingBlock)?.textRuns.orEmpty()
                    val thumbnails = (placed.content as? ItemRowBlock)?.thumbnails.orEmpty().flatMap { it.textRuns }
                    direct + thumbnails
                }
            }.joinToString("\n") { it.text }

            evidence.fixture.report.landlordExpectedSentinels.forEach { sentinel ->
                assertTrue(sentinel in landlordCorpus, "landlord control must expose $sentinel")
            }
            evidence.fixture.report.tenantExpectedSentinels.forEach { sentinel ->
                assertTrue(sentinel in tenantCorpus, "tenant report must retain public evidence $sentinel")
                assertTrue(sentinel in tenantDrawnText, "public evidence must be drawn, not metadata-only")
            }
            evidence.fixture.report.tenantForbiddenSentinels.forEach { sentinel ->
                assertFalse(sentinel in tenantCorpus, "tenant structure leaked $sentinel")
                assertFalse(sentinel in tenantDrawnText, "tenant drawing leaked $sentinel")
            }

            val tenantBlocks = tenant.pages.flatMap { it.blocks }.map { it.content }
            assertTrue(tenantBlocks.none { it is RemediationBlock })
            val landlordJudgments = evidence.judgmentLandlordPlan.pages
                .flatMap { it.blocks }
                .map { it.content }
                .filterIsInstance<ItemRowBlock>()
                .mapNotNull { it.wearOrDamage }
            val tenantJudgmentRows = evidence.judgmentTenantPlan.pages
                .flatMap { it.blocks }
                .map { it.content }
                .filterIsInstance<ItemRowBlock>()
            assertEquals(listOf("DAMAGE"), landlordJudgments, "control report must carry a non-null internal judgment")
            assertTrue(tenantJudgmentRows.isNotEmpty())
            tenantJudgmentRows.forEach { assertNull(it.wearOrDamage) }

            assertEquals(evidence.fixture.expectedDataHash, tenant.dataHash)
            tenant.pages.forEach { page ->
                val footer = page.blocks.single { it.content is FooterBlock }.content as FooterBlock
                assertEquals(evidence.fixture.expectedDataHash, footer.dataHash)
            }
        }
    }
}
