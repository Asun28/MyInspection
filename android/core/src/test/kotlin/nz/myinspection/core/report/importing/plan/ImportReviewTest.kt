package nz.myinspection.core.report.importing.plan

import kotlin.test.*
import org.testng.annotations.Test
import nz.myinspection.core.report.importing.docx.extract.*
import nz.myinspection.core.template.Template
import nz.myinspection.core.template.TemplateItem
import nz.myinspection.core.template.TemplateRoom

class ImportReviewTest {
    @Test fun `exact suggestions and aliases require explicit selection and preserve original state`() {
        val item = ExtractedItem("KITCHEN", text(1, "Bench"), text(2, "GOOD"), text(3, "Keep comment"))
        val review = ImportReview.start(input(manifest(items = listOf(item), fragments = listOf(
            ExtractedFragment(FragmentRole.ITEM, item.name), ExtractedFragment(FragmentRole.COMMENT, item.comment!!),
        ))))
        assertEquals(3, review.sources.size)
        assertTrue(review.sources.all { it.state == ImportDecisionState.ACTION_REQUIRED })
        assertEquals(3, review.unratedTargets.size)
        val selected = review.decide(id(ImportSourceCategory.ITEM), ImportDecision.Item(bench, "FAIR"))
        assertEquals(listOf("FAIR"), selected.items.map { it.status })
        assertEquals(listOf("Keep comment"), selected.items.map { it.note })
        assertEquals(3, selected.sources.count { it.state == ImportDecisionState.CONFIRMED })
        assertEquals(List(3) { id(ImportSourceCategory.ITEM) }, selected.sources.map { it.owner })
        assertTrue(selected.blockers.isEmpty())
        assertEquals(listOf(wall, summary), selected.unratedTargets)
        assertTrue(review.items.isEmpty())
        assertFailsWith<IllegalArgumentException> { selected.decide(id(ImportSourceCategory.ITEM), ImportDecision.Item(bench, "GOOD")) }
        assertFailsWith<IllegalArgumentException> { selected.decide(id(ImportSourceCategory.FRAGMENT), ImportDecision.Note(wall)) }
        val replaced = selected.replace(id(ImportSourceCategory.ITEM), ImportDecision.Item(wall, "GOOD"))
        assertEquals(wall, replaced.items.single().target)
        assertEquals(bench, selected.items.single().target)
    }

    @Test fun `unknown source invented target wrong status and duplicate targets reject atomically`() {
        val review = ImportReview.start(input(manifest(items = listOf(
            ExtractedItem(null, text(1, "unknown"), text(2, "Excellent"), null),
            ExtractedItem("KITCHEN", text(3, "Bench"), null, null),
        ))))
        listOf("", "UNKNOWN", "Excellent").forEach { status ->
            assertFailsWith<IllegalArgumentException> { review.decide(id(ImportSourceCategory.ITEM), ImportDecision.Item(bench, status)) }
        }
        assertFailsWith<IllegalArgumentException> { review.decide(id(ImportSourceCategory.ITEM, 9), ImportDecision.Item(bench, "GOOD")) }
        assertFailsWith<IllegalArgumentException> { review.decide(id(ImportSourceCategory.ITEM), ImportDecision.Item(bench.copy(stableId = "MADE-UP"), "GOOD")) }
        assertFailsWith<IllegalArgumentException> { review.decide(id(ImportSourceCategory.ITEM), ImportDecision.Item(bench.copy(instanceNo = 2), "GOOD")) }
        val selected = review.decide(id(ImportSourceCategory.ITEM), ImportDecision.Item(bench, "GOOD"))
        assertFailsWith<IllegalArgumentException> { selected.decide(id(ImportSourceCategory.ITEM, 1), ImportDecision.Item(bench, "FAIR")) }
        assertEquals(1, selected.items.size)
        assertEquals(1, selected.sources.count { it.state == ImportDecisionState.ACTION_REQUIRED })
        val complete = selected.decide(id(ImportSourceCategory.ITEM, 1), ImportDecision.Item(wall, "FAIR"))
        assertTrue(complete.blockers.isEmpty())
    }

    @Test fun `photos require explicit privacy even when excluded and retain every placement`() {
        val image = ExtractedImage("word/media/private.jpg", "c".repeat(64), 200, 200)
        val review = ImportReview.start(input(manifest(images = listOf(image), placements = listOf(
            DrawingPlacement(text(1, "").source, DrawingKind.INLINE, image.part),
            DrawingPlacement(text(2, "").source, DrawingKind.ANCHORED, image.part),
            DrawingPlacement(text(3, "").source, DrawingKind.INLINE, null),
        ))))
        assertEquals(ImportPhotoReviewState.UNREVIEWED_EXCLUDED, review.plan.photoReviews.single().state)
        assertEquals(4, review.sources.count { it.state == ImportDecisionState.ACTION_REQUIRED })
        assertFailsWith<IllegalArgumentException> { review.decide(id(ImportSourceCategory.IMAGE), ImportDecision.Exclude(ImportExclusionReason.NOT_RELEVANT)) }
        assertFailsWith<IllegalArgumentException> { review.decide(id(ImportSourceCategory.PLACEMENT, 2), ImportDecision.Photo(bench, ImportPrivacy.TENANT_BELONGINGS)) }
        val included = review.decide(id(ImportSourceCategory.IMAGE), ImportDecision.Photo(bench, ImportPrivacy.TENANT_BELONGINGS))
        assertEquals(3, included.sources.count { it.state == ImportDecisionState.CONFIRMED })
        assertEquals(ImportPrivacy.TENANT_BELONGINGS, included.photos.single().privacy)
        assertEquals("c".repeat(64), included.photos.single().sourceSha256)
        assertEquals(1, included.blockers.size)
        val complete = included.decide(id(ImportSourceCategory.PLACEMENT, 2), ImportDecision.Exclude(ImportExclusionReason.MISSING_MEDIA))
        assertTrue(complete.blockers.isEmpty())
        assertEquals(3, complete.unratedTargets.size)
        val excluded = review.decide(id(ImportSourceCategory.IMAGE), ImportDecision.Exclude(ImportExclusionReason.PRIVACY, ImportPrivacy.TENANT_BELONGINGS))
        assertEquals(3, excluded.sources.count { it.state == ImportDecisionState.EXCLUDED })
        assertTrue(excluded.photos.isEmpty())
    }

    @Test fun `caption parent and each candidate need their own decision without residual loss`() {
        val parent = text(10, "Prefix 1 - First 2 - Second suffix")
        val review = ImportReview.start(input(manifest(
            fragments = listOf(ExtractedFragment(FragmentRole.CAPTION, parent)),
            captions = listOf(CaptionCandidate("1", text(10, "First")), CaptionCandidate("2", text(10, "Second"))),
            warnings = listOf(ExtractionWarning(ExtractionWarningCode.AMBIGUOUS_CAPTIONS, parent.source)),
        )))
        val parentFirst = review.decide(id(ImportSourceCategory.FRAGMENT), ImportDecision.Note(bench))
        assertEquals(ImportDecisionState.ACTION_REQUIRED, parentFirst.sources.single { it.id == id(ImportSourceCategory.WARNING) }.state)
        val one = review.decide(id(ImportSourceCategory.CAPTION), ImportDecision.Note(bench))
        assertEquals(3, one.sources.count { it.state == ImportDecisionState.ACTION_REQUIRED })
        val two = one.decide(id(ImportSourceCategory.CAPTION, 1), ImportDecision.Exclude(ImportExclusionReason.DUPLICATE))
        assertFalse(two.blockers.isEmpty())
        val complete = two.decide(id(ImportSourceCategory.FRAGMENT), ImportDecision.Note(bench))
        assertTrue(complete.blockers.isEmpty())
        assertEquals(listOf("Prefix 1 - First 2 - Second suffix", "First"), complete.notes.map { it.text })
        assertEquals("Prefix 1 - First 2 - Second suffix\nFirst", complete.items.single().note)
        assertNull(complete.items.single().status)
        assertEquals(3, complete.unratedTargets.size)
        assertEquals(4, complete.sources.size)
    }

    @Test fun `summary paragraphs aggregate in manifest order and edit clears complete note status`() {
        val paragraphs = listOf(text(50, "First paragraph"), text(4, "Second paragraph"), text(90, "Discard me"))
        var review = ImportReview.start(input(manifest(summary = paragraphs, fragments = listOf(ExtractedFragment(FragmentRole.NARRATIVE, paragraphs[0])))))
        review = review.decide(id(ImportSourceCategory.SUMMARY, 1), ImportDecision.Summary)
        assertFailsWith<IllegalArgumentException> { review.selectSummaryStatus("GOOD") }
        review = review.decide(id(ImportSourceCategory.SUMMARY), ImportDecision.Summary)
            .decide(id(ImportSourceCategory.SUMMARY, 2), ImportDecision.Exclude(ImportExclusionReason.NOT_RELEVANT))
        assertTrue(review.items.isEmpty())
        assertFalse(review.blockers.isEmpty())
        assertFailsWith<IllegalArgumentException> { review.selectSummaryStatus("Excellent") }
        val complete = review.selectSummaryStatus("FAIR")
        assertTrue(complete.blockers.isEmpty())
        assertEquals(summary, complete.items.single().target)
        assertEquals("First paragraph\nSecond paragraph", complete.items.single().note)
        assertEquals("FAIR", complete.items.single().status)
        val changed = complete.replace(id(ImportSourceCategory.SUMMARY), ImportDecision.Exclude(ImportExclusionReason.NOT_RELEVANT))
        assertTrue(changed.items.isEmpty())
        assertFalse(changed.blockers.isEmpty())
        assertEquals("Second paragraph", changed.selectSummaryStatus("GOOD").items.single().note)
        val empty = changed.replace(id(ImportSourceCategory.SUMMARY, 1), ImportDecision.Exclude(ImportExclusionReason.NOT_RELEVANT))
        assertTrue(empty.blockers.isEmpty())
        assertTrue(empty.items.isEmpty())
        assertFailsWith<IllegalArgumentException> { empty.selectSummaryStatus("GOOD") }
    }

    @Test fun `source identity cannot be mapped and global warning cannot be acknowledged`() {
        val author = text(1, "Private Author")
        val review = ImportReview.start(input(manifest(identity = listOf(IdentityCandidate("author", author)),
            fragments = listOf(ExtractedFragment(FragmentRole.IDENTITY, author)),
            warnings = listOf(ExtractionWarning(ExtractionWarningCode.UNRESOLVED_TEXT, null),
                ExtractionWarning(ExtractionWarningCode.URL_EXCLUDED, null)))))
        assertFailsWith<IllegalArgumentException> { review.decide(id(ImportSourceCategory.IDENTITY), ImportDecision.Note(bench)) }
        assertFailsWith<IllegalArgumentException> { review.decide(id(ImportSourceCategory.WARNING), ImportDecision.Exclude(ImportExclusionReason.NOT_RELEVANT)) }
        val excluded = review.decide(id(ImportSourceCategory.IDENTITY), ImportDecision.Exclude(ImportExclusionReason.PROVENANCE))
        assertEquals(3, excluded.sources.count { it.state == ImportDecisionState.EXCLUDED })
        assertEquals(listOf(id(ImportSourceCategory.WARNING)), excluded.blockers.flatMap { it.sourceIds })
        assertEquals(ImportExclusionReason.URL, excluded.sources.single { it.id == id(ImportSourceCategory.WARNING, 1) }.reason)
    }

    @Test fun `owned unresolved warnings follow explicit content but missing media requires exclusion`() {
        val fragment = ExtractedFragment(FragmentRole.UNKNOWN, text(5, "Review this"))
        val review = ImportReview.start(input(manifest(fragments = listOf(fragment), warnings = listOf(
            ExtractionWarning(ExtractionWarningCode.UNRESOLVED_TEXT, fragment.text.source),
            ExtractionWarning(ExtractionWarningCode.MISSING_IMAGE, fragment.text.source),
        ))))
        val mapped = review.decide(id(ImportSourceCategory.FRAGMENT), ImportDecision.Note(bench))
        assertEquals(listOf(id(ImportSourceCategory.WARNING, 1)), mapped.blockers.flatMap { it.sourceIds })
        assertTrue(mapped.replace(id(ImportSourceCategory.FRAGMENT), ImportDecision.Exclude(ImportExclusionReason.MISSING_MEDIA)).blockers.isEmpty())
    }

    @Test fun `context blockers persist and all public collections are immutable`() {
        val invalid = ImportReview.start(input(manifest(), active = true, property = null, date = "2026-02-30"))
        assertEquals(setOf(ImportBlockerCode.ACTIVE_DRAFT, ImportBlockerCode.MISSING_PROPERTY, ImportBlockerCode.INVALID_REPORT_DATE), invalid.blockers.map { it.code }.toSet())
        val review = ImportReview.start(input(manifest(items = listOf(ExtractedItem(null, text(1, "Bench"), null, null)))))
            .decide(id(ImportSourceCategory.ITEM), ImportDecision.Item(bench, "GOOD"))
        listOf(review.sources, review.items, review.notes, review.photos, review.blockers, review.unratedTargets).forEach { list ->
            @Suppress("UNCHECKED_CAST")
            assertFailsWith<UnsupportedOperationException> { (list as MutableList<Any?>).add(null) }
        }
        assertFailsWith<IllegalArgumentException> { review.replace(id(ImportSourceCategory.CAPTION), ImportDecision.Note(bench)) }
    }

    @Test fun `fixed provenance reasons cannot be changed and summary cannot be written through note`() {
        val codes = listOf(ExtractionWarningCode.PAGINATION_EXCLUDED, ExtractionWarningCode.METADATA_EXCLUDED,
            ExtractionWarningCode.URL_EXCLUDED, ExtractionWarningCode.LAYOUT_IMAGE_EXCLUDED)
        val review = ImportReview.start(input(manifest(warnings = codes.map { ExtractionWarning(it, null) },
            fragments = listOf(ExtractedFragment(FragmentRole.NARRATIVE, text(1, "Narrative"))),
            captions = listOf(CaptionCandidate("1", text(2, "Caption"))))))
        assertEquals(listOf(ImportExclusionReason.PAGINATION, ImportExclusionReason.METADATA, ImportExclusionReason.URL, ImportExclusionReason.LAYOUT_IMAGE),
            review.sources.filter { it.id.category == ImportSourceCategory.WARNING }.map { it.reason })
        codes.indices.forEach { index ->
            assertFailsWith<IllegalArgumentException> { review.replace(id(ImportSourceCategory.WARNING, index), ImportDecision.Exclude(ImportExclusionReason.NOT_RELEVANT)) }
        }
        listOf(ImportSourceCategory.FRAGMENT, ImportSourceCategory.CAPTION).forEach { category ->
            assertFailsWith<IllegalArgumentException> { review.decide(id(category), ImportDecision.Note(summary)) }
        }
        assertEquals(2, review.blockers.size)
    }

    @Test fun `separate equal text is retained and replacement cannot steal another target`() {
        val item = ExtractedItem(null, text(1, "Bench"), null, null)
        val review = ImportReview.start(input(manifest(items = listOf(item, item.copy(name = text(2, "Bench"))),
            fragments = listOf(ExtractedFragment(FragmentRole.NARRATIVE, text(3, "Bench"))))))
            .decide(id(ImportSourceCategory.ITEM), ImportDecision.Item(bench, "GOOD"))
            .decide(id(ImportSourceCategory.ITEM, 1), ImportDecision.Item(wall, "FAIR"))
        assertFailsWith<IllegalArgumentException> { review.replace(id(ImportSourceCategory.ITEM), ImportDecision.Item(wall, "GOOD")) }
        assertEquals(listOf(bench, wall), review.items.map { it.target })
        assertEquals(listOf(id(ImportSourceCategory.FRAGMENT)), review.blockers.flatMap { it.sourceIds })
        val notes = review.decide(id(ImportSourceCategory.FRAGMENT), ImportDecision.Note(bench))
        assertEquals("Bench", notes.notes.single().text)
        assertEquals("Bench", notes.items.single { it.target == bench }.note)
        assertTrue(notes.blockers.isEmpty())
    }

    @Test fun `duplicate image parts keep placements unresolved and photo warning cannot bypass privacy`() {
        val image = ExtractedImage("word/media/a.jpg", "c".repeat(64), 100, 100)
        val review = ImportReview.start(input(manifest(images = listOf(image, image), placements = listOf(
            DrawingPlacement(text(1, "").source, DrawingKind.INLINE, image.part)), warnings = listOf(
            ExtractionWarning(ExtractionWarningCode.IMAGE_REVIEW_REQUIRED, SourceLocation(image.part, 0))))))
        val selected = review.decide(id(ImportSourceCategory.IMAGE), ImportDecision.Photo(bench, ImportPrivacy.NO_TENANT_BELONGINGS))
            .decide(id(ImportSourceCategory.IMAGE, 1), ImportDecision.Exclude(ImportExclusionReason.DUPLICATE, ImportPrivacy.NO_TENANT_BELONGINGS))
        assertEquals(listOf(id(ImportSourceCategory.PLACEMENT), id(ImportSourceCategory.WARNING)), selected.blockers.flatMap { it.sourceIds })
        assertFailsWith<IllegalArgumentException> { selected.decide(id(ImportSourceCategory.WARNING), ImportDecision.Photo(bench, ImportPrivacy.NO_TENANT_BELONGINGS)) }
        val single = ImportReview.start(input(manifest(images = listOf(image), warnings = listOf(
            ExtractionWarning(ExtractionWarningCode.IMAGE_REVIEW_REQUIRED, SourceLocation(image.part, 0))))))
        assertEquals(2, single.blockers.size)
        assertTrue(single.decide(id(ImportSourceCategory.IMAGE), ImportDecision.Photo(bench, ImportPrivacy.NO_TENANT_BELONGINGS)).blockers.isEmpty())
        val invalid = ImportReview.start(input(manifest(images = listOf(image.copy(sha256 = "")))))
        assertFailsWith<IllegalArgumentException> { invalid.decide(id(ImportSourceCategory.IMAGE), ImportDecision.Photo(bench, ImportPrivacy.NO_TENANT_BELONGINGS)) }
    }

    @Test fun `summary and ordinary item writers conflict in either order without partial results`() {
        val report = manifest(items = listOf(ExtractedItem(null, text(1, "Unknown"), null, null)), summary = listOf(text(2, "Summary")))
        val review = ImportReview.start(input(report))
        val itemFirst = review.decide(id(ImportSourceCategory.ITEM), ImportDecision.Item(summary, "GOOD"))
        assertFailsWith<IllegalArgumentException> { itemFirst.decide(id(ImportSourceCategory.SUMMARY), ImportDecision.Summary) }
        assertEquals("GOOD", itemFirst.items.single().status)
        val summaryFirst = review.decide(id(ImportSourceCategory.SUMMARY), ImportDecision.Summary).selectSummaryStatus("FAIR")
        assertFailsWith<IllegalArgumentException> { summaryFirst.decide(id(ImportSourceCategory.ITEM), ImportDecision.Item(summary, "GOOD")) }
        assertEquals("Summary", summaryFirst.items.single().note)
        assertEquals("FAIR", summaryFirst.items.single().status)
    }

    @Test fun `ambiguous identity aliases cannot smuggle author text into a native note`() {
        val author = text(1, "Private author")
        val review = ImportReview.start(input(manifest(identity = listOf(IdentityCandidate("author", author), IdentityCandidate("organisation", author)),
            fragments = listOf(ExtractedFragment(FragmentRole.NARRATIVE, author)))))
        assertFailsWith<IllegalArgumentException> { review.decide(id(ImportSourceCategory.FRAGMENT), ImportDecision.Note(bench)) }
        assertTrue(review.notes.isEmpty())
        assertEquals(3, review.blockers.size)
    }

    @Test fun `native note output joins item comment and explicit fragments without overwrite`() {
        val review = ImportReview.start(input(manifest(items = listOf(ExtractedItem(null, text(1, "Bench"), null, text(2, "Original"))),
            fragments = listOf(ExtractedFragment(FragmentRole.NARRATIVE, text(3, "Later"))))))
            .decide(id(ImportSourceCategory.FRAGMENT), ImportDecision.Note(bench))
            .decide(id(ImportSourceCategory.ITEM), ImportDecision.Item(bench, "GOOD"))
        assertEquals(1, review.items.size)
        assertEquals("Original\nLater", review.items.single().note)
    }

    @Test fun `ownerless warnings preserve their named blocker codes`() {
        val review = ImportReview.start(input(manifest(warnings = listOf(
            ExtractionWarning(ExtractionWarningCode.MISSING_IMAGE, null),
            ExtractionWarning(ExtractionWarningCode.AMBIGUOUS_CAPTIONS, null),
            ExtractionWarning(ExtractionWarningCode.IMAGE_REVIEW_REQUIRED, null),
        ))))
        assertEquals(listOf(ImportBlockerCode.MISSING_IMAGE, ImportBlockerCode.AMBIGUOUS_CAPTION, ImportBlockerCode.PHOTO_REVIEW_REQUIRED), review.blockers.map { it.code })
    }

    @Test fun `identity evidence cannot reenter via captions summary or item comments`() {
        val author = text(1, "Private author")
        val identity = listOf(IdentityCandidate("author", author))
        val cases = listOf(
            Triple(manifest(identity = identity, captions = listOf(CaptionCandidate("1", author))), id(ImportSourceCategory.CAPTION), ImportDecision.Note(bench)),
            Triple(manifest(identity = identity, summary = listOf(author)), id(ImportSourceCategory.SUMMARY), ImportDecision.Summary),
            Triple(manifest(identity = identity, items = listOf(ExtractedItem(null, text(2, "Bench"), null, author))), id(ImportSourceCategory.ITEM), ImportDecision.Item(bench, "GOOD")),
        )
        cases.forEach { (report, source, choice) ->
            val review = ImportReview.start(input(report)).decide(id(ImportSourceCategory.IDENTITY), ImportDecision.Exclude(ImportExclusionReason.PROVENANCE))
            assertFailsWith<IllegalArgumentException> { review.decide(source, choice) }
            assertFailsWith<IllegalArgumentException> { review.decide(source, ImportDecision.Exclude(ImportExclusionReason.NOT_RELEVANT)) }
            assertTrue(review.decide(source, ImportDecision.Exclude(ImportExclusionReason.PROVENANCE)).blockers.isEmpty())
            assertTrue(review.items.isEmpty())
            assertEquals(listOf(source), review.blockers.flatMap { it.sourceIds })
        }
        val separate = ImportReview.start(input(manifest(identity = identity, captions = listOf(CaptionCandidate("1", text(3, "Private author"))))))
            .decide(id(ImportSourceCategory.CAPTION), ImportDecision.Note(bench))
        assertEquals("Private author", separate.items.single().note)
    }

    @Test(timeOut = 15_000) fun `large warning inventories retain all obligations within bounded time`() {
        val count = 10_000
        val fragments = List(count) { ExtractedFragment(FragmentRole.CAPTION, text(it, "Caption $it")) }
        val captions = List(count) { CaptionCandidate("$it", fragments[it].text) }
        val warnings = List(count) { ExtractionWarning(ExtractionWarningCode.AMBIGUOUS_CAPTIONS, fragments[it].text.source) }
        val started = System.nanoTime()
        val review = ImportReview.start(input(manifest(fragments = fragments, captions = captions, warnings = warnings)))
        assertTrue(System.nanoTime() - started < 5_000_000_000L, "Review exceeded five seconds")
        assertEquals(count * 3, review.sources.size)
        assertEquals(count * 3, review.blockers.size)
        assertTrue(review.sources.all { it.state == ImportDecisionState.ACTION_REQUIRED })
    }

    private val bench = ImportTarget("KITCHEN", 1, "KIT-BENCH-01", "KITCHEN")
    private val wall = ImportTarget("KITCHEN", 1, "KIT-WALL-01", "KITCHEN")
    private val summary = ImportTarget("GENERAL", 1, "GEN-SUMMARY-01", "GENERAL")
    private fun id(category: ImportSourceCategory, index: Int = 0) = ImportSourceId(category, index)
    private fun text(ordinal: Int, value: String) = ExtractedText(SourceLocation("word/document.xml", ordinal), value)
    private fun input(manifest: DocxExtractionManifest, active: Boolean = false, property: String? = "property-1", date: String = "2026-09-08") = ImportPlanningInput(
        property, "tenancy-1", date, "a".repeat(64), active, RoutineTemplateBinding("routine-v2", "b".repeat(64), Template("ROUTINE", 2,
            listOf(TemplateRoom("KITCHEN"), TemplateRoom("GENERAL")), listOf(
                TemplateItem("KIT-BENCH-01", "INTERIOR", "KITCHEN", "Bench", "台面", listOf("GOOD", "FAIR")),
                TemplateItem("KIT-WALL-01", "INTERIOR", "KITCHEN", "Wall", "墙", listOf("GOOD", "FAIR")),
                TemplateItem("GEN-SUMMARY-01", "GENERAL", "GENERAL", "Summary", "摘要", listOf("GOOD", "FAIR")),
            ))), listOf(ImportRoomInstance("KITCHEN", 1, "KITCHEN"), ImportRoomInstance("GENERAL", 1, "GENERAL")), emptySet(), manifest,
    )
    private fun manifest(items: List<ExtractedItem> = emptyList(), fragments: List<ExtractedFragment> = emptyList(),
        identity: List<IdentityCandidate> = emptyList(), summary: List<ExtractedText> = emptyList(),
        captions: List<CaptionCandidate> = emptyList(), images: List<ExtractedImage> = emptyList(),
        placements: List<DrawingPlacement> = emptyList(), warnings: List<ExtractionWarning> = emptyList(),
    ) = DocxExtractionManifest(items, fragments, warnings, identity, summary, captions, images, placements)
}
