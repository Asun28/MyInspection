package nz.myinspection.core.report.importing.plan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nz.myinspection.core.report.importing.docx.extract.CaptionCandidate
import nz.myinspection.core.report.importing.docx.extract.DocxExtractionManifest
import nz.myinspection.core.report.importing.docx.extract.DrawingKind
import nz.myinspection.core.report.importing.docx.extract.DrawingPlacement
import nz.myinspection.core.report.importing.docx.extract.ExtractedFragment
import nz.myinspection.core.report.importing.docx.extract.ExtractedImage
import nz.myinspection.core.report.importing.docx.extract.ExtractedItem
import nz.myinspection.core.report.importing.docx.extract.ExtractedText
import nz.myinspection.core.report.importing.docx.extract.ExtractionWarning
import nz.myinspection.core.report.importing.docx.extract.ExtractionWarningCode
import nz.myinspection.core.report.importing.docx.extract.FragmentRole
import nz.myinspection.core.report.importing.docx.extract.IdentityCandidate
import nz.myinspection.core.report.importing.docx.extract.SourceLocation
import nz.myinspection.core.template.Template
import nz.myinspection.core.template.TemplateItem
import nz.myinspection.core.template.TemplateRoom

/* R4, 2026-09-08: 11 isolated source mutations each failed a named behavior assertion
 * in the 32-test plan suite (no compile-only failures); original bytes restored.
 * Faults: drop source row; duplicate caption owner; accept unsupported status;
 * invent target; ignore room label; confirm photo initially; rate suggestion;
 * drop second photo; misclassify provenance warning; alias unequal raw text;
 * duplicate image-part placement owner. No tests were pruned.
 * ImportPlanner.kt SHA-256: b1642a10e228b8b2444cbe69f25a0882ecf13863733dd89adca2536226c408f1
 * ImportPlan.kt SHA-256: af236cbeefe2e91700258c3de98b1246804a86b3712fdc21b0695dfebb04f777
 * Local recipes: _local/projection-20260908/run_mutations.py in main checkout;
 * per-fault assertion reports and hashes: .review/resume-mutations in task worktree.
 */
class ImportPlannerTest {
    @Test
    fun `unique exact normalized bilingual name room and status become a nonterminal suggestion`() {
        val item = ExtractedItem(
            room = "KITCHEN",
            name = text(10, "  Bench\t top "),
            status = text(11, "GOOD"),
            comment = text(12, "Keep original comment"),
        )
        val plan = ImportPlanner().project(input(manifest = manifest(items = listOf(item))))
        val row = plan.rows.single { ImportSourceId(ImportSourceCategory.ITEM, 0) in it.sourceIds }

        assertEquals(ImportTarget("KITCHEN", 1, "KIT-BENCH-01", "KITCHEN"), row.candidate?.target)
        assertEquals("GOOD", row.candidate?.suggestedStatus)
        assertEquals(item.status, row.candidate?.sourceStatus)
        assertEquals(listOf(item), row.items)
        assertTrue(plan.blockers.any { it.code == ImportBlockerCode.SUGGESTION_REQUIRES_REVIEW })
    }

    @Test
    fun `ambiguous repeatable target and unsupported or blank status retain evidence and block`() {
        val ambiguous = ExtractedItem(null, text(20, "Walls"), text(21, "Excellent"), text(22, "legacy wording"))
        val blank = ExtractedItem("KITCHEN", text(23, "Bench top"), text(24, "  \t"), null)
        val plan = ImportPlanner().project(input(manifest = manifest(items = listOf(ambiguous, blank))))
        val ambiguousRow = plan.rows.single { ImportSourceId(ImportSourceCategory.ITEM, 0) in it.sourceIds }
        val blankRow = plan.rows.single { ImportSourceId(ImportSourceCategory.ITEM, 1) in it.sourceIds }

        assertNull(ambiguousRow.candidate?.target)
        assertEquals("Excellent", ambiguousRow.candidate?.sourceStatus?.raw)
        assertNull(ambiguousRow.candidate?.suggestedStatus)
        assertEquals("", blankRow.candidate?.sourceStatus?.normalized)
        assertEquals(
            setOf(ImportBlockerCode.AMBIGUOUS_TARGET, ImportBlockerCode.UNSUPPORTED_STATUS, ImportBlockerCode.BLANK_STATUS),
            plan.blockers.map { it.code }.toSet().intersect(
                setOf(ImportBlockerCode.AMBIGUOUS_TARGET, ImportBlockerCode.UNSUPPORTED_STATUS, ImportBlockerCode.BLANK_STATUS),
            ),
        )
    }

    @Test
    fun `exact overlap aliases once while repeated text at another location retains its own owner and complete item evidence`() {
        val shared = text(30, "Bench top")
        val item = ExtractedItem("KITCHEN", shared, text(31, "GOOD"), text(32, "Do not lose me"))
        val sameEvidence = ExtractedFragment(FragmentRole.ITEM, shared)
        val repeatedElsewhere = ExtractedFragment(FragmentRole.ITEM, text(33, "Bench top"))
        val plan = ImportPlanner().project(input(manifest = manifest(items = listOf(item), fragments = listOf(sameEvidence, repeatedElsewhere))))

        val owners = plan.rows.filter { it.sourceIds.any { source -> source.category in setOf(ImportSourceCategory.ITEM, ImportSourceCategory.FRAGMENT) } }
        assertEquals(2, owners.size)
        assertEquals(
            setOf(ImportSourceId(ImportSourceCategory.ITEM, 0), ImportSourceId(ImportSourceCategory.FRAGMENT, 0)),
            owners.single { it.items.isNotEmpty() }.sourceIds.toSet(),
        )
        assertEquals("Do not lose me", owners.single { it.items.isNotEmpty() }.items.single().comment?.raw)
        assertEquals(listOf(ImportSourceId(ImportSourceCategory.FRAGMENT, 1)), owners.single { it.fragments.singleOrNull()?.text?.source?.ordinal == 33 }.sourceIds)
    }

    @Test fun `item name status and comment fragments share their one item owner`() {
        val item = ExtractedItem("KITCHEN", text(34, "Bench top"), text(35, "GOOD"), text(36, "Comment"))
        val plan = ImportPlanner().project(input(manifest(items = listOf(item), fragments = listOf(
            ExtractedFragment(FragmentRole.UNKNOWN, item.name), ExtractedFragment(FragmentRole.UNKNOWN, item.status!!), ExtractedFragment(FragmentRole.UNKNOWN, item.comment!!),
        ))))
        val rows = plan.rows.filter { it.sourceIds.any { id -> id.category in setOf(ImportSourceCategory.ITEM, ImportSourceCategory.FRAGMENT) } }
        assertEquals(1, rows.size)
        assertEquals(setOf(ImportSourceId(ImportSourceCategory.ITEM, 0), ImportSourceId(ImportSourceCategory.FRAGMENT, 0), ImportSourceId(ImportSourceCategory.FRAGMENT, 1), ImportSourceId(ImportSourceCategory.FRAGMENT, 2)), rows.single().sourceIds.toSet())
    }

    @Test
    fun `caption group retains complete parent and every candidate while residual text stays actionable`() {
        val parent = ExtractedFragment(FragmentRole.CAPTION, text(40, "Before text 123 - First caption 456 - Second caption"))
        val first = CaptionCandidate("1", ExtractedText(SourceLocation("word/document.xml", 40, 1), "First caption"))
        val second = CaptionCandidate("2", ExtractedText(SourceLocation("word/document.xml", 40, 2), "Second caption"))
        val plan = ImportPlanner().project(input(manifest = manifest(fragments = listOf(parent), captions = listOf(first, second))))
        val row = plan.rows.single { ImportSourceId(ImportSourceCategory.FRAGMENT, 0) in it.sourceIds }

        assertEquals(
            setOf(
                ImportSourceId(ImportSourceCategory.FRAGMENT, 0),
                ImportSourceId(ImportSourceCategory.CAPTION, 0),
                ImportSourceId(ImportSourceCategory.CAPTION, 1),
            ),
            row.sourceIds.toSet(),
        )
        assertEquals(parent, row.fragments.single())
        assertEquals(listOf(first, second), row.captions)
        assertEquals("Before text 123 - First caption 456 - Second caption", row.fragments.single().text.raw)
        assertTrue(plan.blockers.any { it.code == ImportBlockerCode.AMBIGUOUS_CAPTION && it.sourceIds == row.sourceIds })
    }

    @Test
    fun `two equal item entries keep distinct owners and an equally located fragment remains actionable`() {
        val shared = text(45, "Bench top")
        val first = ExtractedItem("KITCHEN", shared, text(46, "GOOD"), null)
        val second = ExtractedItem("KITCHEN", shared, text(47, "GOOD"), null)
        val fragment = ExtractedFragment(FragmentRole.ITEM, shared)
        val plan = ImportPlanner().project(input(manifest = manifest(items = listOf(first, second), fragments = listOf(fragment))))

        val owners = plan.rows.filter { it.sourceIds.any { source -> source.category in setOf(ImportSourceCategory.ITEM, ImportSourceCategory.FRAGMENT) } }
        assertEquals(3, owners.size)
        assertEquals(listOf(ImportSourceId(ImportSourceCategory.ITEM, 0)), owners.single { it.items.singleOrNull() == first }.sourceIds)
        assertEquals(listOf(ImportSourceId(ImportSourceCategory.ITEM, 1)), owners.single { it.items.singleOrNull() == second }.sourceIds)
        assertEquals(listOf(ImportSourceId(ImportSourceCategory.FRAGMENT, 0)), owners.single { it.fragments.singleOrNull() == fragment }.sourceIds)
        assertTrue(plan.blockers.any {
            it.code == ImportBlockerCode.UNRESOLVED_CONTENT && it.sourceIds == listOf(ImportSourceId(ImportSourceCategory.FRAGMENT, 0))
        })
    }

    @Test
    fun `all manifest categories have one owner and substantive photos remain unreviewed excluded with fixed warning dispositions`() {
        val image = ExtractedImage("word/media/a.jpg", "c".repeat(64), 100, 80)
        val plan = ImportPlanner().project(
            input(
                manifest = manifest(
                    items = listOf(ExtractedItem("KITCHEN", text(50, "Bench top"), text(51, "GOOD"), text(52, "comment"))),
                    fragments = listOf(ExtractedFragment(FragmentRole.NARRATIVE, text(53, "unresolved narrative"))),
                    identity = listOf(IdentityCandidate("address", text(54, "private identity"))),
                    summary = listOf(text(55, "summary paragraph")),
                    captions = listOf(CaptionCandidate("2", text(56, "photo caption"))),
                    images = listOf(image),
                    placements = listOf(
                        DrawingPlacement(text(57, "placement one").source, DrawingKind.INLINE, image.part),
                        DrawingPlacement(text(58, "placement two").source, DrawingKind.ANCHORED, image.part),
                    ),
                    warnings = listOf(
                        ExtractionWarning(ExtractionWarningCode.PAGINATION_EXCLUDED, text(58, "page").source),
                        ExtractionWarning(ExtractionWarningCode.MISSING_IMAGE, null),
                        ExtractionWarning(ExtractionWarningCode.IMAGE_REVIEW_REQUIRED, text(59, "image").source),
                    ),
                ),
            ),
        )

        val owners = plan.rows.flatMap { it.sourceIds }
        val expected = ImportSourceCategory.entries.flatMap { category ->
            val count = when (category) {
                ImportSourceCategory.ITEM, ImportSourceCategory.FRAGMENT, ImportSourceCategory.IDENTITY, ImportSourceCategory.SUMMARY,
                ImportSourceCategory.CAPTION, ImportSourceCategory.IMAGE -> 1
                ImportSourceCategory.PLACEMENT -> 2
                ImportSourceCategory.WARNING -> 3
            }
            (0 until count).map { ImportSourceId(category, it) }
        }
        assertEquals(expected.toSet(), owners.toSet())
        assertEquals(expected.size, owners.size)
        val photo = plan.photoReviews.single()
        assertEquals(ImportSourceId(ImportSourceCategory.IMAGE, 0), photo.imageSource)
        assertEquals(
            listOf(ImportSourceId(ImportSourceCategory.PLACEMENT, 0), ImportSourceId(ImportSourceCategory.PLACEMENT, 1)),
            photo.placementSources,
        )
        assertEquals(ImportPhotoReviewState.UNREVIEWED_EXCLUDED, photo.state)
        assertEquals(ImportPhotoAction.ACTION_REQUIRED, photo.action)
        assertEquals(
            WarningDisposition.EXTRACTOR_PROVENANCE_EXCLUDED,
            plan.rows.single { ImportSourceId(ImportSourceCategory.WARNING, 0) in it.sourceIds }.warningDisposition,
        )
        assertEquals(
            WarningDisposition.GLOBAL_BLOCKER,
            plan.rows.single { ImportSourceId(ImportSourceCategory.WARNING, 1) in it.sourceIds }.warningDisposition,
        )
        assertEquals(
            WarningDisposition.PHOTO_PRIVACY_REVIEW,
            plan.rows.single { ImportSourceId(ImportSourceCategory.WARNING, 2) in it.sourceIds }.warningDisposition,
        )
        assertTrue(plan.blockers.any { it.code == ImportBlockerCode.MISSING_IMAGE })
        assertTrue(plan.blockers.any { it.code == ImportBlockerCode.PHOTO_REVIEW_REQUIRED })
    }

    @Test
    fun `warning categories attach to their semantic owner or a named global obligation`() {
        val image = ExtractedImage("word/media/a.jpg", "d".repeat(64), 20, 20)
        val fragment = ExtractedFragment(FragmentRole.NARRATIVE, text(60, "unresolved"))
        val missingPlacement = DrawingPlacement(text(61, "missing").source, DrawingKind.ANCHORED, null)
        val plan = ImportPlanner().project(
            input(
                manifest = manifest(
                    fragments = listOf(fragment), images = listOf(image), placements = listOf(missingPlacement),
                    warnings = listOf(
                        ExtractionWarning(ExtractionWarningCode.UNRESOLVED_TEXT, fragment.text.source),
                        ExtractionWarning(ExtractionWarningCode.IMAGE_REVIEW_REQUIRED, SourceLocation(image.part, 0)),
                        ExtractionWarning(ExtractionWarningCode.MISSING_IMAGE, missingPlacement.source),
                        ExtractionWarning(ExtractionWarningCode.PAGINATION_EXCLUDED, null),
                        ExtractionWarning(ExtractionWarningCode.AMBIGUOUS_CAPTIONS, null),
                    ),
                ),
            ),
        )

        fun warning(index: Int) = plan.rows.single { ImportSourceId(ImportSourceCategory.WARNING, index) in it.sourceIds }
        assertTrue(ImportSourceId(ImportSourceCategory.FRAGMENT, 0) in warning(0).sourceIds)
        assertEquals(WarningDisposition.SOURCE_OWNER_REQUIRED, warning(0).warningDisposition)
        assertTrue(ImportSourceId(ImportSourceCategory.IMAGE, 0) in warning(1).sourceIds)
        assertEquals(WarningDisposition.PHOTO_PRIVACY_REVIEW, warning(1).warningDisposition)
        assertTrue(ImportSourceId(ImportSourceCategory.PLACEMENT, 0) in warning(2).sourceIds)
        assertEquals(WarningDisposition.SOURCE_OWNER_REQUIRED, warning(2).warningDisposition)
        assertEquals(WarningDisposition.EXTRACTOR_PROVENANCE_EXCLUDED, warning(3).warningDisposition)
        assertEquals(WarningDisposition.CAPTION_ASSOCIATION_BLOCKER, warning(4).warningDisposition)
        assertEquals(listOf(ImportSourceId(ImportSourceCategory.WARNING, 4)), warning(4).sourceIds)
    }

    @Test
    fun `room display label is case sensitive selects one repeatable instance and suppressed rooms create no targets`() {
        val kitchen = ExtractedItem("KITCHEN", text(70, "Bench top"), text(71, "GOOD"), null)
        val bedroomTwo = ExtractedItem("BEDROOM 2", text(72, "Walls"), text(73, "GOOD"), null)
        val exactPlan = ImportPlanner().project(input(manifest = manifest(items = listOf(kitchen, bedroomTwo))))
        val bedroomRow = exactPlan.rows.single { ImportSourceId(ImportSourceCategory.ITEM, 1) in it.sourceIds }
        assertEquals(ImportTarget("BEDROOM", 2, "BED-WALL-01", "BEDROOM 2"), bedroomRow.candidate?.target)
        assertEquals(listOf(
            ImportTarget("KITCHEN", 1, "KIT-BENCH-01", "KITCHEN"),
            ImportTarget("BEDROOM", 1, "BED-WALL-01", "BEDROOM 1"),
            ImportTarget("BEDROOM", 2, "BED-WALL-01", "BEDROOM 2"),
        ), exactPlan.unratedTargets)

        val suppressed = ImportPlanner().project(
            input(manifest = manifest(items = listOf(kitchen))).copy(
                roomInstances = listOf(ImportRoomInstance("KITCHEN", 1, "KITCHEN")),
                suppressedStableIds = setOf("BED-WALL-01"),
            ),
        )
        assertEquals(listOf(ImportTarget("KITCHEN", 1, "KIT-BENCH-01", "KITCHEN")), suppressed.targets)

        val wrongCase = ImportPlanner().project(input(manifest = manifest(items = listOf(kitchen, bedroomTwo.copy(room = "Bedroom 2")))))
        assertNull(wrongCase.rows.single { ImportSourceId(ImportSourceCategory.ITEM, 1) in it.sourceIds }.candidate?.target)
        assertTrue(wrongCase.blockers.any { it.code == ImportBlockerCode.UNKNOWN_TARGET })
    }

    @Test
    fun `empty template identity or malformed template hash cannot pass as the selected Routine binding`() {
        val blankId = ImportPlanner().project(input(manifest()).copy(template = RoutineTemplateBinding("", "b".repeat(64), routineTemplate())))
        val badHash = ImportPlanner().project(input(manifest()).copy(template = RoutineTemplateBinding("routine-v2", "B".repeat(64), routineTemplate())))

        assertTrue(blankId.blockers.any { it.code == ImportBlockerCode.TEMPLATE_NOT_CURRENT_ROUTINE_V2 })
        assertTrue(badHash.blockers.any { it.code == ImportBlockerCode.TEMPLATE_NOT_CURRENT_ROUTINE_V2 })
    }

    @Test
    fun `missing template binding never erases an extracted photo's initial privacy review`() {
        val image = ExtractedImage("word/media/a.jpg", "e".repeat(64), 10, 10)
        val plan = ImportPlanner().project(input(manifest(images = listOf(image))).copy(template = null))

        val photo = plan.photoReviews.single()
        assertEquals(ImportSourceId(ImportSourceCategory.IMAGE, 0), photo.imageSource)
        assertEquals(ImportPhotoReviewState.UNREVIEWED_EXCLUDED, photo.state)
        assertEquals(ImportPhotoAction.ACTION_REQUIRED, photo.action)
    }

    @Test
    fun `identity and summary aliases share one owner and standalone evidence remains visibly unresolved`() {
        val identityText = text(80, "Agent name")
        val summaryText = text(81, "Summary")
        val plan = ImportPlanner().project(
            input(
                manifest(
                    fragments = listOf(
                        ExtractedFragment(FragmentRole.IDENTITY, identityText),
                        ExtractedFragment(FragmentRole.NARRATIVE, summaryText),
                    ),
                    identity = listOf(IdentityCandidate("agent", identityText), IdentityCandidate("other", text(82, "standalone identity"))),
                    summary = listOf(summaryText, text(83, "standalone summary")),
                ),
            ),
        )

        assertEquals(
            setOf(ImportSourceId(ImportSourceCategory.FRAGMENT, 0), ImportSourceId(ImportSourceCategory.IDENTITY, 0)),
            plan.rows.single { ImportSourceId(ImportSourceCategory.IDENTITY, 0) in it.sourceIds }.sourceIds.toSet(),
        )
        assertEquals(
            setOf(ImportSourceId(ImportSourceCategory.FRAGMENT, 1), ImportSourceId(ImportSourceCategory.SUMMARY, 0)),
            plan.rows.single { ImportSourceId(ImportSourceCategory.SUMMARY, 0) in it.sourceIds }.sourceIds.toSet(),
        )
        assertTrue(plan.blockers.any { it.code == ImportBlockerCode.UNRESOLVED_CONTENT && it.sourceIds == listOf(ImportSourceId(ImportSourceCategory.IDENTITY, 1)) })
        assertTrue(plan.blockers.any { it.code == ImportBlockerCode.UNRESOLVED_CONTENT && it.sourceIds == listOf(ImportSourceId(ImportSourceCategory.SUMMARY, 1)) })
    }

    @Test
    fun `unique room target chooses that target's allowed status when names repeat across rooms`() {
        val template = Template(
            "ROUTINE", 2, listOf(TemplateRoom("KITCHEN"), TemplateRoom("BEDROOM")),
            listOf(
                TemplateItem("KIT-WALL-01", "INTERIOR", "KITCHEN", "Walls", "厨房墙面", listOf("GOOD")),
                TemplateItem("BED-WALL-01", "INTERIOR", "BEDROOM", "Walls", "卧室墙面", listOf("FAIR")),
            ),
        )
        val source = ExtractedItem("KITCHEN", text(90, "Walls"), text(91, "GOOD"), null)
        val plan = ImportPlanner().project(
            input(manifest(items = listOf(source))).copy(
                template = RoutineTemplateBinding("routine-v2", "b".repeat(64), template),
                roomInstances = listOf(ImportRoomInstance("KITCHEN", 1, "KITCHEN"), ImportRoomInstance("BEDROOM", 1, "BEDROOM")),
            ),
        )

        val row = plan.rows.single { ImportSourceId(ImportSourceCategory.ITEM, 0) in it.sourceIds }
        assertEquals(ImportTarget("KITCHEN", 1, "KIT-WALL-01", "KITCHEN"), row.candidate?.target)
        assertEquals("GOOD", row.candidate?.suggestedStatus)
    }

    @Test
    fun `multiple warnings retain individual dispositions on their one semantic owner`() {
        val item = ExtractedItem("KITCHEN", text(100, "Bench top"), text(101, "GOOD"), null)
        val summary = text(102, "narrative summary")
        val plan = ImportPlanner().project(
            input(
                manifest(
                    items = listOf(item), summary = listOf(summary),
                    warnings = listOf(
                        ExtractionWarning(ExtractionWarningCode.UNRESOLVED_TEXT, item.name.source),
                        ExtractionWarning(ExtractionWarningCode.AMBIGUOUS_COLUMNS, item.name.source),
                        ExtractionWarning(ExtractionWarningCode.UNRESOLVED_NARRATIVE, summary.source),
                    ),
                ),
            ),
        )

        val itemOwner = plan.rows.single { ImportSourceId(ImportSourceCategory.ITEM, 0) in it.sourceIds }
        assertEquals(
            setOf(ImportSourceId(ImportSourceCategory.ITEM, 0), ImportSourceId(ImportSourceCategory.WARNING, 0), ImportSourceId(ImportSourceCategory.WARNING, 1)),
            itemOwner.sourceIds.toSet(),
        )
        assertEquals(
            listOf(WarningDisposition.SOURCE_OWNER_REQUIRED, WarningDisposition.SOURCE_OWNER_REQUIRED),
            itemOwner.warningReviews.map { it.disposition },
        )
        val summaryOwner = plan.rows.single { ImportSourceId(ImportSourceCategory.SUMMARY, 0) in it.sourceIds }
        assertEquals(listOf(WarningDisposition.SOURCE_OWNER_REQUIRED), summaryOwner.warningReviews.map { it.disposition })
    }

    @Test fun `ambiguous caption parents never share a caption source ID`() {
        val parents = listOf(
            ExtractedFragment(FragmentRole.CAPTION, text(110, "Prefix 1 - Caption")),
            ExtractedFragment(FragmentRole.CAPTION, ExtractedText(SourceLocation("word/document.xml", 110, 2), "Other parent")),
        )
        val caption = CaptionCandidate("1", text(110, "Caption"))
        val plan = ImportPlanner().project(input(manifest(fragments = parents, captions = listOf(caption))))
        assertEquals(listOf(
            listOf(ImportSourceId(ImportSourceCategory.FRAGMENT, 0)),
            listOf(ImportSourceId(ImportSourceCategory.FRAGMENT, 1)),
            listOf(ImportSourceId(ImportSourceCategory.CAPTION, 0)),
        ), plan.rows.map { it.sourceIds })
        assertEquals(parents, plan.rows.flatMap { it.fragments })
        assertEquals(listOf(caption), plan.rows.flatMap { it.captions })
        assertTrue(plan.blockers.any { it.code == ImportBlockerCode.AMBIGUOUS_CAPTION })
    }

    @Test fun `Chinese exact names normalize whitespace but wrong case and legacy values never convert`() {
        val sources = listOf(
            ExtractedItem(" KITCHEN ", text(120, " 台面 "), text(121, " GOOD\t"), null),
            ExtractedItem("KITCHEN", text(122, "bench top"), text(123, "GOOD"), null),
            ExtractedItem("KITCHEN", text(124, "Bench top"), text(125, "good"), null),
            ExtractedItem("KITCHEN", text(126, "Bench top"), text(127, "Clean"), null),
            ExtractedItem("KITCHEN", text(128, "Bench top"), null, null),
        )
        val plan = ImportPlanner().project(input(manifest(items = sources)))
        assertEquals(ImportTarget("KITCHEN", 1, "KIT-BENCH-01", "KITCHEN"), plan.rows[0].candidate?.target)
        assertEquals("GOOD", plan.rows[0].candidate?.suggestedStatus)
        assertNull(plan.rows[1].candidate?.target)
        for (i in 2..4) assertNull(plan.rows[i].candidate?.suggestedStatus)
        assertEquals(sources, plan.rows.flatMap { it.items })
        assertEquals(listOf(" GOOD\t", "GOOD", "good", "Clean", null), plan.rows.map { it.candidate?.sourceStatus?.raw })
    }

    @Test fun `two photos keep separate placements and all fixed provenance exclusions are visible`() {
        val images = listOf(ExtractedImage("word/media/a.jpg", "c".repeat(64), 100, 80), ExtractedImage("word/media/b.jpg", "d".repeat(64), 90, 70))
        val placements = listOf(
            DrawingPlacement(text(130, "").source, DrawingKind.INLINE, images[0].part),
            DrawingPlacement(text(131, "").source, DrawingKind.ANCHORED, images[1].part),
            DrawingPlacement(text(132, "").source, DrawingKind.INLINE, images[0].part),
            DrawingPlacement(text(133, "").source, DrawingKind.INLINE, "word/media/missing.jpg"),
        )
        val warnings = listOf(ExtractionWarningCode.PAGINATION_EXCLUDED, ExtractionWarningCode.METADATA_EXCLUDED,
            ExtractionWarningCode.URL_EXCLUDED, ExtractionWarningCode.LAYOUT_IMAGE_EXCLUDED).map { ExtractionWarning(it, null) }
        val plan = ImportPlanner().project(input(manifest(images = images, placements = placements, warnings = warnings)))
        assertEquals(images, plan.rows.flatMap { it.images })
        assertEquals(listOf(0, 1), plan.photoReviews.map { it.imageSource.index })
        assertEquals(listOf(listOf(0, 2), listOf(1)), plan.photoReviews.map { photo -> photo.placementSources.map { it.index } })
        assertEquals(List(2) { ImportPhotoReviewState.UNREVIEWED_EXCLUDED }, plan.photoReviews.map { it.state })
        assertEquals(List(2) { ImportPhotoAction.ACTION_REQUIRED }, plan.photoReviews.map { it.action })
        assertEquals(warnings, plan.rows.flatMap { it.warnings })
        assertEquals(List(4) { WarningDisposition.EXTRACTOR_PROVENANCE_EXCLUDED }, plan.rows.flatMap { it.warningReviews }.map { it.disposition })
        assertEquals(placements[3], plan.rows.single { ImportSourceId(ImportSourceCategory.PLACEMENT, 3) in it.sourceIds }.placements.single())
        assertTrue(plan.blockers.any { it.code == ImportBlockerCode.MISSING_IMAGE && it.sourceIds == listOf(ImportSourceId(ImportSourceCategory.PLACEMENT, 3)) })
    }

    @Test fun `raw or occurrence differences prevent item fragment aliasing`() {
        val name = text(140, "Bench top")
        val fragments = listOf(
            ExtractedFragment(FragmentRole.ITEM, text(140, " Bench top ")),
            ExtractedFragment(FragmentRole.ITEM, ExtractedText(SourceLocation("word/document.xml", 140, 1), "Bench top")),
        )
        val plan = ImportPlanner().project(input(manifest(items = listOf(ExtractedItem("KITCHEN", name, text(141, "GOOD"), null)), fragments = fragments)))
        assertEquals(3, plan.rows.size)
        assertEquals(fragments, plan.rows.flatMap { it.fragments })
        assertEquals(listOf(1, 1, 1), plan.rows.map { it.sourceIds.size })
    }

    @Test fun `duplicate image parts leave placements singly owned and visibly unresolved`() {
        val images = listOf(ExtractedImage("word/media/a.jpg", "c".repeat(64), 100, 80), ExtractedImage("word/media/a.jpg", "d".repeat(64), 90, 70))
        val placement = DrawingPlacement(text(150, "").source, DrawingKind.INLINE, images[0].part)
        val plan = ImportPlanner().project(input(manifest(images = images, placements = listOf(placement))))
        assertEquals(listOf(
            listOf(ImportSourceId(ImportSourceCategory.IMAGE, 0)),
            listOf(ImportSourceId(ImportSourceCategory.IMAGE, 1)),
            listOf(ImportSourceId(ImportSourceCategory.PLACEMENT, 0)),
        ), plan.rows.map { it.sourceIds })
        assertEquals(images, plan.rows.flatMap { it.images })
        assertEquals(listOf(placement), plan.rows.flatMap { it.placements })
        assertEquals(listOf(emptyList(), emptyList()), plan.photoReviews.map { it.placementSources })
        assertTrue(plan.blockers.any { it.code == ImportBlockerCode.UNRESOLVED_CONTENT && it.sourceIds == listOf(ImportSourceId(ImportSourceCategory.PLACEMENT, 0)) })
    }

    private fun ImportPlanningInput.copy(
        template: RoutineTemplateBinding? = this.template,
        roomInstances: List<ImportRoomInstance> = this.roomInstances,
        suppressedStableIds: Set<String> = this.suppressedStableIds,
    ) = ImportPlanningInput(propertyId, tenancyId, reportDate, sourceSha256, hasActiveDraft, template, roomInstances, suppressedStableIds, manifest)

    private fun input(manifest: DocxExtractionManifest) = ImportPlanningInput(
        propertyId = "property-1", tenancyId = "tenancy-1", reportDate = "2026-09-07", sourceSha256 = "a".repeat(64),
        hasActiveDraft = false,
        template = RoutineTemplateBinding("template-routine-v2", "b".repeat(64), routineTemplate()),
        roomInstances = listOf(
            ImportRoomInstance("KITCHEN", 1, "KITCHEN"),
            ImportRoomInstance("BEDROOM", 1, "BEDROOM 1"),
            ImportRoomInstance("BEDROOM", 2, "BEDROOM 2"),
        ),
        suppressedStableIds = emptySet(),
        manifest = manifest,
    )

    private fun manifest(
        items: List<ExtractedItem> = emptyList(), fragments: List<ExtractedFragment> = emptyList(),
        identity: List<IdentityCandidate> = emptyList(), summary: List<ExtractedText> = emptyList(),
        captions: List<CaptionCandidate> = emptyList(), images: List<ExtractedImage> = emptyList(),
        placements: List<DrawingPlacement> = emptyList(), warnings: List<ExtractionWarning> = emptyList(),
    ) = DocxExtractionManifest(items, fragments, warnings, identity, summary, captions, images, placements)

    private fun text(ordinal: Int, raw: String) = ExtractedText(SourceLocation("word/document.xml", ordinal), raw)

    private fun routineTemplate() = Template(
        "ROUTINE", 2,
        listOf(TemplateRoom("KITCHEN"), TemplateRoom("BEDROOM", repeatable = true)),
        listOf(
            TemplateItem("KIT-BENCH-01", "INTERIOR", "KITCHEN", "Bench top", "台面", listOf("GOOD", "FAIR")),
            TemplateItem("BED-WALL-01", "INTERIOR", "BEDROOM", "Walls", "墙面", listOf("GOOD", "FAIR")),
        ),
    )
}
