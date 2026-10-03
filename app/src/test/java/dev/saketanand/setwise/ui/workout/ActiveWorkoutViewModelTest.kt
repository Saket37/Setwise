package dev.saketanand.setwise.ui.workout

import androidx.lifecycle.SavedStateHandle
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutSet
import dev.saketanand.setwise.testing.StubWorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
class ActiveWorkoutViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeWorkoutRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() =
        ActiveWorkoutViewModel(WORKOUT_ID, repository, FixedDateProvider, SavedStateHandle()).also { vm ->
            backgroundScope.launch { vm.state.collect {} }
        }

    // Sets

    @Test
    fun `ticking off an empty set logs the hints (last time's numbers)`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnSetDoneToggle(setId = 1, weight = "", reps = ""))

        assertEquals(Completion(1, done = true, weightKg = 60.0, reps = 8, durationSec = null), repository.completions.single())
    }

    @Test
    fun `ticking off uses what's in the fields, even before it's saved`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnSetDoneToggle(setId = 1, weight = "62,5", reps = "7"))

        assertEquals(Completion(1, done = true, weightKg = 62.5, reps = 7, durationSec = null), repository.completions.single())
    }

    @Test
    fun `a set with no reps and no hint can't be ticked off`() = runTest(dispatcher) {
        repository.session.value = session(bench(previous = emptyList()))
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnSetDoneToggle(setId = 1, weight = "60", reps = ""))

        assertTrue(repository.completions.isEmpty())
    }

    @Test
    fun `ticking a done set again re-opens it with its values`() = runTest(dispatcher) {
        repository.session.value = session(bench(sets = listOf(set(1, 70.0, 5, done = true), set(2))))
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnSetDoneToggle(setId = 1, weight = "", reps = ""))

        assertEquals(Completion(1, done = false, weightKg = 70.0, reps = 5, durationSec = null), repository.completions.single())
    }

    @Test
    fun `timed exercises save seconds, not reps`() = runTest(dispatcher) {
        repository.session.value = session(plank())
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnSetValuesChange(setId = 30, weight = "", reps = "45"))
        vm.onAction(ActiveWorkoutAction.OnSetDoneToggle(setId = 30, weight = "", reps = "45"))

        assertEquals(listOf(Values(30, null, null, 45)), repository.valueUpdates)
        assertEquals(Completion(30, done = true, weightKg = null, reps = null, durationSec = 45), repository.completions.single())
    }

    // Which exercise is open

    @Test
    fun `the first exercise with sets left is open, and the next opens when it's done`() = runTest(dispatcher) {
        repository.session.value = session(bench(sets = listOf(set(1, 60.0, 8, done = true), set(2))), press())
        val vm = viewModel()
        assertEquals(BENCH, vm.state.value.expandedExerciseId)

        vm.onAction(ActiveWorkoutAction.OnSetDoneToggle(setId = 2, weight = "", reps = ""))
        repository.session.value = session(bench(sets = listOf(set(1, 60.0, 8, done = true), set(2, 60.0, 8, done = true))), press())

        assertEquals(PRESS, vm.state.value.expandedExerciseId)
    }

    @Test
    fun `tapping the open card closes it, tapping another opens that one`() = runTest(dispatcher) {
        repository.session.value = session(bench(), press())
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnExerciseHeaderClick(BENCH))
        assertNull(vm.state.value.expandedExerciseId)

        vm.onAction(ActiveWorkoutAction.OnExerciseHeaderClick(PRESS))
        assertEquals(PRESS, vm.state.value.expandedExerciseId)
    }

    @Test
    fun `picked exercises are added and the first one opens`() = runTest(dispatcher) {
        repository.session.value = session(bench(), press())
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnExercisesPicked(listOf(5, 6)))

        assertEquals(listOf(listOf(5L, 6L)), repository.added)
        // The fake returns the new row ids 500, 501; once they're in the data, 500 is open.
        repository.session.value = session(bench(), press(), bench().copy(id = 500))
        assertEquals(500L, vm.state.value.expandedExerciseId)
    }

    // Finish / discard

    @Test
    fun `finish with nothing ticked off offers to discard instead`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnFinishClick)

        assertEquals(ActiveWorkoutDialog.NothingLogged, vm.state.value.dialog)
        assertTrue(repository.finished.isEmpty())
    }

    @Test
    fun `finish with open sets asks first, then finishes and opens the summary`() = runTest(dispatcher) {
        repository.session.value = session(bench(sets = listOf(set(1, 60.0, 8, done = true), set(2))))
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnFinishClick)
        assertEquals(ActiveWorkoutDialog.FinishWithIncompleteSets(1), vm.state.value.dialog)

        vm.onAction(ActiveWorkoutAction.OnConfirmFinish)
        assertEquals(listOf(WORKOUT_ID), repository.finished)
        assertEquals(ActiveWorkoutEvent.Finished(WORKOUT_ID), vm.events.first())
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `finish with every set done needs no confirmation`() = runTest(dispatcher) {
        repository.session.value = session(bench(sets = listOf(set(1, 60.0, 8, done = true))))
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnFinishClick)

        assertEquals(listOf(WORKOUT_ID), repository.finished)
    }

    @Test
    fun `discard deletes the workout and closes the screen once`() = runTest(dispatcher) {
        val vm = viewModel()
        val events = mutableListOf<ActiveWorkoutEvent>()
        backgroundScope.launch { vm.events.toList(events) }

        vm.onAction(ActiveWorkoutAction.OnDiscardWorkoutClick)
        vm.onAction(ActiveWorkoutAction.OnConfirmDiscard)

        assertEquals(listOf(WORKOUT_ID), repository.discarded)
        // Discarding also makes the workout disappear from the data; still only one Closed.
        assertEquals(listOf(ActiveWorkoutEvent.Closed), events)
    }

    @Test
    fun `removing an exercise with logged sets asks first`() = runTest(dispatcher) {
        repository.session.value = session(bench(sets = listOf(set(1, 60.0, 8, done = true))), press())
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnRemoveExerciseClick(PRESS))
        assertEquals(listOf(PRESS), repository.removed)

        vm.onAction(ActiveWorkoutAction.OnRemoveExerciseClick(BENCH))
        assertEquals(ActiveWorkoutDialog.RemoveExercise(BENCH, "Bench Press", completedSets = 1), vm.state.value.dialog)
        vm.onAction(ActiveWorkoutAction.OnConfirmRemoveExercise)
        assertEquals(listOf(PRESS, BENCH), repository.removed)
    }

    @Test
    fun `a start time later than now becomes now`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnStartTimeChange(LocalTime.of(23, 0)))
        vm.onAction(ActiveWorkoutAction.OnStartTimeChange(LocalTime.of(17, 45)))

        assertEquals(
            listOf(FixedDateProvider.now(), LocalDate.of(2026, 10, 3).atTime(17, 45).atZone(FixedDateProvider.zone).toInstant()),
            repository.startTimes,
        )
    }

    // Fakes

    private data class Completion(val setId: Long, val done: Boolean, val weightKg: Double?, val reps: Int?, val durationSec: Int?)
    private data class Values(val setId: Long, val weightKg: Double?, val reps: Int?, val durationSec: Int?)

    private class FakeWorkoutRepository : StubWorkoutRepository() {
        val session = MutableStateFlow<WorkoutSession?>(session(bench(), press()))
        val completions = mutableListOf<Completion>()
        val valueUpdates = mutableListOf<Values>()
        val finished = mutableListOf<Long>()
        val discarded = mutableListOf<Long>()
        val removed = mutableListOf<Long>()
        val added = mutableListOf<List<Long>>()
        val startTimes = mutableListOf<Instant>()

        override fun observeSession(workoutId: Long): Flow<WorkoutSession?> = session

        override suspend fun setCompleted(setId: Long, completedAt: Instant?, weightKg: Double?, reps: Int?, durationSec: Int?) {
            completions += Completion(setId, completedAt != null, weightKg, reps, durationSec)
        }

        override suspend fun updateSetValues(setId: Long, weightKg: Double?, reps: Int?, durationSec: Int?) {
            valueUpdates += Values(setId, weightKg, reps, durationSec)
        }

        override suspend fun finishWorkout(workoutId: Long, endedAt: Instant): Boolean {
            finished += workoutId
            return true
        }

        override suspend fun discardWorkout(workoutId: Long) {
            discarded += workoutId
            session.value = null
        }

        override suspend fun removeExercise(workoutExerciseId: Long) {
            removed += workoutExerciseId
        }

        override suspend fun addExercises(workoutId: Long, exerciseIds: List<Long>): List<Long> {
            added += exerciseIds
            return exerciseIds.indices.map { 500L + it }
        }

        override suspend fun updateStartTime(workoutId: Long, startedAt: Instant) {
            startTimes += startedAt
        }
    }

    /** Saturday 3 Oct 2026, 18:30 in India. */
    private object FixedDateProvider : DateProvider {
        override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        override fun now(): Instant = LocalDate.of(2026, 10, 3).atTime(18, 30).atZone(zone).toInstant()
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 3))
    }

    private companion object {
        const val WORKOUT_ID = 7L
        const val BENCH = 1L
        const val PRESS = 2L

        fun session(vararg exercises: SessionExercise) = WorkoutSession(
            id = WORKOUT_ID,
            name = "Push Day",
            startedAt = LocalDate.of(2026, 10, 3).atTime(18, 0).atZone(FixedDateProvider.zone).toInstant(),
            endedAt = null,
            exercises = exercises.toList(),
        )

        fun bench(
            sets: List<WorkoutSet> = listOf(set(1), set(2)),
            previous: List<PreviousSet> = listOf(PreviousSet(60.0, 8), PreviousSet(60.0, 8)),
        ) = SessionExercise(BENCH, exercise(10, "Bench Press", ExerciseType.STRENGTH), sets, previous)

        fun press() = SessionExercise(PRESS, exercise(11, "Overhead Press", ExerciseType.STRENGTH), listOf(set(20), set(21)), emptyList())

        fun plank() = SessionExercise(
            3, exercise(12, "Plank", ExerciseType.BODYWEIGHT, timed = true), listOf(set(30)), emptyList(),
        )

        fun set(id: Long, weightKg: Double? = null, reps: Int? = null, done: Boolean = false) = WorkoutSet(
            id = id, setNumber = id.toInt(), weightKg = weightKg, reps = reps, durationSec = null, isCompleted = done, isPr = false,
        )

        fun exercise(id: Long, name: String, type: ExerciseType, timed: Boolean = false) = Exercise(
            id = id, name = name, type = type, muscleGroup = "Chest", equipment = "Barbell",
            defaultRestSec = 120, isTimed = timed, isCustom = false, metrics = null, calorieMethod = null, met = null,
        )
    }
}
