package nz.myinspection.core.report.html

import kotlin.test.Test
import kotlin.test.assertEquals

/** A2: the two escaping contexts, and the promise that nothing else about the text changes. */
class HtmlEscapingTest {

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
     * An ampersand that is already part of an entity is escaped again. That is correct, not a defect: the
     * source is a dictated note, so an entity in it is literal characters the tenant typed and the report
     * has to show those characters back.
     */
    @Test
    fun `escaping is applied once to the raw characters, never to the reader's guess at intent`() {
        assertEquals("&amp;amp; &amp;#60;", HtmlEscaping.text("&amp; &#60;"))
    }

    /**
     * Nothing is dropped or substituted. A control character cannot escape either context once the
     * syntactic characters are entities, and deleting it would make the report disagree with the note that
     * was actually recorded.
     *
     * The invisible code points are built from their numeric values rather than typed or written as `\u`
     * escapes: the editing tools that produce this file decode such an escape into the real character, and
     * a literal NUL or zero-width space here would then be invisible in every editor and every diff, so no
     * reader could tell whether the test still covers what it says it covers (L193).
     */
    @Test
    fun `characters with no syntactic meaning survive byte for byte`() {
        val nul = 0x00.toChar()
        val zeroWidthSpace = 0x200B.toChar()
        val awkward = "tab\there ${nul}nul${zeroWidthSpace}zwsp 厨房 " + String(Character.toChars(0x1F600))
        assertEquals(awkward, HtmlEscaping.text(awkward))
        assertEquals(awkward, HtmlEscaping.attribute(awkward))
    }
}
