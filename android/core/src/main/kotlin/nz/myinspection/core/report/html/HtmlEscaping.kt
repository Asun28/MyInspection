package nz.myinspection.core.report.html

/**
 * Contextual escaping for the two places report text reaches: element content and a double-quoted
 * attribute value. There is no third context - the report document has no `<script>`, and no report text
 * is ever written into `<style>` - so an escaper for a scripting or CSS context would be dead code that
 * only invites someone to open such a context later.
 *
 * ## What is guaranteed
 *
 * Two separate claims. Collapsing them into one sentence is how an earlier version of this file came to
 * assert something its own code contradicts a few lines below:
 *
 * - **the source text is preserved semantically.** Every input character is represented in the output,
 *   either as itself or as the entity that denotes it. Nothing is dropped and nothing becomes a
 *   *different* character: `&` becomes `&amp;`, five characters denoting the one that was there;
 * - **the escaped output encodes to UTF-8 losslessly.** What this function returns survives
 *   `toByteArray(UTF_8)` and back unchanged. That is precisely why the inputs below are refused instead
 *   of escaped - they have no such encoding.
 *
 * What is deliberately NOT claimed is that an HTML parser hands the source text back. The tokenizer
 * resolves entities, which restores it, but it also normalises CR and CRLF to LF, which does not. A
 * guarantee stated above the level where it holds is worse than a narrower one: it stops anybody
 * checking.
 *
 * ## What is refused, and why refusing beats mangling
 *
 * Two inputs cannot be carried faithfully at all, so they are rejected rather than quietly changed:
 *
 * - an **unpaired surrogate** has no UTF-8 form. Encoding one substitutes `?`, so two different notes
 *   would serialise to identical bytes. `core/canon` refuses lone surrogates for exactly this reason;
 * - **U+0000** is replaced with U+FFFD in HTML character data by every conforming parser.
 *
 * This is an evidence product. A report that quietly stops matching what was recorded is a worse outcome
 * than a report that fails loudly, so the failure is loud and names the offending index.
 */
internal object HtmlEscaping {

    /** Escapes for element content and for RCDATA (`<title>`). */
    fun text(value: String): String = escape(value, quotes = false)

    /** Escapes for a double-quoted attribute value. Both quote forms go, so a caller cannot pick wrong. */
    fun attribute(value: String): String = escape(value, quotes = true)

    private fun escape(value: String, quotes: Boolean): String {
        requireCarriable(value)
        val out = StringBuilder(value.length)
        for (character in value) {
            when {
                character == '&' -> out.append("&amp;")
                character == '<' -> out.append("&lt;")
                // Not required to terminate a text run, but leaving it raw makes the output depend on the
                // parser's error recovery rather than on what this function decided.
                character == '>' -> out.append("&gt;")
                quotes && character == '"' -> out.append("&quot;")
                quotes && character == '\'' -> out.append("&#39;")
                else -> out.append(character)
            }
        }
        return out.toString()
    }

    /**
     * Walks code points, not chars: a high surrogate is only well formed when the *next* char is a low
     * surrogate, so the pair is consumed together and anything else in surrogate range is unpaired.
     */
    private fun requireCarriable(value: String) {
        var index = 0
        while (index < value.length) {
            val character = value[index]
            require(character != NUL) {
                "report text contains U+0000 at index $index; an HTML parser replaces it with U+FFFD"
            }
            if (Character.isHighSurrogate(character)) {
                require(index + 1 < value.length && Character.isLowSurrogate(value[index + 1])) {
                    "report text contains an unpaired high surrogate at index $index; it has no UTF-8 form"
                }
                index++
            } else {
                require(!Character.isLowSurrogate(character)) {
                    "report text contains an unpaired low surrogate at index $index; it has no UTF-8 form"
                }
            }
            index++
        }
    }

    /** Built from its numeric value: a literal NUL here is invisible in every editor (L193). */
    private val NUL = 0x00.toChar()
}
