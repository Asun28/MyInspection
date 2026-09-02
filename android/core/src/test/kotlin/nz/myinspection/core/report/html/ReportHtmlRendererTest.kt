package nz.myinspection.core.report.html

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import nz.myinspection.core.report.Audience

/**
 * A1-A4 for the self-contained report document. Every assertion reads the produced string: the
 * deliverable is bytes a browser parses, and a test that inspected an intermediate object would pass
 * while the serializer emitted something else entirely.
 */
class ReportHtmlRendererTest {

    private val renderer = ReportHtmlRenderer(ReportHtmlFixtures.images)

    // ---- A1: structure ------------------------------------------------------------------------------

    @Test
    fun `the document is a complete utf-8 html file and is byte-identical on a second render`() {
        val content = ReportHtmlFixtures.content()
        val first = renderer.render(content)
        assertEquals(first, renderer.render(content))
        assertTrue(first.startsWith("<!DOCTYPE html>\n<html lang=\"en\">"), first.take(80))
        assertContains(first, "<meta charset=\"utf-8\">")
        assertContains(first, "<title>ROUTINE · 12 Aroha Ave &amp; Lane</title>")
        assertContains(first, "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        assertTrue(first.trimEnd().endsWith("</html>"), first.takeLast(40))
    }

    @Test
    fun `headings start at level one and never skip a level`() {
        val levels = Regex("<h([1-6])[ >]").findAll(renderer.render(ReportHtmlFixtures.content()))
            .map { it.groupValues[1].toInt() }
            .toList()
        assertTrue(levels.isNotEmpty())
        assertEquals(1, levels.first())
        assertEquals(1, levels.count { it == 1 }, "a document has exactly one h1: $levels")
        levels.zipWithNext { previous, next ->
            assertTrue(next <= previous + 1, "heading order jumps from h$previous to h$next: $levels")
        }
    }

    @Test
    fun `the landlord document carries every section the shared content declares`() {
        val html = renderer.render(ReportHtmlFixtures.content(Audience.LANDLORD))
        // Glossary, summary, the room and its items, the supplement, the remediation and the disclaimer.
        assertContains(html, "Status glossary")
        assertContains(html, "No issue observed")
        assertContains(html, "Summary")
        assertContains(html, "Kitchen \"A\" &amp; Lounge")
        assertContains(html, "Wall paint")
        assertContains(html, "Carpet")
        assertContains(html, ReportHtmlFixtures.WEAR_SENTINEL)
        assertContains(html, "Follow-up inspection requested.")
        assertContains(html, ReportHtmlFixtures.REMEDIATION_SENTINEL)
        assertContains(html, "HIGH")
        assertContains(html, "not legal, building, engineering")
    }

    /**
     * The same instants the PDF footer and cover restate, in the same spelling, so a reader holding both
     * formats of one inspection never has to work out whether two timestamps are the same moment.
     */
    @Test
    fun `identity timestamps are spelled as the composer spells them`() {
        val html = renderer.render(ReportHtmlFixtures.content())
        assertContains(html, "2025-08-16T00:00:00Z")
        assertContains(html, "2025-08-16T02:00:00Z")
        assertContains(html, "2025-08-16T00:10:00Z")
        assertContains(html, "12 Aroha Ave &amp; Lane")
        // Exact: an identity value and a figure caption are both element text, escaped without quotes.
        assertContains(html, "<dd>TENANCY-42 &amp; &lt;b&gt;</dd>")
        assertContains(
            html,
            "<figcaption class=\"evidence-caption\">1.R.1 &amp; &lt;a&gt; · camera · 2025-08-16T00:16:40Z</figcaption>",
        )
    }

    @Test
    fun `bilingual template text is language tagged and free text is not given a guessed language`() {
        val html = renderer.render(ReportHtmlFixtures.content())
        assertContains(html, "<span class=\"text-en\" lang=\"en\">Carpet</span>")
        assertContains(html, "<span class=\"text-zh\" lang=\"zh\">地毯</span>")
        // A guessed lang would have a screen reader pronounce a dictated note with the wrong voice.
        assertContains(html, "<span class=\"text-original\">")
        assertFalse(Regex("class=\"text-original\" lang=").containsMatchIn(html))
    }

    // ---- A2: self-containment and escaping ----------------------------------------------------------

    @Test
    fun `no tag is a script, a frame, an external sheet, a form or an active object`() {
        val forbidden = setOf("script", "iframe", "link", "form", "object", "embed", "base", "svg")
        val names = tagsOf(renderer.render(ReportHtmlFixtures.content()))
            .mapNotNull { Regex("^<([a-zA-Z][a-zA-Z0-9]*)").find(it)?.groupValues?.get(1)?.lowercase() }
        assertTrue(names.isNotEmpty())
        assertEquals(emptyList(), names.filter { it in forbidden })
    }

    @Test
    fun `no tag carries an event handler or a reference that leaves the file`() {
        val tags = tagsOf(renderer.render(ReportHtmlFixtures.content()))
        val handler = Regex("\\son[a-z]+\\s*=")
        assertEquals(emptyList(), tags.filter { handler.containsMatchIn(it) })
        assertEquals(emptyList(), tags.filter { it.contains("://") })
        assertEquals(emptyList(), tags.filter { it.contains("href=") })
        val sources = Regex("src=\"([^\"]*)\"").findAll(tags.joinToString(" ")).map { it.groupValues[1] }.toList()
        assertTrue(sources.isNotEmpty())
        assertEquals(emptyList(), sources.filterNot { it.startsWith("data:image/") })
    }

    /**
     * Not merely that some escaping happened: the exact raw run must be absent and the exact escaped run
     * present, since a serializer that escaped `<` and forgot `&` satisfies any looser assertion.
     */
    @Test
    fun `a hostile dictated note cannot open a tag`() {
        val html = renderer.render(ReportHtmlFixtures.content())
        assertFalse(html.contains(ReportHtmlFixtures.HOSTILE_NOTE), "the raw note reached the document")
        assertContains(html, HtmlEscaping.text(ReportHtmlFixtures.HOSTILE_NOTE))
        assertFalse(html.contains("<script"))
        // The handler text must still appear, as the note's own visible words. What matters is that it is
        // inside no tag: asserting its absence from the whole document would fail a correct renderer.
        assertEquals(emptyList(), tagsOf(html).filter { it.contains("onerror") })
        assertContains(html, "&lt;img src=x onerror=y&gt;")
    }

    @Test
    fun `evidence is embedded as a data uri and never as a path`() {
        val html = renderer.render(ReportHtmlFixtures.content())
        val encoded = Base64.getEncoder().encodeToString(ReportHtmlFixtures.jpegBytes)
        assertContains(html, "src=\"data:image/jpeg;base64,$encoded\"")
        assertEquals(2, Regex("<img ").findAll(html).count())
    }

    /**
     * The document budget is far larger than this report can spend, so only the per-image rule can be
     * refusing. Sizing both bounds alike hides that check: the document bound refuses the same picture,
     * and deleting the per-image check then leaves the suite green (it did - M14 survived that way).
     */
    @Test
    fun `an image past the per-image bound is refused and the whole document still renders`() {
        val bounds = HtmlImageBounds(maxImageBytes = 3, maxTotalImageBytes = 1024)
        val offered = mutableListOf<Int>()
        val source = ReportImageSource { _, max -> offered += max; EmbeddedImage("image/jpeg", ReportHtmlFixtures.jpegBytes) }
        val html = ReportHtmlRenderer(source, bounds).render(ReportHtmlFixtures.content())
        assertEquals(0, Regex("<img ").findAll(html).count())
        assertEquals(2, Regex("class=\"evidence-missing\"").findAll(html).count())
        assertContains(html, "1.2.1")
        // The port is handed the ceiling, so a conforming one never reads the oversized file at all.
        assertEquals(listOf(3, 3), offered)
    }

    /**
     * Cumulative, not a second per-image bound: each picture is within `maxImageBytes` and the second is
     * refused only because the first already spent the document's budget.
     */
    @Test
    fun `the document bound is spent across images, not checked against each one`() {
        val bounds = HtmlImageBounds(maxImageBytes = 4, maxTotalImageBytes = 4)
        val offered = mutableListOf<Int>()
        val source = ReportImageSource { _, max -> offered += max; EmbeddedImage("image/jpeg", ReportHtmlFixtures.jpegBytes) }
        val renderer = ReportHtmlRenderer(source, bounds)
        val html = renderer.render(ReportHtmlFixtures.content())
        assertEquals(1, Regex("<img ").findAll(html).count())
        assertEquals(1, Regex("class=\"evidence-missing\"").findAll(html).count())
        // Call count, not just the forwarded limit: once the document's budget is gone the port is not
        // asked at all, so the second picture is never materialised only to be turned away.
        assertEquals(listOf(4), offered)
        // The budget belongs to the document: one instance rendering two reports must not spend it once.
        offered.clear()
        assertEquals(html, renderer.render(ReportHtmlFixtures.content()))
    }

    /**
     * The type gate is at the boundary, so bytes the port had no right to hand over cannot be constructed
     * and no later path has to remember to check. SVG is the case that matters: it can carry script.
     */
    @Test
    fun `an unexpected media type or an empty picture cannot become an embedded image`() {
        assertFalse(EmbeddedImage.ALLOWED_MEDIA_TYPES.contains("image/svg+xml"))
        assertFailsWith<IllegalArgumentException> { EmbeddedImage("image/svg+xml", ReportHtmlFixtures.jpegBytes) }
        assertFailsWith<IllegalArgumentException> { EmbeddedImage("image/jpeg", ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { HtmlImageBounds(maxImageBytes = 8, maxTotalImageBytes = 4) }
    }

    /**
     * Both directions, over enough documents to reach every branch: a landlord import with pictures, a
     * tenant report (the only agreement) and an unreadable-evidence render (the only missing notice).
     * One direction alone would let a class nobody emits survive as dead surface for the next card.
     */
    @Test
    fun `the classes the renderer emits and the classes HtmlClass declares are the same set`() {
        val documents = listOf(
            renderer.render(
                ReportHtmlFixtures.content(Audience.LANDLORD, true, ReportHtmlFixtures.provenance()),
            ),
            renderer.render(ReportHtmlFixtures.content(Audience.TENANT)),
            ReportHtmlRenderer(ReportHtmlFixtures.noImages).render(ReportHtmlFixtures.content()),
        )
        val emitted = documents.flatMap { html ->
            Regex("class=\"([^\"]*)\"").findAll(html).flatMap { it.groupValues[1].split(" ") }
        }.filter { it.isNotBlank() }.toSet()
        assertEquals(HtmlClass.entries.map { it.cssName }.toSet(), emitted)
    }

    @Test
    fun `a css name is derived from its entry, so a selector cannot drift from the class it targets`() {
        // Literals: rebuilding the expectation from the object under test agrees with any derivation (L165).
        assertEquals("evidence-figure", HtmlClass.EVIDENCE_FIGURE.cssName)
        assertEquals("report", HtmlClass.REPORT.cssName)
        assertEquals("text-original", HtmlClass.TEXT_ORIGINAL.cssName)
        assertEquals(emptyList(), HtmlClass.entries.filter { it.cssName.any { c -> c == '_' || c.isUpperCase() } })
    }

    // ---- A3: alternatives and landmarks ---------------------------------------------------------------

    @Test
    fun `an image alternative names the evidence, its item and its room`() {
        val alts = Regex("alt=\"([^\"]*)\"").findAll(renderer.render(ReportHtmlFixtures.content()))
            .map { it.groupValues[1] }
            .toList()
        assertEquals(2, alts.size)
        val itemAlt = alts.single { it.contains("1.2.1") }
        assertContains(itemAlt, "Carpet")
        // Attribute context, so the quote is an entity here and stays a raw quote in element text above.
        assertContains(itemAlt, "Kitchen &quot;A&quot; &amp; Lounge")
        val roomAlt = alts.single { it.contains("1.R.1") }
        assertContains(roomAlt, "Kitchen &quot;A&quot; &amp; Lounge")
        assertFalse(alts.any { it.isBlank() })
    }

    @Test
    fun `a figure whose evidence cannot be read keeps its number, caption and place in the reading order`() {
        val html = ReportHtmlRenderer(ReportHtmlFixtures.noImages).render(ReportHtmlFixtures.content())
        assertEquals(0, Regex("<img ").findAll(html).count())
        assertEquals(2, Regex("<figure ").findAll(html).count())
        assertContains(html, "1.2.1")
        assertContains(html, "1.R.1")
        assertContains(html, "Photograph not embedded")
        assertContains(html, "照片未内嵌")
    }

    @Test
    fun `the native landmark order is header, main, footer`() {
        val html = renderer.render(ReportHtmlFixtures.content())
        val header = html.indexOf("<header ")
        val main = html.indexOf("<main>")
        val footer = html.indexOf("<footer ")
        assertTrue(header in 1..<main, "header at $header, main at $main")
        assertTrue(main < footer, "main at $main, footer at $footer")
        assertEquals(1, Regex("<main>").findAll(html).count())
    }

    // ---- A4: redaction and fingerprint ----------------------------------------------------------------

    /**
     * The control halves matter as much: without them a renderer emitting no wear, no remediation and no
     * reference at all would pass the tenant assertions perfectly.
     */
    @Test
    fun `landlord-only text the projection removed is absent from the tenant document at byte level`() {
        val landlord = renderer.render(ReportHtmlFixtures.content(Audience.LANDLORD))
        assertContains(landlord, ReportHtmlFixtures.WEAR_SENTINEL)
        assertContains(landlord, ReportHtmlFixtures.REMEDIATION_SENTINEL)

        val tenant = renderer.render(ReportHtmlFixtures.content(Audience.TENANT))
        assertFalse(tenant.contains(ReportHtmlFixtures.WEAR_SENTINEL), "wear or damage reached the tenant")
        assertFalse(tenant.contains(ReportHtmlFixtures.REMEDIATION_SENTINEL), "remediation reached the tenant")
        assertContains(tenant, "Tenant agreement")
    }

    @Test
    fun `a private photo the projection removed leaves no trace, and privacy scope is what decides that`() {
        val included = renderer.render(
            ReportHtmlFixtures.content(Audience.LANDLORD, includePrivacyPhotos = true),
        )
        assertContains(included, ReportHtmlFixtures.PRIVATE_PHOTO_SENTINEL)

        val excluded = renderer.render(ReportHtmlFixtures.content(Audience.LANDLORD))
        assertFalse(
            excluded.contains(ReportHtmlFixtures.PRIVATE_PHOTO_SENTINEL),
            "a privacy-excluded photo reference reached the document",
        )
    }

    @Test
    fun `the embedded fingerprint is the fingerprint of the content that was rendered`() {
        val landlord = ReportHtmlFixtures.content(Audience.LANDLORD)
        val tenant = ReportHtmlFixtures.content(Audience.TENANT)
        assertNotEquals(landlord.semanticFingerprint, tenant.semanticFingerprint)
        assertContains(renderer.render(landlord), landlord.semanticFingerprint)
        assertContains(renderer.render(tenant), tenant.semanticFingerprint)
        assertFalse(renderer.render(tenant).contains(landlord.semanticFingerprint))
    }

    @Test
    fun `the native data hash is restated, not recomputed from the filtered document`() {
        val tenant = ReportHtmlFixtures.content(Audience.TENANT)
        assertContains(renderer.render(tenant), tenant.nativeIntegrity.dataHash)
        assertEquals(
            ReportHtmlFixtures.content(Audience.LANDLORD).nativeIntegrity.dataHash,
            tenant.nativeIntegrity.dataHash,
        )
    }

    @Test
    fun `import provenance is a separate labelled claim and a native report has none`() {
        val imported = ReportHtmlFixtures.content(provenance = ReportHtmlFixtures.provenance())
        val html = renderer.render(imported)
        assertContains(html, "Imported source")
        assertContains(html, "a".repeat(64))
        assertContains(html, "extractor-1")
        assertContains(html, "2026-03-01")
        assertFalse(renderer.render(ReportHtmlFixtures.content()).contains("Imported source"))
    }

    /**
     * Attacker text can never produce a raw `<`, so every match is a tag the renderer wrote; an attribute
     * value holds no raw `>` for the same reason.
     */
    private fun tagsOf(html: String): List<String> =
        Regex("<[a-zA-Z][^>]*>").findAll(html).map { it.value }.toList()
}
/*
 * R4 receipt. 26 single-point mutations, each applied alone and restored before the next, to files pinned
 * at these SHA-256 digests: HtmlClass 390dbe11bfd0ea94, ReportHtmlRenderer 4815929e321f081c,
 * ReportHtmlStylesheet ac7f91e4a4bbdc8a, ReportImageSource 2a306e36a1b63074. 26 killed, 0 survived.
 *
 * Every kill is a failing test - not a compile error, and not a command that never ran. The harness runs
 * the unmutated suite first as a positive control and records a kill only when Gradle reports failing
 * tests: a compile error, a failing test and a shell that cannot find the wrapper all exit 1, and an
 * earlier pass of this batch scored a perfect 31/31 purely because cmd.exe rejected a forward slash in
 * the command name (L282, widened - a kill must be positively attributed, never inferred from an exit
 * code). Escaping itself is no longer mutated here: HtmlEscaping belongs to
 * T3-REPORT-HTML-CHARACTER-POLICY, which carries its own receipt.
 *
 * class contract  M8, M9 break the derived cssName, caught by the two-way parity test.
 * evidence bounds M10, M11, M13 widen what an EmbeddedImage accepts; M12 drops a bounds invariant;
 *                 M14 calls the port even with the budget exhausted; M15 tells the port the per-image
 *                 ceiling while ignoring what is left, M16 the reverse; M17 never spends the budget;
 *                 M18 moves the budget onto the renderer, costing a second report its pictures;
 *                 M32 drops the backstop against a port that overshoots, M33 makes it off by one.
 * evidence output M19 drops the missing-photograph notice; M20 drops a figure caption.
 * escaping use    M21 escapes an image alternative as element text rather than as an attribute;
 *                 M22, M30, M31 leave a caption, the title and an identity value unescaped.
 * structure       M23, M24 weaken or drop the Chinese half; M25 guesses a language for free text;
 *                 M26 moves the header inside main; M27 removes the only h1; M28 skips a heading level;
 *                 M29 restates the native hash in the fingerprint slot.
 */
