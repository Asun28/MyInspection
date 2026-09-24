package nz.myinspection.core.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * R4: M1-M5 changed default rows/roles; M6-M10 removed finite, positive-size, signed or edge guards;
 * M11-M12 rejected valid edge equality. All 12 compiled, ran five tests and failed the named assertion.
 * Each run restored all three source files by SHA-256. Remote restored report tests: 255; core e2e: 6;
 * no failures, errors or skips. The historical local run had 328 report tests and detected the earlier
 * source-inventory omission with its exact-list assertion; those are separate local-baseline evidence.
 * Production SHA-256: FFC986CA48FC2E7C19E7811484A9EDF639673754D356568AF9CFE3126B6E2F41.
 */
class ReportTypographyTest {
    @Test
    fun `default rows and language roles are fixed`() {
        val profile = ReportTypography.DEFAULT
        assertEquals(TextStyleProfile(12.0, 5), profile.profileFor(TextStyle.TITLE))
        assertEquals(TextStyleProfile(11.0, 6), profile.profileFor(TextStyle.BODY))
        assertEquals(TextStyleProfile(9.0, 4), profile.profileFor(TextStyle.CAPTION))
        assertEquals(TextFontRole.LATIN_SANS, profile.roleFor(TextLanguage.EN))
        listOf(TextLanguage.ZH, TextLanguage.ORIGINAL, TextLanguage.NEUTRAL).forEach {
            assertEquals(TextFontRole.CJK_FALLBACK, profile.roleFor(it))
        }
    }

    @Test
    fun `an explicit profile preserves its own rows without changing defaults`() {
        val custom = ReportTypography(TextStyleProfile(3.0, 4), TextStyleProfile(2.0, 3), TextStyleProfile(1.0, 2))
        assertEquals(TextStyleProfile(3.0, 4), custom.profileFor(TextStyle.TITLE))
        assertEquals(TextStyleProfile(2.0, 3), custom.profileFor(TextStyle.BODY))
        assertEquals(TextStyleProfile(1.0, 2), custom.profileFor(TextStyle.CAPTION))
        assertEquals(TextStyleProfile(12.0, 5), ReportTypography.DEFAULT.title)
    }

    @Test
    fun `signed glyph bounds may touch both line box edges`() {
        assertTrue(runCatching { valid.requireFitsLineBox(11) }.isSuccess)
        assertTrue(runCatching {
            valid.copy(baselineOffsetPt = 0.0, glyphTopPt = 0.0, glyphBottomPt = 11.0).requireFitsLineBox(11)
        }.isSuccess)
        assertEquals(-8.0, valid.glyphTopPt)
    }

    @Test
    fun `inert snapshots are rejected at the guard for each invalid sign or edge`() {
        val invalid = listOf(
            "zero size" to valid.copy(fontSizePt = 0.0),
            "negative size" to valid.copy(fontSizePt = -1.0),
            "negative baseline" to valid.copy(baselineOffsetPt = -0.1),
            "positive top" to valid.copy(glyphTopPt = 0.1),
            "negative bottom" to valid.copy(glyphBottomPt = -0.1),
            "top escapes" to valid.copy(glyphTopPt = -8.1),
            "bottom escapes" to valid.copy(glyphBottomPt = 3.1),
        )
        invalid.forEach { (name, metric) ->
            assertFailsWith<IllegalArgumentException>(name) { metric.requireFitsLineBox(11) }
        }
    }

    @Test
    fun `each point field rejects NaN and both infinities without a profile binding check`() {
        val replace = listOf<(Double) -> TextMetricSnapshot>(
            { valid.copy(fontSizePt = it) },
            { valid.copy(baselineOffsetPt = it) },
            { valid.copy(glyphTopPt = it) },
            { valid.copy(glyphBottomPt = it) },
        )
        replace.forEachIndexed { index, field ->
            listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
                val metric = field(value)
                assertFailsWith<IllegalArgumentException>("field $index: $value") { metric.requireFitsLineBox(11) }
            }
        }
    }

    private val valid = TextMetricSnapshot(
        TextStyle.BODY, TextLanguage.EN, TextFontRole.LATIN_SANS, 2.0, 8.0, -8.0, 3.0,
    )
}
