package dev.saketanand.setwise.ui.history

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.model.DayState
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration
import dev.saketanand.setwise.domain.ai.HistoryReply

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
    val today: LocalDate? = null,
    /** "Ask about your training": the question asked and its reply. */
    val ask: AskUi = AskUi(),
) {
    val isEmpty: Boolean get() = !isLoading && months.isEmpty()

    /** A day is selected but nothing was logged on it: shown as a card, the list doesn't move. */
    val isSelectedDayEmpty: Boolean
        get() = selectedDate != null && months.none { month -> month.workouts.any { it.date == selectedDate } }

    /** The empty selected day is in the past: it can be logged, or marked rest / missed. */
    val canCheckInSelectedDay: Boolean
        get() = isSelectedDayEmpty && today != null && selectedDate!!.isBefore(today)

    val selectedDayState: DayState
        get() = days.firstOrNull { it.date == selectedDate }?.state ?: DayState.None

    /** The calendar's range: the strip's oldest day's month up to this month. */
    val firstMonth: YearMonth? get() = days.lastOrNull()?.date?.let(YearMonth::from)
    val lastMonth: YearMonth? get() = days.firstOrNull()?.date?.let(YearMonth::from)
}

/** Under the calendar: "8 workouts · 6 rest days · 1 missed". */
@Immutable
data class MonthSummaryUi(val workouts: Int, val restDays: Int, val missedDays: Int)

/** One chip of the day strip: "S 3" and its mark: • trained, ✕ missed, moon = rest, blank = unanswered. */
@Immutable
data class DayUi(
    val date: LocalDate,
    val isToday: Boolean,
    val state: DayState,
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

/** The asked question, while it's looked up, then its reply. */
@Immutable
data class AskUi(
    val question: String = "",
    val isLooking: Boolean = false,
    val reply: HistoryReply? = null,
) {
    /** The answer panel shows instead of the day strip and the list. */
    val isOpen: Boolean get() = isLooking || reply != null
}
