package nz.myinspection.core.report.importing.plan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nz.myinspection.core.report.importing.docx.extract.DocxExtractionManifest
import nz.myinspection.core.template.Template
import nz.myinspection.core.template.TemplateItem
import nz.myinspection.core.template.TemplateRoom

class ImportPlanTest {
    private val sha = "a".repeat(64)
    private val kitchen = TemplateItem(
        stableId = "KIT-BENCH-01", area = "INTERIOR", room = "KITCHEN",
        textEn = "Bench top", textZh = "台面", allowedStatuses = listOf("GOOD", "FAIR"),
    )
    private val bedroom = TemplateItem(
        stableId = "BED-WALL-01", area = "INTERIOR", room = "BEDROOM",
        textEn = "Walls", textZh = "墙面", allowedStatuses = listOf("GOOD", "FAIR"),
    )

    @Test
    fun `missing selected context source binding and template binding remain named blockers`() {
        val plan = ImportPlanner().project(
            input(propertyId = null, tenancyId = null, reportDate = null, sourceSha256 = "not-a-digest", template = null),
        )

        assertEquals(
            setOf(
                ImportBlockerCode.MISSING_PROPERTY,
                ImportBlockerCode.MISSING_TENANCY,
                ImportBlockerCode.MISSING_REPORT_DATE,
                ImportBlockerCode.INVALID_SOURCE_SHA256,
                ImportBlockerCode.MISSING_TEMPLATE,
            ),
            plan.blockers.map { it.code }.toSet(),
        )
        assertEquals("not-a-digest", plan.context.sourceSha256)
        assertEquals(emptyList(), plan.targets)
    }

    @Test
    fun `active draft blocks the valid Routine v2 snapshot rather than becoming database authority`() {
        val plan = ImportPlanner().project(input(hasActiveDraft = true))

        assertEquals(setOf(ImportBlockerCode.ACTIVE_DRAFT), plan.blockers.map { it.code }.toSet())
        assertEquals("template-routine-v2", plan.context.template?.id)
        assertEquals("b".repeat(64), plan.context.template?.contentHash)
    }

    @Test
    fun `only Routine version 2 is a current template binding`() {
        val cases = listOf(
            "wrong type" to template(type = "EXIT", version = 2),
            "old version" to template(type = "ROUTINE", version = 1),
            "future version" to template(type = "ROUTINE", version = 3),
        )
        for ((label, candidate) in cases) {
            val plan = ImportPlanner().project(input(template = binding(template = candidate)))
            assertEquals(setOf(ImportBlockerCode.TEMPLATE_NOT_CURRENT_ROUTINE_V2), plan.blockers.map { it.code }.toSet(), label)
        }

        val current = ImportPlanner().project(input(template = binding(template = template(type = "ROUTINE", version = 2))))
        assertFalse(current.blockers.any { it.code == ImportBlockerCode.TEMPLATE_NOT_CURRENT_ROUTINE_V2 })
    }

    @Test
    fun `each malformed configured room inventory is rejected while the valid inventory is accepted`() {
        val valid = listOf(
            ImportRoomInstance("KITCHEN", 1, "KITCHEN"),
            ImportRoomInstance("BEDROOM", 1, "BEDROOM 1"),
            ImportRoomInstance("BEDROOM", 2, "BEDROOM 2"),
        )
        val malformed = listOf(
            "duplicate identity" to listOf(valid[0], valid[1], valid[1]),
            "missing configured room" to listOf(valid[0], valid[1]),
            "noncontiguous instance number" to listOf(valid[0], valid[1], ImportRoomInstance("BEDROOM", 3, "BEDROOM 3")),
            "second singleton instance" to valid + ImportRoomInstance("KITCHEN", 2, "KITCHEN 2"),
        )
        for ((label, rooms) in malformed) {
            val plan = ImportPlanner().project(input(roomInstances = rooms))
            assertEquals(setOf(ImportBlockerCode.INVALID_ROOM_INVENTORY), plan.blockers.map { it.code }.toSet(), label)
        }

        val accepted = ImportPlanner().project(input(roomInstances = valid))
        assertFalse(accepted.blockers.any { it.code == ImportBlockerCode.INVALID_ROOM_INVENTORY })
    }

    @Test
    fun `unknown suppressed stable ID is rejected independently of an otherwise valid room inventory`() {
        val plan = ImportPlanner().project(input(suppressedStableIds = setOf("UNKNOWN-STABLE-ID")))

        assertEquals(setOf(ImportBlockerCode.UNKNOWN_SUPPRESSED_STABLE_ID), plan.blockers.map { it.code }.toSet())
    }

    @Test
    fun `plan snapshots configured identity and template binding before callers mutate their collections`() {
        val rooms = mutableListOf(
            ImportRoomInstance("KITCHEN", 1, "KITCHEN"),
            ImportRoomInstance("BEDROOM", 1, "BEDROOM 1"),
            ImportRoomInstance("BEDROOM", 2, "BEDROOM 2"),
        )
        val items = mutableListOf(kitchen.copy(allowedStatuses = mutableListOf("GOOD", "FAIR")), bedroom)
        val statuses = items.first().allowedStatuses as MutableList<String>
        val callerTemplate = Template("ROUTINE", 2, listOf(TemplateRoom("KITCHEN"), TemplateRoom("BEDROOM", true)), items)
        val suppressed = mutableSetOf<String>()
        val frozenInput = input(roomInstances = rooms, template = binding(callerTemplate), suppressedStableIds = suppressed)
        rooms.clear()
        items.clear()
        statuses.clear()
        suppressed += "KIT-BENCH-01"
        val plan = ImportPlanner().project(frozenInput)

        assertEquals(
            listOf(
                ImportTarget("KITCHEN", 1, "KIT-BENCH-01", "KITCHEN"),
                ImportTarget("BEDROOM", 1, "BED-WALL-01", "BEDROOM 1"),
                ImportTarget("BEDROOM", 2, "BED-WALL-01", "BEDROOM 2"),
            ),
            plan.targets,
        )
        @Suppress("UNCHECKED_CAST")
        assertFalse((plan.targets as MutableList<ImportTarget>).runCatching { clear() }.isSuccess)
        assertEquals("template-routine-v2", plan.context.template?.id)
        assertEquals("b".repeat(64), plan.context.template?.contentHash)
        assertEquals(
            listOf(
                ImportRoomInstance("KITCHEN", 1, "KITCHEN"),
                ImportRoomInstance("BEDROOM", 1, "BEDROOM 1"),
                ImportRoomInstance("BEDROOM", 2, "BEDROOM 2"),
            ),
            plan.context.roomInstances,
        )
        assertEquals(emptySet(), plan.context.suppressedStableIds)
        assertEquals(listOf("GOOD", "FAIR"), plan.context.template?.template?.items?.first()?.allowedStatuses)
        assertFalse(plan.context.hasActiveDraft)
        assertEquals("property-1", plan.context.propertyId)
        assertEquals("tenancy-1", plan.context.tenancyId)
        assertEquals("2026-09-07", plan.context.reportDate)
        assertEquals(sha, plan.context.sourceSha256)
        assertEquals(frozenInput.manifest.normalizedDigest, plan.context.manifestDigest)
    }

    @Test fun `invalid calendar dates reach the integrated projection as named blockers`() {
        for (date in listOf("banana", "2026-02-29", "2026-09-31", "")) {
            val plan = ImportPlanner().project(input(reportDate = date))
            assertTrue(plan.blockers.any { it.code == ImportBlockerCode.INVALID_REPORT_DATE }, date)
        }
    }

    // R3 repair: bypassing each of the five ImportPlan immutable wrappers independently
    // fails this assertion test in the 33-test suite; production hashes match the first R4 batch.
    @Test fun `plan constructor detaches all five caller lists and exposes immutable collections`() {
        val target = ImportTarget("KITCHEN", 1, "KIT-BENCH-01", "KITCHEN")
        val source = ImportSourceId(ImportSourceCategory.IMAGE, 0)
        val row = ImportReviewRow(listOf(source))
        val blocker = ImportPlanningBlocker(ImportBlockerCode.PHOTO_REVIEW_REQUIRED, listOf(source))
        val photo = ImportPhotoReview(source, emptyList(), ImportPhotoReviewState.UNREVIEWED_EXCLUDED, ImportPhotoAction.ACTION_REQUIRED)
        val targets = mutableListOf(target)
        val rows = mutableListOf(row)
        val blockers = mutableListOf(blocker)
        val photos = mutableListOf(photo)
        val unrated = mutableListOf(target)
        val plan = ImportPlan(ImportPlanningSnapshot.create(input()).context, targets, rows, blockers, photos, unrated)
        listOf(targets, rows, blockers, photos, unrated).forEach { it.clear() }
        assertEquals(listOf(target), plan.targets)
        assertEquals(listOf(row), plan.rows)
        assertEquals(listOf(blocker), plan.blockers)
        assertEquals(listOf(photo), plan.photoReviews)
        assertEquals(listOf(target), plan.unratedTargets)
        for (exposed in listOf(plan.targets, plan.rows, plan.blockers, plan.photoReviews, plan.unratedTargets)) {
            assertFailsWith<UnsupportedOperationException> { (exposed as MutableList<*>).clear() }
        }
    }

    private fun input(
        propertyId: String? = "property-1",
        tenancyId: String? = "tenancy-1",
        reportDate: String? = "2026-09-07",
        sourceSha256: String? = sha,
        hasActiveDraft: Boolean = false,
        template: RoutineTemplateBinding? = binding(),
        roomInstances: List<ImportRoomInstance> = listOf(
            ImportRoomInstance("KITCHEN", 1, "KITCHEN"),
            ImportRoomInstance("BEDROOM", 1, "BEDROOM 1"),
            ImportRoomInstance("BEDROOM", 2, "BEDROOM 2"),
        ),
        suppressedStableIds: Set<String> = emptySet(),
    ) = ImportPlanningInput(
        propertyId, tenancyId, reportDate, sourceSha256, hasActiveDraft, template,
        roomInstances, suppressedStableIds,
        DocxExtractionManifest(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
    )

    private fun binding(template: Template = template()) = RoutineTemplateBinding("template-routine-v2", "b".repeat(64), template)

    private fun template(type: String = "ROUTINE", version: Int = 2) = Template(
        type = type,
        version = version,
        rooms = listOf(TemplateRoom("KITCHEN"), TemplateRoom("BEDROOM", repeatable = true)),
        items = listOf(kitchen, bedroom),
    )
}
