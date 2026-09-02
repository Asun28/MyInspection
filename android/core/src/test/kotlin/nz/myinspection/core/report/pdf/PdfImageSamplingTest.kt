package nz.myinspection.core.report.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A4's arithmetic. Expected values are written out rather than recomputed from the production constants, so
 * changing a constant fails a test instead of moving both sides of the comparison.
 */
class PdfImageSamplingTest {
    /** Rounds up: a slot sampled fractionally under its box is soft, one extra pixel row costs nothing. */
    @Test
    fun `target pixels are the ceiling of millimetres times dpi over 25 point 4`() {
        assertEquals(378, PdfImageSampling.targetPixels(lengthMm = 100, dpi = 96))
        assertEquals(473, PdfImageSampling.targetPixels(lengthMm = 100, dpi = 120))
        assertEquals(1182, PdfImageSampling.targetPixels(lengthMm = 100, dpi = 300))
        // 254 mm is exactly ten inches, so this one divides evenly and must not be rounded up to 961.
        assertEquals(960, PdfImageSampling.targetPixels(lengthMm = 254, dpi = 96))
        assertEquals(1, PdfImageSampling.targetPixels(lengthMm = 1, dpi = 1))
    }

    /**
     * Both inputs are bounded, not merely required to be positive. Without an upper bound the millimetre and
     * dpi product overflows `Long` for large accepted values and the pixel count wraps to a wrong, often
     * negative, answer instead of being refused.
     */
    @Test
    fun `a slot or density outside its sane range is refused rather than wrapped`() {
        assertFailsWith<IllegalArgumentException> { PdfImageSampling.targetPixels(lengthMm = 0, dpi = 96) }
        assertFailsWith<IllegalArgumentException> { PdfImageSampling.targetPixels(lengthMm = 10, dpi = 0) }
        assertFailsWith<IllegalArgumentException> { PdfImageSampling.targetPixels(lengthMm = 10_001, dpi = 96) }
        assertFailsWith<IllegalArgumentException> { PdfImageSampling.targetPixels(lengthMm = 100, dpi = 10_001) }
        assertFailsWith<IllegalArgumentException> {
            PdfImageSampling.targetPixels(lengthMm = Int.MAX_VALUE, dpi = Int.MAX_VALUE)
        }
    }

    /**
     * BitmapFactory rounds any sample size down to a power of two, so the value has to be the largest power
     * of two that still leaves both decoded dimensions at or above the target. The halving loop from the
     * platform's own "Loading Large Bitmaps" guide is not repeated here: a test that restates the
     * implementation proves nothing.
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

    /** The doubling step is computed in Long: a source near Int.MAX must not wrap while searching. */
    @Test
    fun `an enormous source still resolves to a power of two`() {
        assertEquals(1_073_741_824, PdfImageSampling.inSampleSize(Int.MAX_VALUE, Int.MAX_VALUE, 1, 1))
    }

    /**
     * The bound rounds up, the opposite of target pixels: a decoder handing back the ceiling must not
     * overrun a budget computed from the floor.
     */
    @Test
    fun `decoded bytes are the rounded up dimensions at four bytes a pixel`() {
        assertEquals(4L * 1000 * 750, PdfImageSampling.decodedBytes(4000, 3000, sampleSize = 4))
        assertEquals(4L * 501 * 376, PdfImageSampling.decodedBytes(2001, 1501, sampleSize = 4))
        assertEquals(4L * 4000 * 3000, PdfImageSampling.decodedBytes(4000, 3000, sampleSize = 1))
    }

    /**
     * A byte cost too large for a `Long` saturates instead of wrapping, the answer `ImportBounds` already
     * gives for the same multiplication. A bound that went negative would read as "free" to every caller,
     * which is the exact opposite of what a memory bound is for.
     */
    @Test
    fun `a byte cost too large for a Long saturates instead of going negative`() {
        assertEquals(
            Long.MAX_VALUE,
            PdfImageSampling.decodedBytes(Int.MAX_VALUE, Int.MAX_VALUE, sampleSize = 1),
        )
    }
}
