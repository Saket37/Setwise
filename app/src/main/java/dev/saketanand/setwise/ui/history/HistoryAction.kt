package dev.saketanand.setwise.ui.history

import java.time.LocalDate

/** What the user can do on [HistoryScreen]. */
sealed interface HistoryAction {
    /** A day in the strip: select it (jump there in the list), or clear it if it's selected. */
    data class OnDayClick(val date: LocalDate) : HistoryAction

    /** Opens the workout's summary (handled in HistoryScreenRoot). */
    data class OnWorkoutClick(val workoutId: Long) : HistoryAction
}
