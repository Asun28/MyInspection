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
 * The recovery a state offers when it has one. Only [ScheduleScreenState.Error] carries a recovery
 * today, and it carries a single value rather than a menu, so a state cannot offer a choice of two.
 */
enum class ScheduleRecovery {
    RETRY,
}

/**
 * The mutually exclusive level. Exactly one of these is rendered at a time. What each branch
 * carries differs by design: [Content] carries rows, [FilteredEmpty] carries the filter that
 * emptied it, [Error] carries its recovery, and [Loading] and [NoContentEmpty] carry neither
 * because there is nothing yet to carry. The action a branch offers is not held here at all, it is
 * derived by [actionSlot].
 *
 * [Error] is declared here but never produced by this card: the only transition into it is the
 * PERMANENT_FAILURE branch of a registration, which belongs to T4-SCHEDULE-UI-REMINDER-ACTIONS.
 * Its action-slot arity is asserted here, the path into it is not, because that path does not yet
 * exist and asserting it would be vacuous.
 */
sealed interface ScheduleScreenState {
    data object Loading : ScheduleScreenState

    data class Content(val rows: List<ScheduleRow>) : ScheduleScreenState

    data object NoContentEmpty : ScheduleScreenState

    data class FilteredEmpty(val filter: InspectionScheduleType) : ScheduleScreenState

    data class Error(val recovery: ScheduleRecovery) : ScheduleScreenState
}

/**
 * The action a screen state offers. Single-valued by construction rather than by rule: [actionSlot]
 * returns one of these or none, never a collection, so "this state offers two actions" is not a
 * thing an implementation can express while still type-checking. Which target each slot points at,
 * and what it is called, belong to T4-SCHEDULE-UI-PRESENTATION.
 *
 * [ScheduleScreenState.Loading] and [ScheduleScreenState.Content] deliberately offer none. Loading
 * is a 300ms-threshold state over a local disk read with nothing to act on yet, and a content
 * screen's actions belong to its rows.
 */
enum class ScheduleActionSlot {
    NEXT,
    CLEAR_FILTER,
    RETRY,
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
 * Everything the schedule renders from. [navigating] is the unsettled-route marker that the
 * duplicate-suppression rule is stated against: a second activation is refused by consulting the
 * marker, not by hoping the caller checks.
 */
data class ScheduleUiState(
    val occurrences: List<ScheduleOccurrence>,
    val screen: ScheduleScreenState,
    val filter: InspectionScheduleType?,
    val scrollIndex: Int,
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
}

/** What the host is asked to do. Recorded rather than performed, so a reducer stays testable. */
sealed interface ScheduleEffect {
    data class Navigate(val route: ScheduleRoute) : ScheduleEffect
}

/** One reduction: the state that follows, and everything the host should do about it. */
data class ScheduleTransition(
    val state: ScheduleUiState,
    val effects: List<ScheduleEffect>,
)

/**
 * Projects occurrences and events onto states. Pure: every dependency arrives as an argument, so
 * permission timing and registration, which need a port and a clock, stay out of here and live in
 * T4-SCHEDULE-UI-REMINDER-ACTIONS instead.
 */
object ScheduleReducer {
    /** The state before anything has been read. Loading, not empty: nothing has been looked at. */
    fun initial(): ScheduleUiState = ScheduleUiState(
        occurrences = emptyList(),
        screen = ScheduleScreenState.Loading,
        filter = null,
        scrollIndex = 0,
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
}
