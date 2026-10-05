package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/**
 * "‹ October 2026 ›", the weekday initials, then the month's days in weeks. Each day is drawn
 * by [dayContent] (it fills an equal-width column); blank cells pad the first and last week.
 *
 * @param onPreviousMonth null disables ‹ (nothing earlier to show); same for [onNextMonth].
 */
@Composable
fun SetwiseMonthCalendar(
    month: YearMonth,
    onPreviousMonth: (() -> Unit)?,
    onNextMonth: (() -> Unit)?,
    modifier: Modifier = Modifier,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    dayContent: @Composable (LocalDate) -> Unit,
) {
    val locale = currentLocale()
    val weekdays = (0L until 7L).map { firstDayOfWeek.plus(it) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    // Read out when ‹ / › changes the month.
                    .semantics { heading(); liveRegion = LiveRegionMode.Polite },
            )
            MonthArrow(R.drawable.ic_chevron_left, stringResource(R.string.previous_month), onPreviousMonth)
            MonthArrow(R.drawable.ic_chevron_right, stringResource(R.string.next_month), onNextMonth)
        }
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                )
            }
        }
        monthWeeks(month, firstDayOfWeek).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) dayContent(date)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(icon: Int, description: String, onClick: (() -> Unit)?) {
    SetwiseIconButton(
        icon = icon,
        contentDescription = description,
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        size = 44.dp,
        iconSize = 20.dp,
        colors = SetwiseIconButtonDefaults.plainColors(),
    )
}

/** The month in weeks of 7, starting on [firstDayOfWeek]; null = a blank cell outside the month. */
fun monthWeeks(month: YearMonth, firstDayOfWeek: DayOfWeek): List<List<LocalDate?>> {
    val leading = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells = List<LocalDate?>(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    return cells.chunked(7).map { week -> week + List(7 - week.size) { null } }
}

@PreviewComponents
@Composable
private fun SetwiseMonthCalendarPreview() = SetwisePreview {
    SetwiseMonthCalendar(month = YearMonth.of(2026, 10), onPreviousMonth = {}, onNextMonth = null) { date ->
        Text(date.dayOfMonth.toString(), modifier = Modifier.padding(vertical = 10.dp))
    }
}
