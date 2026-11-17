package nz.myinspection.core.report.importing.plan

import kotlin.test.*
import org.testng.annotations.Test
import nz.myinspection.core.report.importing.docx.extract.*
import nz.myinspection.core.template.*
import nz.myinspection.core.report.importing.plan.ImportSourceCategory as Category

class ImportReviewPreviewTest {
    @Test fun `complete preview explicitly confirms all exact choices and retains aliases and unrated items`() {
        val comment = pText(3, "Original comment")
        val initial = ImportReview.start(pInput(pManifest(items = listOf(pItem("Bench", 1).copy(comment = comment), pItem("Wall", 4)),
            fragments = listOf(ExtractedFragment(FragmentRole.COMMENT, comment)))))
        val preview = ImportReviewPreview.capture(initial)
        assertSame(initial, preview.review)
        assertEquals(2, preview.review.plan.rows.size)
        assertEquals(3, preview.review.sources.size)
        assertEquals(listOf(pId(Category.ITEM), pId(Category.ITEM, 1)), preview.bulkSources)
        assertFalse(preview.isReady(initial))
        assertTrue(initial.items.isEmpty())
        val confirmed = preview.confirmExact(initial, preview.bulkSources.reversed())
        assertTrue(confirmed.blockers.isEmpty())
        assertEquals(listOf("GOOD", "GOOD"), confirmed.items.map { it.status })
        assertEquals("Original comment", confirmed.items.first().note)
        assertEquals(listOf(pSummary), confirmed.unratedTargets)
        assertEquals(pId(Category.ITEM), confirmed.sources.single { it.id.category == Category.FRAGMENT }.owner)
        assertFalse(preview.isReady(confirmed))
        assertTrue(ImportReviewPreview.capture(confirmed).isReady(confirmed))
        assertFailsWith<UnsupportedOperationException> { (preview.bulkSources as MutableList).clear() }
    }

    @Test fun `partial unknown duplicate and stale bulk reject atomically`() {
        val review = ImportReview.start(pInput(pManifest(items = listOf(pItem("Bench", 1), pItem("Wall", 4)))))
        val preview = ImportReviewPreview.capture(review)
        listOf(emptyList(), listOf(pId(Category.ITEM)), listOf(pId(Category.ITEM, 9)),
            preview.bulkSources + pId(Category.ITEM)).forEach { request ->
            assertFailsWith<IllegalArgumentException> { preview.confirmExact(review, request) }
            assertTrue(review.items.isEmpty())
            assertEquals(2, review.blockers.size)
        }
        val changed = review.decide(pId(Category.ITEM), ImportDecision.Item(pBench, "FAIR"))
        assertFailsWith<IllegalArgumentException> { preview.confirmExact(changed, preview.bulkSources) }
        assertEquals("FAIR", changed.items.single().status)
        val foreign = ImportReview.start(pInput(pManifest(items = listOf(pItem("Bench", 1), pItem("Wall", 4))), property = "property-2"))
        assertFailsWith<IllegalArgumentException> { preview.confirmExact(foreign, preview.bulkSources) }
        assertTrue(foreign.items.isEmpty())
        val duplicate = ImportReview.start(pInput(pManifest(items = listOf(pItem("Bench", 1), pItem("Bench", 4)))))
        val duplicatePreview = ImportReviewPreview.capture(duplicate)
        assertFailsWith<IllegalArgumentException> { duplicatePreview.confirmExact(duplicate, duplicatePreview.bulkSources) }
        assertTrue(duplicate.items.isEmpty())
        assertEquals(2, duplicate.blockers.size)
    }

    @Test fun `material revisions invalidate preview even after reverting to identical decisions`() {
        val review = ImportReview.start(pInput(pManifest(items = listOf(pItem("Bench", 1)))))
            .decide(pId(Category.ITEM), ImportDecision.Item(pBench, "GOOD"))
        val preview = ImportReviewPreview.capture(review)
        val revisions = listOf(
            review.replace(pId(Category.ITEM), ImportDecision.Item(pWall, "GOOD")),
            review.replace(pId(Category.ITEM), ImportDecision.Exclude(ImportExclusionReason.NOT_RELEVANT)),
            review.replace(pId(Category.ITEM), ImportDecision.Item(pBench, "FAIR"))
                .replace(pId(Category.ITEM), ImportDecision.Item(pBench, "GOOD")),
            ImportReview.start(pInput(pManifest(items = listOf(pItem("Bench", 1))), property = "property-2"))
                .decide(pId(Category.ITEM), ImportDecision.Item(pBench, "GOOD")),
        )
        assertTrue(preview.isReady(review))
        revisions.forEach {
            assertFalse(preview.isReady(it))
            assertFailsWith<IllegalArgumentException> { ImportMappingReceipt.create(it, preview) }
            assertTrue(ImportReviewPreview.capture(it).isReady(it))
        }
    }

    @Test fun `unsupported statuses caption aliases and identity never join exact bulk`() {
        val caption = pText(9, "Bench")
        val review = ImportReview.start(pInput(pManifest(items = listOf(
            pItem("Bench", 1, "Excellent"), pItem("Wall", 4, ""), pItem("Bench", 9)),
            fragments = listOf(ExtractedFragment(FragmentRole.CAPTION, caption)),
            captions = listOf(CaptionCandidate("1", caption)))))
        val preview = ImportReviewPreview.capture(review)
        assertTrue(preview.bulkSources.isEmpty())
        assertFailsWith<IllegalArgumentException> { preview.confirmExact(review, listOf(pId(Category.CAPTION))) }
        assertFailsWith<IllegalArgumentException> { review.decide(pId(Category.ITEM), ImportDecision.Item(pBench, "Excellent")) }
        assertFalse(preview.isReady(review))
        val identityReview = ImportReview.start(pInput(pManifest(items = listOf(pItem("Bench", 1)),
            identity = listOf(IdentityCandidate("author", pText(1, "Bench"))))))
        assertFailsWith<IllegalArgumentException> {
            ImportReviewPreview.capture(identityReview).confirmExact(identityReview, listOf(pId(Category.ITEM)))
        }
        assertTrue(identityReview.items.isEmpty())
    }

    @Test fun `privacy summary and every caption constituent gate integrated readiness`() {
        val image = ExtractedImage("word/media/private.jpg", "c".repeat(64), 100, 100)
        val parent = pText(10, "Prefix 1 - Caption suffix")
        var review = ImportReview.start(pInput(pManifest(items = listOf(pItem("Bench", 1)),
            fragments = listOf(ExtractedFragment(FragmentRole.CAPTION, parent)),
            captions = listOf(CaptionCandidate("1", pText(10, "Caption"))),
            summary = listOf(pText(40, "First"), pText(20, "Second")), images = listOf(image),
            placements = listOf(DrawingPlacement(pText(12, "").source, DrawingKind.INLINE, image.part)),
            warnings = listOf(ExtractionWarning(ExtractionWarningCode.AMBIGUOUS_CAPTIONS, parent.source)))))
        val start = ImportReviewPreview.capture(review)
        assertEquals(ImportPhotoReviewState.UNREVIEWED_EXCLUDED, start.review.plan.photoReviews.single().state)
        assertEquals(listOf(pId(Category.ITEM)), start.bulkSources)
        review = start.confirmExact(review, start.bulkSources)
        assertTrue(review.photos.isEmpty())
        listOf(
            pId(Category.CAPTION) to ImportDecision.Note(pBench),
            pId(Category.FRAGMENT) to ImportDecision.Exclude(ImportExclusionReason.DUPLICATE),
            pId(Category.SUMMARY, 1) to ImportDecision.Summary,
            pId(Category.SUMMARY) to ImportDecision.Summary,
            pId(Category.IMAGE) to ImportDecision.Photo(pBench, ImportPrivacy.TENANT_BELONGINGS),
        ).forEach { (id, decision) ->
            val preview = ImportReviewPreview.capture(review)
            assertFalse(preview.isReady(review))
            assertFailsWith<IllegalArgumentException> { ImportMappingReceipt.create(review, preview) }
            review = review.decide(id, decision)
        }
        assertFalse(ImportReviewPreview.capture(review).isReady(review))
        review = review.selectSummaryStatus("FAIR")
        val ready = ImportReviewPreview.capture(review)
        assertTrue(ready.isReady(review))
        assertEquals("First\nSecond", review.items.single { it.target == pSummary }.note)
        assertEquals("Caption", review.items.single { it.target == pBench }.note)
        assertEquals(1, review.photos.size)
        assertFalse(ready.isReady(review.replace(pId(Category.IMAGE), ImportDecision.Exclude(ImportExclusionReason.PRIVACY, ImportPrivacy.TENANT_BELONGINGS))))
        assertFalse(ready.isReady(review.selectSummaryStatus("GOOD")))
        assertFalse(ready.isReady(review.replace(pId(Category.SUMMARY), ImportDecision.Exclude(ImportExclusionReason.NOT_RELEVANT))))
    }

    @Test fun `terminal sources cannot override current context or ownerless warnings`() {
        listOf(pInput(pManifest(), property = null), pInput(pManifest(), tenancy = null),
            pInput(pManifest(), date = "2026-02-30"), pInput(pManifest(), active = true),
            pInput(pManifest(), binding = null), pInput(pManifest(), binding = pBinding(type = "EXIT")),
            pInput(pManifest(), binding = pBinding(version = 1)),
            pInput(pManifest(warnings = listOf(ExtractionWarning(ExtractionWarningCode.MISSING_IMAGE, null)))))
            .forEach { input ->
                val review = ImportReview.start(input)
                val preview = ImportReviewPreview.capture(review)
                assertFalse(preview.isReady(review))
                assertFailsWith<IllegalArgumentException> { ImportMappingReceipt.create(review, preview) }
                assertTrue(preview.review.blockers.isNotEmpty())
            }
    }

    @Test fun `photo alone cannot become ready without explicit privacy even when excluded`() {
        val review = ImportReview.start(pInput(pManifest(images = listOf(ExtractedImage("word/media/a.jpg", "c".repeat(64), 100, 100)))))
        val preview = ImportReviewPreview.capture(review)
        assertFalse(preview.isReady(review))
        assertFailsWith<IllegalArgumentException> { ImportMappingReceipt.create(review, preview) }
        assertFailsWith<IllegalArgumentException> { review.decide(pId(Category.IMAGE), ImportDecision.Exclude(ImportExclusionReason.PRIVACY)) }
        val excluded = review.decide(pId(Category.IMAGE), ImportDecision.Exclude(ImportExclusionReason.PRIVACY, ImportPrivacy.TENANT_BELONGINGS))
        assertTrue(ImportReviewPreview.capture(excluded).isReady(excluded))
        assertTrue(excluded.photos.isEmpty())
        assertEquals(ImportDecisionState.EXCLUDED, excluded.sources.single().state)
    }
}

internal val pBench = ImportTarget("KITCHEN", 1, "KIT-BENCH-01", "KITCHEN")
internal val pWall = ImportTarget("KITCHEN", 1, "KIT-WALL-01", "KITCHEN")
internal val pSummary = ImportTarget("GENERAL", 1, "GEN-SUMMARY-01", "GENERAL")
internal fun pId(category: Category, index: Int = 0) = ImportSourceId(category, index)
internal fun pText(ordinal: Int, value: String) = ExtractedText(SourceLocation("word/document.xml", ordinal), value)
internal fun pItem(name: String, ordinal: Int, status: String = "GOOD") = ExtractedItem(null, pText(ordinal, name), pText(ordinal + 1, status), null)
internal fun pBinding(id: String = "routine-v2", hash: String = "b".repeat(64), type: String = "ROUTINE", version: Int = 2) =
    RoutineTemplateBinding(id, hash, Template(type, version, listOf(TemplateRoom("KITCHEN"), TemplateRoom("GENERAL")), listOf(
        TemplateItem("KIT-BENCH-01", "INTERIOR", "KITCHEN", "Bench", "台面", listOf("GOOD", "FAIR")),
        TemplateItem("KIT-WALL-01", "INTERIOR", "KITCHEN", "Wall", "墙", listOf("GOOD", "FAIR")),
        TemplateItem("GEN-SUMMARY-01", "GENERAL", "GENERAL", "Summary", "摘要", listOf("GOOD", "FAIR")),
    )))
internal fun pInput(manifest: DocxExtractionManifest, property: String? = "property-1", tenancy: String? = "tenancy-1",
    date: String = "2026-09-08", active: Boolean = false, binding: RoutineTemplateBinding? = pBinding(),
    sha: String = "a".repeat(64), suppressed: Set<String> = emptySet(),
    rooms: List<ImportRoomInstance> = listOf(ImportRoomInstance("KITCHEN", 1, "KITCHEN"), ImportRoomInstance("GENERAL", 1, "GENERAL")),
) = ImportPlanningInput(property, tenancy, date, sha, active, binding, rooms, suppressed, manifest)
internal fun pManifest(items: List<ExtractedItem> = emptyList(), fragments: List<ExtractedFragment> = emptyList(),
    identity: List<IdentityCandidate> = emptyList(), summary: List<ExtractedText> = emptyList(),
    captions: List<CaptionCandidate> = emptyList(), images: List<ExtractedImage> = emptyList(),
    placements: List<DrawingPlacement> = emptyList(), warnings: List<ExtractionWarning> = emptyList(),
) = DocxExtractionManifest(items, fragments, warnings, identity, summary, captions, images, placements)
