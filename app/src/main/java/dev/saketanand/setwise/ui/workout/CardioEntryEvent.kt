package dev.saketanand.setwise.ui.workout

/** One-off things [CardioEntryViewModel] tells the screen to do. */
sealed interface CardioEntryEvent {
    /** Saved: back to the workout. */
    data object Logged : CardioEntryEvent

    /** The exercise is no longer in the workout. */
    data object Closed : CardioEntryEvent

    data object SaveFailed : CardioEntryEvent
}
