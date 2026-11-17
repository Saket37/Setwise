package dev.saketanand.setwise.domain.repository

import dev.saketanand.setwise.domain.model.EditExerciseResult
import dev.saketanand.setwise.domain.model.ExerciseUsage
import dev.saketanand.setwise.domain.model.NewExercise

/** Changing the user's own exercises (#146): edit, delete, or merge into another. Built-in ones stay as they are. */
interface ExerciseEditor {
    /**
     * A custom exercise's name, muscle group, equipment and rest (#146); how it's logged
     * ([NewExercise.type], [NewExercise.isTimed]) stays, since its sets depend on it.
     */
    suspend fun editExercise(id: Long, exercise: NewExercise): EditExerciseResult

    suspend fun usage(id: Long): ExerciseUsage

    /** A custom exercise in no workout, taken out of its templates too. False if it's in a workout. */
    suspend fun deleteExercise(id: Long): Boolean

    /**
     * Custom exercise [fromId]'s workouts and template places move to [intoId], which is logged
     * the same way, then [fromId] is deleted. False if either is missing or they don't match.
     */
    suspend fun mergeExercise(fromId: Long, intoId: Long): Boolean
}
