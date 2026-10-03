package dev.saketanand.setwise.data.repository

import dev.saketanand.setwise.data.local.dao.ExerciseDao
import dev.saketanand.setwise.data.mapper.toDomain
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.RecentExercise
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExerciseRepositoryImpl(
    private val exerciseDao: ExerciseDao,
) : ExerciseRepository {

    override fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>> =
        exerciseDao.observeExercises(query.trim(), muscleGroup)
            // Outer map: Flow operator (each emission). Inner map: List (each row).
            .map { entities -> entities.map { it.toDomain() } }

    override fun observeMuscleGroups(): Flow<List<String>> = exerciseDao.observeMuscleGroups()

    override fun observeRecentExercises(limit: Int): Flow<List<RecentExercise>> =
        exerciseDao.observeRecentExercises(limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeExerciseCount(): Flow<Int> = exerciseDao.observeCount()
}
