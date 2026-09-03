package nz.myinspection.app.feature.schedule

import java.time.Instant
import nz.myinspection.core.schedule.InspectionScheduleType
import nz.myinspection.core.schedule.ScheduleAdvice

/**
 * One property-and-type pair as the schedule lists it, carrying the advice the merged planner
 * already produced. The reducer projects an advice, it never recomputes one, so the recurrence
 * rules stay owned by [nz.myinspection.core.schedule.SchedulePlanner] alone.
 */
data class ScheduleOccurrence(
    val propertyId: String,
    val propertyName: String,
    val inspectionType: InspectionScheduleType,
    val advice: ScheduleAdvice,
)

/**
 * What a badge says about a row. [NONE] is a declared value rather than a missing one, so that a
 * one-off row is required to carry the absence rather than merely happening not to carry a badge.
 */
enum class ScheduleBadge {
    DUE,
    FIRST_INSPECTION,
    NONE,
}

/**
 * What a row is, decided by that row's own advice. A content screen may carry all three kinds at
 * once, which is why the kind lives here and the mutually exclusive level is [ScheduleScreenState].
 * [dueAt] is null on every kind that declares no due date, so "renders no due date" is a property
 * of the value rather than a rule a renderer has to remember.
 */
sealed interface ScheduleRow {
    val propertyId: String
    val propertyName: String
    val inspectionType: InspectionScheduleType
    val badge: ScheduleBadge
    val dueAt: Instant?

    data class Due(
        override val propertyId: String,
        override val propertyName: String,
        override val inspectionType: InspectionScheduleType,
        override val dueAt: Instant,
    ) : ScheduleRow {
        override val badge: ScheduleBadge = ScheduleBadge.DUE
    }

    data class FirstInspection(
        override val propertyId: String,
        override val propertyName: String,
        override val inspectionType: InspectionScheduleType,
    ) : ScheduleRow {
        override val badge: ScheduleBadge = ScheduleBadge.FIRST_INSPECTION
        override val dueAt: Instant? = null
    }

    data class OneOff(
        override val propertyId: String,
        override val propertyName: String,
        override val inspectionType: InspectionScheduleType,
    ) : ScheduleRow {
        override val badge: ScheduleBadge = ScheduleBadge.NONE
        override val dueAt: Instant? = null
    }
}

/**
 * The recovery a screen state offers when it has one. Only [ScheduleScreenState.Error] carries one,
 * and it carries a single value rather than a menu, so a state cannot offer a choice of two. A
 * blocked notification permission is not in here: it does not replace the screen, so it is derived
 * by [permissionRecovery] instead.
 */
enum class ScheduleRecovery {
    RETRY,
}

/**
 * What the last permission read said. [UNKNOWN] is not a denial: below API 33 the presenter never
 * reads at all, and before the first read there is nothing to offer a recovery from. Keeping the
 * three apart is what stops a never-read state from rendering as blocked.
 */
enum class SchedulePermissionState {
    UNKNOWN,
    GRANTED,
    BLOCKED,
}

/**
 * The mutually exclusive level. Exactly one of these is rendered at a time. What each branch
 * carries differs by design: [Content] carries rows, [FilteredEmpty] carries the filter that
 * emptied it, [Error] carries its recovery, and [Loading] and [NoContentEmpty] carry neither
 * because there is nothing yet to carry. The action a branch offers is not held here at all, it is
 * derived by [actionSlot].
 *
 * [Error] has exactly one way in: a registration that settled on a cause whose outcome is
 * PERMANENT_FAILURE. Its recovery is a retry of that same occurrence, which is why
 * [ScheduleUiState.submission] outlives the pending occurrence a permanent failure discards.
 */
sealed interface ScheduleScreenState {
    data object Loading : ScheduleScreenState

    data class Content(val rows: List<ScheduleRow>) : ScheduleScreenState

    data object NoContentEmpty : ScheduleScreenState

    data class FilteredEmpty(val filter: InspectionScheduleType) : ScheduleScreenState

    data class Error(val recovery: ScheduleRecovery) : ScheduleScreenState
}

/**
 * The action a screen state or the permission recovery offers. Single-valued by construction rather
 * than by rule: [actionSlot] and [permissionRecovery] each return one of these or none, never a
 * collection, so "this offers two actions" is not a thing an implementation can express while still
 * type-checking. Which target each slot points at, and what it is called, belong to
 * T4-SCHEDULE-UI-PRESENTATION.
 *
 * [ScheduleScreenState.Loading] and [ScheduleScreenState.Content] deliberately offer none. Loading
 * is a 300ms-threshold state over a local disk read with nothing to act on yet, and a content
 * screen's actions belong to its rows.
 */
enum class ScheduleActionSlot {
    NEXT,
    CLEAR_FILTER,
    RETRY,
    OPEN_SETTINGS,
}

/** The action a state offers, or null where the state declares none. */
val ScheduleScreenState.actionSlot: ScheduleActionSlot?
    get() = when (this) {
        is ScheduleScreenState.Loading -> null
        is ScheduleScreenState.Content -> null
        is ScheduleScreenState.NoContentEmpty -> ScheduleActionSlot.NEXT
        is ScheduleScreenState.FilteredEmpty -> ScheduleActionSlot.CLEAR_FILTER
        is ScheduleScreenState.Error -> ScheduleActionSlot.RETRY
    }

/**
 * The recovery offered while notifications are blocked, or none while they are not. It sits beside
 * the screen rather than replacing it, because a blocked notification permission does not stop the
 * schedule from being read, and the in-app schedule is exactly the fallback context/DESIGN.md names
 * for notifications. [SchedulePermissionState.UNKNOWN] offers nothing, so a device that never reads
 * cannot render as blocked.
 */
val ScheduleUiState.permissionRecovery: ScheduleActionSlot?
    get() = when (permission) {
        SchedulePermissionState.BLOCKED -> ScheduleActionSlot.OPEN_SETTINGS
        SchedulePermissionState.UNKNOWN -> null
        SchedulePermissionState.GRANTED -> null
    }

/**
 * The registration this presenter submitted, and whether it has settled. One value rather than two,
 * because "which occurrence may a retry replay" and "is that occurrence still unsettled" are read
 * off the same submission and could otherwise disagree.
 */
data class ScheduleSubmission(
    val reminder: PendingReminder,
    val settled: Boolean,
)

/**
 * Everything the schedule renders from. [navigating] and [submission] are the two unsettled-work
 * markers the duplicate-suppression rules are stated against: a second activation and a second
 * registration are refused by consulting the marker, not by hoping the caller checks.
 *
 * [pending] and [submission] answer different questions about the same occurrence. [pending] is the
 * one a granted read may register without the user asking again, so a permanent failure clears it
 * and no later resume can silently resubmit. [submission] is what an explicit retry replays, so the
 * occurrence that failed outlives the clearing of [pending].
 */
data class ScheduleUiState(
    val occurrences: List<ScheduleOccurrence>,
    val screen: ScheduleScreenState,
    val filter: InspectionScheduleType?,
    val scrollIndex: Int,
    val permission: SchedulePermissionState,
    val pending: PendingReminder?,
    val submission: ScheduleSubmission?,
    val navigating: Boolean,
)

/** What happened, as the reducer is told about it. */
sealed interface ScheduleEvent {
    data class OccurrencesLoaded(val occurrences: List<ScheduleOccurrence>) : ScheduleEvent

    data class FilterSelected(val filter: InspectionScheduleType?) : ScheduleEvent

    data class RowActivated(
        val propertyId: String,
        val inspectionType: InspectionScheduleType,
    ) : ScheduleEvent

    data object RouteSettled : ScheduleEvent

    data class ScrollChanged(val index: Int) : ScheduleEvent

    data class ReminderRequested(val reminder: PendingReminder) : ScheduleEvent

    /**
     * A registration settled. [occurrenceId] is the occurrence this settlement is about, which the
     * presenter knows because it is the one it submitted: the waiter is handed a cause alone, so
     * reading a generation number here would mean inventing the other half of an identity.
     */
    data class RegistrationSettled(
        val cause: ReminderRegistrationCause,
        val occurrenceId: String,
    ) : ScheduleEvent
}

/**
 * What the host is asked to do. Recorded rather than performed, so a reducer stays testable. There
 * is no permission-request effect, which is how "this app never asks for notifications by itself"
 * stays true: a resume has nothing to reach for, rather than a rule telling it not to.
 */
sealed interface ScheduleEffect {
    data class Navigate(val route: ScheduleRoute) : ScheduleEffect

    data class Register(val reminder: PendingReminder) : ScheduleEffect
}

/**
 * Hands a reminder to the merged registration machinery. Production delegates to
 * [ReminderScheduler.register], which is why the waiter signature is that method's.
 */
interface ScheduleRegistrationPort {
    fun register(reminder: PendingReminder, waiter: (ReminderRegistrationCause) -> Unit)
}

/** Performs a navigation the reducer decided on. */
interface ScheduleRoutePort {
    fun navigate(route: ScheduleRoute)
}

/** Opens this app's platform notification settings. Never requests a permission. */
interface ScheduleSettingsPort {
    fun openNotificationSettings()
}

/** One reduction: the state that follows, and everything the host should do about it. */
data class ScheduleTransition(
    val state: ScheduleUiState,
    val effects: List<ScheduleEffect>,
)

/**
 * Projects occurrences and events onto states. Pure: every dependency arrives as an argument, so
 * what is read and when it is read stay out of here and live in [SchedulePresenter], which is the
 * only thing here that holds a port.
 */
object ScheduleReducer {
    /** The state before anything has been read. Loading, not empty: nothing has been looked at. */
    fun initial(): ScheduleUiState = ScheduleUiState(
        occurrences = emptyList(),
        screen = ScheduleScreenState.Loading,
        filter = null,
        scrollIndex = 0,
        permission = SchedulePermissionState.UNKNOWN,
        pending = null,
        submission = null,
        navigating = false,
    )

    /** Projects each occurrence onto the row kind its own advice names. */
    fun rowsOf(occurrences: List<ScheduleOccurrence>): List<ScheduleRow> =
        occurrences.map { occurrence ->
            when (val advice = occurrence.advice) {
                is ScheduleAdvice.Due -> ScheduleRow.Due(
                    propertyId = occurrence.propertyId,
                    propertyName = occurrence.propertyName,
                    inspectionType = occurrence.inspectionType,
                    dueAt = advice.dueAt,
                )

                is ScheduleAdvice.FirstInspection -> ScheduleRow.FirstInspection(
                    propertyId = occurrence.propertyId,
                    propertyName = occurrence.propertyName,
                    inspectionType = occurrence.inspectionType,
                )

                is ScheduleAdvice.NoRecurrence -> ScheduleRow.OneOff(
                    propertyId = occurrence.propertyId,
                    propertyName = occurrence.propertyName,
                    inspectionType = occurrence.inspectionType,
                )
            }
        }

    fun reduce(state: ScheduleUiState, event: ScheduleEvent): ScheduleTransition = when (event) {
        is ScheduleEvent.OccurrencesLoaded ->
            ScheduleTransition(project(state.copy(occurrences = event.occurrences)), emptyList())

        is ScheduleEvent.FilterSelected ->
            ScheduleTransition(project(state.copy(filter = event.filter)), emptyList())

        is ScheduleEvent.ScrollChanged ->
            ScheduleTransition(state.copy(scrollIndex = event.index), emptyList())

        is ScheduleEvent.RowActivated -> activate(state, event)

        is ScheduleEvent.RouteSettled ->
            ScheduleTransition(state.copy(navigating = false), emptyList())

        is ScheduleEvent.ReminderRequested -> ScheduleTransition(
            state.copy(pending = event.reminder),
            listOf(ScheduleEffect.Register(event.reminder)),
        )

        is ScheduleEvent.RegistrationSettled -> settle(state, event)
    }

    /**
     * Chooses the screen. The filtered-empty and no-content branches are separated by whether a
     * filter is what emptied the list, because those two states offer different next actions.
     */
    private fun project(state: ScheduleUiState): ScheduleUiState {
        val filter = state.filter
        val visible = state.occurrences.filter { filter == null || it.inspectionType == filter }
        val screen = when {
            visible.isNotEmpty() -> ScheduleScreenState.Content(rowsOf(visible))
            filter != null -> ScheduleScreenState.FilteredEmpty(filter)
            else -> ScheduleScreenState.NoContentEmpty
        }
        return state.copy(screen = screen)
    }

    /** One activation at a time: the marker is consulted, not the caller's memory. */
    private fun activate(
        state: ScheduleUiState,
        event: ScheduleEvent.RowActivated,
    ): ScheduleTransition = if (state.navigating) {
        ScheduleTransition(state, emptyList())
    } else {
        ScheduleTransition(
            state.copy(navigating = true),
            listOf(
                ScheduleEffect.Navigate(
                    ScheduleRoute(event.propertyId, event.inspectionType),
                ),
            ),
        )
    }

    /**
     * Applies a settlement by the outcome its cause carries, never by the cause itself: the four
     * outcomes are the whole vocabulary, so a cause added upstream lands in the right branch
     * without this file being edited.
     *
     * A settlement is applied only by the registration it names. A waiter carries the occurrence it
     * was submitted for, so a callback that arrives after the presenter moved on to another
     * occurrence closes nothing: the in-flight marker it would clear, and the error it would draw,
     * belong to a registration this settlement knows nothing about.
     *
     * SKIPPED then returns the state it was given, unchanged down to the submission. Both skipped
     * causes say the registration was moot rather than unsuccessful: the occurrence was closed, or
     * a later generation took it over and is still running. Neither leaves the user anything to
     * retry, so the marker that suppresses a retry is exactly what should stay.
     */
    private fun settle(
        state: ScheduleUiState,
        event: ScheduleEvent.RegistrationSettled,
    ): ScheduleTransition {
        val settling = state.submission?.takeIf { it.reminder.isOccurrence(event.occurrenceId) }
            ?: return ScheduleTransition(state, emptyList())
        return when (event.cause.outcome) {
            ReminderRegistrationOutcome.SKIPPED -> ScheduleTransition(state, emptyList())

            ReminderRegistrationOutcome.ADMITTED -> ScheduleTransition(
                state.copy(
                    pending = state.pending?.takeUnless { it.isOccurrence(event.occurrenceId) },
                    submission = null,
                ),
                emptyList(),
            )

            ReminderRegistrationOutcome.RETRYABLE_FAILURE -> ScheduleTransition(
                state.copy(
                    pending = state.pending?.takeIf { it.isOccurrence(event.occurrenceId) },
                    submission = settling.copy(settled = true),
                ),
                emptyList(),
            )

            ReminderRegistrationOutcome.PERMANENT_FAILURE -> ScheduleTransition(
                state.copy(
                    pending = state.pending?.takeUnless { it.isOccurrence(event.occurrenceId) },
                    submission = settling.copy(settled = true),
                    screen = ScheduleScreenState.Error(ScheduleRecovery.RETRY),
                ),
                emptyList(),
            )
        }
    }
}

/** Whether this reminder is the occurrence a settlement names. */
private fun PendingReminder.isOccurrence(occurrenceId: String): Boolean =
    toSpec().occurrenceId == occurrenceId

/**
 * Owns what the reducer deliberately does not: what is read, and when. The API level arrives as a
 * value rather than being read from [android.os.Build], so the API 33 boundary is exercisable on a
 * plain JVM and the rule is a fact about the argument rather than about the host.
 */
class SchedulePresenter(
    private val sdkInt: Int,
    private val permissions: ReminderPermissionPort,
    private val registrations: ScheduleRegistrationPort,
    private val routes: ScheduleRoutePort,
    private val settings: ScheduleSettingsPort,
) {
    var state: ScheduleUiState = ScheduleReducer.initial()
        private set

    /** Applies an event and performs whatever the reduction asked for. */
    fun dispatch(event: ScheduleEvent) {
        val transition = ScheduleReducer.reduce(state, event)
        state = transition.state
        transition.effects.forEach(::perform)
    }

    /**
     * The resume edge. At API 33 and above it reads first, so a grant or a revocation made outside
     * the app is seen before anything is rendered from it, and a grant that arrived while the user
     * was in settings registers what the denial had stored. It asks for nothing: the only request a
     * platform can receive is one a user action makes, and there is no request effect for this path
     * to reach for.
     */
    fun onResume() {
        if (sdkInt < REQUIRES_PERMISSION_SDK) {
            return
        }
        if (readPermission()) {
            state.pending?.let(::submit)
        }
    }

    /**
     * The user's reminder action. Below API 33 it submits without reading, because there is no
     * runtime notification permission to read. At or above it, the read happens immediately before
     * the submission rather than being inherited from the resume, so a grant revoked in between
     * stops this action rather than the next one, and the refused occurrence is stored for the next
     * granted read instead of being lost.
     */
    fun onReminderAction(reminder: PendingReminder) {
        if (sdkInt >= REQUIRES_PERMISSION_SDK && !readPermission()) {
            state = state.copy(pending = reminder)
            return
        }
        dispatch(ScheduleEvent.ReminderRequested(reminder))
    }

    /**
     * The user's retry. It replays the submission this presenter made, so the occurrence it
     * registers is the one that failed: no path here derives an occurrence id, and an admitted
     * registration leaves no submission for this to replay.
     */
    fun onRetry() {
        state.submission?.reminder?.let(::submit)
    }

    /** The user's recovery on a blocked permission. Leaves the app, requests nothing. */
    fun onOpenSettings() {
        settings.openNotificationSettings()
    }

    /** Reads once and records what was read. Returns what the platform said, not what was stored. */
    private fun readPermission(): Boolean {
        val granted = permissions.isPostNotificationsGranted()
        state = state.copy(
            permission = if (granted) {
                SchedulePermissionState.GRANTED
            } else {
                SchedulePermissionState.BLOCKED
            },
        )
        return granted
    }

    private fun perform(effect: ScheduleEffect) = when (effect) {
        is ScheduleEffect.Navigate -> routes.navigate(effect.route)
        is ScheduleEffect.Register -> submit(effect.reminder)
    }

    /**
     * The one place a registration is submitted, which is why "no second registration while this
     * occurrence is unsettled" holds for a retry, a resume and a repeated action alike: the guard
     * is here rather than at each caller, so no caller can be the one that forgets it.
     *
     * The occurrence id comes from the same factory the scheduler uses. A route it cannot resolve
     * is a precondition failure of the caller, not a state to render: whether a route is valid is
     * the scheduler's answer to give, and a schedule row's property id comes from the planner, so
     * this card does not add a second place that decides it.
     */
    private fun submit(reminder: PendingReminder) {
        val occurrenceId = reminder.toSpec().occurrenceId
        val inFlight = state.submission
        if (inFlight != null && !inFlight.settled &&
            inFlight.reminder.toSpec().occurrenceId == occurrenceId
        ) {
            return
        }
        state = state.copy(submission = ScheduleSubmission(reminder, settled = false))
        registrations.register(reminder) { cause ->
            dispatch(ScheduleEvent.RegistrationSettled(cause, occurrenceId))
        }
    }

    private companion object {
        /** POST_NOTIFICATIONS became a runtime permission in Android 13. */
        const val REQUIRES_PERMISSION_SDK = 33
    }
}
