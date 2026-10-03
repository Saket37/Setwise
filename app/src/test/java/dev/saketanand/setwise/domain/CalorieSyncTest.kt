package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.CalorieEstimate
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.Intensity
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutSet
import dev.saketanand.setwise.domain.ai.CalorieEstimator
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.testing.FakeUserSettingsRepository
import dev.saketanand.setwise.testing.StubWorkoutRepository
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalorieSyncTest {

    private val workouts = FakeWorkoutRepository()

    @Test
    fun `fills in workouts without calories once the body weight is known`() = runTest(UnconfinedTestDispatcher()) {
        val settings = FakeUserSettingsRepository()
        backgroundScope.launch { CalorieSync(workouts, settings, CalorieEstimator(FakeOnDeviceModel())).run() }

        assertTrue(workouts.saved.isEmpty()) // no weight yet

        settings.setBodyWeightKg(70.0)

        // 60 min, 18 sets: moderate, 350 kcal; saved, so it's no longer "without calories".
        assertEquals(mapOf(1L to CalorieEstimate(350, Intensity.Moderate)), workouts.saved)
        assertTrue(workouts.missing.value.isEmpty())
    }

    @Test
    fun `a workout whose times were edited is estimated again`() = runTest(UnconfinedTestDispatcher()) {
        val settings = FakeUserSettingsRepository(UserSettings(bodyWeightKg = 70.0))
        backgroundScope.launch { CalorieSync(workouts, settings, CalorieEstimator(FakeOnDeviceModel())).run() }
        assertEquals(1, workouts.saved.size)

        workouts.saved.clear()
        workouts.missing.value = listOf(1L) // like Room after updateFinishedTimes

        assertEquals(1, workouts.saved.size)
    }

    private class FakeWorkoutRepository : StubWorkoutRepository() {
        val missing = MutableStateFlow(listOf(1L))
        val saved = mutableMapOf<Long, CalorieEstimate>()

        override fun observeWorkoutsWithoutCalories(): Flow<List<Long>> = missing
        override fun observeSession(workoutId: Long): Flow<WorkoutSession?> {
            val start = Instant.parse("2026-10-03T12:00:00Z")
            val bench = SessionExercise(
                id = 1,
                exercise = Exercise(1, "Bench", ExerciseType.STRENGTH, "Chest", "Barbell", 120, false, false, null, null, null),
                sets = List(18) { WorkoutSet(it.toLong(), it + 1, 60.0, 8, null, isCompleted = true, isPr = false) },
                previousSets = emptyList(),
            )
            return flowOf(WorkoutSession(workoutId, "W", null, start, start.plusSeconds(3_600), listOf(bench)))
        }
        override suspend fun setCalories(workoutId: Long, estimate: CalorieEstimate, source: String) {
            saved[workoutId] = estimate
            missing.update { it - workoutId }
        }
    }
}
