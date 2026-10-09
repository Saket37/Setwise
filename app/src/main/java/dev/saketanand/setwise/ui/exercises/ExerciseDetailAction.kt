package dev.saketanand.setwise.ui.exercises

/** What the user can do on [ExerciseDetailScreen] besides moving around (#146). */
sealed interface ExerciseDetailAction {
    /** ⋮ → Edit. → nav */
    data object OnEditClick : ExerciseDetailAction

    /** ⋮ → Delete exercise: deletes it, or offers a merge when it has history. */
    data object OnDeleteClick : ExerciseDetailAction

    data object OnDismissDialog : ExerciseDetailAction

    data object OnConfirmDelete : ExerciseDetailAction

    data class OnMergeQueryChange(val query: String) : ExerciseDetailAction

    data class OnMergeTargetClick(val exerciseId: Long) : ExerciseDetailAction

    data object OnConfirmMerge : ExerciseDetailAction
}

/** One-off things [ExerciseDetailViewModel] tells the screen. */
sealed interface ExerciseDetailEvent {
    data object ChangeFailed : ExerciseDetailEvent
}
