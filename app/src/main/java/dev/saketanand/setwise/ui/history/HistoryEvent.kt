package dev.saketanand.setwise.ui.history

/** One-off things [HistoryViewModel] tells the screen to do. */
sealed interface HistoryEvent {
    /** A workout for a past day was created; open it. */
    data class WorkoutStarted(val workoutId: Long) : HistoryEvent

    /** "Log workout" while another workout is running: finish or discard that one first. */
    data object WorkoutAlreadyRunning : HistoryEvent

    data object SaveFailed : HistoryEvent
}
