package nz.myinspection.core.media

import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * Asserts [ExifOrientation.transformFor] against the exact 2x2 correction matrix each EXIF orientation
 * value requires — not just "some rotation happened". Expected matrices below are derived independently
 * from the EXIF spec's row/column semantics (exiftool.org/TagNames/EXIF.html "Orientation"), not copied
 * from the production table, so a sign or axis-swap bug in the table shows up as a wrong matrix, not a
 * green test (L165).
 *
 * [compose] rebuilds the 2x2 linear map [OrientationTransform] describes — mirror horizontal first
 * (if set), then rotate clockwise — using elementary rotation/flip matrices, matching how
 * android.graphics.Matrix.postScale/postRotate compose (post-concat = flip acts on the point first).
 */
class ExifOrientationTest {
    private fun matMul(a: IntArray, b: IntArray): IntArray {
        // a, b as [a00,a01,a10,a11] meaning [[a00,a01],[a10,a11]]
        return intArrayOf(
            a[0] * b[0] + a[1] * b[2], a[0] * b[1] + a[1] * b[3],
            a[2] * b[0] + a[3] * b[2], a[2] * b[1] + a[3] * b[3],
        )
    }

    private fun rotate(degrees: Int): IntArray = when (degrees) {
        0 -> intArrayOf(1, 0, 0, 1)
        90 -> intArrayOf(0, -1, 1, 0)
        180 -> intArrayOf(-1, 0, 0, -1)
        270 -> intArrayOf(0, 1, -1, 0)
        else -> error("test only covers the 4 clockwise right-angle rotations, got $degrees")
    }

    private fun compose(transform: OrientationTransform): IntArray {
        val flip = if (transform.flipHorizontal) intArrayOf(-1, 0, 0, 1) else intArrayOf(1, 0, 0, 1)
        return matMul(rotate(transform.rotationDegrees), flip)
    }

    private fun assertMatrix(expected: IntArray, exifOrientation: Int) {
        assertContentEquals(
            expected,
            compose(ExifOrientation.transformFor(exifOrientation)),
            "orientation $exifOrientation must compose to the matrix EXIF spec requires",
        )
    }

    @Test
    fun `orientation 1 normal is the identity matrix`() = assertMatrix(intArrayOf(1, 0, 0, 1), 1)

    @Test
    fun `orientation 2 mirror horizontal negates x only`() = assertMatrix(intArrayOf(-1, 0, 0, 1), 2)

    @Test
    fun `orientation 3 rotate 180 negates both axes`() = assertMatrix(intArrayOf(-1, 0, 0, -1), 3)

    @Test
    fun `orientation 4 mirror vertical negates y only`() = assertMatrix(intArrayOf(1, 0, 0, -1), 4)

    @Test
    fun `orientation 5 transpose swaps x and y with no negation`() = assertMatrix(intArrayOf(0, 1, 1, 0), 5)

    @Test
    fun `orientation 6 rotate 90 CW maps right to down`() = assertMatrix(intArrayOf(0, -1, 1, 0), 6)

    @Test
    fun `orientation 7 transverse swaps x and y and negates both`() = assertMatrix(intArrayOf(0, -1, -1, 0), 7)

    @Test
    fun `orientation 8 rotate 270 CW maps right to up`() = assertMatrix(intArrayOf(0, 1, -1, 0), 8)

    @Test
    fun `the 4 mirrored orientations (2, 4, 5, 7) are exactly those with flipHorizontal true`() {
        val mirrored = setOf(2, 4, 5, 7)
        for (orientation in 1..8) {
            assertContentEquals(
                intArrayOf(if (orientation in mirrored) 1 else 0),
                intArrayOf(if (ExifOrientation.transformFor(orientation).flipHorizontal) 1 else 0),
                "orientation $orientation flipHorizontal must be ${orientation in mirrored}",
            )
        }
    }

    @Test
    fun `undefined orientation (0) and any out-of-range value are treated as already upright`() {
        assertMatrix(intArrayOf(1, 0, 0, 1), 0)
        assertMatrix(intArrayOf(1, 0, 0, 1), 9)
    }
}
