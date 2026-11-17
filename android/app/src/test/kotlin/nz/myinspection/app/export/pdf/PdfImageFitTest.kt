package nz.myinspection.app.export.pdf

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import nz.myinspection.core.report.ImagePurpose
import nz.myinspection.core.report.pdf.PdfImagePlacement

class PdfImageFitTest {
    private val wide = PdfPointRect(10f, 20f, 110f, 70f)
    private val tall = PdfPointRect(30f, 40f, 110f, 140f)

    @Test
    fun `the frame is the unchanged placement rectangle in page points`() {
        assertEquals(PdfPointRect(10f, 20f, 110f, 70f), PdfFitCenter.frame(placement(10, 20, 100, 50)))
        assertEquals(PdfPointRect(30f, 40f, 110f, 140f), PdfFitCenter.frame(placement(30, 40, 80, 100)))
    }

    @Test
    fun `a placement without positive width or height is refused`() {
        listOf(placement(10, 20, 0, 50), placement(10, 20, 100, 0), placement(10, 20, -1, 50), placement(10, 20, 100, -1))
            .forEach { assertFailsWith<IllegalArgumentException>("$it") { PdfFitCenter.frame(it) } }
    }

    @Test
    fun `landscape image fills the frame width and is centred vertically`() {
        assertRect(PdfPointRect(10f, 25f, 110f, 65f), PdfFitCenter.fit(wide, 1000, 400))
    }

    @Test
    fun `portrait image fills the frame height and is centred horizontally`() {
        assertRect(PdfPointRect(47.5f, 20f, 72.5f, 70f), PdfFitCenter.fit(wide, 1000, 2000))
    }

    @Test
    fun `square image in a tall offset frame keeps its aspect ratio`() {
        assertRect(PdfPointRect(30f, 50f, 110f, 130f), PdfFitCenter.fit(tall, 300, 300))
    }

    @Test
    fun `long image is fitted whole, narrow and centred`() {
        assertRect(PdfPointRect(58.586957f, 40f, 81.413043f, 140f), PdfFitCenter.fit(tall, 4200, 18400))
    }

    @Test
    fun `a frame edge at zero on a page is not crossed by Double rounding`() {
        // 11 * (50 / 11) is 50.0000000000000071 in Double, so the unclamped top edge would be -3.6e-15.
        val upright = PdfFitCenter.fit(PdfPointRect(0f, 0f, 100f, 50f), 1, 11)
        assertEquals(0f, upright.top, "top of $upright")
        assertRect(PdfPointRect(47.727272f, 0f, 52.272728f, 50f), upright)
        val sideways = PdfFitCenter.fit(PdfPointRect(0f, 0f, 50f, 100f), 11, 1)
        assertEquals(0f, sideways.left, "left of $sideways")
        assertRect(PdfPointRect(0f, 47.727272f, 50f, 52.272728f), sideways)
    }

    @Test
    fun `at the extremes of the Float range every edge is finite, inside the frame and where the fit puts it`() {
        val max = Float.MAX_VALUE
        val e = Math.scalb(1f, 126) + Math.scalb(3f, 103) // max - e rounds up in Float and would overflow the edge
        val near = Math.scalb(3f, 74) // -max..near rounds up in Double and would overshoot the edge
        // frame, image width x height, expected rectangle (the exact fit, up to 1e-6 of the frame's magnitude)
        listOf(
            Triple(PdfPointRect(0f, 0f, max, max), 999 to 999, PdfPointRect(0f, 0f, max, max)),
            Triple(PdfPointRect(-max, -max, max, max), 1 to 1, PdfPointRect(-max, -max, max, max)),
            Triple(PdfPointRect(e, 0f, max, max), 1 to 1, PdfPointRect(e, e / 2, max, max - e / 2)),
            Triple(PdfPointRect(0f, e, max, max), 1 to 1, PdfPointRect(e / 2, e, max - e / 2, max)),
            Triple(PdfPointRect(-max, 0f, near, max), 2 to 1, PdfPointRect(-max, max / 4, near, max / 4 * 3)),
            Triple(PdfPointRect(0f, -max, max, near), 1 to 2, PdfPointRect(max / 4, -max, max / 4 * 3, near)),
            Triple(PdfPointRect(0f, 0f, max, max), 27 to 1, PdfPointRect(0f, max / 27 * 13, max, max / 27 * 14)),
            Triple(PdfPointRect(0f, 0f, max, max), 1 to 27, PdfPointRect(max / 27 * 13, 0f, max / 27 * 14, max)),
        ).forEach { (frame, image, expected) ->
            val fitted = PdfFitCenter.fit(frame, image.first, image.second)
            val edges = listOf(fitted.left, fitted.top, fitted.right, fitted.bottom)
            edges.forEach { assertTrue(it.isFinite(), "edge of $fitted in $frame") }
            assertTrue(frame.left <= fitted.left && fitted.left <= fitted.right && fitted.right <= frame.right, "x of $fitted in $frame")
            assertTrue(frame.top <= fitted.top && fitted.top <= fitted.bottom && fitted.bottom <= frame.bottom, "y of $fitted in $frame")
            val slack = 1e-6f * listOf(frame.left, frame.top, frame.right, frame.bottom).maxOf { abs(it) }
            assertRect(expected, fitted, slack)
        }
    }

    @Test
    fun `odd decoded dimensions are fitted as given, not rounded`() {
        assertRect(PdfPointRect(10.149701f, 20f, 109.850299f, 70f), PdfFitCenter.fit(wide, 999, 501))
    }

    @Test
    fun `non-positive decoded dimensions are refused`() {
        listOf(0 to 400, 1000 to 0, -1 to 400, 1000 to -1).forEach { (width, height) ->
            assertFailsWith<IllegalArgumentException>("${width}x$height") { PdfFitCenter.fit(wide, width, height) }
        }
    }

    @Test
    fun `a frame without finite edges and positive extents is refused`() {
        listOf(
            PdfPointRect(10f, 20f, 10f, 70f),
            PdfPointRect(10f, 20f, 110f, 20f),
            PdfPointRect(110f, 20f, 10f, 70f),
            PdfPointRect(10f, 70f, 110f, 20f),
            PdfPointRect(Float.NaN, 20f, 110f, 70f),
            PdfPointRect(10f, Float.NaN, 110f, 70f),
            PdfPointRect(10f, 20f, Float.POSITIVE_INFINITY, 70f),
            PdfPointRect(Float.NEGATIVE_INFINITY, 20f, 110f, 70f),
            PdfPointRect(10f, 20f, 110f, Float.POSITIVE_INFINITY),
            PdfPointRect(10f, Float.NEGATIVE_INFINITY, 110f, 70f),
        ).forEach { assertFailsWith<IllegalArgumentException>("$it") { PdfFitCenter.fit(it, 1000, 400) } }
    }

    private fun placement(x: Int, y: Int, w: Int, h: Int) =
        PdfImagePlacement("018f4a5e-1267-7d3a-8b18-0425d7f0b4aa", ImagePurpose.INLINE, x, y, w, h)

    private fun assertRect(expected: PdfPointRect, actual: PdfPointRect, slack: Float = 0.0001f) {
        assertEquals(expected.left, actual.left, slack, "left of $actual")
        assertEquals(expected.top, actual.top, slack, "top of $actual")
        assertEquals(expected.right, actual.right, slack, "right of $actual")
        assertEquals(expected.bottom, actual.bottom, slack, "bottom of $actual")
    }
}

/*
 * R4 (T3-PDF-IMAGE-FIT): 26/26 single-point compiling mutants of PdfImageFit.kt killed. Kill evidence per mutant is
 * the TestNG report for PdfImageFitTest being produced (so the mutant compiled; 11 tests ran) with the named case
 * failing by java.lang.AssertionError. Production PdfImageFit.kt SHA-256 at batch time and after each restore:
 *   56FCE5A3080BD1CAFB33B8BC5045DFABABDC8E242897E2804F4BAAA4C52ACD33
 * Command per mutant: cmd /c android\gradlew.bat -p android --offline --no-daemon --no-build-cache -q
 *   :app:testDebugUnitTest --tests nz.myinspection.app.export.pdf.PdfImageFitTest
 * M01 maxOf scale (centre-crop) -> landscape          M02 width = frameWidth (stretch) -> portrait
 * M03 height = frameHeight (stretch) -> landscape     M04 left = frame.left (no h-centring) -> portrait
 * M05 top = frame.top (no v-centring) -> landscape    M06 frame width/height swapped -> unchanged placement
 * M07 frame x taken from yPt -> unchanged placement   M18 bottom edge computed from width -> landscape
 * M08/M09 either half of the placement guard dropped -> placement refused
 * M10/M11 either half of the decoded-size guard dropped -> non-positive decoded
 * M12/M13 either positive-extent clause dropped -> frame refused
 * M14-M17 the finiteness check of one edge (left, top, right, bottom) dropped -> frame refused
 * Killed only by the extremes case: M19/M20 fitted width or height computed in Float, M21/M22 frame width or height
 * extent computed in Float, M25/M26 the clamp of the right or bottom edge dropped. M23/M24 (the clamp of the left or
 * top edge dropped) are killed by the extremes case and by the page-scale zero-edge case.
 */
