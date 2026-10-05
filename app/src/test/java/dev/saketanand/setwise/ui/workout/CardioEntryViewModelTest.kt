package dev.saketanand.setwise.ui.workout

import androidx.lifecycle.SavedStateHandle
import dev.saketanand.setwise.domain.model.CardioEntry
import dev.saketanand.setwise.domain.model.CardioMetric
import dev.saketanand.setwise.domain.model.CardioValues
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.testing.StubWorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardioEntryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        CardioEntryViewModel(WORKOUT_EXERCISE_ID, repository, FixedDateProvider, handle)

    @Test
    fun `steppers start from last time and stay in range`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(5.0, vm.state.value.inclinePct, 0.0)
        assertTrue(CardioMetric.INCLINE in vm.state.value.metrics)

        vm.onAction(CardioEntryAction.OnInclineChange(+3))
        assertEquals(6.5, vm.state.value.inclinePct, 0.0)
        repeat(40) { vm.onAction(CardioEntryAction.OnInclineChange(-1)) }
        assertEquals(0.0, vm.state.value.inclinePct, 0.0)
    }

    @Test
    fun `logging saves the values and goes back`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(CardioEntryAction.OnLogClick(CardioInputs(minutes = "25", distance = "3")))

        assertEquals(CardioEntryEvent.Logged, vm.events.first())
        assertEquals(CardioValues(1_500, inclinePct = 5.0, speedMinKmh = 5.5, speedMaxKmh = 7.5, distanceKm = 3.0), repository.logged.single())
    }

    @Test
    fun `a missing duration shows an error until a field is edited`() = runTest(dispatcher) {
        repository.entry.value = repository.entry.value!!.copy(lastTime = null)
        val vm = viewModel()

        vm.onAction(CardioEntryAction.OnLogClick(CardioInputs()))
        assertEquals(CardioInputError.MissingDuration, vm.state.value.error)
        assertTrue(repository.logged.isEmpty())

        vm.onAction(CardioEntryAction.OnInputEdited)
        assertNull(vm.state.value.error)
    }

    private class FakeRepository : StubWorkoutRepository() {
        val entry = MutableStateFlow<CardioEntry?>(
            CardioEntry(
                workoutId = 1,
                exercise = Exercise(
                    3, "Treadmill", ExerciseType.CARDIO, "Cardio", "Machine", 0, isTimed = false, isCustom = false,
                    metrics = listOf(CardioMetric.DURATION, CardioMetric.INCLINE, CardioMetric.SPEED, CardioMetric.DISTANCE),
                    calorieMethod = null, met = null,
                ),
                logged = null,
                lastTime = CardioValues(1_800, inclinePct = 5.0, speedMinKmh = 5.5, speedMaxKmh = 7.5, distanceKm = 3.9),
            )
        )
        val logged = mutableListOf<CardioValues>()

        override fun observeCardioEntry(workoutExerciseId: Long): Flow<CardioEntry?> = entry
        override suspend fun logCardio(workoutExerciseId: Long, values: CardioValues, completedAt: Instant) {
            logged += values
        }
    }

    private object FixedDateProvider : DateProvider {
        override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        override fun now(): Instant = LocalDate.of(2026, 10, 3).atTime(18, 30).atZone(zone).toInstant()
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 3))
    }

    private companion object {
        const val WORKOUT_EXERCISE_ID = 42L
    }
}
