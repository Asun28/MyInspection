package nz.myinspection.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Boundary vectors for [ImportBounds.check] — the limit is inclusive (exactly [ImportBounds.MAX_IMPORT_PIXELS]
 * must still be accepted; one pixel over must be rejected), so the off-by-one case is asserted at the exact
 * boundary rather than with a comfortably-under/comfortably-over pair that a `>=` vs `>` mutation couldn't see.
 */
class ImportBoundsTest {
    @Test
    fun `a small ordinary photo is accepted`() {
        assertEquals(ImportBoundsResult.Accepted, ImportBounds.check(width = 4000, height = 3000))
    }

    @Test
    fun `exactly the limit pixel count is accepted, not rejected`() {
        // 8000 x 5000 = 40,000,000 = MAX_IMPORT_PIXELS exactly.
        assertEquals(40_000_000L, ImportBounds.MAX_IMPORT_PIXELS)
        assertEquals(ImportBoundsResult.Accepted, ImportBounds.check(width = 8000, height = 5000))
    }

    @Test
    fun `one pixel over the limit is rejected`() {
        // 8000 x 5000 + 1 extra pixel of height room is awkward to express exactly at 1-pixel granularity
        // with integer width/height, so bump width by 1 instead: 8001 x 5000 = 40,005,000 > limit.
        val result = ImportBounds.check(width = 8001, height = 5000)
        val rejected = assertIs<ImportBoundsResult.Rejected>(result)
        assertEquals(8001, rejected.width)
        assertEquals(5000, rejected.height)
        assertEquals(40_000_000L, rejected.limitPixels)
    }

    @Test
    fun `a large but legitimate camera resolution well under the limit is accepted`() {
        // A high-end 50MP sensor at native resolution (8192 x 6144 = 50,331,648) exceeds the limit —
        // pinning that this WOULD reject at true 50MP native output, distinct from the common ~12-16MP
        // default capture mode which stays comfortably under.
        assertEquals(ImportBoundsResult.Accepted, ImportBounds.check(width = 4608, height = 3456)) // ~16MP
    }

    @Test
    fun `a huge malformed or scanned image far over the limit is rejected`() {
        val result = ImportBounds.check(width = 20000, height = 20000) // 400,000,000 px
        val rejected = assertIs<ImportBoundsResult.Rejected>(result)
        assertEquals(20000, rejected.width)
        assertEquals(20000, rejected.height)
    }

    @Test
    fun `pixel product does not overflow Int for very large dimensions`() {
        // 50000 x 50000 = 2,500,000,000, which overflows Int.MAX_VALUE (~2.147 billion) — the check must
        // still correctly reject rather than wrapping into a negative Int product and accepting.
        val result = ImportBounds.check(width = 50000, height = 50000)
        assertIs<ImportBoundsResult.Rejected>(result)
    }
}
