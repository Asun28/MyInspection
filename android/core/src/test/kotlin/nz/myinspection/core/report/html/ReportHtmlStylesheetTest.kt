package nz.myinspection.core.report.html

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import nz.myinspection.core.report.Audience

class ReportHtmlStylesheetTest {
    private val css = ReportHtmlStylesheet.css

    @Test
    fun `phone and enlarged text keep relative sizing and scrollable evidence`() {
        val narrow = media(Regex("@media\\s+screen\\s+and\\s+\\(max-width:\\s*40rem\\)"), "M01 width query")
        assertTrue(declarations(narrow, ".report").containsKey("padding"), "M01 narrow report padding removed")
        val report = declarations(css, ".report")
        assertEquals("100%", report["font-size"], "M02 text no longer respects the reader's font size")
        assertEquals("anywhere", report["overflow-wrap"], "M03 long text no longer wraps")
        assertTrue(report["max-width"].orEmpty().endsWith("rem"), "M04 report width no longer scales with text")
        assertFalse(
            Regex("(?i)(?:^|[;{])\\s*(?:min-|max-)?(?:width|inline-size)\\s*:[^;}]*\\d(?:px|pt)\\b")
                .containsMatchIn(css),
            "M04 fixed width prevents narrow or 200 percent text layout",
        )
        val table = declarations(css, ".item-table")
        assertEquals("block", table["display"], "M05 evidence table lost its scroll box")
        assertEquals("auto", table["overflow-x"], "M06 evidence overflow no longer remains reachable")
        assertEquals("100%", table["max-width"], "M05 evidence table can outgrow the viewport")
        assertEquals("normal", declarations(css, ".item-status")["white-space"], "M07 status cannot wrap")
    }

    @Test
    fun `A4 print keeps each figure item and bilingual parent together`() {
        val print = media(Regex("@media\\s+print"), "M08 print query")
        assertEquals("A4", declarations(print, "@page")["size"], "M09 print paper changed")
        for ((selector, mutation) in listOf(
            ".evidence-figure" to "M10 figure break control",
            ".item-row" to "M11 item row break control",
            "*:has(> .text-en + .text-zh)" to "M12 bilingual parent break control",
        )) {
            assertEquals("avoid", declarations(print, selector)["break-inside"], "$mutation removed")
        }
        val table = declarations(print, ".item-table")
        assertEquals("table", table["display"], "M13 print retained the screen scroll box")
        assertEquals("visible", table["overflow"], "M13 print can clip evidence")
        assertEquals("fixed", table["table-layout"], "M13 print columns can overflow A4")
        assertEquals("light", declarations(print, ".report")["color-scheme"], "M14 print inherited a dark canvas")

        val html = documents().joinToString("\n")
        assertTrue(
            Regex("<(?:h[1-3]|p|dt|dd|th|caption)[^>]*>\\s*<span class=\"text-en\"[^>]*>[^<]*</span>\\s*" +
                "<span class=\"text-zh\"[^>]*>[^<]*</span>").containsMatchIn(html),
            "M12 fixture no longer exercises the bilingual parent's real markup",
        )
    }

    @Test
    fun `dark and forced colours preserve a visible textual status`() {
        val dark = media(
            Regex("@media\\s+screen\\s+and\\s+\\(prefers-color-scheme:\\s*dark\\)"), "M15 dark query",
        )
        val forced = media(Regex("@media\\s+\\(forced-colors:\\s*active\\)"), "M16 forced colours query")
        val lightReport = declarations(css, ".report")
        val darkReport = declarations(dark, ".report")
        val forcedReport = declarations(forced, ".report")
        assertEquals("dark", darkReport["color-scheme"], "M15 dark mode removed")
        for ((property, expected) in mapOf(
            "--paper" to "#182226",
            "--ink" to "#edf4f5",
            "--muted" to "#c0d1d5",
            "--line" to "#7b979f",
            "--wash" to "#25343a",
        )) {
            assertEquals(expected, darkReport[property], "M28 dark palette $property removed or changed")
            val lightColour = lightReport[property] ?: fail("M30 light palette $property missing")
            assertNotEquals(
                lightColour.lowercase(), darkReport[property]?.lowercase(),
                "M30 dark palette $property still uses light colour",
            )
        }
        for ((property, expected) in mapOf(
            "--paper" to "Canvas",
            "--ink" to "CanvasText",
            "--muted" to "CanvasText",
            "--line" to "CanvasText",
            "--wash" to "Canvas",
        )) {
            assertEquals(expected, forcedReport[property], "M29 forced palette $property removed or changed")
        }
        assertEquals("auto", forcedReport["forced-color-adjust"], "M18 forced colours overridden")
        val status = declarations(css, ".item-status")
        assertEquals("700", status["font-weight"], "M19 status lost its non-colour emphasis")
        assertTrue(status["border"].orEmpty().contains("solid"), "M19 status lost its non-colour border")
        assertEquals("CanvasText", declarations(forced, ".item-status")["border-color"], "M17 status border lost")
        val emittedStatuses = documents().flatMap { html ->
            Regex("<td class=\"item-status\">([^<]+)</td>").findAll(html).map { it.groupValues[1] }.toList()
        }.toSet()
        assertEquals(setOf("GOOD", "POOR"), emittedStatuses, "M20 actual status words were replaced by colour")
    }

    @Test
    fun `stylesheet selectors enum and real renderer branches have two-way parity`() {
        val expected = HtmlClass.entries.map { it.cssName }.toSet()
        val emitted = documents().flatMap { html ->
            Regex("class=\"([^\"]+)\"").findAll(html)
                .flatMap { it.groupValues[1].split(Regex("\\s+")) }.toList()
        }.toSet()
        assertEquals(expected, emitted, "M21 fixture no longer reaches every renderer class")
        assertTrue("evidence-missing" in emitted, "M21 image-failure branch was omitted")
        assertTrue("text-original" in emitted, "M21 free-text branch was omitted")
        val selected = allRules(css).flatMap { rule ->
            Regex("\\.([a-z][a-z0-9-]*)").findAll(rule.selector).map { it.groupValues[1] }.toList()
        }.toSet()
        assertEquals(expected, selected, "M22/M23 stylesheet added or omitted a class selector")
        assertEquals(emitted, selected, "M22/M23 emitted and styled classes drifted")
    }

    @Test
    fun `all modes use local fonts and cannot hide reorder or manufacture evidence`() {
        val uncommented = css.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse(
            Regex("(?i)url\\s*\\(|@import\\b|@font-face\\b|expression\\s*\\(|javascript:|https?:|<script")
                .containsMatchIn(uncommented),
            "M24 stylesheet gained an external or active resource",
        )
        val fonts = declarations(css, ".report")["font-family"].orEmpty()
        assertTrue(fonts.contains("system-ui") && fonts.contains("sans-serif"), "M25 system font fallback removed")
        assertTrue(fonts.contains("Microsoft YaHei") && fonts.contains("PingFang SC"), "M25 CJK font fallback removed")
        val forbidden = Regex(
            "(?i)(?:^|;)\\s*(?:display\\s*:\\s*none|visibility\\s*:\\s*(?:hidden|collapse)|" +
                "overflow(?:-[xy])?\\s*:\\s*(?:hidden|clip)|opacity\\s*:\\s*0(?:[;\\s]|$)|" +
                "font-size\\s*:\\s*0(?:[;\\s]|$)|color\\s*:\\s*transparent|text-indent\\s*:\\s*-|" +
                "order\\s*:|flex-direction\\s*:[^;]*reverse|content\\s*:)",
        )
        for (rule in allRules(uncommented)) {
            assertFalse(forbidden.containsMatchIn(rule.body), "M26 content changed by CSS: ${rule.selector}")
        }
    }

    private fun documents(): List<String> = listOf(
        ReportHtmlRenderer(ReportHtmlFixtures.images).render(
            ReportHtmlFixtures.content(Audience.LANDLORD, true, ReportHtmlFixtures.provenance()),
        ),
        ReportHtmlRenderer(ReportHtmlFixtures.images).render(ReportHtmlFixtures.content(Audience.TENANT)),
        ReportHtmlRenderer(ReportHtmlFixtures.noImages).render(ReportHtmlFixtures.content()),
    )

    private fun media(selector: Regex, mutation: String): String = blocks(css).singleOrNull {
        selector.matches(it.selector)
    }?.body ?: fail("$mutation missing or duplicated")

    private fun declarations(source: String, selector: String): Map<String, String> = blocks(source)
        .filter { selector in it.selector.split(',').map(String::trim) }
        .flatMap { rule -> rule.body.split(';').filter(String::isNotBlank).map { declaration ->
            val parts = declaration.trim().split(':', limit = 2)
            assertEquals(2, parts.size, "Malformed declaration in $selector: $declaration")
            parts[0].trim() to parts[1].trim()
        } }
        .toMap()

    private data class Rule(val selector: String, val body: String)

    private fun allRules(source: String): List<Rule> = blocks(source).flatMap {
        if (it.selector.startsWith("@media")) allRules(it.body) else listOf(it)
    }

    // Parse the stylesheet's brace-delimited rules so a print assertion cannot match a screen rule.
    private fun blocks(source: String): List<Rule> {
        val clean = source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        val result = mutableListOf<Rule>()
        var start = 0
        while (start < clean.length) {
            val open = clean.indexOf('{', start)
            if (open < 0) {
                assertTrue(clean.substring(start).isBlank(), "Unparsed stylesheet tail")
                break
            }
            var depth = 1
            var end = open + 1
            while (depth > 0 && end < clean.length) {
                when (clean[end++]) {
                    '{' -> depth++
                    '}' -> depth--
                }
            }
            assertEquals(0, depth, "Unbalanced stylesheet braces")
            result += Rule(clean.substring(start, open).trim(), clean.substring(open + 1, end - 1))
            start = end
        }
        return result
    }
}
