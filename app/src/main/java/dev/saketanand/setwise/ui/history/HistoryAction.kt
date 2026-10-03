package dev.saketanand.setwise.ui.history

import dev.saketanand.setwise.domain.model.DayStatus
import java.time.LocalDate

/** What the user can do on [HistoryScreen]. */
sealed interface HistoryAction {
    /** A day in the strip: select it (jump there in the list), or clear it if it's selected. */
    data class OnDayClick(val date: LocalDate) : HistoryAction

    /** A day in the calendar: select it (never clears, unlike the strip). */
    data class OnCalendarDayClick(val date: LocalDate) : HistoryAction

    /** Opens the workout's summary (handled in HistoryScreenRoot). */
    data class OnWorkoutClick(val workoutId: Long) : HistoryAction

    /** "Log workout" on an empty past day: starts a workout on that day. */
    data class OnLogWorkoutClick(val date: LocalDate) : HistoryAction

    /** "Rest" / "Missed" on an empty past day; null clears the mark. */
    data class OnMarkDay(val date: LocalDate, val status: DayStatus?) : HistoryAction
}
