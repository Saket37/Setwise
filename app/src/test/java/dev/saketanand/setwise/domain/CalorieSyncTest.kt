package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.ai.CalorieEstimator
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.CalorieEstimate
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.Intensity
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.Sex
import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutSet
import dev.saketanand.setwise.testing.FakeBodyRepository
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.testing.FakeUserSettingsRepository
import dev.saketanand.setwise.testing.StubWorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
        backgroundScope.launch { CalorieSync(workouts, settings, FakeBodyRepository(), CalorieEstimator(FakeOnDeviceModel()), FixedDateProvider).run() }

        assertTrue(workouts.saved.isEmpty()) // no weight yet

        settings.setBodyWeightKg(70.0)

        // 60 min, 18 sets: moderate, 350 kcal; saved, so it's no longer "without calories".
        assertEquals(mapOf(1L to CalorieEstimate(350, Intensity.Moderate)), workouts.saved)
        assertTrue(workouts.missing.value.isEmpty())
    }

    @Test
    fun `a BMR from the profile personalises the estimate`() = runTest(UnconfinedTestDispatcher()) {
        // 70 kg, 175 cm, 30, male: Mifflin-St Jeor 1,649 kcal/day = 68.7 kcal per MET-hour.
        val settings = FakeUserSettingsRepository(UserSettings(bodyWeightKg = 70.0, heightCm = 175.0, birthYear = 1996, sex = Sex.Male))
        backgroundScope.launch { CalorieSync(workouts, settings, FakeBodyRepository(), CalorieEstimator(FakeOnDeviceModel()), FixedDateProvider).run() }

        // 60 min, 18 sets: moderate, MET 5 × 68.7 × 1 h (350 with weight alone).
        assertEquals(mapOf(1L to CalorieEstimate(344, Intensity.Moderate)), workouts.saved)
    }

    @Test
    fun `a body report's BMR is used, and a new one re-estimates nothing already saved`() = runTest(UnconfinedTestDispatcher()) {
        val body = FakeBodyRepository(listOf(BodyMeasurement(id = 1, measuredOn = LocalDate.of(2026, 9, 20), weightKg = 70.0, bmrKcal = 1_440)))
        val settings = FakeUserSettingsRepository(UserSettings(bodyWeightKg = 70.0))
        backgroundScope.launch { CalorieSync(workouts, settings, body, CalorieEstimator(FakeOnDeviceModel()), FixedDateProvider).run() }

        // 1,440 kcal/day = 60 per MET-hour: 5 × 60 × 1 h.
        assertEquals(mapOf(1L to CalorieEstimate(300, Intensity.Moderate)), workouts.saved)
    }

    @Test
    fun `a workout whose times were edited is estimated again`() = runTest(UnconfinedTestDispatcher()) {
        val settings = FakeUserSettingsRepository(UserSettings(bodyWeightKg = 70.0))
        backgroundScope.launch { CalorieSync(workouts, settings, FakeBodyRepository(), CalorieEstimator(FakeOnDeviceModel()), FixedDateProvider).run() }
        assertEquals(1, workouts.saved.size)

        workouts.saved.clear()
        workouts.missing.value = listOf(1L) // like Room after updateFinishedTimes

        assertEquals(1, workouts.saved.size)
    }

    @Test
    fun `a slow model answer isn't thrown away when another estimate is saved meanwhile`() = runTest {
        workouts.missing.value = listOf(1L, 2L)
        val model = FakeOnDeviceModel(ModelAvailability.Ready, thinkingMs = 3_000, answer = { """{"kcal": 360, "intensity": "moderate"}""" })
        val settings = FakeUserSettingsRepository(UserSettings(bodyWeightKg = 70.0))
        val sync = backgroundScope.launch { CalorieSync(workouts, settings, FakeBodyRepository(), CalorieEstimator(model), FixedDateProvider).run() }

        advanceTimeBy(10_000)

        assertEquals(setOf(1L, 2L), workouts.saved.keys)
        assertEquals(2, model.requests.size) // one question per workout, none restarted
        sync.cancel()
    }

    @Test
    fun `only recent workouts go to the model, an old backlog gets the formula`() = runTest(UnconfinedTestDispatcher()) {
        workouts.missing.value = listOf(1L, 2L)
        workouts.endedAt[2L] = Instant.parse("2026-09-01T13:00:00Z") // a month ago
        val model = FakeOnDeviceModel(ModelAvailability.Ready, answer = { """{"kcal": 360, "intensity": "moderate"}""" })
        val settings = FakeUserSettingsRepository(UserSettings(bodyWeightKg = 70.0))
        backgroundScope.launch { CalorieSync(workouts, settings, FakeBodyRepository(), CalorieEstimator(model), FixedDateProvider).run() }

        assertEquals(1, model.requests.size)
        assertEquals(360, workouts.saved[1L]?.kcal) // the model's
        assertEquals(350, workouts.saved[2L]?.kcal) // the formula's
    }

    @Test
    fun `once the model fails, the rest of the pass uses the formula`() = runTest(UnconfinedTestDispatcher()) {
        workouts.missing.value = listOf(1L, 2L, 3L)
        val model = FakeOnDeviceModel(ModelAvailability.Ready, answer = { error("PER_APP_BATTERY_USE_QUOTA_EXCEEDED") })
        val settings = FakeUserSettingsRepository(UserSettings(bodyWeightKg = 70.0))
        backgroundScope.launch { CalorieSync(workouts, settings, FakeBodyRepository(), CalorieEstimator(model), FixedDateProvider).run() }

        assertEquals(1, model.requests.size) // not asked again for 2 and 3
        assertEquals(setOf(1L, 2L, 3L), workouts.saved.keys) // all still get a number
    }

    private object FixedDateProvider : DateProvider {
        override val zone: ZoneId = ZoneId.of("UTC")
        override fun now(): Instant = Instant.parse("2026-10-03T18:00:00Z")
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 3))
    }

    private class FakeWorkoutRepository : StubWorkoutRepository() {
        val missing = MutableStateFlow(listOf(1L))
        val saved = mutableMapOf<Long, CalorieEstimate>()
        val endedAt = mutableMapOf<Long, Instant>()

        override fun observeWorkoutsWithoutCalories(): Flow<List<Long>> = missing
        override fun observeSession(workoutId: Long): Flow<WorkoutSession?> {
            val start = Instant.parse("2026-10-03T12:00:00Z")
            val bench = SessionExercise(
                id = 1,
                exercise = Exercise(1, "Bench", ExerciseType.STRENGTH, "Chest", "Barbell", 120, false, false, null, null, null),
                sets = List(18) { WorkoutSet(it.toLong(), it + 1, 60.0, 8, null, isCompleted = true, isPr = false) },
                previousSets = emptyList(),
            )
            return flowOf(
                WorkoutSession(
                    workoutId, "W", null, (endedAt[workoutId] ?: start.plusSeconds(3_600)).minusSeconds(3_600),
                    endedAt[workoutId] ?: start.plusSeconds(3_600), listOf(bench), calories = saved[workoutId]?.kcal,
                ),
            )
        }
        override suspend fun setCalories(workoutId: Long, estimate: CalorieEstimate, source: String) {
            saved[workoutId] = estimate
            missing.update { it - workoutId }
        }
    }
}
