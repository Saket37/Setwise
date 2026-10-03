package dev.saketanand.setwise.ui.history

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration

/** Everything [HistoryScreen] draws. */
@Immutable
data class HistoryUiState(
    val isLoading: Boolean = true,
    /** The day strip, newest (today) first: back to the first workout, at least four weeks. */
    val days: List<DayUi> = emptyList(),
    /** Newest month first; each with its workouts newest first. */
    val months: List<HistoryMonthUi> = emptyList(),
    /** Day tapped in the strip: the list jumps there and outlines its workouts. */
    val selectedDate: LocalDate? = null,
) {
    val isEmpty: Boolean get() = !isLoading && months.isEmpty()
}

/** One chip of the day strip: "S 3 •" (dot = trained that day). */
@Immutable
data class DayUi(
    val date: LocalDate,
    val isToday: Boolean,
    val trained: Boolean,
    val isSelected: Boolean,
)

/** "OCTOBER 2026" and its workouts. */
@Immutable
data class HistoryMonthUi(
    val month: YearMonth,
    val workouts: List<HistoryWorkoutUi>,
)

/** "FRI 2 · Push Day · 2 PRs · 1h 9m · 8,420 kg". */
@Immutable
data class HistoryWorkoutUi(
    val id: Long,
    val name: String,
    val date: LocalDate,
    val duration: Duration,
    val volumeKg: Double,
    val distanceKm: Double,
    val calories: Int?,
    val personalRecords: Int,
)
