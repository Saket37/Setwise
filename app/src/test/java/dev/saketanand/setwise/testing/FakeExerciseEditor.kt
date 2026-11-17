package dev.saketanand.setwise.testing

import dev.saketanand.setwise.domain.model.EditExerciseResult
import dev.saketanand.setwise.domain.model.ExerciseUsage
import dev.saketanand.setwise.domain.model.NewExercise
import dev.saketanand.setwise.domain.repository.ExerciseEditor

/** Records what was edited, deleted or merged; [usage] is what every exercise is in. */
class FakeExerciseEditor(var usage: ExerciseUsage = ExerciseUsage(workouts = 0, templates = 0)) : ExerciseEditor {
    val edited = mutableListOf<Pair<Long, NewExercise>>()
    val deleted = mutableListOf<Long>()
    val merged = mutableListOf<Pair<Long, Long>>()

    override suspend fun editExercise(id: Long, exercise: NewExercise): EditExerciseResult {
        edited += id to exercise
        return EditExerciseResult.Saved
    }

    override suspend fun usage(id: Long): ExerciseUsage = usage

    override suspend fun deleteExercise(id: Long): Boolean {
        if (usage.workouts > 0) return false
        deleted += id
        return true
    }

    override suspend fun mergeExercise(fromId: Long, intoId: Long): Boolean {
        merged += fromId to intoId
        return true
    }
}
