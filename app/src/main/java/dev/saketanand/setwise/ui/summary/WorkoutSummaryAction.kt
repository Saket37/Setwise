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

    /** The calories tile without a body weight: asks for it (saved in Settings). */
    data object OnAddBodyWeightClick : WorkoutSummaryAction
    data class OnSaveBodyWeight(val text: String) : WorkoutSummaryAction
    data object OnBodyWeightDismiss : WorkoutSummaryAction

    /** Tap on the workout's name: the rename dialog. */
    data object OnRenameClick : WorkoutSummaryAction
    data class OnRenameConfirm(val name: String) : WorkoutSummaryAction
    data object OnRenameDismiss : WorkoutSummaryAction

    /** ⋮ → "Delete workout": asks first; confirming deletes it and closes the summary. */
    data object OnDeleteClick : WorkoutSummaryAction
    data object OnConfirmDelete : WorkoutSummaryAction
    data object OnDeleteDismiss : WorkoutSummaryAction

    /** An exercise row: its detail (progress, past sessions). → nav */
    data class OnExerciseClick(val exerciseId: Long) : WorkoutSummaryAction

    /** Menu → "Edit sets": the workout's sets in the workout screen, to correct them. */
    data object OnEditSetsClick : WorkoutSummaryAction
}
