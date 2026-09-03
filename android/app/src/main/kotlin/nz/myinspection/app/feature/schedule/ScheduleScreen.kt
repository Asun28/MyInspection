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

/**
 * The Compose wiring for the schedule route. This card proves only that the typed state and event
 * contracts compile against a real Compose tree: no runtime, pixel or semantics-tree behaviour is
 * claimed, and every behavioural assertion lives in ScheduleUiTest against the reducer and the
 * presenter instead.
 *
 * Two things are deliberately absent. Copy and date formatting are owned by
 * T4-SCHEDULE-UI-PRESENTATION, so the labels below are placeholders that card replaces. And no
 * enum constant name, occurrence id or ISO instant is ever drawn: REQ-023 forbids the first two
 * and context/DESIGN.md forbids the third, so the row kind and the recovery kind choose a branch
 * here rather than supplying a string.
 */
@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onEvent: (ScheduleEvent) -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when (val screen = state.screen) {
            is ScheduleScreenState.Loading -> Text(text = "Loading the schedule")
            is ScheduleScreenState.NoContentEmpty -> Text(text = "Nothing is due")
            is ScheduleScreenState.FilteredEmpty -> Text(text = "Nothing matches this filter")
            is ScheduleScreenState.Error -> Button(onClick = onRetry) {
                Text(text = "Try again")
            }
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
