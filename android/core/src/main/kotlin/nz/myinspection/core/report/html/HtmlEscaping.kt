package nz.myinspection.core.report.html

/**
 * Contextual escaping for the two places report text reaches: element content and a double-quoted
 * attribute value. There is no third context — this document has no `<script>`, and no report text is
 * ever written into `<style>` — so an escaper for a scripting or CSS context would be dead code that
 * only invites someone to open such a context later.
 *
 * Nothing is stripped or substituted. A control character or an unpaired byte in a dictated note cannot
 * break out of either context once the syntactic characters are entities, and silently deleting it would
 * make the report disagree with the evidence it claims to record.
 */
internal object HtmlEscaping {

    /**
     * Escapes for element content and for RCDATA (`<title>`). `>` is escaped too: it is not required to
     * terminate a text run, but leaving it raw makes the output depend on the parser's error recovery.
     */
    fun text(value: String): String = escape(value, quotes = false)

    /** Escapes for a double-quoted attribute value. Both quote forms go, so the caller cannot pick wrong. */
    fun attribute(value: String): String = escape(value, quotes = true)

    private fun escape(value: String, quotes: Boolean): String {
        val out = StringBuilder(value.length)
        for (character in value) {
            when {
                character == '&' -> out.append("&amp;")
                character == '<' -> out.append("&lt;")
                character == '>' -> out.append("&gt;")
                quotes && character == '"' -> out.append("&quot;")
                quotes && character == '\'' -> out.append("&#39;")
                else -> out.append(character)
            }
        }
        return out.toString()
    }
}
