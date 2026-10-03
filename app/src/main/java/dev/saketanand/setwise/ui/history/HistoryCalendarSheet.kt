package dev.saketanand.setwise.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.DayState
import dev.saketanand.setwise.ui.designsystem.components.SetwiseMonthCalendar
import dev.saketanand.setwise.ui.designsystem.theme.numberSmall
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

/**
 * The strip as a month: same marks, today tinted, the selected day outlined. ‹ › go back to the
 * first workout's month. Tapping a day selects it (like the strip) and closes the sheet; days
 * outside the history (before it, or ahead) are dimmed and can't be picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryCalendarSheet(uiState: HistoryUiState, onDayClick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val firstMonth = uiState.firstMonth ?: return
    val lastMonth = uiState.lastMonth ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }
    // Opens on the selected day's month, else this month. Saved as text: YearMonth isn't Bundle-able.
    var monthText by rememberSaveable { mutableStateOf((uiState.selectedDate?.let(YearMonth::from) ?: lastMonth).toString()) }
    val month = YearMonth.parse(monthText).coerceIn(firstMonth, lastMonth)
    val days = remember(uiState.days) { uiState.days.associateBy { it.date } }
    val summary = remember(month, uiState) { monthSummary(month, uiState) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SetwiseMonthCalendar(
                month = month,
                onPreviousMonth = { monthText = month.minusMonths(1).toString() }.takeIf { month > firstMonth },
                onNextMonth = { monthText = month.plusMonths(1).toString() }.takeIf { month < lastMonth },
            ) { date ->
                CalendarDay(
                    date = date,
                    day = days[date],
                    isSelected = date == uiState.selectedDate,
                    onClick = {
                        // Slide away first, then select: the strip and list move once it's gone.
                        if (!isClosing) {
                            isClosing = true
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDayClick(date)
                                onDismiss()
                            }
                        }
                    },
                )
            }
            Text(
                // Workouts always ("0 workouts" says something); rest and missed only when there are any.
                text = listOfNotNull(
                    pluralStringResource(R.plurals.month_workouts, summary.workouts, summary.workouts),
                    summary.restDays.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.month_rest_days, it, it) },
                    summary.missedDays.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.month_missed_days, it, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** One day: the number and its mark. [day] is null outside the history: dimmed, not clickable. */
@Composable
private fun CalendarDay(date: LocalDate, day: DayUi?, isSelected: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val isToday = day?.isToday == true
    val description = dayDescription(date, day?.state ?: DayState.None)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .selectable(selected = isSelected, enabled = day != null, onClick = onClick, role = Role.Button)
            .clearAndSetSemantics {
                contentDescription = description
                selected = isSelected
                if (day == null) disabled() else this.onClick { onClick(); true }
            }
            .alpha(if (day == null) 0.38f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.numberSmall,
            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
        DayMark(day?.state ?: DayState.None)
    }
}
