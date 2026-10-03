package dev.saketanand.setwise.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutSet
import dev.saketanand.setwise.testing.FakeRestTimer
import dev.saketanand.setwise.testing.StubWorkoutRepository
import dev.saketanand.setwise.timer.NextUp
import dev.saketanand.setwise.timer.RestTimerState
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
import kotlinx.coroutines.cancel
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
import dev.saketanand.setwise.domain.ai.ExerciseAssistant
import dev.saketanand.setwise.domain.ai.QuickLogInterpreter
import dev.saketanand.setwise.domain.ai.QuickLogResult
import dev.saketanand.setwise.domain.model.CreateExerciseResult
import dev.saketanand.setwise.domain.model.NewExercise
import dev.saketanand.setwise.domain.model.RecentExercise
import dev.saketanand.setwise.domain.model.SetFact
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.domain.ai.Heard
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.testing.FakeSpeechInput
import dev.saketanand.setwise.testing.FakeSpokenTextFixer
import kotlinx.coroutines.test.advanceTimeBy

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeWorkoutRepository()
    private val restTimer = FakeRestTimer()
    private var notificationRefreshes = 0

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val model = FakeOnDeviceModel()
    private val speech = FakeSpeechInput(availability = ModelAvailability.Unavailable)
    private val fixer = FakeSpokenTextFixer()

    private fun TestScope.viewModel() =
        ActiveWorkoutViewModel(
            WORKOUT_ID, repository, FixedDateProvider, SavedStateHandle(),
            writeScope = backgroundScope,
            restTimer = restTimer,
            restNotifications = { notificationRefreshes++ },
            quickLogInterpreter = QuickLogInterpreter(model, ExerciseAssistant(model)),
            exerciseRepository = FakeLibrary,
            speechInput = speech,
            spokenTextFixer = fixer,
        ).also { vm ->
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

    @Test
    fun `0 reps is not a set, so it falls back to the hint or isn't logged`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnSetDoneToggle(setId = 1, weight = "60", reps = "0"))
        assertEquals("hint used instead of 0", 8, repository.completions.single().reps)

        repository.session.value = session(bench(previous = emptyList()))
        vm.onAction(ActiveWorkoutAction.OnSetDoneToggle(setId = 2, weight = "60", reps = "0"))
        assertEquals(1, repository.completions.size)
    }

    @Test
    fun `edits are still saved after the screen's ViewModel scope is gone`() = runTest(dispatcher) {
        val vm = viewModel()
        // Minimising clears the ViewModel; the write queue runs in the app scope, not viewModelScope.
        vm.viewModelScope.cancel()

        vm.onAction(ActiveWorkoutAction.OnSetValuesChange(setId = 1, weight = "65", reps = "8"))

        assertEquals(listOf(Values(1, 65.0, 8, null)), repository.valueUpdates)
    }

    // Rest timer

    @Test
    fun `ticking off a set starts a rest for the exercise, naming the next set`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnSetDoneToggle(setId = 1, weight = "", reps = ""))

        assertEquals(FakeRestTimer.Start(WORKOUT_ID, 120, NextUp.Set(2)), restTimer.starts.single())
    }

    @Test
    fun `ticking off a set in a workout logged for a past day starts no rest`() = runTest(dispatcher) {
        val pastDay = LocalDate.of(2026, 9, 28).atTime(18, 0).atZone(FixedDateProvider.zone).toInstant()
        repository.session.value = repository.session.value?.copy(startedAt = pastDay)
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnSetDoneToggle(setId = 1, weight = "", reps = ""))

        assertEquals(1, repository.completions.size) // still logged
        assertTrue(restTimer.starts.isEmpty())
    }

    @Test
    fun `after an exercise's last set, the rest names the next exercise`() = runTest(dispatcher) {
        repository.session.value = session(bench(sets = listOf(set(1, 60.0, 8, done = true), set(2))), press())
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnSetDoneToggle(setId = 2, weight = "", reps = ""))

        assertEquals(NextUp.Exercise("Overhead Press"), restTimer.starts.single().next)
    }

    @Test
    fun `un-ticking doesn't start a rest`() = runTest(dispatcher) {
        repository.session.value = session(bench(sets = listOf(set(1, 60.0, 8, done = true), set(2))))
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnSetDoneToggle(setId = 1, weight = "", reps = ""))

        assertTrue(restTimer.starts.isEmpty())
    }

    @Test
    fun `the bar shows this workout's rest only, and its buttons reach the timer`() = runTest(dispatcher) {
        val vm = viewModel()

        restTimer.state.value = RestTimerState(workoutId = 99, endsAtElapsed = 5_000, totalMillis = 60_000, next = NextUp.Nothing)
        assertNull(vm.state.value.rest)

        restTimer.state.value = RestTimerState(WORKOUT_ID, endsAtElapsed = 5_000, totalMillis = 60_000, next = NextUp.Set(3))
        assertEquals(RestUi(5_000, 60_000, nextSetNumber = 3, nextExerciseName = null), vm.state.value.rest)

        vm.onAction(ActiveWorkoutAction.OnRestAdjust(15))
        vm.onAction(ActiveWorkoutAction.OnRestSkip)
        assertEquals(listOf(15), restTimer.adjustments)
        assertEquals(1, restTimer.skips)
    }

    @Test
    fun `allowing notifications shows the rest notification again`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnNotificationsAllowed)

        assertEquals(1, notificationRefreshes)
    }

    @Test
    fun `finishing or discarding stops the rest`() = runTest(dispatcher) {
        repository.session.value = session(bench(sets = listOf(set(1, 60.0, 8, done = true))))
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnFinishClick)
        assertEquals(listOf(WORKOUT_ID), restTimer.cancels)

        val other = viewModel()
        other.onAction(ActiveWorkoutAction.OnConfirmDiscard)
        assertEquals(listOf(WORKOUT_ID, WORKOUT_ID), restTimer.cancels)
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
    fun `finish with every set done needs no confirmation and keeps Finish disabled`() = runTest(dispatcher) {
        repository.session.value = session(bench(sets = listOf(set(1, 60.0, 8, done = true))))
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnFinishClick)
        vm.onAction(ActiveWorkoutAction.OnFinishClick)

        assertEquals("finished once", listOf(WORKOUT_ID), repository.finished)
        assertTrue("disabled until the summary opens", vm.state.value.isFinishing)
    }

    @Test
    fun `a failed finish re-enables Finish and says so`() = runTest(dispatcher) {
        repository.session.value = session(bench(sets = listOf(set(1, 60.0, 8, done = true))))
        repository.failFinish = true
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnFinishClick)

        assertEquals(ActiveWorkoutEvent.SaveFailed, vm.events.first())
        assertEquals(false, vm.state.value.isFinishing)
    }

    @Test
    fun `a workout that is already finished closes the screen`() = runTest(dispatcher) {
        repository.session.value = session(bench()).copy(endedAt = FixedDateProvider.now())
        val vm = viewModel()

        assertEquals(ActiveWorkoutEvent.Closed, vm.events.first())
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
    fun `tapping the name renames the workout`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnRenameClick)
        assertTrue(vm.state.value.dialog is ActiveWorkoutDialog.Rename)
        vm.onAction(ActiveWorkoutAction.OnRenameConfirm("Leg Day"))

        assertEquals(null, vm.state.value.dialog)
        assertEquals(listOf("Leg Day"), repository.renames)
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

    // Quick log

    @Test
    fun `a quick-logged line is shown, then added into the exercise's open sets`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnQuickLogSubmit("bench 3x8 at 60"))
        val preview = vm.state.value.quickLog.preview!!
        assertEquals("Bench Press", preview.exerciseName)
        assertEquals("bench", preview.matchedFrom)
        assertEquals(List(3) { SetFact(60.0, 8, null) }, preview.sets)
        assertTrue(repository.loggedSets.isEmpty()) // nothing until confirmed

        vm.onAction(ActiveWorkoutAction.OnQuickLogConfirm)

        assertEquals(listOf(Triple(BENCH, 10L, List(3) { SetFact(60.0, 8, null) })), repository.loggedSets)
        assertEquals(null, vm.state.value.quickLog.preview)
        assertTrue(restTimer.starts.isEmpty()) // a quick log is after the fact: no rest
    }

    @Test
    fun `a line it can't use says why, until it's changed`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnQuickLogSubmit("zercher squat 3x5 at 80"))
        assertEquals(QuickLogResult.Reason.NoExercise, vm.state.value.quickLog.problem)

        // The same line (as when a spoken one is filled in and read at once): still said.
        vm.onAction(ActiveWorkoutAction.OnQuickLogEdited("zercher squat 3x5 at 80"))
        assertEquals(QuickLogResult.Reason.NoExercise, vm.state.value.quickLog.problem)

        vm.onAction(ActiveWorkoutAction.OnQuickLogEdited("zercher squat 3x5 at 8"))
        assertEquals(null, vm.state.value.quickLog.problem)
    }

    @Test
    fun `edit drops the card without adding anything`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(ActiveWorkoutAction.OnQuickLogSubmit("60 for 8")) // the open exercise

        vm.onAction(ActiveWorkoutAction.OnQuickLogEdit)
        vm.onAction(ActiveWorkoutAction.OnQuickLogConfirm)

        assertEquals(null, vm.state.value.quickLog.preview)
        assertTrue(repository.loggedSets.isEmpty())
    }

    @Test
    fun `listening shows what's heard, and a pause after it reads the line`() = runTest(dispatcher) {
        speech.availability = ModelAvailability.Ready
        speech.script = listOf(Heard.Partial("bench three"), Heard.Final("bench three sets of eight at sixty"))
        val vm = viewModel()
        val events = mutableListOf<ActiveWorkoutEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        assertTrue(vm.state.value.onDeviceSpeech)

        vm.onAction(ActiveWorkoutAction.OnStartListening)
        advanceTimeBy(350)
        assertEquals(QuickLogUi(isListening = true, heard = "bench three"), vm.state.value.quickLog)
        advanceTimeBy(300)
        assertEquals("bench three sets of eight at sixty", vm.state.value.quickLog.heard)

        advanceTimeBy(1_300) // the pause after it
        assertEquals(1, speech.stops)
        assertEquals(List(3) { SetFact(60.0, 8, null) }, vm.state.value.quickLog.preview?.sets)
        assertTrue(ActiveWorkoutEvent.QuickLogHeard("bench three sets of eight at sixty") in events)
        assertTrue(fixer.asked.isEmpty()) // understood as heard: no proofreading
    }

    @Test
    fun `a line not understood as heard is proofread once, and kept if it then is`() = runTest(dispatcher) {
        speech.availability = ModelAvailability.Ready
        speech.script = listOf(Heard.Final("bench tree sets of ate at sixty"))
        fixer.fixes = mapOf("bench tree sets of ate at sixty" to "bench three sets of eight at sixty")
        val vm = viewModel()
        val events = mutableListOf<ActiveWorkoutEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }

        vm.onAction(ActiveWorkoutAction.OnStartListening)
        advanceTimeBy(2_000)

        assertEquals(listOf("bench tree sets of ate at sixty"), fixer.asked)
        assertEquals(3, vm.state.value.quickLog.preview?.sets?.size)
        assertEquals(ActiveWorkoutEvent.QuickLogHeard("bench three sets of eight at sixty"), events.last())
    }

    @Test
    fun `silence, or a failing mic, says so`() = runTest(dispatcher) {
        speech.availability = ModelAvailability.Ready
        val vm = viewModel()

        vm.onAction(ActiveWorkoutAction.OnStartListening)
        vm.onAction(ActiveWorkoutAction.OnStopListening)
        advanceTimeBy(100)
        assertEquals(MicProblem.NothingHeard, vm.state.value.quickLog.micProblem)

        speech.failure = IllegalStateException("no mic")
        vm.onAction(ActiveWorkoutAction.OnStartListening)
        advanceTimeBy(100)
        assertEquals(MicProblem.Failed, vm.state.value.quickLog.micProblem)
    }

    @Test
    fun `using the phone's recognizer gets on-device speech ready once`() = runTest(dispatcher) {
        speech.availability = ModelAvailability.Downloadable
        val vm = viewModel()
        assertEquals(false, vm.state.value.onDeviceSpeech)

        vm.onAction(ActiveWorkoutAction.OnPhoneSpeechUsed)
        vm.onAction(ActiveWorkoutAction.OnPhoneSpeechUsed)
        advanceTimeBy(100)

        assertEquals(1, speech.downloads)
    }

    private object FakeLibrary : ExerciseRepository {
        private val library = listOf(bench().exercise, press().exercise, plank().exercise)
        override fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>> = flowOf(library)
        override fun observeMuscleGroups(): Flow<List<String>> = flowOf(emptyList())
        override fun observeRecentExercises(limit: Int): Flow<List<RecentExercise>> = flowOf(emptyList())
        override fun observeExerciseCount(): Flow<Int> = flowOf(library.size)
        override suspend fun createExercise(exercise: NewExercise): CreateExerciseResult = CreateExerciseResult.Created(0)
        override suspend fun getExercises(ids: List<Long>): List<Exercise> = emptyList()
        override fun observeExercise(id: Long): Flow<Exercise?> = flowOf(null)
    }

    private class FakeWorkoutRepository : StubWorkoutRepository() {
        val loggedSets = mutableListOf<Triple<Long?, Long, List<SetFact>>>()

        override suspend fun logSets(workoutId: Long, workoutExerciseId: Long?, exerciseId: Long, sets: List<SetFact>, completedAt: Instant): Long {
            loggedSets += Triple(workoutExerciseId, exerciseId, sets)
            return workoutExerciseId ?: 0
        }

        val session = MutableStateFlow<WorkoutSession?>(session(bench(), press()))
        val completions = mutableListOf<Completion>()
        val valueUpdates = mutableListOf<Values>()
        val finished = mutableListOf<Long>()
        val discarded = mutableListOf<Long>()
        val removed = mutableListOf<Long>()
        val added = mutableListOf<List<Long>>()
        val startTimes = mutableListOf<Instant>()
        val renames = mutableListOf<String>()

        override suspend fun renameWorkout(workoutId: Long, name: String) {
            renames += name
        }

        override fun observeSession(workoutId: Long): Flow<WorkoutSession?> = session

        override suspend fun setCompleted(setId: Long, completedAt: Instant?, weightKg: Double?, reps: Int?, durationSec: Int?) {
            completions += Completion(setId, completedAt != null, weightKg, reps, durationSec)
        }

        override suspend fun updateSetValues(setId: Long, weightKg: Double?, reps: Int?, durationSec: Int?) {
            valueUpdates += Values(setId, weightKg, reps, durationSec)
        }

        var failFinish = false

        override suspend fun finishWorkout(workoutId: Long, endedAt: Instant): Boolean {
            if (failFinish) error("disk full")
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
            templateId = null,
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
