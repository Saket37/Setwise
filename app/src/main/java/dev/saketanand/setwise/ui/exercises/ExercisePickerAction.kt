package dev.saketanand.setwise.ui.exercises

/** What the user can do on [ExercisePickerScreen]. */
sealed interface ExercisePickerAction {
    data class OnQueryChange(val query: String) : ExercisePickerAction

    /** null = "All". Tapping the selected chip again also goes back to "All". */
    data class OnMuscleGroupClick(val muscleGroup: String?) : ExercisePickerAction

    data class OnExerciseToggle(val exerciseId: Long) : ExercisePickerAction

    // Navigation (handled in ExercisePickerScreenRoot)

    /** "Add N exercises": return the selection to the screen that opened the picker. */
    data object OnAddClick : ExercisePickerAction

    /** Made on "New exercise" (or a library one chosen there instead): selected. */
    data class OnExerciseCreated(val exerciseId: Long) : ExercisePickerAction

    /** "Create new" / "Create “xyz”": the search text pre-fills the name. */
    data object OnCreateNewClick : ExercisePickerAction

    data object OnBackClick : ExercisePickerAction
}
