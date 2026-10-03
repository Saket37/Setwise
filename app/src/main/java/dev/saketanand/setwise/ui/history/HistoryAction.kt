package dev.saketanand.setwise.ui.history

/** What the user can do on [HistoryScreen]. */
sealed interface HistoryAction {
    /** Opens the workout's summary (handled in HistoryScreenRoot). */
    data class OnWorkoutClick(val workoutId: Long) : HistoryAction
}
