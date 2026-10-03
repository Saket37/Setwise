package dev.saketanand.setwise.ui.exercises

/** What the user can do on [CreateExerciseScreen]. */
sealed interface CreateExerciseAction {
    data class OnNameChange(val name: String) : CreateExerciseAction
    data class OnKindClick(val kind: ExerciseKindOption) : CreateExerciseAction
    data class OnMuscleGroupClick(val muscleGroup: String) : CreateExerciseAction
    data class OnEquipmentClick(val equipment: String) : CreateExerciseAction
    data class OnRestChange(val steps: Int) : CreateExerciseAction

    /** "Use this" on the library match: pick it instead of creating a copy. */
    data object OnUseMatchClick : CreateExerciseAction

    data object OnCreateClick : CreateExerciseAction

    /** ✕ or back. → nav */
    data object OnCloseClick : CreateExerciseAction
}

/** One-off things [CreateExerciseViewModel] tells the screen to do. */
sealed interface CreateExerciseEvent {
    /** Created, or an existing one chosen: back to the picker with it selected. */
    data class Done(val exerciseId: Long) : CreateExerciseEvent

    data object SaveFailed : CreateExerciseEvent
}
