package dev.saketanand.setwise.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.components.SectionLabel
import dev.saketanand.setwise.ui.designsystem.components.SetwiseEmptyState
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCard
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTag
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTagDefaults
import dev.saketanand.setwise.ui.designsystem.theme.numberLarge
import dev.saketanand.setwise.ui.designsystem.theme.numberMedium
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.toShortDurationLabel
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToLong
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.History].
 * @param onOpenWorkout Opens the workout summary.
 */
@Composable
fun HistoryScreenRoot(
    onOpenWorkout: (workoutId: Long) -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                is HistoryAction.OnWorkoutClick -> onOpenWorkout(action.workoutId)
            }
        },
    )
}

/** Design "History": title, this week's strip, then finished workouts by month. */
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onAction: (HistoryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.history),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            if (uiState.week.isNotEmpty()) WeekStrip(uiState.week)
        }
        when {
            uiState.isLoading -> Unit
            uiState.isEmpty -> SetwiseEmptyState(
                title = stringResource(R.string.no_workouts_yet),
                message = stringResource(R.string.no_workouts_yet_message),
                icon = R.drawable.ic_nav_history,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.months.forEach { month ->
                    item(key = "month-${month.month}", contentType = "month") {
                        val label = month.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
                        SectionLabel(label, Modifier.padding(top = 8.dp, bottom = 2.dp))
                    }
                    items(month.workouts, key = { "workout-${it.id}" }, contentType = { "workout" }) { workout ->
                        HistoryWorkoutRow(workout, onClick = { onAction(HistoryAction.OnWorkoutClick(workout.id)) })
                    }
                }
            }
        }
    }
}

/** Monday to Sunday: today highlighted, a dot on days with a finished workout. */
@Composable
private fun WeekStrip(week: List<WeekDayUi>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        week.forEach { day ->
            val locale = Locale.getDefault()
            val dayName = day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
            val description = if (day.trained) stringResource(R.string.a11y_week_day_trained, dayName) else dayName
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .background(
                        if (day.isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        MaterialTheme.shapes.large,
                    )
                    .clearAndSetSemantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                val textColor = if (day.isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.isToday) textColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.numberMedium, color = textColor)
                Box(
                    Modifier
                        .size(6.dp)
                        .background(if (day.trained) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                )
            }
        }
    }
}

/** "FRI | 2  Push Day [2 PRs] · 1h 9m · 8,420 kg". */
@Composable
private fun HistoryWorkoutRow(workout: HistoryWorkoutUi, onClick: () -> Unit) {
    val locale = Locale.getDefault()
    SetwiseListCard(
        onClick = onClick,
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = workout.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(workout.date.dayOfMonth.toString(), style = MaterialTheme.typography.numberLarge)
                }
                Box(
                    Modifier
                        .padding(start = 0.dp)
                        .width(1.dp)
                        .height(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(workout.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (workout.personalRecords > 0) {
                    SetwiseTag(
                        text = pluralStringResource(R.plurals.pr_count, workout.personalRecords, workout.personalRecords),
                        style = SetwiseTagDefaults.pr().copy(
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                            textStyle = MaterialTheme.typography.labelSmall,
                        ),
                    )
                }
            }
        },
        supportingContent = { Text(workoutMeta(workout, locale), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        horizontalSpacing = 14.dp,
        textSpacing = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "1h 9m · 8,420 kg · 412 kcal": volume, else cardio distance; calories once estimated. */
@Composable
private fun workoutMeta(workout: HistoryWorkoutUi, locale: Locale): String {
    val parts = mutableListOf(workout.duration.toShortDurationLabel())
    when {
        workout.volumeKg > 0 ->
            parts += "${NumberFormat.getIntegerInstance(locale).format(workout.volumeKg.roundToLong())} ${stringResource(R.string.unit_kg)}"
        workout.distanceKm > 0 ->
            parts += "${NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(workout.distanceKm)} ${stringResource(R.string.unit_km)}"
    }
    workout.calories?.let { parts += "$it ${stringResource(R.string.unit_kcal)}" }
    return parts.joinToString(" · ")
}
