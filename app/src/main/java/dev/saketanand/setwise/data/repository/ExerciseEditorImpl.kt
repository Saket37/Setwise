package dev.saketanand.setwise.data.repository

import dev.saketanand.setwise.data.local.dao.ExerciseDao
import dev.saketanand.setwise.data.mapper.toDomain
import dev.saketanand.setwise.domain.model.EditExerciseResult
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.ExerciseUsage
import dev.saketanand.setwise.domain.model.NewExercise
import dev.saketanand.setwise.domain.repository.ExerciseEditor

/** Edit, delete and merge custom exercises (#146); the DAO keeps built-in ones as they are. */
class ExerciseEditorImpl(
    private val exerciseDao: ExerciseDao,
) : ExerciseEditor {

    override suspend fun editExercise(id: Long, exercise: NewExercise): EditExerciseResult {
        val name = tidyExerciseName(exercise.name)
        exerciseDao.findByNameIgnoringCase(name)?.takeIf { it.id != id }?.let { return EditExerciseResult.NameTaken(it.toDomain()) }
        val current = exerciseDao.getById(id) ?: return EditExerciseResult.Saved
        val isCardio = current.type == ExerciseType.CARDIO
        exerciseDao.updateCustom(
            id = id,
            name = name,
            muscleGroup = if (isCardio) CARDIO_MUSCLE_GROUP else exercise.muscleGroup,
            equipment = exercise.equipment,
            restSec = if (isCardio) 0 else exercise.restSec,
        )
        return EditExerciseResult.Saved
    }

    override suspend fun usage(id: Long): ExerciseUsage =
        ExerciseUsage(workouts = exerciseDao.countWorkouts(id), templates = exerciseDao.countTemplates(id))

    override suspend fun deleteExercise(id: Long): Boolean = exerciseDao.deleteUnused(id)

    override suspend fun mergeExercise(fromId: Long, intoId: Long): Boolean {
        if (fromId == intoId) return false
        val from = exerciseDao.getById(fromId)?.takeIf { it.isCustom } ?: return false
        val into = exerciseDao.getById(intoId) ?: return false
        // Its sets are logged one way: the other exercise must take them the same way.
        if (from.type != into.type || from.isTimed != into.isTimed) return false
        exerciseDao.merge(fromId, intoId)
        return true
    }
}
