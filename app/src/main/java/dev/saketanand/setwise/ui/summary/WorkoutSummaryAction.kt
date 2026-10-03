package dev.saketanand.setwise.ui.summary

import java.time.LocalTime

/** What the user can do on [WorkoutSummaryScreen]. */
sealed interface WorkoutSummaryAction {
    data object OnSaveAsTemplateClick : WorkoutSummaryAction

    // Edit times dialog

    data object OnEditTimesClick : WorkoutSummaryAction
    data class OnPickTime(val field: TimeField) : WorkoutSummaryAction
    data class OnTimePicked(val time: LocalTime) : WorkoutSummaryAction
    data object OnTimePickerDismiss : WorkoutSummaryAction
    data object OnSaveTimes : WorkoutSummaryAction
    data object OnEditTimesDismiss : WorkoutSummaryAction

    // Navigation (handled in WorkoutSummaryScreenRoot)

    /** "Done" or ✕. */
    data object OnDoneClick : WorkoutSummaryAction
}
