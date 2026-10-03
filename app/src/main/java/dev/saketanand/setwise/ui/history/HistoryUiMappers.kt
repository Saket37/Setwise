package dev.saketanand.setwise.ui.history

import dev.saketanand.setwise.domain.model.WorkoutHistoryItem
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Domain → UI for History. Plain functions, unit-tested without Android. */

/** The day strip always reaches at least this far back, so it fills the row. */
const val MIN_STRIP_DAYS = 28

fun historyUi(
    history: List<WorkoutHistoryItem>,
    today: LocalDate,
    zone: ZoneId,
    selectedDate: LocalDate? = null,
): HistoryUiState {
    val workouts = history.map { it.toUi(zone) }
    val trainedDays = workouts.mapTo(HashSet()) { it.date }
    val firstDay = minOf(workouts.minOfOrNull { it.date } ?: today, today.minusDays(MIN_STRIP_DAYS - 1L))
    return HistoryUiState(
        isLoading = false,
        days = generateSequence(today) { it.minusDays(1) }
            .takeWhile { !it.isBefore(firstDay) }
            .map { day -> DayUi(date = day, isToday = day == today, trained = day in trainedDays, isSelected = day == selectedDate) }
            .toList(),
        // The list is newest first, so grouping keeps months (and workouts in them) newest first.
        months = workouts.groupBy { YearMonth.from(it.date) }.map { (month, inMonth) -> HistoryMonthUi(month, inMonth) },
        selectedDate = selectedDate,
    )
}

/**
 * Where the list should jump for [date]: the index (month headers included) of its first workout,
 * or, on a rest day, of the nearest earlier workout. Null if there's none on or before [date].
 */
fun listIndexFor(date: LocalDate, months: List<HistoryMonthUi>): Int? {
    var index = 0
    months.forEach { month ->
        index++ // the month header
        month.workouts.forEach { workout ->
            if (!workout.date.isAfter(date)) return index
            index++
        }
    }
    return null
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
