package nz.myinspection.core.e2e

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
            val publicSentinel = evidence.fixture.report.tenantExpectedSentinels.single()
            val landlordJudgmentRows = evidence.judgmentLandlordPlan.pages
                .flatMap { it.blocks }
                .map { it.content }
                .filterIsInstance<ItemRowBlock>()
            val judgmentTenant = evidence.judgmentTenantPlan
            val judgmentTenantBlocks = judgmentTenant.pages
                .flatMap { it.blocks }
                .map { it.content }
            val judgmentTenantRows = judgmentTenantBlocks.filterIsInstance<ItemRowBlock>()
            val judgmentTenantDrawnText = judgmentTenant.pages.flatMap { page ->
                page.blocks.flatMap { placed ->
                    val direct = (placed.content as? TextBearingBlock)?.textRuns.orEmpty()
                    val thumbnails = (placed.content as? ItemRowBlock)?.thumbnails.orEmpty().flatMap { it.textRuns }
                    direct + thumbnails
                }
            }.joinToString("\n") { it.text }
            val landlordTarget = landlordJudgmentRows.single { it.note == publicSentinel }
            val tenantTarget = judgmentTenantRows.single { it.note == publicSentinel }
            assertEquals(evidence.judgmentLandlordPlan.dataHash, judgmentTenant.dataHash)
            assertNotEquals(evidence.fixture.expectedDataHash, judgmentTenant.dataHash, "judgment control must use its modified canonical input")
            assertEquals("DAMAGE", landlordTarget.wearOrDamage, "control report must carry a non-null internal judgment")
            assertNull(tenantTarget.wearOrDamage, "the same objective row must remain while its internal judgment is removed")
            judgmentTenantRows.forEach { assertNull(it.wearOrDamage) }
            assertTrue(publicSentinel in judgmentTenant.toString())
            assertTrue(publicSentinel in judgmentTenantDrawnText)
            assertTrue(judgmentTenantBlocks.none { it is RemediationBlock })
            evidence.fixture.report.tenantForbiddenSentinels.forEach { sentinel ->
                assertFalse(sentinel in judgmentTenant.toString(), "judgment-bearing tenant structure leaked $sentinel")
                assertFalse(sentinel in judgmentTenantDrawnText, "judgment-bearing tenant drawing leaked $sentinel")
            }

            assertEquals(evidence.fixture.expectedDataHash, tenant.dataHash)
            tenant.pages.forEach { page ->
                val footer = page.blocks.single { it.content is FooterBlock }.content as FooterBlock
                assertEquals(evidence.fixture.expectedDataHash, footer.dataHash)
            }
        }
    }
}
