package nz.myinspection.core.report.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import nz.myinspection.core.report.ImagePurpose

/**
 * A1 and A4 at the level of one profile: the published dpi table, and the arithmetic that turns a slot's
 * millimetres into decode parameters. The expected values here are written out as literals rather than
 * recomputed from the production constants, so changing a constant fails a test instead of moving both sides.
 */
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
     * An unknown persisted value retains the published default rather than inventing a profile, exactly as
     * `PhotoQualityProfile.fromStoredValue` does for stored photo bytes.
     */
    @Test
    fun `an unknown or missing stored value falls back to the default`() {
        // MEDIUM is written out rather than named through DEFAULT: naming it would compare the production
        // value with itself, and did stay green when DEFAULT was mutated to HIGH.
        assertEquals(PdfExportQuality.HIGH, PdfExportQuality.fromStoredValue("high"))
        assertEquals(PdfExportQuality.MEDIUM, PdfExportQuality.fromStoredValue(null))
        assertEquals(PdfExportQuality.MEDIUM, PdfExportQuality.fromStoredValue("ultra"))
        assertEquals(PdfExportQuality.MEDIUM, PdfExportQuality.fromStoredValue("HIGH"))
    }

    /**
     * Target pixels round up: a slot sampled at slightly under its dpi would be visibly soft, whereas one
     * extra pixel row costs nothing. 25.4 mm to the inch, so 100 mm at 96 dpi is 377.95 pixels.
     */
    @Test
    fun `target pixels are the ceiling of millimetres times dpi over 25 point 4`() {
        assertEquals(378, PdfImageSampling.targetPixels(lengthMm = 100, dpi = 96))
        assertEquals(473, PdfImageSampling.targetPixels(lengthMm = 100, dpi = 120))
        assertEquals(1182, PdfImageSampling.targetPixels(lengthMm = 100, dpi = 300))
        // 254 mm is exactly ten inches, so this one divides evenly and must not be rounded up to 961.
        assertEquals(960, PdfImageSampling.targetPixels(lengthMm = 254, dpi = 96))
        assertEquals(1, PdfImageSampling.targetPixels(lengthMm = 1, dpi = 1))
    }

    @Test
    fun `a non positive slot or dpi is a caller error, not a silently empty picture`() {
        assertFailsWith<IllegalArgumentException> { PdfImageSampling.targetPixels(lengthMm = 0, dpi = 96) }
        assertFailsWith<IllegalArgumentException> { PdfImageSampling.targetPixels(lengthMm = 10, dpi = 0) }
    }

    /**
     * The rule is BitmapFactory's: the decoder rounds any sample size down to a power of two, so the value
     * has to be the largest power of two that still leaves both decoded dimensions at or above the target.
     * Expected values are written out; the halving loop from the platform's own "Loading Large Bitmaps"
     * guide is not repeated here, because a test that restates the implementation proves nothing.
     */
    @Test
    fun `the sample size is the largest power of two that keeps both dimensions at or above target`() {
        assertEquals(4, PdfImageSampling.inSampleSize(4000, 3000, 1000, 750))
        assertEquals(2, PdfImageSampling.inSampleSize(4000, 3000, 1001, 750))
        assertEquals(2, PdfImageSampling.inSampleSize(2001, 1500, 1000, 750))
        assertEquals(8, PdfImageSampling.inSampleSize(8000, 6000, 1000, 750))
    }

    /** A source already at or below the target is decoded whole: subsampling it would lose real detail. */
    @Test
    fun `a source no larger than the target is never subsampled`() {
        assertEquals(1, PdfImageSampling.inSampleSize(800, 600, 1000, 750))
        assertEquals(1, PdfImageSampling.inSampleSize(1000, 750, 1000, 750))
        assertEquals(1, PdfImageSampling.inSampleSize(1999, 1499, 1000, 750))
    }

    /**
     * The budget is an upper bound, so the decoded length rounds up: a decoder that hands back the ceiling
     * must not overrun a bound computed from the floor.
     */
    @Test
    fun `decoded bytes are the rounded up dimensions at four bytes a pixel`() {
        assertEquals(4L * 1000 * 750, PdfImageSampling.decodedBytes(4000, 3000, sampleSize = 4))
        assertEquals(4L * 501 * 376, PdfImageSampling.decodedBytes(2001, 1501, sampleSize = 4))
        assertEquals(4L * 4000 * 3000, PdfImageSampling.decodedBytes(4000, 3000, sampleSize = 1))
    }

    /**
     * Whatever else changes between profiles, a higher profile never asks for fewer pixels. This is the
     * "output size is broadly monotonic" clause of requirement section 8 at the one place this card can
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
