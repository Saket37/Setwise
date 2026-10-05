package dev.saketanand.setwise.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.DayStatus
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseDayChoices
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import dev.saketanand.setwise.util.toShortDayLabel
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * "Did you train?": past days without a workout, each with Log workout / Rest / Missed, then
 * "Mark all as rest" and "Not now" (or "Done" once something's answered).
 * Actions: OnCheckInMark, OnCheckInMarkAllRest, OnCheckInLogWorkout, OnCheckInDismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayCheckInSheet(checkIn: CheckInUi, onAction: (HomeAction) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }

    // Slide the sheet away first (like the start sheet), then act.
    val closeThen: (HomeAction) -> Unit = { action ->
        if (!isClosing) {
            isClosing = true
            scope.launch { sheetState.hide() }.invokeOnCompletion { onAction(action) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { onAction(HomeAction.OnCheckInDismiss) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        DayCheckInContent(checkIn = checkIn, onAction = onAction, closeThen = closeThen)
    }
}

@Composable
private fun DayCheckInContent(checkIn: CheckInUi, onAction: (HomeAction) -> Unit, closeThen: (HomeAction) -> Unit) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.check_in_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.check_in_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        checkIn.days.forEach { day ->
            CheckInDayRow(day = day, onAction = onAction, closeThen = closeThen)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (checkIn.days.any { it.status == null }) {
                SetwiseButton(
                    text = stringResource(R.string.mark_all_as_rest),
                    onClick = { closeThen(HomeAction.OnCheckInMarkAllRest) },
                    style = SetwiseButtonStyle.Outlined,
                    size = SetwiseButtonSize.Medium,
                    modifier = Modifier.weight(1f),
                )
            }
            SetwiseButton(
                text = stringResource(if (checkIn.isAnyAnswered) R.string.done else R.string.not_now),
                onClick = { closeThen(HomeAction.OnCheckInDismiss) },
                style = if (checkIn.isAnyAnswered) SetwiseButtonStyle.Filled else SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.check_in_settings_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Fri, 2 Oct" and its choices. */
@Composable
private fun CheckInDayRow(day: CheckInDayUi, onAction: (HomeAction) -> Unit, closeThen: (HomeAction) -> Unit) {
    val isRest = day.status == DayStatus.Rest
    val isMissed = day.status == DayStatus.Missed
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = day.date.toShortDayLabel(currentLocale()),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SetwiseDayChoices(
            isRest = isRest,
            isMissed = isMissed,
            onLogWorkout = { closeThen(HomeAction.OnCheckInLogWorkout(day.date)) },
            onRestClick = { onAction(HomeAction.OnCheckInMark(day.date, if (isRest) null else DayStatus.Rest)) },
            onMissedClick = { onAction(HomeAction.OnCheckInMark(day.date, if (isMissed) null else DayStatus.Missed)) },
        )
    }
}

@PreviewComponents
@Composable
private fun DayCheckInContentPreview() = SetwisePreview {
    DayCheckInContent(
        checkIn = CheckInUi(
            listOf(
                CheckInDayUi(LocalDate.of(2026, 10, 2), status = null),
                CheckInDayUi(LocalDate.of(2026, 9, 30), status = DayStatus.Rest),
            ),
        ),
        onAction = {},
        closeThen = {},
    )
}
