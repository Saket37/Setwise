package dev.saketanand.setwise.ui.history

import dev.saketanand.setwise.domain.model.WorkoutHistoryItem
import dev.saketanand.setwise.util.weekDays
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Domain → UI for History. Plain functions, unit-tested without Android. */

fun historyUi(history: List<WorkoutHistoryItem>, today: LocalDate, zone: ZoneId): HistoryUiState {
    val workouts = history.map { it.toUi(zone) }
    val trainedDays = workouts.mapTo(HashSet()) { it.date }
    return HistoryUiState(
        isLoading = false,
        week = weekDays(today).map { day -> WeekDayUi(date = day, isToday = day == today, trained = day in trainedDays) },
        // The list is newest first, so grouping keeps months (and workouts in them) newest first.
        months = workouts.groupBy { YearMonth.from(it.date) }.map { (month, inMonth) -> HistoryMonthUi(month, inMonth) },
    )
}

private fun WorkoutHistoryItem.toUi(zone: ZoneId) = HistoryWorkoutUi(
    id = id,
    name = name,
    date = startedAt.atZone(zone).toLocalDate(),
    duration = duration,
    volumeKg = volumeKg,
    distanceKm = distanceKm,
    calories = calories,
    personalRecords = personalRecords,
)
