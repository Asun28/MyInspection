package nz.myinspection.core.media

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Boundary vectors for [ExifSubSecond.parseMillis] — the EXIF SubSecTime string is a decimal-fraction
 * digit string (not fixed-width millis), and the whole point of taking only the first three digits is to
 * structurally rule out numeric overflow regardless of input length (see the object's KDoc).
 */
class ExifSubSecondTest {
    @Test
    fun `null input yields 0`() = assertEquals(0L, ExifSubSecond.parseMillis(null))

    @Test
    fun `empty string yields 0`() = assertEquals(0L, ExifSubSecond.parseMillis(""))

    @Test
    fun `a single digit is right-padded to three, so 5 means 0point5s = 500ms not 5ms`() =
        assertEquals(500L, ExifSubSecond.parseMillis("5"))

    @Test
    fun `two digits are right-padded to three`() = assertEquals(500L, ExifSubSecond.parseMillis("50"))

    @Test
    fun `three digits are used as-is`() = assertEquals(500L, ExifSubSecond.parseMillis("500"))

    @Test
    fun `more than three digits are truncated to the first three, not rounded`() =
        assertEquals(123L, ExifSubSecond.parseMillis("1239")) // would round to 124 if rounding; must be 123

    @Test
    fun `21 nines does not overflow and yields exactly 999`() =
        assertEquals(999L, ExifSubSecond.parseMillis("9".repeat(21)))

    @Test
    fun `non-digit garbage yields 0`() {
        assertEquals(0L, ExifSubSecond.parseMillis("12a"))
        assertEquals(0L, ExifSubSecond.parseMillis("abc"))
        assertEquals(0L, ExifSubSecond.parseMillis("-5"))
        assertEquals(0L, ExifSubSecond.parseMillis(" 5"))
    }

    @Test
    fun `all zero digits yields 0`() = assertEquals(0L, ExifSubSecond.parseMillis("000"))
}
