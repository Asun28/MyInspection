package nz.myinspection.core.report.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A1-A4: the two escaping contexts, and the character policy the document can actually honour.
 *
 * Every invisible code point here is built from its numeric value. Writing one as a literal, or as a
 * backslash-u escape, is not an option: the editing tools that produce this file decode such an escape
 * into the real character, and a literal NUL or lone surrogate would then be invisible in every editor
 * and every diff, so no reader could tell whether the test still covers what it claims (L193).
 */
class HtmlEscapingTest {

    private val nul = 0x00.toChar()
    private val zeroWidthSpace = 0x200B.toChar()
    private val highSurrogate = 0xD83D.toChar()
    private val lowSurrogate = 0xDE00.toChar()

    // ---- A1: the two contexts ------------------------------------------------------------------------

    @Test
    fun `element text escapes the three syntactic characters and leaves quotes alone`() {
        assertEquals(
            "5 &lt; 6 &amp; 7 &gt; 6 \"q\" 'q'",
            HtmlEscaping.text("5 < 6 & 7 > 6 \"q\" 'q'"),
        )
    }

    @Test
    fun `attribute text escapes both quote forms as well`() {
        assertEquals(
            "5 &lt; 6 &amp; 7 &gt; 6 &quot;q&quot; &#39;q&#39;",
            HtmlEscaping.attribute("5 < 6 & 7 > 6 \"q\" 'q'"),
        )
    }

    /**
     * An entity already present in the source is escaped again. That is correct: the source is a dictated
     * note, so those are literal characters the tenant typed and the report has to show them back.
     */
    @Test
    fun `escaping is applied to the raw characters, never to a guess at the author's intent`() {
        assertEquals("&amp;amp; &amp;#60;", HtmlEscaping.text("&amp; &#60;"))
    }

    // ---- A2/A3: what the document refuses to carry ---------------------------------------------------

    /**
     * A lone surrogate has no UTF-8 form at all. Kotlin substitutes '?' when encoding one, so two
     * different notes would serialise to identical bytes - the same defect `core/canon` refuses for the
     * same reason. Refusing beats mangling: a report is evidence, and evidence that quietly stops
     * matching what was recorded is worse than a report that fails loudly.
     */
    @Test
    fun `an unpaired surrogate is refused in both contexts`() {
        val loneHigh = "note " + highSurrogate + " tail"
        val loneLow = "note " + lowSurrogate + " tail"
        assertFailsWith<IllegalArgumentException> { HtmlEscaping.text(loneHigh) }
        assertFailsWith<IllegalArgumentException> { HtmlEscaping.attribute(loneHigh) }
        assertFailsWith<IllegalArgumentException> { HtmlEscaping.text(loneLow) }
        assertFailsWith<IllegalArgumentException> { HtmlEscaping.attribute(loneLow) }
    }

    /**
     * A high surrogate at the very end of the string has no following char to pair with. It is the case a
     * bounds check written as `index + 1 < length` and one written as `index + 1 <= length` disagree on.
     */
    @Test
    fun `a high surrogate at the end of the string is refused, not read past`() {
        assertFailsWith<IllegalArgumentException> { HtmlEscaping.text("trailing " + highSurrogate) }
    }

    /** Two high surrogates in a row: the first is unpaired even though a surrogate does follow it. */
    @Test
    fun `a high surrogate followed by another high surrogate is refused`() {
        assertFailsWith<IllegalArgumentException> {
            HtmlEscaping.text("" + highSurrogate + highSurrogate)
        }
    }

    /** U+0000 is replaced with U+FFFD in HTML character data, so the document cannot carry it faithfully. */
    @Test
    fun `a null character is refused in both contexts`() {
        assertFailsWith<IllegalArgumentException> { HtmlEscaping.text("note " + nul) }
        assertFailsWith<IllegalArgumentException> { HtmlEscaping.attribute("note " + nul) }
    }

    // ---- A4: the preservation claim, proven where it is claimed --------------------------------------

    /**
     * The claim is about the bytes of the file, so it is asserted on the bytes: encode to UTF-8, decode
     * again, compare. Comparing Kotlin Strings alone would prove nothing about encoding, which is exactly
     * how the earlier version of this test managed to assert byte preservation while permitting a lone
     * surrogate that has no bytes.
     */
    @Test
    fun `everything the policy admits survives a real UTF-8 round trip`() {
        val emoji = String(Character.toChars(0x1F600))
        val admitted = "tab\there " + zeroWidthSpace + "zwsp 厨房 " + emoji + " <&\"'>"
        for (escaped in listOf(HtmlEscaping.text(admitted), HtmlEscaping.attribute(admitted))) {
            assertEquals(escaped, String(escaped.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        }
        // The paired surrogates of the emoji reach the bytes as one four-byte sequence, so the refusal
        // above targets unpaired surrogates only and does not simply ban astral characters.
        assertTrue(HtmlEscaping.text(emoji).toByteArray(Charsets.UTF_8).contentEquals(emoji.toByteArray(Charsets.UTF_8)))
    }

    /**
     * CR is admitted and reaches the file unchanged. What is NOT claimed is that a parser gives it back:
     * the HTML tokenizer normalises CR and CRLF to LF. The guarantee is narrowed to the level where it is
     * true - the file's bytes - rather than left as a sentence that is simply false above that level.
     */
    @Test
    fun `a carriage return is preserved in the bytes, and only in the bytes`() {
        val withCr = "first" + 0x0D.toChar() + 0x0A.toChar() + "second"
        assertTrue(HtmlEscaping.text(withCr).toByteArray(Charsets.UTF_8).contains(0x0D))
    }
}
/*
 * R4 receipt. 15 single-point mutations, each applied alone to HtmlEscaping.kt at SHA-256
 * 26c904df72a63f0b3447ec9cf51ecb7ebc41c0c64ddda33305f213a1ed6799e0 and restored to that same hash before
 * the next. 15 killed, 0 survived, 0 compile-kills, 0 no-runs.
 *
 * Each kill is a failing test. The harness runs the unmutated suite first as a positive control and
 * records a kill only when Gradle reports that tests ran and failed, because a compile error, a failing
 * test, and a shell that cannot start the wrapper all exit 1 - a kill has to be positively attributed,
 * never inferred from an exit code (L282, widened after a sibling batch scored a perfect 31/31 without
 * ever starting Gradle).
 *
 * contexts  M1-M5 drop one escaped character each; M6 escapes an attribute as element text; M7 silently
 *           drops every character with no syntactic meaning.
 * policy    M8 removes the policy check from both entry points; M9, M10, M11 admit U+0000, an unpaired
 *           high surrogate and an unpaired low surrogate; M15 refuses U+0001 instead of U+0000.
 * pairing   M12 lets the bounds check read one past the end of the string; M13 drops the index advance so
 *           a well-formed pair's low half is judged unpaired; M14 stops recognising a high surrogate at
 *           all, which admits every pair as two unpaired halves.
 *
 * The receipt is a comment in a test file and is inert: unlike a stylesheet constant, it is not data any
 * assertion reads, so adding it after the batch cannot change a verdict the batch recorded.
 */
