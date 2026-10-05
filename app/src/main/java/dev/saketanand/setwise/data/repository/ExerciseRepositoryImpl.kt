package dev.saketanand.setwise.data.repository

import dev.saketanand.setwise.data.local.dao.ExerciseDao
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.data.mapper.toDomain
import dev.saketanand.setwise.domain.model.CardioMetric
import dev.saketanand.setwise.domain.model.CreateExerciseResult
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.NewExercise
import dev.saketanand.setwise.domain.model.RecentExercise
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val CARDIO_MUSCLE_GROUP = "Cardio"

class ExerciseRepositoryImpl(
    private val exerciseDao: ExerciseDao,
) : ExerciseRepository {

    override suspend fun createExercise(exercise: NewExercise): CreateExerciseResult {
        val name = exercise.name.trim().replace(Regex("\\s+"), " ")
        exerciseDao.findByNameIgnoringCase(name)?.let { return CreateExerciseResult.NameTaken(it.toDomain()) }
        val isCardio = exercise.type == ExerciseType.CARDIO
        val id = exerciseDao.insert(
            ExerciseEntity(
                name = name,
                type = exercise.type,
                muscleGroup = if (isCardio) CARDIO_MUSCLE_GROUP else exercise.muscleGroup,
                equipment = exercise.equipment,
                defaultRestSec = if (isCardio) 0 else exercise.restSec,
                isTimed = exercise.isTimed && !isCardio,
                isCustom = true,
                // Cardio is logged as time and distance (no calorie formula: estimated generically).
                metrics = if (isCardio) listOf(CardioMetric.DURATION, CardioMetric.DISTANCE) else null,
            )
        )
        return CreateExerciseResult.Created(id)
    }

    override suspend fun getExercises(ids: List<Long>): List<Exercise> {
        val byId = exerciseDao.getByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toDomain() }
    }

    override fun observeExercise(id: Long): Flow<Exercise?> =
        exerciseDao.observeById(id).map { it?.toDomain() }

    override fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>> =
        exerciseDao.observeExercises(query.trim(), muscleGroup)
            // Outer map: Flow operator (each emission). Inner map: List (each row).
            .map { entities -> entities.map { it.toDomain() } }

    override fun observeMuscleGroups(): Flow<List<String>> = exerciseDao.observeMuscleGroups()

    override fun observeRecentExercises(limit: Int): Flow<List<RecentExercise>> =
        exerciseDao.observeRecentExercises(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeExerciseCount(): Flow<Int> = exerciseDao.observeCount()
}
