package nz.myinspection.app.feature.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.ZoneId

/**
 * The Compose wiring for the schedule route. This card proves only that the typed presentation
 * contract compiles against a real Compose tree: no runtime, pixel or semantics-tree behaviour is
 * claimed, and every behavioural assertion lives in ScheduleUiTest against SchedulePresentation,
 * the reducer and the presenter instead.
 *
 * Nothing here decides what a state says or which action it offers. Both are read from
 * SchedulePresentation, so the copy a test asserts is the copy that renders, and a state whose
 * content someone forgot is not a blank screen but a compile error over there.
 *
 * The content screen is drawn row by row rather than from the flat list contentOf returns, because
 * each row owns a click target. Those are not two authorities: the row projection is what contentOf
 * itself composes, and a test asserts the reconstruction equals it element for element, so the two
 * cannot drift into disagreeing about what a screen shows.
 *
 * Chrome still carries visible text here, and this file applies no token values. The token
 * vocabularies themselves now live with their binding in the successor card, so neither card
 * declares a token the other is supposed to draw. Both belong to
 * T4-SCHEDULE-UI-SYMBOL-CHROME: it replaces these labels with glyphs, turns the same
 * ScheduleActionName into an accessible name, and binds the token vocabularies this card declares
 * to real spacing, type, shape and colour. Declaring and drawing were split between the two cards
 * so that neither declares something it does not then apply.
 */
@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    now: Instant,
    zone: ZoneId,
    onEvent: (ScheduleEvent) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SchedulePresentation.feedbackBannerOf(state)?.let { banner ->
            FeedbackBanner(banner, onEvent, onRetry, onNext, onOpenSettings)
        }
        when (val screen = state.screen) {
            is ScheduleScreenState.Content -> {
                ContentText(SchedulePresentation.countPhrase(screen.rows.size))
                ScheduleRows(rows = screen.rows, now = now, zone = zone, onEvent = onEvent)
            }

            else -> SchedulePresentation.contentOf(screen, now, zone).forEach { value ->
                ContentText(value)
            }
        }
        when (val action = SchedulePresentation.actionOf(state.screen)) {
            is ScheduleStateAction.One ->
                ActionButton(action, onEvent, onRetry, onNext, onOpenSettings)

            is ScheduleStateAction.None -> Unit
        }
    }
}

/**
 * One content value. A due line draws both of its fields: the absolute date is the value's own
 * text, and the relative phrase follows it rather than replacing it, which is REQ-036 as drawn
 * rather than only as declared.
 */
@Composable
private fun ContentText(value: ScheduleContentValue) {
    Text(text = value.text)
    if (value is ScheduleContentValue.DueLine) {
        Text(text = value.relative)
    }
}

/**
 * The feedback banner: its copy and its one secondary recovery, drawn as one region so the recovery
 * sits inside the banner rather than loose beside the screen's own primary action (REQ-048).
 */
@Composable
private fun FeedbackBanner(
    banner: ScheduleFeedbackBanner,
    onEvent: (ScheduleEvent) -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        banner.content.forEach { value -> ContentText(value) }
        Button(
            onClick = { perform(banner.recovery.slot, onEvent, onRetry, onNext, onOpenSettings) },
        ) {
            Text(text = banner.recovery.actionName.phrase)
        }
    }
}

/** Routes one action slot to the callback that performs it. Shared so the two call sites agree. */
private fun perform(
    slot: ScheduleActionSlot,
    onEvent: (ScheduleEvent) -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (slot) {
        ScheduleActionSlot.NEXT -> onNext()
        ScheduleActionSlot.CLEAR_FILTER -> onEvent(ScheduleEvent.FilterSelected(null))
        ScheduleActionSlot.RETRY -> onRetry()
        ScheduleActionSlot.OPEN_SETTINGS -> onOpenSettings()
    }
}

@Composable
private fun ActionButton(
    action: ScheduleStateAction.One,
    onEvent: (ScheduleEvent) -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Button(onClick = { perform(action.slot, onEvent, onRetry, onNext, onOpenSettings) }) {
        Text(text = action.actionName.phrase)
    }
}

@Composable
private fun ScheduleRows(
    rows: List<ScheduleRow>,
    now: Instant,
    zone: ZoneId,
    onEvent: (ScheduleEvent) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items = rows, key = { row -> row.propertyId + row.inspectionType.name }) { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onEvent(ScheduleEvent.RowActivated(row.propertyId, row.inspectionType))
                    },
            ) {
                SchedulePresentation.rowContentOf(row, now, zone).forEach { value ->
                    ContentText(value)
                }
            }
        }
    }
}
