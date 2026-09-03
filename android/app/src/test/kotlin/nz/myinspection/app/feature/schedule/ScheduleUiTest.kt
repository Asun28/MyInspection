package nz.myinspection.app.feature.schedule

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nz.myinspection.core.schedule.InspectionScheduleType
import nz.myinspection.core.schedule.ScheduleAdvice

/**
 * Black-box acceptance for the schedule reducer. Every test drives a compiled entry point with
 * concrete inputs and asserts only domain state plus recorded effects. No test reads source,
 * resources or a compiled artifact, and no test touches Compose: ScheduleScreen is covered by
 * assembleDebug as compile evidence only.
 *
 * Permission timing, registration outcomes and retry belong to
 * T4-SCHEDULE-UI-REMINDER-ACTIONS and are deliberately absent here.
 */
class ScheduleUiTest {

    private val dueAt = Instant.parse("2026-09-20T02:00:00Z")
    private val previous = Instant.parse("2026-06-21T02:00:00Z")

    private fun due(
        propertyId: String = "property-a",
        type: InspectionScheduleType = InspectionScheduleType.ROUTINE,
    ) = ScheduleOccurrence(
        propertyId = propertyId,
        propertyName = "12 Rimu Street",
        inspectionType = type,
        advice = ScheduleAdvice.Due(dueAt = dueAt, previousFinalizedAt = previous),
    )

    private fun first(
        propertyId: String = "property-b",
        type: InspectionScheduleType = InspectionScheduleType.ANNUAL,
    ) = ScheduleOccurrence(
        propertyId = propertyId,
        propertyName = "8 Kauri Lane",
        inspectionType = type,
        advice = ScheduleAdvice.FirstInspection,
    )

    private fun oneOff(
        propertyId: String = "property-c",
        type: InspectionScheduleType = InspectionScheduleType.INGOING,
    ) = ScheduleOccurrence(
        propertyId = propertyId,
        propertyName = "3 Totara Way",
        inspectionType = type,
        advice = ScheduleAdvice.NoRecurrence,
    )

    private fun loaded(vararg occurrences: ScheduleOccurrence): ScheduleUiState =
        ScheduleReducer.reduce(
            ScheduleReducer.initial(),
            ScheduleEvent.OccurrencesLoaded(occurrences.toList()),
        ).state

    private fun contentOf(state: ScheduleUiState): ScheduleScreenState.Content =
        assertIs<ScheduleScreenState.Content>(state.screen)

    // ------------------------------------------------- A1 row kinds and badges

    @Test
    fun `REQ-001 a due row carries name type absolute date and the due badge`() {
        val row = contentOf(loaded(due())).rows.single()

        val dueRow = assertIs<ScheduleRow.Due>(row)
        assertEquals("property-a", dueRow.propertyId)
        assertEquals("12 Rimu Street", dueRow.propertyName)
        assertEquals(InspectionScheduleType.ROUTINE, dueRow.inspectionType)
        assertEquals(dueAt, dueRow.dueAt)
        assertEquals(ScheduleBadge.DUE, dueRow.badge)
    }

    @Test
    fun `REQ-004 a first-inspection row declares its badge and no due date`() {
        val row = contentOf(loaded(first())).rows.single()

        val firstRow = assertIs<ScheduleRow.FirstInspection>(row)
        assertEquals(ScheduleBadge.FIRST_INSPECTION, firstRow.badge)
        assertNull(firstRow.dueAt)
    }

    @Test
    fun `REQ-005 a one-off row declares no due date and no count badge`() {
        val row = contentOf(loaded(oneOff())).rows.single()

        val oneOffRow = assertIs<ScheduleRow.OneOff>(row)
        assertEquals(ScheduleBadge.NONE, oneOffRow.badge)
        assertNull(oneOffRow.dueAt)
    }

    @Test
    fun `REQ-001 004 005 one content screen carries all three row kinds at once`() {
        val rows = contentOf(loaded(due(), first(), oneOff())).rows

        assertEquals(3, rows.size)
        assertEquals(
            listOf(ScheduleBadge.DUE, ScheduleBadge.FIRST_INSPECTION, ScheduleBadge.NONE),
            rows.map(ScheduleRow::badge),
        )
        assertEquals(listOf(dueAt, null, null), rows.map(ScheduleRow::dueAt))
    }

    @Test
    fun `REQ-001 a row carries the property name and never the raw property id`() {
        val row = contentOf(loaded(due())).rows.single()

        assertEquals("12 Rimu Street", row.propertyName)
        assertFalse(row.propertyName.contains("property-a"))
    }

    // ------------------------------------------------------- A2 route effects

    @Test
    fun `REQ-002 activating a due row emits exactly one route effect carrying property and type`() {
        val transition = ScheduleReducer.reduce(
            loaded(due()),
            ScheduleEvent.RowActivated("property-a", InspectionScheduleType.ROUTINE),
        )

        val effect = assertIs<ScheduleEffect.Navigate>(transition.effects.single())
        assertEquals("property-a", effect.route.propertyId)
        assertEquals(InspectionScheduleType.ROUTINE, effect.route.inspectionType)
        assertTrue(transition.state.navigating)
    }

    @Test
    fun `REQ-003 a second activation while the route is unsettled emits no second effect`() {
        val activated = ScheduleReducer.reduce(
            loaded(due()),
            ScheduleEvent.RowActivated("property-a", InspectionScheduleType.ROUTINE),
        ).state

        val again = ScheduleReducer.reduce(
            activated,
            ScheduleEvent.RowActivated("property-a", InspectionScheduleType.ROUTINE),
        )

        assertEquals(emptyList(), again.effects)
        assertTrue(again.state.navigating)
    }

    @Test
    fun `REQ-003 an activation after the route settles emits again`() {
        val activated = ScheduleReducer.reduce(
            loaded(due()),
            ScheduleEvent.RowActivated("property-a", InspectionScheduleType.ROUTINE),
        ).state
        val settled = ScheduleReducer.reduce(activated, ScheduleEvent.RouteSettled).state

        val again = ScheduleReducer.reduce(
            settled,
            ScheduleEvent.RowActivated("property-a", InspectionScheduleType.ROUTINE),
        )

        assertFalse(settled.navigating)
        assertIs<ScheduleEffect.Navigate>(again.effects.single())
    }

    // ------------------------------------------- A3 filtering and restoration

    @Test
    fun `REQ-006 a filter retains only the occurrences whose type equals the selection`() {
        val filtered = ScheduleReducer.reduce(
            loaded(due(), first(), oneOff()),
            ScheduleEvent.FilterSelected(InspectionScheduleType.ANNUAL),
        ).state

        val rows = contentOf(filtered).rows
        assertEquals(1, rows.size)
        assertEquals(InspectionScheduleType.ANNUAL, rows.single().inspectionType)
        assertEquals(InspectionScheduleType.ANNUAL, filtered.filter)
    }

    @Test
    fun `REQ-006 clearing the filter restores every occurrence`() {
        val filtered = ScheduleReducer.reduce(
            loaded(due(), first(), oneOff()),
            ScheduleEvent.FilterSelected(InspectionScheduleType.ANNUAL),
        ).state

        val cleared = ScheduleReducer.reduce(filtered, ScheduleEvent.FilterSelected(null)).state

        assertEquals(3, contentOf(cleared).rows.size)
        assertNull(cleared.filter)
    }

    @Test
    fun `REQ-007 restoring after a configuration change keeps the filter and the scroll position`() {
        val filtered = ScheduleReducer.reduce(
            loaded(due(), first()),
            ScheduleEvent.FilterSelected(InspectionScheduleType.ANNUAL),
        ).state
        val scrolled = ScheduleReducer.reduce(filtered, ScheduleEvent.ScrollChanged(7)).state

        val restored = ScheduleReducer.reduce(
            scrolled,
            ScheduleEvent.OccurrencesLoaded(listOf(due(), first())),
        ).state

        assertEquals(InspectionScheduleType.ANNUAL, restored.filter)
        assertEquals(7, restored.scrollIndex)
        assertEquals(1, contentOf(restored).rows.size)
    }

    @Test
    fun `REQ-008 a filter matching nothing renders filtered-empty and not the no-content state`() {
        val filtered = ScheduleReducer.reduce(
            loaded(due()),
            ScheduleEvent.FilterSelected(InspectionScheduleType.EXIT),
        ).state

        val empty = assertIs<ScheduleScreenState.FilteredEmpty>(filtered.screen)
        assertEquals(InspectionScheduleType.EXIT, empty.filter)
    }

    @Test
    fun `REQ-008 an empty list under a filter is filtered-empty rather than no-content`() {
        val filtered = ScheduleReducer.reduce(
            loaded(),
            ScheduleEvent.FilterSelected(InspectionScheduleType.EXIT),
        ).state

        assertIs<ScheduleScreenState.FilteredEmpty>(filtered.screen)
    }

    // ------------------------------------------------- A4 single action slots

    @Test
    fun `REQ-009 no occurrences and no filter renders no-content with exactly one action slot`() {
        val state = loaded()

        assertIs<ScheduleScreenState.NoContentEmpty>(state.screen)
        assertNull(state.filter)
        assertEquals(ScheduleActionSlot.NEXT, state.screen.actionSlot)
    }

    @Test
    fun `REQ-009 the initial state is loading rather than empty and offers no action`() {
        val initial = ScheduleReducer.initial()

        assertIs<ScheduleScreenState.Loading>(initial.screen)
        assertNull(initial.screen.actionSlot)
    }

    @Test
    fun `REQ-009 every non-content screen state declares its own single action slot`() {
        assertEquals(
            listOf(
                null,
                null,
                ScheduleActionSlot.NEXT,
                ScheduleActionSlot.CLEAR_FILTER,
                ScheduleActionSlot.RETRY,
            ),
            listOf<ScheduleScreenState>(
                ScheduleScreenState.Loading,
                ScheduleScreenState.Content(emptyList()),
                ScheduleScreenState.NoContentEmpty,
                ScheduleScreenState.FilteredEmpty(InspectionScheduleType.EXIT),
                ScheduleScreenState.Error(ScheduleRecovery.RETRY),
            ).map(ScheduleScreenState::actionSlot),
        )
    }
}

/*
 * R4 mutation receipt for T4-SCHEDULE-UI, acceptance A5.
 *
 * Production SHA-256 before the batch and after every restore, identical, so no file was
 * left mutated and the receipt describes exactly the code that ships (L196, L270):
 *   ScheduleModels.kt  22600da5bdbf09ecc45e5eed5c9bf88fde6f1d7ae4d827b77bcbeab0b47d2034
 *   ScheduleScreen.kt  113d1d78b9da91f7d5ee5d6d8491786fe1bc7536287af3edd2378619acf4c01b
 *
 * Each row below is one single semantic edit to production code, applied with the tests
 * unchanged. Verdicts, exit codes and killing test names are read from the TestNG result
 * XML by the batch, not written by hand. No mutation targets a comment, a string literal
 * or a test, so none of them can be killed for a reason other than behaviour.
 *
 * 12 mutations, 12 killed, 0 survived.
 *
 * M1   A1  ScheduleRow.OneOff.badge NONE to DUE
 *      KILLED exit 1, killed by
 *        REQ-001 004 005 one content screen carries all three row kinds at once
 *        REQ-005 a one-off row declares no due date and no count badge
 * M2   A1  ScheduleRow.FirstInspection.dueAt null to Instant.EPOCH
 *      KILLED exit 1, killed by
 *        REQ-001 004 005 one content screen carries all three row kinds at once
 *        REQ-004 a first-inspection row declares its badge and no due date
 * M3   A1  rowsOf NoRecurrence branch builds FirstInspection instead of OneOff
 *      KILLED exit 1, killed by
 *        REQ-001 004 005 one content screen carries all three row kinds at once
 *        REQ-005 a one-off row declares no due date and no count badge
 * M4   A2  activate guard state.navigating to false, so nothing is suppressed
 *      KILLED exit 1, killed by
 *        REQ-003 a second activation while the route is unsettled emits no second effect
 * M5   A2  activate sets navigating = false instead of true
 *      KILLED exit 1, killed by
 *        REQ-002 activating a due row emits exactly one route effect carrying property and type
 *        REQ-003 a second activation while the route is unsettled emits no second effect
 * M6   A2  RouteSettled sets navigating = true instead of false
 *      KILLED exit 1, killed by
 *        REQ-003 an activation after the route settles emits again
 * M7   A3  project filter predicate inspectionType == filter to true
 *      KILLED exit 1, killed by
 *        REQ-006 a filter retains only the occurrences whose type equals the selection
 *        REQ-007 restoring after a configuration change keeps the filter and the scroll position
 *        REQ-008 a filter matching nothing renders filtered-empty and not the no-content state
 * M8   A3  project FilteredEmpty branch made unreachable, falls through to NoContentEmpty
 *      KILLED exit 1, killed by
 *        REQ-008 a filter matching nothing renders filtered-empty and not the no-content state
 *        REQ-008 an empty list under a filter is filtered-empty rather than no-content
 * M9   A3  ScrollChanged drops the new index and keeps the old state
 *      KILLED exit 1, killed by
 *        REQ-007 restoring after a configuration change keeps the filter and the scroll position
 * M10  A4  actionSlot for NoContentEmpty NEXT to null
 *      KILLED exit 1, killed by
 *        REQ-009 every non-content screen state declares its own single action slot
 *        REQ-009 no occurrences and no filter renders no-content with exactly one action slot
 * M11  A4  actionSlot for FilteredEmpty CLEAR_FILTER to NEXT
 *      KILLED exit 1, killed by
 *        REQ-009 every non-content screen state declares its own single action slot
 * M12  A1  rowsOf Due branch fills propertyName from propertyId
 *      KILLED exit 1, killed by
 *        REQ-001 a due row carries name type absolute date and the due badge
 *        REQ-001 a row carries the property name and never the raw property id
 */
