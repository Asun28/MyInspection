package nz.myinspection.core.e2e

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nz.myinspection.core.report.FooterBlock

class GoldenEvidenceCoreE2ETest {
    @Test
    fun `real persisted evidence has one hash across DB landlord report and independent recalculation`() {
        GoldenEvidenceCoreHarness.execute().use { evidence ->
            val expectedHash = evidence.fixture.expectedDataHash

            assertEquals(expectedHash, evidence.dbDataHash, "finalize must persist the frozen golden hash")
            assertEquals(expectedHash, evidence.landlordPlan.dataHash, "the report plan must carry the DB hash")
            assertEquals(expectedHash, evidence.independentDataHash, "the DB-only independent calculation must agree")
            assertEquals(
                evidence.fixture.photos.map { it.expectedContentHash }.sorted(),
                evidence.persistedPhotoHashes.sorted(),
                "every fixture photo must be associated in the database with its streamed digest",
            )
            assertEquals(
                evidence.fixture.photos.map { it.expectedContentHash }.sorted(),
                evidence.assetContentHashes.sorted(),
                "every fixture photo must exist on disk with its streamed digest",
            )

            val footers = evidence.landlordPlan.pages.map { page ->
                page.blocks.single { it.content is FooterBlock }.content as FooterBlock
            }
            assertTrue(footers.isNotEmpty())
            footers.forEach { footer ->
                assertEquals(expectedHash, footer.dataHash)
                assertEquals(expectedHash.take(12), footer.shortHash)
                assertEquals(
                    listOf("${expectedHash.take(12)} · ${footer.pageNumber}/${footer.totalPages}"),
                    footer.textRuns.map { it.text },
                    "the drawn footer must expose the short hash and page sequence",
                )
            }
        }
    }
}
