package nz.myinspection.app.feature.schedule

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nz.myinspection.core.schedule.InspectionScheduleType
import nz.myinspection.core.schedule.ScheduleAdvice

/**
 * Black-box acceptance for the schedule reducer and its presenter. Every test drives a compiled
 * entry point with concrete inputs and asserts only domain state plus recorded effects and port
 * traffic. No test reads source, resources or a compiled artifact, and no test touches Compose:
 * ScheduleScreen is covered by assembleDebug as compile evidence only.
 *
 * The REQ-001..009 tests below were accepted by T4-SCHEDULE-UI and their section headers carry
 * that card's acceptance ids. REQ-010..023 and the A1..A5 headers further down are this card's.
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

    // ----------------------------- T4-SCHEDULE-UI A1 row kinds and badges

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

    // ------------------------------- T4-SCHEDULE-UI A2 route effects

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

    // --------------------- T4-SCHEDULE-UI A3 filtering and restoration

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

    // ----------------------------- T4-SCHEDULE-UI A4 single action slots

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
    fun `REQ-009 each screen state declares at most one action slot and never a collection`() {
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

    // ------------------------------------------------ presenter ports and harness

    private val reminder = PendingReminder(
        route = ScheduleRoute("property-a", InspectionScheduleType.ROUTINE),
        dueAt = dueAt,
    )

    private val otherReminder = PendingReminder(
        route = ScheduleRoute("property-z", InspectionScheduleType.ANNUAL),
        dueAt = dueAt,
    )

    private fun occurrenceIdOf(pending: PendingReminder) = pending.toSpec().occurrenceId

    private fun causesFor(outcome: ReminderRegistrationOutcome) =
        ReminderRegistrationCause.entries.filter { it.outcome == outcome }

    private class FakePermissions(var granted: Boolean) : ReminderPermissionPort {
        var reads = 0
            private set

        override fun isPostNotificationsGranted(): Boolean {
            reads++
            return granted
        }
    }

    private class FakeRegistrations : ScheduleRegistrationPort {
        val submitted = mutableListOf<PendingReminder>()
        private var waiter: ((ReminderRegistrationCause) -> Unit)? = null

        override fun register(
            reminder: PendingReminder,
            waiter: (ReminderRegistrationCause) -> Unit,
        ) {
            submitted += reminder
            this.waiter = waiter
        }

        fun settle(cause: ReminderRegistrationCause) {
            val settling = assertNotNull(waiter, "nothing was registered")
            settling(cause)
        }
    }

    private class FakeRoutes : ScheduleRoutePort {
        val navigated = mutableListOf<ScheduleRoute>()

        override fun navigate(route: ScheduleRoute) {
            navigated += route
        }
    }

    private class FakeSettings : ScheduleSettingsPort {
        var opened = 0
            private set

        override fun openNotificationSettings() {
            opened++
        }
    }

    private class Harness(sdkInt: Int, granted: Boolean) {
        val permissions = FakePermissions(granted)
        val registrations = FakeRegistrations()
        val routes = FakeRoutes()
        val settings = FakeSettings()
        val presenter = SchedulePresenter(
            sdkInt = sdkInt,
            permissions = permissions,
            registrations = registrations,
            routes = routes,
            settings = settings,
        )
    }

    // --------------------------------------------------- A1 permission read timing

    @Test
    fun `REQ-010 at API 33 resume reads once and records what that read said`() {
        val granted = Harness(sdkInt = 33, granted = true)
        val denied = Harness(sdkInt = 33, granted = false)

        granted.presenter.onResume()
        denied.presenter.onResume()

        assertEquals(1, granted.permissions.reads)
        assertEquals(1, denied.permissions.reads)
        assertEquals(SchedulePermissionState.GRANTED, granted.presenter.state.permission)
        assertEquals(SchedulePermissionState.BLOCKED, denied.presenter.state.permission)
    }

    @Test
    fun `REQ-011 at API 33 the reminder action reads again before submitting the registration`() {
        val harness = Harness(sdkInt = 33, granted = true)
        harness.presenter.onResume()

        harness.presenter.onReminderAction(reminder)

        assertEquals(2, harness.permissions.reads)
        assertEquals(listOf(reminder), harness.registrations.submitted)
    }

    @Test
    fun `REQ-011 a grant revoked between resume and the action stops that same action`() {
        val harness = Harness(sdkInt = 33, granted = true)
        harness.presenter.onResume()
        harness.permissions.granted = false

        harness.presenter.onReminderAction(reminder)

        assertEquals(2, harness.permissions.reads)
        assertEquals(SchedulePermissionState.BLOCKED, harness.presenter.state.permission)
        assertEquals(emptyList(), harness.registrations.submitted)
    }

    @Test
    fun `REQ-012 below API 33 resume and the action never read the permission`() {
        val harness = Harness(sdkInt = 32, granted = false)

        harness.presenter.onResume()
        harness.presenter.onReminderAction(reminder)

        assertEquals(0, harness.permissions.reads)
        assertEquals(listOf(reminder), harness.registrations.submitted)
        assertEquals(SchedulePermissionState.UNKNOWN, harness.presenter.state.permission)
        assertNull(harness.presenter.state.permissionRecovery)
    }

    // ------------------------------------ A2 grant, denial, revocation and return

    @Test
    fun `REQ-013 creating and resuming without a user action reaches no port but the read`() {
        val harness = Harness(sdkInt = 33, granted = false)

        harness.presenter.onResume()
        harness.presenter.onResume()

        assertEquals(emptyList(), harness.registrations.submitted)
        assertEquals(0, harness.settings.opened)
        assertEquals(emptyList(), harness.routes.navigated)
        assertNull(harness.presenter.state.pending)
    }

    @Test
    fun `REQ-014 a granted read registers the pending occurrence a denial had stored`() {
        val harness = Harness(sdkInt = 33, granted = false)
        harness.presenter.onReminderAction(reminder)
        assertEquals(emptyList(), harness.registrations.submitted)

        harness.permissions.granted = true
        harness.presenter.onResume()

        assertEquals(listOf(reminder), harness.registrations.submitted)
        assertNull(harness.presenter.state.permissionRecovery)
    }

    @Test
    fun `REQ-015 a denied read offers one open settings action and leaves the list readable`() {
        val harness = Harness(sdkInt = 33, granted = false)
        harness.presenter.dispatch(ScheduleEvent.OccurrencesLoaded(listOf(due())))

        harness.presenter.onReminderAction(reminder)

        val state = harness.presenter.state
        assertEquals(SchedulePermissionState.BLOCKED, state.permission)
        assertEquals(ScheduleActionSlot.OPEN_SETTINGS, state.permissionRecovery)
        assertEquals(1, contentOf(state).rows.size)
        assertEquals(0, harness.settings.opened)
        assertEquals(emptyList(), harness.registrations.submitted)
        assertEquals(reminder, state.pending)
    }

    @Test
    fun `REQ-016 a permission revoked after being granted blocks and reaches no other port`() {
        val harness = Harness(sdkInt = 33, granted = true)
        harness.presenter.onResume()
        assertEquals(SchedulePermissionState.GRANTED, harness.presenter.state.permission)

        harness.permissions.granted = false
        harness.presenter.onResume()

        assertEquals(ScheduleActionSlot.OPEN_SETTINGS, harness.presenter.state.permissionRecovery)
        assertEquals(0, harness.settings.opened)
        assertEquals(emptyList(), harness.registrations.submitted)
    }

    @Test
    fun `REQ-017 returning from settings reads again before the permission state is rendered`() {
        val harness = Harness(sdkInt = 33, granted = false)
        harness.presenter.onReminderAction(reminder)

        harness.presenter.onOpenSettings()
        harness.permissions.granted = true
        harness.presenter.onResume()

        assertEquals(1, harness.settings.opened)
        assertEquals(SchedulePermissionState.GRANTED, harness.presenter.state.permission)
        assertNull(harness.presenter.state.permissionRecovery)
        assertEquals(listOf(reminder), harness.registrations.submitted)
    }

    // ------------------------------------------------ A3 settlement by outcome

    @Test
    fun `REQ-018 a retryable settlement retains the pending it names and discards any other`() {
        val timeout = ReminderRegistrationCause.ENQUEUE_CALLBACK_TIMEOUT
        val inFlight = ScheduleSubmission(reminder, settled = false)
        val named = loaded(due()).copy(pending = reminder, submission = inFlight)
        val other = loaded(due()).copy(pending = otherReminder, submission = inFlight)
        val settlement = ScheduleEvent.RegistrationSettled(timeout, occurrenceIdOf(reminder))

        val keptState = ScheduleReducer.reduce(named, settlement).state
        val droppedState = ScheduleReducer.reduce(other, settlement).state

        assertEquals(reminder, keptState.pending)
        assertEquals(named.screen, keptState.screen)
        assertNull(droppedState.pending)
    }

    @Test
    fun `REQ-018 every retryable cause retains the occurrence it names and settles it`() {
        val retryable = causesFor(ReminderRegistrationOutcome.RETRYABLE_FAILURE)
        assertTrue(retryable.isNotEmpty())

        retryable.forEach { cause ->
            val before = loaded(due())
                .copy(pending = reminder, submission = ScheduleSubmission(reminder, settled = false))

            val settled = ScheduleReducer.reduce(
                before,
                ScheduleEvent.RegistrationSettled(cause, occurrenceIdOf(reminder)),
            ).state

            assertEquals(reminder, settled.pending, "retryable cause $cause dropped the pending")
            assertEquals(true, settled.submission?.settled, "retryable cause $cause stayed in flight")
        }
    }

    @Test
    fun `REQ-019 every permanent cause discards the pending and errors with one recovery`() {
        val permanent = causesFor(ReminderRegistrationOutcome.PERMANENT_FAILURE)
        assertTrue(permanent.isNotEmpty())

        permanent.forEach { cause ->
            val before = loaded(due())
                .copy(pending = reminder, submission = ScheduleSubmission(reminder, settled = false))

            val settled = ScheduleReducer.reduce(
                before,
                ScheduleEvent.RegistrationSettled(cause, occurrenceIdOf(reminder)),
            ).state

            assertNull(settled.pending, "permanent cause $cause kept the pending")
            val error = assertIs<ScheduleScreenState.Error>(settled.screen, "cause $cause did not error")
            assertEquals(ScheduleRecovery.RETRY, error.recovery)
            assertEquals(ScheduleActionSlot.RETRY, settled.screen.actionSlot)
        }
    }

    @Test
    fun `REQ-020 every skipped cause leaves the rendered state and the effects unchanged`() {
        val skipped = causesFor(ReminderRegistrationOutcome.SKIPPED)
        assertTrue(skipped.isNotEmpty())
        val before = loaded(due())
            .copy(pending = reminder, submission = ScheduleSubmission(reminder, settled = false))

        skipped.forEach { cause ->
            val transition = ScheduleReducer.reduce(
                before,
                ScheduleEvent.RegistrationSettled(cause, occurrenceIdOf(reminder)),
            )

            assertEquals(before.screen, transition.state.screen, "skipped cause $cause redrew")
            assertEquals(before.pending, transition.state.pending, "skipped cause $cause moved it")
            assertEquals(emptyList(), transition.effects, "skipped cause $cause emitted an effect")
            assertEquals(true, transition.state.submission?.settled, "cause $cause stayed in flight")
        }
    }

    @Test
    fun `REQ-021 every admitted cause clears the pending and leaves nothing to replay`() {
        val admitted = causesFor(ReminderRegistrationOutcome.ADMITTED)
        assertTrue(admitted.isNotEmpty())

        admitted.forEach { cause ->
            val before = loaded(due())
                .copy(pending = reminder, submission = ScheduleSubmission(reminder, settled = false))

            val settled = ScheduleReducer.reduce(
                before,
                ScheduleEvent.RegistrationSettled(cause, occurrenceIdOf(reminder)),
            ).state

            assertNull(settled.pending, "admitted cause $cause kept the pending")
            assertNull(settled.submission, "admitted cause $cause left something to replay")
            assertEquals(before.screen, settled.screen)
        }
    }

    @Test
    fun `A3 a settlement that does not name the current submission changes nothing`() {
        val before = loaded(due())
            .copy(pending = otherReminder, submission = ScheduleSubmission(otherReminder, false))

        val transition = ScheduleReducer.reduce(
            before,
            ScheduleEvent.RegistrationSettled(
                ReminderRegistrationCause.ENQUEUE_SUBMIT_FATAL,
                occurrenceIdOf(reminder),
            ),
        )

        assertEquals(before, transition.state)
        assertEquals(emptyList(), transition.effects)
    }

    // ------------------------------------------ A4 retry identity and suppression

    @Test
    fun `REQ-021 retry re-registers the same occurrence id rather than deriving a new one`() {
        val harness = Harness(sdkInt = 32, granted = true)
        harness.presenter.onReminderAction(reminder)
        harness.registrations.settle(ReminderRegistrationCause.ENQUEUE_CALLBACK_TIMEOUT)

        harness.presenter.onRetry()

        assertEquals(2, harness.registrations.submitted.size)
        assertEquals(
            occurrenceIdOf(harness.registrations.submitted[0]),
            occurrenceIdOf(harness.registrations.submitted[1]),
        )
    }

    @Test
    fun `REQ-021 retry after a permanent failure re-registers the occurrence that failed`() {
        val harness = Harness(sdkInt = 32, granted = true)
        harness.presenter.onReminderAction(reminder)
        harness.registrations.settle(ReminderRegistrationCause.ENQUEUE_SUBMIT_FATAL)
        assertIs<ScheduleScreenState.Error>(harness.presenter.state.screen)

        harness.presenter.onRetry()

        assertEquals(listOf(reminder, reminder), harness.registrations.submitted)
    }

    @Test
    fun `REQ-021 retry after an admitted registration submits nothing`() {
        val harness = Harness(sdkInt = 32, granted = true)
        harness.presenter.onReminderAction(reminder)
        harness.registrations.settle(ReminderRegistrationCause.CALLBACK_CONFIRMED_ADMISSION)

        harness.presenter.onRetry()

        assertEquals(listOf(reminder), harness.registrations.submitted)
    }

    @Test
    fun `REQ-022 retry while the same occurrence is unsettled submits no second registration`() {
        val harness = Harness(sdkInt = 32, granted = true)
        harness.presenter.onReminderAction(reminder)

        harness.presenter.onRetry()

        assertEquals(listOf(reminder), harness.registrations.submitted)
    }

    @Test
    fun `REQ-022 a resume while the same occurrence is unsettled submits no second registration`() {
        val harness = Harness(sdkInt = 33, granted = true)
        harness.presenter.onReminderAction(reminder)

        harness.presenter.onResume()

        assertEquals(listOf(reminder), harness.registrations.submitted)
    }

    // ------------------------------------------------ A6 the ports the presenter drives

    @Test
    fun `A6 an activation dispatched through the presenter reaches the injected route port`() {
        val harness = Harness(sdkInt = 32, granted = true)
        harness.presenter.dispatch(ScheduleEvent.OccurrencesLoaded(listOf(due())))

        harness.presenter.dispatch(
            ScheduleEvent.RowActivated("property-a", InspectionScheduleType.ROUTINE),
        )

        assertEquals(
            listOf(ScheduleRoute("property-a", InspectionScheduleType.ROUTINE)),
            harness.routes.navigated,
        )
    }

    @Test
    fun `A1 a directly dispatched reminder request cannot skip the action-time read`() {
        val harness = Harness(sdkInt = 33, granted = false)

        harness.presenter.dispatch(ScheduleEvent.ReminderRequested(reminder))

        assertEquals(1, harness.permissions.reads)
        assertEquals(emptyList(), harness.registrations.submitted)
        assertEquals(SchedulePermissionState.BLOCKED, harness.presenter.state.permission)
        assertEquals(reminder, harness.presenter.state.pending)
    }

    @Test
    fun `A1 retry at API 33 reads again and a grant revoked since then stops it`() {
        val harness = Harness(sdkInt = 33, granted = true)
        harness.presenter.onReminderAction(reminder)
        harness.registrations.settle(ReminderRegistrationCause.ENQUEUE_SUBMIT_FATAL)
        harness.permissions.granted = false

        harness.presenter.onRetry()

        assertEquals(listOf(reminder), harness.registrations.submitted)
        assertEquals(ScheduleActionSlot.OPEN_SETTINGS, harness.presenter.state.permissionRecovery)
    }

    @Test
    fun `A3 a retry that is admitted takes the error state down`() {
        val harness = Harness(sdkInt = 32, granted = true)
        harness.presenter.dispatch(ScheduleEvent.OccurrencesLoaded(listOf(due())))
        harness.presenter.onReminderAction(reminder)
        harness.registrations.settle(ReminderRegistrationCause.ENQUEUE_SUBMIT_FATAL)
        assertIs<ScheduleScreenState.Error>(harness.presenter.state.screen)

        harness.presenter.onRetry()
        harness.registrations.settle(ReminderRegistrationCause.CALLBACK_CONFIRMED_ADMISSION)

        assertEquals(1, contentOf(harness.presenter.state).rows.size)
        assertNull(harness.presenter.state.submission)
    }

    @Test
    fun `A3 a skipped settlement leaves the retry it did not resolve available`() {
        val harness = Harness(sdkInt = 32, granted = true)
        harness.presenter.onReminderAction(reminder)
        harness.registrations.settle(ReminderRegistrationCause.ENQUEUE_SUBMIT_FATAL)
        harness.presenter.onRetry()
        harness.registrations.settle(ReminderRegistrationCause.OCCURRENCE_CLOSED)

        harness.presenter.onRetry()

        assertEquals(listOf(reminder, reminder, reminder), harness.registrations.submitted)
    }

    // ------------------------------------------------------------ A5 redaction

    @Test
    fun `REQ-023 no id cause name or property id reaches the error state or the recovery`() {
        val harness = Harness(sdkInt = 33, granted = false)
        harness.presenter.onReminderAction(reminder)
        harness.permissions.granted = true
        harness.presenter.onResume()
        harness.registrations.settle(ReminderRegistrationCause.RECEIPT_QUARANTINED)
        harness.permissions.granted = false
        harness.presenter.onResume()

        assertEquals(ScheduleActionSlot.OPEN_SETTINGS, harness.presenter.state.permissionRecovery)
        val rendered = harness.presenter.state.screen.toString() +
            harness.presenter.state.permissionRecovery.toString()
        assertFalse(rendered.contains(occurrenceIdOf(reminder)), "the screen leaked the occurrence id")
        assertFalse(
            rendered.contains(ReminderRegistrationCause.RECEIPT_QUARANTINED.name),
            "the screen leaked the cause constant name",
        )
        assertFalse(rendered.contains(reminder.route.propertyId), "the screen leaked a property id")
    }

    @Test
    fun `REQ-023 no row kind renders a property id in place of the property name`() {
        val names = contentOf(loaded(due(), first(), oneOff())).rows.map(ScheduleRow::propertyName)

        assertEquals(listOf("12 Rimu Street", "8 Kauri Lane", "3 Totara Way"), names)
        assertTrue(names.none { it.contains("property-") }, "a row name leaked a property id")
    }
}


/*
 * R4 mutation receipt for T4-SCHEDULE-UI, acceptance A5.
 *
 * Production SHA-256 before the batch and after every restore, identical, so no file was
 * left mutated and the receipt describes exactly the code that ships (L196, L270). These are the
 * hashes T4-SCHEDULE-UI-REMINDER-ACTIONS ships: editing ScheduleModels.kt voided the original run
 * (L270), so all fifteen rows below were re-run unchanged against exactly these bytes.
 *   ScheduleModels.kt  5c379c6cd20cc640485302678ec4ccf009b052a9cad4fd67616a0afdb1818cbf
 *   ScheduleScreen.kt  bc42f48c9e30ca68cc89deb8f2c922bc0c14b45a9e23d15bf8e192abc94d95ce
 *
 * Named selector, run once per mutation with the tests unchanged. A nonzero exit from this
 * exact command is what 'killed' means below:
 *   gradlew -p android --offline --no-daemon --rerun-tasks --no-build-cache \
 *     :app:testDebugUnitTest --tests nz.myinspection.app.feature.schedule.ScheduleUiTest
 *
 * Each row below is one single semantic edit to production code, applied with the tests
 * unchanged. Verdicts, exit codes and killing test names are read from the TestNG result
 * XML by the batch, not written by hand. No mutation targets a comment, a string literal
 * or a test, so none of them can be killed for a reason other than behaviour.
 *
 * 15 mutations, 15 killed, 0 survived.
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
 *        REQ-009 each screen state declares at most one action slot and never a collection
 *        REQ-009 no occurrences and no filter renders no-content with exactly one action slot
 * M11  A4  actionSlot for FilteredEmpty CLEAR_FILTER to NEXT
 *      KILLED exit 1, killed by
 *        REQ-009 each screen state declares at most one action slot and never a collection
 * M12  A1  rowsOf Due branch fills propertyName from propertyId
 *      KILLED exit 1, killed by
 *        REQ-001 a due row carries name type absolute date and the due badge
 *        REQ-001 a row carries the property name and never the raw property id
 * M13  A1  OccurrencesLoaded branch ignores the newly loaded occurrences
 *      KILLED exit 1, killed by
 *        REQ-001 004 005 one content screen carries all three row kinds at once
 *        REQ-001 a due row carries name type absolute date and the due badge
 *        REQ-001 a row carries the property name and never the raw property id
 *        REQ-004 a first-inspection row declares its badge and no due date
 *        REQ-005 a one-off row declares no due date and no count badge
 *        REQ-006 a filter retains only the occurrences whose type equals the selection
 *        REQ-006 clearing the filter restores every occurrence
 *        REQ-007 restoring after a configuration change keeps the filter and the scroll position
 * M14  A3  FilterSelected branch ignores the newly selected filter
 *      KILLED exit 1, killed by
 *        REQ-006 a filter retains only the occurrences whose type equals the selection
 *        REQ-007 restoring after a configuration change keeps the filter and the scroll position
 *        REQ-008 a filter matching nothing renders filtered-empty and not the no-content state
 *        REQ-008 an empty list under a filter is filtered-empty rather than no-content
 * M15  A1  project screen-selection criterion visible.isNotEmpty() forced to false
 *      KILLED exit 1, killed by
 *        REQ-001 004 005 one content screen carries all three row kinds at once
 *        REQ-001 a due row carries name type absolute date and the due badge
 *        REQ-001 a row carries the property name and never the raw property id
 *        REQ-004 a first-inspection row declares its badge and no due date
 *        REQ-005 a one-off row declares no due date and no count badge
 *        REQ-006 a filter retains only the occurrences whose type equals the selection
 *        REQ-006 clearing the filter restores every occurrence
 *        REQ-007 restoring after a configuration change keeps the filter and the scroll position
 */


/*
 * R4 mutation receipt for T4-SCHEDULE-UI-REMINDER-ACTIONS, acceptance A6. The block above is the
 * merged reducer's, re-run against these same bytes. Same hashes, same selector, same meaning of
 * KILLED: the command exits nonzero AND the JUnit XML exists and names failing tests, so a
 * compilation error cannot pass for a behavioural kill (L282). Verdicts, exit codes, failure
 * counts and killing test names are read from that XML by the batch, never written by hand. Every
 * row is one single semantic edit to production code applied with the tests untouched, and no row
 * targets a comment, a string literal or a test. Each row names one killing test of the count
 * beside it. Rows are tagged with this card's A1-A5. A6 is discharged by the receipts themselves.
 *
 * 40 mutations, 40 killed, 0 survived.
 *
 * M16 A1  onResume drops the API 33 floor, so a pre-33 device reads the permission
 *     KILLED exit 1, 1 test, REQ-012 below API 33 resume and the action never read the permission
 * M17 A1  the runtime-permission floor moves from 33 to 32
 *     KILLED exit 1, 1 test, REQ-012 below API 33 resume and the action never read the permission
 * M18 A1  submit inherits the stored permission state instead of reading again
 *     KILLED exit 1, 5 tests, A1 retry at API 33 reads again and a grant revoked since then stops it
 * M19 A2  readPermission records a grant as blocked and a denial as granted
 *     KILLED exit 1, 9 tests, REQ-010 at API 33 resume reads once and records what that read said
 * M20 A2  a granted resume reads but never registers the stored pending occurrence
 *     KILLED exit 1, 3 tests, REQ-014 a granted read registers the pending occurrence a denial had stored
 * M21 A2  a requested reminder is not stored, so a refused read loses it
 *     KILLED exit 1, 5 tests, A1 a directly dispatched reminder request cannot skip the action-time read
 * M22 A2  permissionRecovery offers nothing while the permission is blocked
 *     KILLED exit 1, 4 tests, A1 retry at API 33 reads again and a grant revoked since then stops it
 * M23 A2  permissionRecovery renders a never-read permission as blocked
 *     KILLED exit 1, 1 test, REQ-012 below API 33 resume and the action never read the permission
 * M24 A2  onOpenSettings reaches no settings port
 *     KILLED exit 1, 1 test, REQ-017 returning from settings reads again before the permission state is rendered
 * M25 A3  settle accepts a settlement that names another occurrence
 *     KILLED exit 1, 1 test, A3 a settlement that does not name the current submission changes nothing
 * M26 A3  an admitted settlement keeps the submission it admitted
 *     KILLED exit 1, 2 tests, A3 a retry that is admitted takes the error state down
 * M27 A3  an admitted settlement keeps the pending occurrence it names
 *     KILLED exit 1, 1 test, REQ-021 every admitted cause clears the pending and leaves nothing to replay
 * M28 A3  a retryable settlement retains only what it does not name
 *     KILLED exit 1, 2 tests, REQ-018 every retryable cause retains the occurrence it names and settles it
 * M29 A3  a retryable settlement leaves its submission unsettled
 *     KILLED exit 1, 2 tests, REQ-018 every retryable cause retains the occurrence it names and settles it
 * M30 A3  a permanent settlement keeps the pending occurrence it names
 *     KILLED exit 1, 1 test, REQ-019 every permanent cause discards the pending and errors with one recovery
 * M31 A3  a permanent settlement renders no error state
 *     KILLED exit 1, 3 tests, A3 a retry that is admitted takes the error state down
 * M32 A3  a skipped settlement clears the pending and the submission
 *     KILLED exit 1, 2 tests, A3 a skipped settlement leaves the retry it did not resolve available
 * M33 A4  the submission guard fires on a settled submission instead of an unsettled one
 *     KILLED exit 1, 5 tests, A3 a skipped settlement leaves the retry it did not resolve available
 * M34 A4  the submission guard compares the occurrence ids for inequality
 *     KILLED exit 1, 2 tests, REQ-022 retry while the same occurrence is unsettled submits no second registration
 * M35 A4  retry replays the pending occurrence instead of the submitted one
 *     KILLED exit 1, 3 tests, A3 a skipped settlement leaves the retry it did not resolve available
 * M36 A4  submit records its own registration as already settled
 *     KILLED exit 1, 2 tests, REQ-022 retry while the same occurrence is unsettled submits no second registration
 * M37 A5  rowsOf FirstInspection branch renders the property id as the property name
 *     KILLED exit 1, 1 test, REQ-023 no row kind renders a property id in place of the property name
 * M38 A1  submit registers without reading the permission at all
 *     KILLED exit 1, 6 tests, A1 retry at API 33 reads again and a grant revoked since then stops it
 * M39 A3  an admitted settlement leaves an error state on the screen
 *     KILLED exit 1, 1 test, A3 a retry that is admitted takes the error state down
 * M40 A3  a skipped settlement leaves its submission unsettled
 *     KILLED exit 1, 2 tests, A3 a skipped settlement leaves the retry it did not resolve available
 */
