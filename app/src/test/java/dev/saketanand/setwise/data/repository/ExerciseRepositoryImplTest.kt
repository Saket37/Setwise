package dev.saketanand.setwise.data.repository

import app.cash.turbine.test
import dev.saketanand.setwise.data.local.dao.ExerciseDao
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.domain.model.CalorieMethod
import dev.saketanand.setwise.domain.model.CardioMetric
import dev.saketanand.setwise.domain.model.ExerciseType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseRepositoryImplTest {

    private val treadmill = ExerciseEntity(
        id = 7,
        name = "Treadmill",
        type = ExerciseType.CARDIO,
        muscleGroup = "Cardio",
        equipment = "Machine",
        defaultRestSec = 0,
        metrics = listOf(CardioMetric.DURATION, CardioMetric.INCLINE),
        calorieMethod = CalorieMethod.ACSM_TREADMILL,
    )

    @Test
    fun `entities are mapped to domain exercises`() = runTest {
        val repository = ExerciseRepositoryImpl(FakeExerciseDao(listOf(treadmill)))

        repository.observeExercises("", null).test {
            val exercise = awaitItem().single()
            assertEquals(7L, exercise.id)
            assertEquals("Treadmill", exercise.name)
            assertEquals(ExerciseType.CARDIO, exercise.type)
            assertEquals(listOf(CardioMetric.DURATION, CardioMetric.INCLINE), exercise.metrics)
            assertEquals(CalorieMethod.ACSM_TREADMILL, exercise.calorieMethod)
            awaitComplete()
        }
    }

    @Test
    fun `search query is trimmed before reaching the dao`() = runTest {
        val dao = FakeExerciseDao(emptyList())
        ExerciseRepositoryImpl(dao).observeExercises("  press ", "Chest").test {
            awaitItem()
            awaitComplete()
        }
        assertEquals("press", dao.lastQuery)
        assertEquals("Chest", dao.lastMuscleGroup)
    }

    /** Minimal in-memory DAO; only the methods the repository uses do anything. */
    private class FakeExerciseDao(private val rows: List<ExerciseEntity>) : ExerciseDao {
        var lastQuery: String? = null
        var lastMuscleGroup: String? = null

        override fun observeExercises(query: String, muscleGroup: String?): Flow<List<ExerciseEntity>> {
            lastQuery = query
            lastMuscleGroup = muscleGroup
            return flowOf(rows)
        }

        override fun observeMuscleGroups(): Flow<List<String>> =
            flowOf(rows.map { it.muscleGroup }.distinct())

        override suspend fun insertAll(exercises: List<ExerciseEntity>): List<Long> = error("unused")
        override suspend fun count(): Int = rows.size
        override fun observeCount(): Flow<Int> = flowOf(rows.size)
        override suspend fun getById(id: Long): ExerciseEntity? = rows.find { it.id == id }
        override suspend fun insert(exercise: ExerciseEntity): Long = error("unused")
    }
}
