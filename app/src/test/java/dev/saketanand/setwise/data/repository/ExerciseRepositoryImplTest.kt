package dev.saketanand.setwise.data.repository

import app.cash.turbine.test
import dev.saketanand.setwise.data.local.dao.ExerciseDao
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.data.local.relation.RecentExerciseRow
import dev.saketanand.setwise.domain.model.CalorieMethod
import dev.saketanand.setwise.domain.model.CardioMetric
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PreviousSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `recent exercises carry their last top set, or none if no set was completed`() = runTest {
        val dao = FakeExerciseDao(
            rows = emptyList(),
            recent = listOf(
                RecentExerciseRow(treadmill.copy(id = 1, name = "Bench Press"), lastUsedAt = 2, lastWeightKg = 60.0, lastReps = 8),
                RecentExerciseRow(treadmill.copy(id = 2, name = "Plank"), lastUsedAt = 1, lastWeightKg = null, lastReps = null),
            ),
        )
        ExerciseRepositoryImpl(dao).observeRecentExercises(limit = 5).test {
            val (bench, plank) = awaitItem()
            assertEquals(PreviousSet(weightKg = 60.0, reps = 8), bench.lastSet)
            assertNull(plank.lastSet)
            awaitComplete()
        }
    }

    /** Minimal in-memory DAO; only the methods the repository uses do anything. */
    private class FakeExerciseDao(
        private val rows: List<ExerciseEntity>,
        private val recent: List<RecentExerciseRow> = emptyList(),
    ) : ExerciseDao {
        var lastQuery: String? = null
        var lastMuscleGroup: String? = null

        override fun observeExercises(query: String, muscleGroup: String?): Flow<List<ExerciseEntity>> {
            lastQuery = query
            lastMuscleGroup = muscleGroup
            return flowOf(rows)
        }

        override fun observeMuscleGroups(): Flow<List<String>> =
            flowOf(rows.map { it.muscleGroup }.distinct())

        override fun observeRecentExercises(limit: Int): Flow<List<RecentExerciseRow>> = flowOf(recent.take(limit))

        override suspend fun insertAll(exercises: List<ExerciseEntity>): List<Long> = error("unused")
        override suspend fun count(): Int = rows.size
        override fun observeCount(): Flow<Int> = flowOf(rows.size)
        override suspend fun getById(id: Long): ExerciseEntity? = rows.find { it.id == id }
        override fun observeById(id: Long): Flow<ExerciseEntity?> = flowOf(rows.find { it.id == id })
        override suspend fun getByIds(ids: List<Long>): List<ExerciseEntity> = rows.filter { it.id in ids }
        override suspend fun findByNameIgnoringCase(name: String): ExerciseEntity? = rows.find { it.name.equals(name, ignoreCase = true) }
        override suspend fun getByName(name: String): ExerciseEntity? = rows.find { it.name == name }
        override suspend fun insert(exercise: ExerciseEntity): Long = error("unused")
    }
}
