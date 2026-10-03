package dev.saketanand.setwise.ui.workout

/** One-off things the ViewModel tells the screen to do. */
sealed interface ActiveWorkoutEvent {
    /** Saved as finished: open the summary. */
    data class Finished(val workoutId: Long) : ActiveWorkoutEvent

    /** Discarded (or deleted elsewhere): leave the screen. */
    data object Closed : ActiveWorkoutEvent

    data object SaveFailed : ActiveWorkoutEvent

    /** A quick-logged line was added: clear the bar. */
    data object QuickLogAdded : ActiveWorkoutEvent
}
