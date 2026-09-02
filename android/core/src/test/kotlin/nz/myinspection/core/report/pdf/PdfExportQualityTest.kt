package nz.myinspection.core.report.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nz.myinspection.core.report.ImagePurpose

/** A1 at the level of one profile: the published dpi table and how a profile is chosen. */
class PdfExportQualityTest {
    /** Requirement section 8, decided 2026-08-19. Inline first, appendix second. */
    @Test
    fun `the four profiles publish the dpi table the requirement fixed`() {
        assertEquals(
            listOf(
                Triple("low", 96, 120),
                Triple("medium", 120, 160),
                Triple("high", 150, 200),
                Triple("extra_high", 200, 300),
            ),
            PdfExportQuality.entries.map { Triple(it.storedValue, it.inlineDpi, it.appendixDpi) },
        )
        assertEquals(PdfExportQuality.MEDIUM, PdfExportQuality.DEFAULT)
    }

    @Test
    fun `each profile reads its dpi from the slot's purpose`() {
        PdfExportQuality.entries.forEach { quality ->
            assertEquals(quality.inlineDpi, quality.dpiFor(ImagePurpose.INLINE))
            assertEquals(quality.appendixDpi, quality.dpiFor(ImagePurpose.APPENDIX))
        }
    }

    /**
     * An unknown persisted value retains the published default rather than inventing a profile, as
     * `PhotoQualityProfile.fromStoredValue` does for stored photo bytes. MEDIUM is written out rather than
     * named through DEFAULT: naming it would compare the production value with itself, and did stay green
     * when DEFAULT was mutated to HIGH.
     */
    @Test
    fun `an unknown or missing stored value falls back to the default`() {
        assertEquals(PdfExportQuality.HIGH, PdfExportQuality.fromStoredValue("high"))
        assertEquals(PdfExportQuality.MEDIUM, PdfExportQuality.fromStoredValue(null))
        assertEquals(PdfExportQuality.MEDIUM, PdfExportQuality.fromStoredValue("ultra"))
        assertEquals(PdfExportQuality.MEDIUM, PdfExportQuality.fromStoredValue("HIGH"))
    }

    /**
     * Whatever else changes between profiles, a higher profile never asks for fewer pixels. This is the
     * "output size is broadly monotonic" clause of requirement section 8, at the one place this card can
     * prove it: the sampling request itself.
     */
    @Test
    fun `a higher profile never requests fewer pixels than a lower one`() {
        ImagePurpose.entries.forEach { purpose ->
            val requested = PdfExportQuality.entries.map { PdfImageSampling.targetPixels(120, it.dpiFor(purpose)) }
            assertEquals(requested.sorted(), requested, "profiles must be declared in non-decreasing dpi order")
            assertTrue(requested.first() < requested.last(), "the four profiles must not all sample identically")
        }
    }
}
