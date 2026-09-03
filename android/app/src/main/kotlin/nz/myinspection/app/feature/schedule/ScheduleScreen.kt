package nz.myinspection.app.feature.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The Compose wiring for the schedule route. This card proves only that the typed state and event
 * contracts compile against a real Compose tree: no runtime, pixel or semantics-tree behaviour is
 * claimed, and the reducer assertions live in ScheduleUiTest instead.
 *
 * Three things are deliberately absent. Copy and date formatting are owned by
 * T4-SCHEDULE-UI-PRESENTATION, so the labels below are placeholders that card replaces. The error
 * branch is rendered without an action, because retry is behaviour this card forbids and
 * T4-SCHEDULE-UI-REMINDER-ACTIONS owns. And no occurrence id, enum constant name or ISO instant is
 * drawn: context/DESIGN.md forbids showing database enums and ISO timestamps, so the row kind
 * chooses a branch here rather than supplying a string. The inspection type does appear in the
 * LazyColumn item key below, which is list identity rather than rendered text and reaches no
 * screen.
 */
@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onEvent: (ScheduleEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when (val screen = state.screen) {
            is ScheduleScreenState.Loading -> Text(text = "Loading the schedule")
            is ScheduleScreenState.NoContentEmpty -> Text(text = "Nothing is due")
            is ScheduleScreenState.FilteredEmpty -> Text(text = "Nothing matches this filter")
            is ScheduleScreenState.Error -> Text(text = "That did not go through")
            is ScheduleScreenState.Content -> ScheduleRows(rows = screen.rows, onEvent = onEvent)
        }
    }
}

@Composable
private fun ScheduleRows(rows: List<ScheduleRow>, onEvent: (ScheduleEvent) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items = rows, key = { row -> row.propertyId + row.inspectionType.name }) { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onEvent(ScheduleEvent.RowActivated(row.propertyId, row.inspectionType))
                    },
            ) {
                Text(text = row.propertyName)
            }
        }
    }
}
