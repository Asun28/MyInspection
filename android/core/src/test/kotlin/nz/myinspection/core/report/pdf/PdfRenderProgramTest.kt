package nz.myinspection.core.report.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import nz.myinspection.core.report.ImagePurpose
import nz.myinspection.core.report.TextLanguage
import nz.myinspection.core.report.TextStyle

/** A2's conversion and A4's page bound, on the program types themselves rather than through the builder. */
class PdfRenderProgramTest {
    @Test
    fun `an A4 page is 595 by 842 points`() {
        assertEquals(595, PdfGeometry.mmToPt(210))
        assertEquals(842, PdfGeometry.mmToPt(297))
        assertEquals(0, PdfGeometry.mmToPt(0))
        assertEquals(43, PdfGeometry.mmToPt(15))
        assertEquals(595, PdfPageProgram(1, emptyList()).widthPt)
        assertEquals(842, PdfPageProgram(1, emptyList()).heightPt)
    }

    /**
     * The whole point of starting one page at a time and recycling each bitmap after it is drawn: the page
     * costs the largest single picture, not the total. A bound written as a sum would describe a renderer
     * holding every decoded bitmap alive for the whole page, and would quietly bless it.
     *
     * Both pictures come from one 4000x3000 source. The 142x114 thumbnail samples at 16 and decodes
     * 250x188 = 188,000 bytes; the 1134x756 plate samples at 2 and decodes 2000x1500 = 12,000,000 bytes.
     * Their sum, 12,188,000, is the answer this assertion exists to reject. The order is tried both ways so
     * that neither "the first picture" nor "the last" can pass as a bound.
     */
    @Test
    fun `a page's decoded byte bound is its largest picture, not the sum of its pictures`() {
        val sources = mapOf("thumb" to PdfSourcePixels(4000, 3000), "plate" to PdfSourcePixels(4000, 3000))
        listOf(listOf(thumb, plate), listOf(plate, thumb)).forEach { ops ->
            assertEquals(12_000_000L, PdfPageProgram(1, ops).decodedByteBound(sources))
        }
    }

    @Test
    fun `a page with no pictures needs no decode budget`() {
        val text = PdfTextOp("x", TextLanguage.EN, TextStyle.BODY, 0, 0, 10, 10)
        assertEquals(0L, PdfPageProgram(1, listOf(text)).decodedByteBound(emptyMap()))
    }

    /** A picture whose source dimensions are unknown cannot be bounded, so it is refused rather than skipped. */
    @Test
    fun `a picture missing from the source dimensions is refused, not treated as free`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            PdfPageProgram(7, listOf(thumb, plate)).decodedByteBound(mapOf("thumb" to PdfSourcePixels(4000, 3000)))
        }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("plate"), "the message must name the unbounded picture: $message")
        assertTrue(message.contains("page 7"), "the message must name the page: $message")
    }

    @Test
    fun `a source dimension that is not positive is refused at construction`() {
        assertFailsWith<IllegalArgumentException> { PdfSourcePixels(0, 100) }
        assertFailsWith<IllegalArgumentException> { PdfSourcePixels(100, -1) }
    }

    private companion object {
        val thumb = PdfImageOp(
            PdfImagePlacement("thumb", ImagePurpose.INLINE, xPt = 227, yPt = 57, widthPt = 85, heightPt = 68),
            targetWidthPx = 142,
            targetHeightPx = 114,
        )
        val plate = PdfImageOp(
            PdfImagePlacement("plate", ImagePurpose.APPENDIX, xPt = 43, yPt = 170, widthPt = 510, heightPt = 340),
            targetWidthPx = 1134,
            targetHeightPx = 756,
        )
    }
}
