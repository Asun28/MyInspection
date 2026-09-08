package nz.myinspection.app.feature.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
 * The four token vocabularies are bound to Compose values below rather than being declared and left
 * unused. Spacing, typography and shape resolve to the numbers context/DESIGN.md gives them.
 * Colour resolves through MaterialTheme.colorScheme rather than to literals, so this file hardcodes
 * no scheme and the light and dark pair stays the theme's to answer, which is what leaves REQ-044
 * to T4-SCHEDULE-UI-SYMBOL-CHROME rather than half-answering it here.
 *
 * Chrome still carries visible text on this card. T4-SCHEDULE-UI-SYMBOL-CHROME replaces those
 * labels with glyphs and turns the same ScheduleActionName into an accessible name.
 */
@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onEvent: (ScheduleEvent) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(ScheduleSpacingToken.SCREEN_GUTTER.dp())) {
        SchedulePresentation.permissionContentOf(state).forEach { value ->
            ContentText(value = value, token = ScheduleTypographyToken.BODY_MD)
        }
        SchedulePresentation.permissionActionOf(state)?.let { action ->
            ActionButton(
                action = action,
                onEvent = onEvent,
                onRetry = onRetry,
                onNext = onNext,
                onOpenSettings = onOpenSettings,
            )
        }
        val screen = state.screen
        SchedulePresentation.contentOf(screen).forEach { value ->
            ContentText(value = value, token = typographyOf(value))
        }
        when (screen) {
            is ScheduleScreenState.Content -> ScheduleRows(rows = screen.rows, onEvent = onEvent)
            else -> Unit
        }
        when (val action = SchedulePresentation.actionOf(screen)) {
            is ScheduleStateAction.One -> ActionButton(
                action = action,
                onEvent = onEvent,
                onRetry = onRetry,
                onNext = onNext,
                onOpenSettings = onOpenSettings,
            )

            is ScheduleStateAction.None -> Unit
        }
    }
}

/** Which typography role a content value takes. A count opens its state, so it leads. */
private fun typographyOf(value: ScheduleContentValue): ScheduleTypographyToken = when (value) {
    is ScheduleContentValue.CountPhrase -> ScheduleTypographyToken.TITLE_LG
    is ScheduleContentValue.PropertyName -> ScheduleTypographyToken.TITLE_MD
    is ScheduleContentValue.DueLine -> ScheduleTypographyToken.BODY_SM
    is ScheduleContentValue.TypeLabel -> ScheduleTypographyToken.LABEL_MD
    is ScheduleContentValue.Message -> ScheduleTypographyToken.BODY_MD
}

@Composable
private fun ContentText(value: ScheduleContentValue, token: ScheduleTypographyToken) {
    Text(
        text = value.text,
        style = token.textStyle(),
        color = ScheduleColorRole.ON_SURFACE.color(),
        modifier = Modifier.padding(bottom = ScheduleSpacingToken.SM.dp()),
    )
}

@Composable
private fun ActionButton(
    action: ScheduleStateAction.One,
    onEvent: (ScheduleEvent) -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Button(
        onClick = {
            when (action.slot) {
                ScheduleActionSlot.NEXT -> onNext()
                ScheduleActionSlot.CLEAR_FILTER -> onEvent(ScheduleEvent.FilterSelected(null))
                ScheduleActionSlot.RETRY -> onRetry()
                ScheduleActionSlot.OPEN_SETTINGS -> onOpenSettings()
            }
        },
        shape = ScheduleShapeToken.FULL.shape(),
        modifier = Modifier.padding(top = ScheduleSpacingToken.MD.dp()),
    ) {
        Text(text = action.actionName.phrase, style = ScheduleTypographyToken.LABEL_MD.textStyle())
    }
}

@Composable
private fun ScheduleRows(rows: List<ScheduleRow>, onEvent: (ScheduleEvent) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items = rows, key = { row -> row.propertyId + row.inspectionType.name }) { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = ScheduleSpacingToken.SM.dp())
                    .clickable {
                        onEvent(ScheduleEvent.RowActivated(row.propertyId, row.inspectionType))
                    },
            ) {
                ContentText(
                    value = ScheduleContentValue.PropertyName(row.propertyName),
                    token = ScheduleTypographyToken.TITLE_MD,
                )
                ContentText(
                    value = SchedulePresentation.typeLabel(row.inspectionType),
                    token = ScheduleTypographyToken.LABEL_MD,
                )
            }
        }
    }
}

/** The spacing step this token names, in the units context/DESIGN.md gives it. */
private fun ScheduleSpacingToken.dp(): Dp = when (this) {
    ScheduleSpacingToken.XS -> 4.dp
    ScheduleSpacingToken.SM -> 8.dp
    ScheduleSpacingToken.MD -> 12.dp
    ScheduleSpacingToken.LG -> 16.dp
    ScheduleSpacingToken.XL -> 24.dp
    ScheduleSpacingToken.XXL -> 32.dp
    ScheduleSpacingToken.XXXL -> 40.dp
    ScheduleSpacingToken.TOUCH -> 48.dp
    ScheduleSpacingToken.ACTION -> 56.dp
    ScheduleSpacingToken.SCREEN_GUTTER -> 16.dp
}

/** The type role this token names, in the sizes context/DESIGN.md gives it. */
private fun ScheduleTypographyToken.textStyle(): TextStyle = when (this) {
    ScheduleTypographyToken.TITLE_LG ->
        TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)

    ScheduleTypographyToken.TITLE_MD ->
        TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)

    ScheduleTypographyToken.BODY_MD ->
        TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)

    ScheduleTypographyToken.BODY_SM ->
        TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)

    ScheduleTypographyToken.LABEL_MD ->
        TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
}

/** The corner radius this token names. */
private fun ScheduleShapeToken.shape(): Shape = when (this) {
    ScheduleShapeToken.SM -> RoundedCornerShape(4.dp)
    ScheduleShapeToken.MD -> RoundedCornerShape(8.dp)
    ScheduleShapeToken.FULL -> RoundedCornerShape(percent = 50)
}

/**
 * The colour this role names, taken from the active scheme rather than from a literal, so light and
 * dark stay the theme's answer and this file carries no hardcoded palette.
 */
@Composable
private fun ScheduleColorRole.color(): Color = when (this) {
    ScheduleColorRole.PRIMARY -> MaterialTheme.colorScheme.primary
    ScheduleColorRole.SURFACE -> MaterialTheme.colorScheme.surface
    ScheduleColorRole.ON_SURFACE -> MaterialTheme.colorScheme.onSurface
    ScheduleColorRole.ON_SURFACE_VARIANT -> MaterialTheme.colorScheme.onSurfaceVariant
    ScheduleColorRole.TERTIARY -> MaterialTheme.colorScheme.tertiary
    ScheduleColorRole.ERROR -> MaterialTheme.colorScheme.error
}
