package dev.saketanand.setwise.domain.model

/** A custom exercise as the user describes it on "New exercise". */
data class NewExercise(
    val name: String,
    val type: ExerciseType,
    /** A timed hold (plank): seconds instead of reps. */
    val isTimed: Boolean,
    val muscleGroup: String,
    val equipment: String,
    val restSec: Int,
)

sealed interface CreateExerciseResult {
    data class Created(val exerciseId: Long) : CreateExerciseResult

    /** An exercise with this name (ignoring case) already exists. */
    data class NameTaken(val existing: Exercise) : CreateExerciseResult
}

sealed interface EditExerciseResult {
    data object Saved : EditExerciseResult

    /** Another exercise has this name (ignoring case). */
    data class NameTaken(val existing: Exercise) : EditExerciseResult
}

/** Where an exercise is used: what deleting it would touch. */
data class ExerciseUsage(val workouts: Int, val templates: Int)
