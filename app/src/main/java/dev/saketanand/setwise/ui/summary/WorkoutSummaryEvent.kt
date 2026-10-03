package dev.saketanand.setwise.ui.summary

/** One-off things the ViewModel tells the screen. */
sealed interface WorkoutSummaryEvent {
    data object TemplateSaved : WorkoutSummaryEvent
    data object SaveFailed : WorkoutSummaryEvent

    /** The workout is gone or isn't finished: nothing to summarise. */
    data object Closed : WorkoutSummaryEvent
}
