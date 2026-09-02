package nz.myinspection.core.report.html

/**
 * The document's only styling carrier: one `<style>` block, no inline `style` attribute, no external
 * sheet or font. Only the readability floor lives here — without these rules a full-resolution embedded
 * photograph renders at its natural pixel width and an evidence table has no visible cells. Everything
 * else (responsive, print, dark, forced-colour) is `T3-REPORT-HTML-PRESENTATION`, which grows this
 * constant rather than adding a second stylesheet: a second one would be a second place for a rule to
 * hide report content, and privacy is settled upstream. Selectors are built from [HtmlClass], never
 * written as literal class strings, so a rule cannot come to point at a class nothing emits.
 */
internal object ReportHtmlStylesheet {

    val css: String = """
        .${HtmlClass.ITEM_TABLE.cssName} { border-collapse: collapse; width: 100%; }
        .${HtmlClass.ITEM_TABLE.cssName} th, .${HtmlClass.ITEM_TABLE.cssName} td {
          border: 1px solid currentColor; padding: 0.4rem; text-align: start; vertical-align: top;
        }
        .${HtmlClass.EVIDENCE_FIGURE.cssName} { margin: 0; max-width: 20rem; }
        .${HtmlClass.EVIDENCE_FIGURE.cssName} img { width: 100%; height: auto; }
    """.trimIndent()
}
