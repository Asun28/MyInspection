package nz.myinspection.app.feature.schedule

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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
     * SKIPPED leaves the rendered state and the effects alone but still settles the submission.
     * Both skipped causes are terminal answers to this registration, moot rather than unsuccessful,
     * so the flight really is over: keeping it open would suspend the retry for good, and an error
     * already on screen would then offer a recovery action that cannot act.
     *
     * That is the invariant the branches below hold together: an error is rendered only while a
     * settled submission is there for a retry to replay. The registration that finally succeeds is
     * therefore what takes the error down.
     */
    private fun settle(
        state: ScheduleUiState,
        event: ScheduleEvent.RegistrationSettled,
    ): ScheduleTransition {
        val settling = state.submission?.takeIf { it.reminder.isOccurrence(event.occurrenceId) }
            ?: return ScheduleTransition(state, emptyList())
        return when (event.cause.outcome) {
            ReminderRegistrationOutcome.SKIPPED -> ScheduleTransition(
                state.copy(submission = settling.copy(settled = true)),
                emptyList(),
            )

            ReminderRegistrationOutcome.ADMITTED -> ScheduleTransition(
                state.copy(
                    pending = state.pending?.takeUnless { it.isOccurrence(event.occurrenceId) },
                    submission = null,
                    screen = withoutError(state),
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

    /**
     * The screen once nothing is left to retry: an error gives way to whatever the occurrences and
     * the filter say, and every other screen is left exactly as it was.
     */
    private fun withoutError(state: ScheduleUiState): ScheduleScreenState =
        if (state.screen is ScheduleScreenState.Error) project(state).screen else state.screen
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
     * was in settings releases what the denial had stored. [submit] reads once more of its own
     * accord: this read decides what is rendered, that one is the one immediately before a
     * registration. It asks for nothing: the only request a platform can receive is one a user
     * action makes, and there is no request effect for this path to reach for.
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
     * The user's reminder action. It stores the occurrence and asks for the registration, and the
     * permission read that gates it happens in [submit] rather than here, so no entry point can be
     * the one that forgets it. A refused read leaves the occurrence stored for the next granted
     * one rather than losing it, because the reduction records it before the submission is tried.
     */
    fun onReminderAction(reminder: PendingReminder) {
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
     * The one place a registration is submitted, which is why both rules that gate one hold for a
     * retry, a resume, an action and a directly dispatched request alike: the API 33 read and the
     * "no second registration while this occurrence is unsettled" guard are here rather than at
     * each caller, so no caller can be the one that forgets either. The read is the last thing
     * before the submission, which is what makes a grant revoked since the resume stop this
     * registration rather than the next one.
     *
     * The occurrence id comes from the same factory the scheduler uses. A route it cannot resolve
     * is a precondition failure of the caller, not a state to render: whether a route is valid is
     * the scheduler's answer to give, and a schedule row's property id comes from the planner, so
     * this card does not add a second place that decides it.
     */
    private fun submit(reminder: PendingReminder) {
        if (sdkInt >= REQUIRES_PERMISSION_SDK && !readPermission()) {
            return
        }
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

/** The actions the top app bar may carry. REQ-031 caps this at two, so the enum is the cap. */
enum class ScheduleTopAppBarAction(val actionName: ScheduleActionName) {
    FILTER(ScheduleActionName.FILTER),
}

/**
 * What an action is called. The phrase is authored here rather than derived from an enum name or a
 * route, because a name derived from an identifier is the one thing context/DESIGN.md's symbol-only
 * admission refuses. This card renders the phrase as visible text, and T4-SCHEDULE-UI-SYMBOL-CHROME
 * turns the same value into an accessible name once the control becomes a glyph.
 */
enum class ScheduleActionName(val phrase: String) {
    ADD_PROPERTY("Add a property"),
    CLEAR_FILTER("Clear the inspection type filter"),
    RETRY("Retry registering this reminder"),
    OPEN_SETTINGS("Open notification settings"),
    FILTER("Filter by inspection type"),
}

/**
 * Why a state offers no primary action. A reason rather than a null, so that a state offering none
 * has said so: the same reason ScheduleBadge.NONE is a declared value. A state whose author forgot
 * to decide cannot compile, because the sealed action type below has no third shape.
 */
enum class ScheduleNoActionReason {
    NOTHING_TO_ACT_ON_YET,
    ACTIONS_BELONG_TO_ROWS,
}

/**
 * What a state declares about its one primary action. Two shapes, never a list: "this state offers
 * two primary actions" is not expressible, so REQ-030's cap is structural rather than a rule a
 * renderer has to obey.
 */
sealed interface ScheduleStateAction {
    data class One(
        val slot: ScheduleActionSlot,
        val actionName: ScheduleActionName,
    ) : ScheduleStateAction

    data class None(val reason: ScheduleNoActionReason) : ScheduleStateAction
}

/**
 * A recovery offered beside the screen rather than by it. Deliberately not a [ScheduleStateAction]:
 * that type is the one primary action a state declares, and a feedback banner's recovery is a
 * secondary action by REQ-030, so keeping them apart in the type system is what makes "two primary
 * actions at once" unconstructable rather than merely discouraged.
 */
data class ScheduleSecondaryAction(
    val slot: ScheduleActionSlot,
    val actionName: ScheduleActionName,
)

/**
 * Non-blocking feedback drawn beside the screen it leaves readable, which is what
 * context/DESIGN.md's `feedback-banner` is for. A banner always carries both halves: copy that
 * says what happened and exactly one secondary recovery, so a banner with nothing to do about it
 * is not expressible.
 */
data class ScheduleFeedbackBanner(
    val content: List<ScheduleContentValue>,
    val recovery: ScheduleSecondaryAction,
)

/**
 * How many tappable controls a state may show at once. Two shapes rather than a bare number, so
 * that [Unbounded] is an answer chosen among alternatives instead of the only thing expressible.
 */
sealed interface ScheduleControlCountPolicy {
    data object Unbounded : ScheduleControlCountPolicy

    data class AtMost(val count: Int) : ScheduleControlCountPolicy
}

/**
 * A rendered domain value. Every member carries [text], because REQ-053's guarantee is that a
 * domain value keeps its text and numerals: there is no member that carries a glyph instead, so
 * "this count was replaced by a symbol" is not a state this type can hold.
 */
sealed interface ScheduleContentValue {
    val text: String

    /** Copy that explains a state. Chrome by the scope boundary, but still text on this card. */
    data class Message(override val text: String) : ScheduleContentValue

    /** A property name, exactly as the planner supplied it. */
    data class PropertyName(override val text: String) : ScheduleContentValue

    /**
     * A due line. [text] is the absolute date and is never empty, and [relative] is added to it rather
     * than replacing it, which is REQ-036 held as a shape rather than as a rule.
     */
    data class DueLine(
        override val text: String,
        val relative: String,
    ) : ScheduleContentValue

    /** A count rendered as a complete plural-aware phrase, never a bare numeral. REQ-037. */
    data class CountPhrase(
        val count: Int,
        override val text: String,
    ) : ScheduleContentValue

    /**
     * An inspection type as the user reads it. OD-1 judged the type a domain value, so it keeps its
     * text. The phrase is authored here rather than taken from the enum constant, which
     * T4-SCHEDULE-UI REQ-023 forbids reaching a screen.
     */
    data class TypeLabel(
        val type: InspectionScheduleType,
        override val text: String,
    ) : ScheduleContentValue
}

/**
 * The presentation contract: what each state declares about its action, what it puts on screen, and
 * how a date and a count are spelled. Pure and parameterised the way ScheduleReducer is, so nothing
 * here reads a clock, a locale or a system setting. OD-8 fixed the date form, so the formatting
 * below is deliberately hand-rolled rather than delegated to a locale-sensitive formatter: a
 * formatter that consults Locale.getDefault would render 05/19/2026 on one device and 19/05/2026 on
 * another, and this app's schedule feeds a record that can end up in a tenancy dispute.
 */
object SchedulePresentation {
    /** Month names in the fixed NZ form OD-8 chose. Spelled out, so no numeric order is ambiguous. */
    private val monthNames = listOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
    )

    /** What the top app bar carries. REQ-031 caps it at two and the enum is that cap. */
    val topAppBarActions: List<ScheduleTopAppBarAction> = ScheduleTopAppBarAction.entries

    /**
     * What a screen state declares about its one primary action. The slot is read off the reducer's
     * own [actionSlot] rather than decided again here, so the two cannot drift into disagreeing
     * about which state acts. This card authors only the name.
     */
    fun actionOf(screen: ScheduleScreenState): ScheduleStateAction =
        when (val slot = screen.actionSlot) {
            null -> ScheduleStateAction.None(noActionReasonOf(screen))
            else -> ScheduleStateAction.One(slot, nameOf(slot))
        }

    /**
     * The non-blocking feedback shown beside the screen, or none while there is nothing to say.
     * Its recovery is a [ScheduleSecondaryAction] rather than a [ScheduleStateAction], which is
     * what stops a blocked permission from putting a second primary action on a screen that
     * already has one: the two are different types, so "this screen shows two primary actions" is
     * not a state an implementation can express while still type-checking. REQ-030 and REQ-048 are
     * therefore both structural here rather than rules a renderer has to keep.
     */
    fun feedbackBannerOf(state: ScheduleUiState): ScheduleFeedbackBanner? =
        state.permissionRecovery?.let { slot ->
            ScheduleFeedbackBanner(
                content = listOf(ScheduleContentValue.Message(PERMISSION_BLOCKED_MESSAGE)),
                recovery = ScheduleSecondaryAction(slot, nameOf(slot)),
            )
        }

    /**
     * What bounds the number of tappable controls one state may show at once. Unbounded is a
     * declared answer chosen from alternatives rather than a rule nobody wrote: the content screen
     * is a scrolling list, so any fixed number is false once the list is long enough, and REQ-030
     * and REQ-031 are the two constraints that actually hold.
     */
    val visibleControlPolicy: ScheduleControlCountPolicy = ScheduleControlCountPolicy.Unbounded

    /**
     * What a screen state puts on screen. Never empty, and not because a rule says so: the content
     * screen opens with its count phrase, so even a content state constructed with no rows at all
     * still says something true. That is what keeps A2 from resting on the reducer happening never
     * to build an empty one.
     *
     * The content screen's rows go through [rowContentOf], so a due occurrence's date reaches the
     * screen by the same path a test drives rather than by a renderer remembering to format one.
     * [now] and [zone] are arguments for the same reason nothing else here reads a clock.
     */
    fun contentOf(
        screen: ScheduleScreenState,
        now: Instant,
        zone: ZoneId,
    ): List<ScheduleContentValue> = when (screen) {
        is ScheduleScreenState.Loading ->
            listOf(ScheduleContentValue.Message(LOADING_MESSAGE))

        is ScheduleScreenState.Content ->
            listOf(countPhrase(screen.rows.size)) +
                screen.rows.flatMap { row -> rowContentOf(row, now, zone) }

        is ScheduleScreenState.NoContentEmpty ->
            listOf(ScheduleContentValue.Message(NO_CONTENT_MESSAGE))

        is ScheduleScreenState.FilteredEmpty ->
            listOf(ScheduleContentValue.Message(FILTERED_EMPTY_MESSAGE), typeLabel(screen.filter))

        is ScheduleScreenState.Error ->
            listOf(ScheduleContentValue.Message(ERROR_MESSAGE))
    }

    /**
     * What one row puts on screen. The due line is present exactly when the row carries a due date,
     * which is a property of the row kind rather than a rule: [ScheduleRow.dueAt] is null on every
     * kind that declares no due date, so a first-inspection row cannot acquire one here.
     */
    fun rowContentOf(
        row: ScheduleRow,
        now: Instant,
        zone: ZoneId,
    ): List<ScheduleContentValue> = listOfNotNull(
        ScheduleContentValue.PropertyName(row.propertyName),
        typeLabel(row.inspectionType),
        row.dueAt?.let { dueAt -> dueLine(dueAt, now, zone) },
    )

    /**
     * An absolute date in the fixed form OD-8 chose, for example `19 May 2026`. Every part is
     * assembled from the month table and from Int.toString, neither of which consults a locale, so
     * a device set to another language or another numeral system still renders this exact string.
     */
    fun absoluteDate(instant: Instant, zone: ZoneId): String {
        val date = instant.atZone(zone).toLocalDate()
        return "${date.dayOfMonth} ${monthNames[date.monthValue - 1]} ${date.year}"
    }

    /**
     * A due line: the absolute date always, with a relative phrase added to it. The absolute date
     * is the field the type calls [ScheduleContentValue.DueLine.text], so REQ-036's "relative only
     * in addition" is the shape of the value rather than a rule a renderer has to keep.
     */
    fun dueLine(dueAt: Instant, now: Instant, zone: ZoneId): ScheduleContentValue.DueLine {
        val days = ChronoUnit.DAYS.between(
            now.atZone(zone).toLocalDate(),
            dueAt.atZone(zone).toLocalDate(),
        )
        val relative = when {
            days == 0L -> "today"
            days > 0L -> "in ${dayCount(days)}"
            else -> "${dayCount(-days)} ago"
        }
        return ScheduleContentValue.DueLine(text = absoluteDate(dueAt, zone), relative = relative)
    }

    /** An inspection type as the user reads it, authored rather than derived. OD-1, REQ-023. */
    fun typeLabel(type: InspectionScheduleType): ScheduleContentValue.TypeLabel {
        val text = when (type) {
            InspectionScheduleType.ROUTINE -> "Routine"
            InspectionScheduleType.ANNUAL -> "Annual home check"
            InspectionScheduleType.INGOING -> "Ingoing"
            InspectionScheduleType.EXIT -> "Exit"
        }
        return ScheduleContentValue.TypeLabel(type = type, text = text)
    }

    /** A count as a complete plural-aware phrase, never a bare numeral. REQ-037. */
    fun countPhrase(count: Int): ScheduleContentValue.CountPhrase {
        val text = when (count) {
            0 -> "No inspections due"
            1 -> "1 inspection due"
            else -> "$count inspections due"
        }
        return ScheduleContentValue.CountPhrase(count = count, text = text)
    }

    /**
     * Why a state that offers none offers none. Only the two states whose [actionSlot] is null can
     * reach this, and the content screen is the one that differs: its actions are its rows. No
     * branch throws, because a throw here would be a guard no mutation could kill.
     */
    private fun noActionReasonOf(screen: ScheduleScreenState): ScheduleNoActionReason =
        when (screen) {
            is ScheduleScreenState.Content -> ScheduleNoActionReason.ACTIONS_BELONG_TO_ROWS
            else -> ScheduleNoActionReason.NOTHING_TO_ACT_ON_YET
        }

    /** What each slot is called. One authored phrase per slot, never derived from the slot name. */
    private fun nameOf(slot: ScheduleActionSlot): ScheduleActionName = when (slot) {
        ScheduleActionSlot.NEXT -> ScheduleActionName.ADD_PROPERTY
        ScheduleActionSlot.CLEAR_FILTER -> ScheduleActionName.CLEAR_FILTER
        ScheduleActionSlot.RETRY -> ScheduleActionName.RETRY
        ScheduleActionSlot.OPEN_SETTINGS -> ScheduleActionName.OPEN_SETTINGS
    }

    private fun dayCount(days: Long): String = if (days == 1L) "1 day" else "$days days"

    private const val LOADING_MESSAGE = "Reading the schedule from this device"

    private const val NO_CONTENT_MESSAGE = "No inspections are scheduled yet"

    private const val FILTERED_EMPTY_MESSAGE = "No inspections match this filter"

    private const val ERROR_MESSAGE = "That reminder did not go through"

    private const val PERMISSION_BLOCKED_MESSAGE =
        "Reminders are turned off. The schedule below still works"
}
