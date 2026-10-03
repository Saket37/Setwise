package dev.saketanand.setwise.ui.history

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import dev.saketanand.setwise.domain.model.DayState
import dev.saketanand.setwise.domain.model.DayStatus
import dev.saketanand.setwise.ui.ObserveAsEvents
import dev.saketanand.setwise.ui.designsystem.components.SetwiseDayChoices
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import dev.saketanand.setwise.ui.designsystem.components.IconTile
import dev.saketanand.setwise.ui.designsystem.components.SetwiseIconButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseIconButtonDefaults
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCardDefaults
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCardOutline
import dev.saketanand.setwise.util.toShortDayLabel
import java.time.LocalDate
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
import dev.saketanand.setwise.ui.currentLocale
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
 * @param onWorkoutStarted Opens a workout just started for a past day ("Log workout").
 */
@Composable
fun HistoryScreenRoot(
    onOpenWorkout: (workoutId: Long) -> Unit,
    onWorkoutStarted: (workoutId: Long) -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is HistoryEvent.WorkoutStarted -> onWorkoutStarted(event.workoutId)
            // TODO: replace with a snackbar once the screen has a SnackbarHost.
            HistoryEvent.WorkoutAlreadyRunning ->
                Toast.makeText(context, R.string.workout_already_running, Toast.LENGTH_LONG).show()
            HistoryEvent.SaveFailed -> Toast.makeText(context, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
    }
    HistoryScreen(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                is HistoryAction.OnWorkoutClick -> onOpenWorkout(action.workoutId)
                else -> viewModel.onAction(action)
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
            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.history),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .semantics { heading() },
            )
            if (uiState.days.isNotEmpty()) {
                DayStrip(days = uiState.days, onDayClick = { onAction(HistoryAction.OnDayClick(it)) })
            }
            val selected = uiState.selectedDate
            if (selected != null && uiState.isSelectedDayEmpty) {
                EmptyDayCard(
                    date = selected,
                    state = uiState.selectedDayState,
                    canCheckIn = uiState.canCheckInSelectedDay,
                    onAction = onAction,
                )
            }
        }
        when {
            uiState.isLoading -> Unit
            uiState.isEmpty -> SetwiseEmptyState(
                title = stringResource(R.string.no_workouts_yet),
                message = stringResource(R.string.no_workouts_yet_message),
                icon = R.drawable.ic_nav_history,
            )
            else -> WorkoutList(uiState = uiState, onAction = onAction)
        }
    }
}

/** Finished workouts by month. Jumps to the selected day and outlines its workouts. */
@Composable
private fun WorkoutList(uiState: HistoryUiState, onAction: (HistoryAction) -> Unit) {
    val listState = rememberLazyListState()
    // A day tapped in the strip → its workouts (or the nearest earlier ones) at the top.
    LaunchedEffect(uiState.selectedDate) {
        val date = uiState.selectedDate ?: return@LaunchedEffect
        val index = listIndexOf(date, uiState.months) ?: return@LaunchedEffect // empty day: card instead
        // One item back: keep the month header (or the row above) in view for context.
        listState.animateScrollToItem((index - 1).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        uiState.months.forEach { month ->
            item(key = "month-${month.month}", contentType = "month") {
                val label = month.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", currentLocale()))
                SectionLabel(label, Modifier.padding(top = 8.dp, bottom = 2.dp))
            }
            items(month.workouts, key = { "workout-${it.id}" }, contentType = { "workout" }) { workout ->
                HistoryWorkoutRow(
                    workout = workout,
                    isHighlighted = workout.date == uiState.selectedDate,
                    onClick = { onAction(HistoryAction.OnWorkoutClick(workout.id)) },
                )
            }
        }
    }
}

/**
 * Day chips from today (right edge) back to the first workout; scroll left for the past.
 * Today is tinted, the selected day outlined, trained days dotted. The 1st of a month shows the
 * month ("OCT") instead of the weekday, for orientation while scrolling.
 */
@Composable
private fun DayStrip(days: List<DayUi>, onDayClick: (LocalDate) -> Unit) {
    LazyRow(
        // Newest first + reverse layout: opens at today, on the right.
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(days, key = { it.date.toEpochDay() }, contentType = { "day" }) { day ->
            DayChip(day = day, onClick = { onDayClick(day.date) })
        }
    }
}

@Composable
private fun DayChip(day: DayUi, onClick: () -> Unit) {
    val locale = currentLocale()
    val dayName = day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    val description = when (day.state) {
        DayState.Trained -> stringResource(R.string.a11y_week_day_trained, dayName)
        DayState.Rest -> stringResource(R.string.a11y_day_rest, dayName)
        DayState.Missed -> stringResource(R.string.a11y_day_missed, dayName)
        DayState.Unanswered, DayState.None -> dayName
    }
    val shape = MaterialTheme.shapes.large
    val textColor = if (day.isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .width(DayChipWidth)
            .height(64.dp)
            .clip(shape)
            .background(if (day.isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .border(2.dp, if (day.isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .selectable(selected = day.isSelected, onClick = onClick, role = Role.Tab)
            // One clear announcement per chip instead of "S", "3".
            .clearAndSetSemantics {
                contentDescription = description
                selected = day.isSelected
                this.onClick { onClick(); true }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = if (day.date.dayOfMonth == 1) {
                day.date.month.getDisplayName(TextStyle.SHORT, locale).uppercase(locale)
            } else {
                day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (day.isToday) textColor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.numberMedium, color = textColor)
        DayMark(day.state)
    }
}

/** Under the date: • trained, ✕ missed, moon = rest; nothing otherwise (same height either way). */
@Composable
private fun DayMark(state: DayState) {
    val size = Modifier.size(8.dp)
    when (state) {
        DayState.Trained -> Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        DayState.Missed -> Icon(painterResource(R.drawable.ic_close), null, size, tint = MaterialTheme.colorScheme.error)
        DayState.Rest -> Icon(painterResource(R.drawable.ic_moon), null, size, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        DayState.Unanswered, DayState.None -> Box(size)
    }
}

/**
 * "Wed, 23 Sep · No workout logged  ✕": the selected day has nothing to jump to. A past day also
 * gets "+ Log workout [Rest] [Missed]" (tapping the selected one clears it).
 */
@Composable
private fun EmptyDayCard(date: LocalDate, state: DayState, canCheckIn: Boolean, onAction: (HistoryAction) -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .clip(MaterialTheme.shapes.large)
            .background(SetwiseListCardDefaults.raisedColors().containerColor),
    ) {
        SetwiseListCard(
            headlineContent = { Text(date.toShortDayLabel(currentLocale())) },
            supportingContent = {
                Text(
                    stringResource(
                        when (state) {
                            DayState.Rest -> R.string.rest_day
                            DayState.Missed -> R.string.missed_day
                            else -> R.string.no_workout_logged
                        },
                    ),
                )
            },
            leadingContent = {
                IconTile(
                    icon = when (state) {
                        DayState.Rest -> R.drawable.ic_moon
                        DayState.Missed -> R.drawable.ic_close
                        else -> R.drawable.ic_nav_history
                    },
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                SetwiseIconButton(
                    icon = R.drawable.ic_close,
                    contentDescription = stringResource(R.string.clear_selected_day),
                    onClick = { onAction(HistoryAction.OnDayClick(date)) },
                    size = 40.dp,
                    colors = SetwiseIconButtonDefaults.plainColors(MaterialTheme.colorScheme.onSurfaceVariant),
                )
            },
            colors = SetwiseListCardDefaults.transparentColors(),
            contentPadding = PaddingValues(start = 14.dp, top = 10.dp, end = 6.dp, bottom = if (canCheckIn) 4.dp else 10.dp),
        )
        if (canCheckIn) {
            val isRest = state == DayState.Rest
            val isMissed = state == DayState.Missed
            SetwiseDayChoices(
                isRest = isRest,
                isMissed = isMissed,
                onLogWorkout = { onAction(HistoryAction.OnLogWorkoutClick(date)) },
                onRestClick = { onAction(HistoryAction.OnMarkDay(date, if (isRest) null else DayStatus.Rest)) },
                onMissedClick = { onAction(HistoryAction.OnMarkDay(date, if (isMissed) null else DayStatus.Missed)) },
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            )
        }
    }
}

/** 7 chips fill a phone's width (like the design's week); more scroll in. */
private val DayChipWidth = 46.dp

/** "FRI | 2  Push Day [2 PRs] · 1h 9m · 8,420 kg". */
@Composable
private fun HistoryWorkoutRow(workout: HistoryWorkoutUi, isHighlighted: Boolean, onClick: () -> Unit) {
    val locale = currentLocale()
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
        outline = if (isHighlighted) SetwiseListCardOutline.Solid(MaterialTheme.colorScheme.primary) else SetwiseListCardOutline.None,
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
