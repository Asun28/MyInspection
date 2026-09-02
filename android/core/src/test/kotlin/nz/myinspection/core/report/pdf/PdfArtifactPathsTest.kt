package nz.myinspection.core.report.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import nz.myinspection.core.report.Audience

/** A3. Two audiences times four qualities is eight artifacts of one inspection that must all coexist. */
class PdfArtifactPathsTest {
    @Test
    fun `a report path names the property, the inspection, the audience and the quality`() {
        assertEquals(
            "reports/prop-0001/insp-0001-landlord-medium.pdf",
            PdfArtifactPaths.reportRelPath("prop-0001", "insp-0001", Audience.LANDLORD, PdfExportQuality.MEDIUM),
        )
        assertEquals(
            "reports/prop-0001/insp-0001-tenant-extra_high.pdf",
            PdfArtifactPaths.reportRelPath("prop-0001", "insp-0001", Audience.TENANT, PdfExportQuality.EXTRA_HIGH),
        )
    }

    /**
     * The point of putting both labels in the name: a verified High archive copy must not be destroyed by
     * someone re-exporting the same inspection at Medium to share, and the landlord copy must not be
     * destroyed by the tenant copy. Eight distinct names for one inspection, with none repeated.
     */
    @Test
    fun `no two audience and quality combinations of one inspection share a path`() {
        val paths = Audience.entries.flatMap { audience ->
            PdfExportQuality.entries.map { PdfArtifactPaths.reportRelPath("prop-0001", "insp-0001", audience, it) }
        }
        assertEquals(8, paths.size)
        assertEquals(paths.size, paths.toSet().size, "an audience or quality label is missing from the name")
    }

    /**
     * The same guard `MediaPaths` puts on photo paths: these arguments are UUIDv7 in practice, so this is
     * about a corrupt or hostile value smuggling a separator or a traversal segment into a derived path.
     */
    @Test
    fun `a segment that could escape or reshape the path is refused`() {
        listOf("", " ", "..", ".", "a/b", "a\\b").forEach { bad ->
            assertFailsWith<IllegalArgumentException>("property segment $bad was accepted") {
                PdfArtifactPaths.reportRelPath(bad, "insp-0001", Audience.LANDLORD, PdfExportQuality.LOW)
            }
            assertFailsWith<IllegalArgumentException>("inspection segment $bad was accepted") {
                PdfArtifactPaths.reportRelPath("prop-0001", bad, Audience.LANDLORD, PdfExportQuality.LOW)
            }
        }
    }
}
