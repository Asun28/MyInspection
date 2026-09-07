package nz.myinspection.core.report.importing.plan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nz.myinspection.core.report.importing.docx.extract.DocxExtractionManifest
import nz.myinspection.core.report.importing.docx.extract.CaptionCandidate
import nz.myinspection.core.report.importing.docx.extract.DrawingKind
import nz.myinspection.core.report.importing.docx.extract.DrawingPlacement
import nz.myinspection.core.report.importing.docx.extract.ExtractedFragment
import nz.myinspection.core.report.importing.docx.extract.ExtractedImage
import nz.myinspection.core.report.importing.docx.extract.ExtractedItem
import nz.myinspection.core.report.importing.docx.extract.ExtractionWarning
import nz.myinspection.core.report.importing.docx.extract.ExtractionWarningCode
import nz.myinspection.core.report.importing.docx.extract.FragmentRole
import nz.myinspection.core.report.importing.docx.extract.IdentityCandidate
import nz.myinspection.core.report.importing.docx.extract.ExtractedText
import nz.myinspection.core.report.importing.docx.extract.SourceLocation
import nz.myinspection.core.template.Template
import nz.myinspection.core.template.TemplateItem
import nz.myinspection.core.template.TemplateRoom

class ImportPlanningSnapshotTest {
    @Test
    fun `snapshot binds every selected context field and keeps all configured targets unrated`() {
        val manifest = manifest("evidence")
        val snapshot = ImportPlanningSnapshot.create(input(manifest = manifest, hasActiveDraft = true))

        assertEquals("property-1", snapshot.context.propertyId)
        assertEquals("tenancy-1", snapshot.context.tenancyId)
        assertEquals("2024-02-29", snapshot.context.reportDate)
        assertEquals("a".repeat(64), snapshot.context.sourceSha256)
        assertEquals(manifest.normalizedDigest, snapshot.context.manifestDigest)
        assertEquals("routine-v2", snapshot.context.template?.id)
        assertEquals("b".repeat(64), snapshot.context.template?.contentHash)
        assertTrue(snapshot.context.hasActiveDraft)
        assertEquals(rooms(), snapshot.context.roomInstances)
        val suppressed = ImportPlanningSnapshot.create(input(suppressedStableIds = setOf("KIT-BENCH-01")))
        assertEquals(rooms(), suppressed.context.roomInstances)
        assertEquals(setOf("KIT-BENCH-01"), suppressed.context.suppressedStableIds)
        assertEquals(targets(), snapshot.targets)
        assertEquals(targets(), snapshot.unratedTargets)
        assertTrue(snapshot.blockers.any { it.code == ImportBlockerCode.ACTIVE_DRAFT })
    }

    @Test
    fun `input construction freezes caller collections and template internals before snapshotting`() {
        val rooms = rooms().toMutableList()
        val suppressed = mutableSetOf<String>()
        val items = templateItems().map { it.copy(allowedStatuses = mutableListOf("GOOD", "FAIR")) }.toMutableList()
        val statuses = items.first().allowedStatuses as MutableList<String>
        val templateRooms = mutableListOf(TemplateRoom("KITCHEN"), TemplateRoom("BEDROOM", true))
        val input = input(roomInstances = rooms, suppressedStableIds = suppressed, template = binding(items = items, rooms = templateRooms))
        rooms.clear(); suppressed += "KIT-BENCH-01"; items.clear(); statuses.clear(); templateRooms.clear()
        val snapshot = ImportPlanningSnapshot.create(input)

        assertEquals(rooms(), input.roomInstances)
        assertEquals(emptySet(), input.suppressedStableIds)
        assertEquals(listOf("GOOD", "FAIR"), input.template?.template?.items?.first()?.allowedStatuses)
        assertEquals(targets(), snapshot.targets)
        val selectedTemplate = requireNotNull(input.template).template
        assertEquals(listOf(TemplateRoom("KITCHEN"), TemplateRoom("BEDROOM", true)), selectedTemplate.rooms)
        val exposed = listOf(input.roomInstances, selectedTemplate.rooms, selectedTemplate.items,
            selectedTemplate.items.first().allowedStatuses, snapshot.context.roomInstances,
            snapshot.targets, snapshot.unratedTargets, snapshot.blockers)
        exposed.forEach(::assertImmutableList)
        for (values in listOf(input.suppressedStableIds, snapshot.context.suppressedStableIds)) {
            assertFalse(runCatching { (values as MutableSet<String>).add("UNSELECTED") }.isSuccess)
        }
    }

    @Test
    fun `missing context invalid source and invalid calendar date are isolated named blockers`() {
        val missing = ImportPlanningSnapshot.create(input(propertyId = null, tenancyId = null, reportDate = null, sourceSha256 = "bad", template = null))
        assertEquals(setOf(ImportBlockerCode.MISSING_PROPERTY, ImportBlockerCode.MISSING_TENANCY, ImportBlockerCode.MISSING_REPORT_DATE, ImportBlockerCode.INVALID_SOURCE_SHA256, ImportBlockerCode.MISSING_TEMPLATE), missing.blockers.map { it.code }.toSet())
        val isolatedMissing = listOf(
            input(propertyId = null) to ImportBlockerCode.MISSING_PROPERTY,
            input(propertyId = "") to ImportBlockerCode.MISSING_PROPERTY,
            input(propertyId = "  ") to ImportBlockerCode.MISSING_PROPERTY,
            input(tenancyId = null) to ImportBlockerCode.MISSING_TENANCY,
            input(tenancyId = "") to ImportBlockerCode.MISSING_TENANCY,
            input(tenancyId = "  ") to ImportBlockerCode.MISSING_TENANCY,
            input(reportDate = null) to ImportBlockerCode.MISSING_REPORT_DATE,
            input(sourceSha256 = null) to ImportBlockerCode.INVALID_SOURCE_SHA256,
            input(template = null) to ImportBlockerCode.MISSING_TEMPLATE,
        )
        for ((request, code) in isolatedMissing) {
            assertEquals(setOf(code), ImportPlanningSnapshot.create(request).blockers.map { it.code }.toSet())
        }
        for (date in listOf("banana", "2023-02-29", "2024-02-30", "2024-2-29", "2024-02-29x", "+10000-01-01")) {
            val snapshot = ImportPlanningSnapshot.create(input(reportDate = date))
            assertEquals(setOf(ImportBlockerCode.INVALID_REPORT_DATE), snapshot.blockers.map { it.code }.toSet(), date)
        }
        for (date in listOf("2023-02-28", "2024-02-29")) assertFalse(ImportPlanningSnapshot.create(input(reportDate = date)).blockers.any { it.code == ImportBlockerCode.INVALID_REPORT_DATE })
    }

    @Test
    fun `only complete current Routine v2 template bindings and valid configured identities create targets`() {
        val invalidTemplates = listOf(
            binding(type = "EXIT"), binding(version = 1), binding(version = 3), binding(id = ""),
            binding(hash = "B".repeat(64)), binding(hash = "g".repeat(64)), binding(hash = "b".repeat(63)),
            binding(items = templateItems() + templateItems().first()),
            binding(items = templateItems().mapIndexed { index, item -> if (index == 0) item.copy(allowedStatuses = listOf("INVALID")) else item }),
        )
        for (binding in invalidTemplates) assertTrue(ImportPlanningSnapshot.create(input(template = binding)).blockers.any { it.code == ImportBlockerCode.TEMPLATE_NOT_CURRENT_ROUTINE_V2 })
        val malformedRooms = listOf(
            rooms().drop(1), listOf(rooms().first()), rooms() + rooms()[1],
            listOf(rooms()[0], rooms()[1], ImportRoomInstance("BEDROOM", 3, "BEDROOM 3")),
            listOf(ImportRoomInstance("KITCHEN", 1, "KITCHEN 1"), ImportRoomInstance("KITCHEN", 2, "KITCHEN 2")) + rooms().drop(1),
            listOf(ImportRoomInstance("KITCHEN", 1, "WRONG")) + rooms().drop(1),
            rooms() + ImportRoomInstance("UNKNOWN", 1, "UNKNOWN"),
        )
        for (roomInstances in malformedRooms) assertTrue(ImportPlanningSnapshot.create(input(roomInstances = roomInstances)).blockers.any { it.code == ImportBlockerCode.INVALID_ROOM_INVENTORY })
        assertTrue(ImportPlanningSnapshot.create(input(suppressedStableIds = setOf("UNKNOWN"))).blockers.any { it.code == ImportBlockerCode.UNKNOWN_SUPPRESSED_STABLE_ID })
        assertEquals(targets().filter { it.stableId == "KIT-BENCH-01" }, ImportPlanningSnapshot.create(input(suppressedStableIds = setOf("BED-WALL-01"), roomInstances = listOf(rooms().first()))).targets)
    }

    @Test
    fun `room count boundary and partial suppression preserve exactly the remaining native identities`() {
        fun configured(count: Int) = listOf(rooms().first()) +
            (1..count).map { ImportRoomInstance("BEDROOM", it.toLong(), "BEDROOM $it") }
        val maximum = ImportPlanningSnapshot.create(input(roomInstances = configured(99)))
        assertEquals(emptyList(), maximum.blockers)
        assertEquals(100, maximum.targets.size)
        assertEquals(ImportTarget("BEDROOM", 99, "BED-WALL-01", "BEDROOM 99"), maximum.targets.last())
        assertEquals(setOf(ImportBlockerCode.INVALID_ROOM_INVENTORY),
            ImportPlanningSnapshot.create(input(roomInstances = configured(100))).blockers.map { it.code }.toSet())

        val sink = TemplateItem("KIT-SINK-01", "INTERIOR", "KITCHEN", "Sink", "水槽", listOf("GOOD"))
        val partial = ImportPlanningSnapshot.create(input(
            template = binding(items = templateItems() + sink), suppressedStableIds = setOf("KIT-BENCH-01"),
        ))
        assertEquals(emptyList(), partial.blockers)
        assertEquals(listOf(
            ImportTarget("KITCHEN", 1, "KIT-SINK-01", "KITCHEN"),
            ImportTarget("BEDROOM", 1, "BED-WALL-01", "BEDROOM 1"),
            ImportTarget("BEDROOM", 2, "BED-WALL-01", "BEDROOM 2"),
        ), partial.targets)
        assertEquals(partial.targets, partial.unratedTargets)

        val bedroomItems = listOf(
            TemplateItem("BED-Z-01", "INTERIOR", "BEDROOM", "Zebra wall", "斑马墙", listOf("GOOD")),
            TemplateItem("BED-A-01", "INTERIOR", "BEDROOM", "Alpha wall", "字母墙", listOf("GOOD")),
        )
        val reversed = ImportPlanningSnapshot.create(input(
            template = binding(items = bedroomItems),
            roomInstances = rooms().drop(1).reversed(),
        ))
        val expectedReversed = listOf(
            ImportTarget("BEDROOM", 2, "BED-Z-01", "BEDROOM 2"),
            ImportTarget("BEDROOM", 2, "BED-A-01", "BEDROOM 2"),
            ImportTarget("BEDROOM", 1, "BED-Z-01", "BEDROOM 1"),
            ImportTarget("BEDROOM", 1, "BED-A-01", "BEDROOM 1"),
        )
        assertEquals(emptyList(), reversed.blockers)
        assertEquals(expectedReversed, reversed.targets)
        assertEquals(expectedReversed, reversed.unratedTargets)
    }

    @Test
    fun `shared row blocker and photo collections detach callers and reject mutation`() {
        val location = SourceLocation("word/document.xml", 1)
        val text = ExtractedText(location, "Retain this evidence")
        val ids = mutableListOf(ImportSourceId(ImportSourceCategory.ITEM, 0))
        val items = mutableListOf(ExtractedItem("KITCHEN", text, null, null))
        val fragments = mutableListOf(ExtractedFragment(FragmentRole.UNKNOWN, text))
        val identities = mutableListOf(IdentityCandidate("TITLE", text))
        val summaries = mutableListOf(text)
        val captions = mutableListOf(CaptionCandidate("123", text))
        val images = mutableListOf(ExtractedImage("word/media/a.png", "c".repeat(64), 640, 480))
        val placements = mutableListOf(DrawingPlacement(location, DrawingKind.INLINE, "word/media/a.png"))
        val warnings = mutableListOf(ExtractionWarning(ExtractionWarningCode.UNRESOLVED_TEXT, location))
        val reviews = mutableListOf(ImportWarningReview(warnings.single(), WarningDisposition.SOURCE_OWNER_REQUIRED))
        val photoIds = mutableListOf(ImportSourceId(ImportSourceCategory.PLACEMENT, 0))
        val row = ImportReviewRow(ids, items, fragments, identities, summaries, captions, images, placements, warnings, reviews)
        val blocker = ImportPlanningBlocker(ImportBlockerCode.UNRESOLVED_CONTENT, ids)
        val photo = ImportPhotoReview(ImportSourceId(ImportSourceCategory.IMAGE, 0), photoIds,
            ImportPhotoReviewState.UNREVIEWED_EXCLUDED, ImportPhotoAction.ACTION_REQUIRED)
        listOf(ids, items, fragments, identities, summaries, captions, images, placements, warnings, reviews, photoIds)
            .forEach { it.clear() }
        val exposed = listOf(row.sourceIds, row.items, row.fragments, row.identity, row.summaries, row.captions,
            row.images, row.placements, row.warnings, row.warningReviews, blocker.sourceIds, photo.placementSources)
        assertEquals(List(12) { 1 }, exposed.map { it.size })
        exposed.forEach(::assertImmutableList)
        assertEquals("Retain this evidence", row.items.single().name.raw)
    }

    @Suppress("UNCHECKED_CAST")
    private fun assertImmutableList(values: List<*>) {
        assertFalse(runCatching { (values as MutableList<Any?>).add(Any()) }.isSuccess)
        if (values.isNotEmpty()) {
            assertFalse(runCatching { (values as MutableList<Any?>)[0] = Any() }.isSuccess)
        }
    }

    private fun input(
        propertyId: String? = "property-1", tenancyId: String? = "tenancy-1", reportDate: String? = "2024-02-29", sourceSha256: String? = "a".repeat(64),
        hasActiveDraft: Boolean = false, template: RoutineTemplateBinding? = binding(), roomInstances: List<ImportRoomInstance> = rooms(), suppressedStableIds: Set<String> = emptySet(), manifest: DocxExtractionManifest = manifest(),
    ) = ImportPlanningInput(propertyId, tenancyId, reportDate, sourceSha256, hasActiveDraft, template, roomInstances, suppressedStableIds, manifest)
    private fun binding(id: String = "routine-v2", hash: String = "b".repeat(64), type: String = "ROUTINE", version: Int = 2, items: List<TemplateItem> = templateItems(), rooms: List<TemplateRoom> = templateRooms()) = RoutineTemplateBinding(id, hash, Template(type, version, rooms, items))
    private fun templateItems() = listOf(TemplateItem("KIT-BENCH-01", "INTERIOR", "KITCHEN", "Bench top", "台面", listOf("GOOD", "FAIR")), TemplateItem("BED-WALL-01", "INTERIOR", "BEDROOM", "Walls", "墙面", listOf("GOOD", "FAIR")))
    private fun templateRooms() = listOf(TemplateRoom("KITCHEN"), TemplateRoom("BEDROOM", true))
    private fun rooms() = listOf(ImportRoomInstance("KITCHEN", 1, "KITCHEN"), ImportRoomInstance("BEDROOM", 1, "BEDROOM 1"), ImportRoomInstance("BEDROOM", 2, "BEDROOM 2"))
    private fun targets() = listOf(ImportTarget("KITCHEN", 1, "KIT-BENCH-01", "KITCHEN"), ImportTarget("BEDROOM", 1, "BED-WALL-01", "BEDROOM 1"), ImportTarget("BEDROOM", 2, "BED-WALL-01", "BEDROOM 2"))
    private fun manifest(raw: String = "") = DocxExtractionManifest(emptyList(), emptyList(), emptyList(), emptyList(), listOf(ExtractedText(SourceLocation("word/document.xml", 1), raw)), emptyList(), emptyList(), emptyList())
}
