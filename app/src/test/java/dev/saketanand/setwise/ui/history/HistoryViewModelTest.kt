package dev.saketanand.setwise.ui.history

import androidx.lifecycle.SavedStateHandle
import dev.saketanand.setwise.domain.ai.ExerciseAssistant
import dev.saketanand.setwise.domain.ai.HistoryAssistant
import dev.saketanand.setwise.domain.ai.HistoryReply
import dev.saketanand.setwise.domain.model.ActiveWorkout
import dev.saketanand.setwise.domain.model.CreateExerciseResult
import dev.saketanand.setwise.domain.model.DayStatus
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.HistoryAnswer
import dev.saketanand.setwise.domain.model.LoggedSetRecord
import dev.saketanand.setwise.domain.model.NewExercise
import dev.saketanand.setwise.domain.model.RecentExercise
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.testing.FakeDayMarkRepository
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.testing.FakeUserSettingsRepository
import dev.saketanand.setwise.testing.StubWorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
class HistoryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        workouts: StubWorkoutRepository = StubWorkoutRepository(),
        marks: FakeDayMarkRepository = FakeDayMarkRepository(),
    ) =
        HistoryViewModel(
            workouts, marks, FakeUserSettingsRepository(), FixedDateProvider, handle,
            HistoryAssistant(model, ExerciseAssistant(model)), Library,
        ).also { vm ->
            backgroundScope.launch { vm.state.collect {} }
        }

    private val model = FakeOnDeviceModel()

    @Test
    fun `a question is looked up in the training log, and closing it goes back`() = runTest(dispatcher) {
        val day = LocalDate.of(2026, 9, 28).atTime(18, 0).atZone(FixedDateProvider.zone).toInstant()
        val logged = object : StubWorkoutRepository() {
            override suspend fun getTrainingLog() = listOf(
                LoggedSetRecord(10, "Leg Day", day, SQUAT.id, SQUAT.name, "Quads", 1, 100.0, 5, null, null, isPr = true),
            )
        }
        val vm = viewModel(workouts = logged)

        vm.onAction(HistoryAction.OnAsk("When did I last squat 100 kg?"))

        val reply = vm.state.value.ask.reply as HistoryReply.Answered
        assertEquals(10L, (reply.answer as HistoryAnswer.Lifted).workout.id)
        assertTrue(vm.state.value.ask.isOpen)

        vm.onAction(HistoryAction.OnAskClosed)
        assertEquals(AskUi(), vm.state.value.ask)
    }

    private object Library : ExerciseRepository {
        override fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>> = flowOf(listOf(SQUAT))
        override fun observeMuscleGroups(): Flow<List<String>> = flowOf(emptyList())
        override fun observeRecentExercises(limit: Int): Flow<List<RecentExercise>> = flowOf(emptyList())
        override fun observeExerciseCount(): Flow<Int> = flowOf(1)
        override suspend fun createExercise(exercise: NewExercise): CreateExerciseResult = CreateExerciseResult.Created(0)
        override suspend fun getExercises(ids: List<Long>): List<Exercise> = emptyList()
        override fun observeExercise(id: Long): Flow<Exercise?> = flowOf(null)
    }

    @Test
    fun `tapping a day selects it, tapping it again clears it`() = runTest(dispatcher) {
        val vm = viewModel()
        val day = LocalDate.of(2026, 9, 30)

        vm.onAction(HistoryAction.OnDayClick(day))
        assertEquals(day, vm.state.value.selectedDate)

        vm.onAction(HistoryAction.OnDayClick(LocalDate.of(2026, 9, 29)))
        assertEquals(LocalDate.of(2026, 9, 29), vm.state.value.selectedDate)

        vm.onAction(HistoryAction.OnDayClick(LocalDate.of(2026, 9, 29)))
        assertNull(vm.state.value.selectedDate)
    }

    @Test
    fun `a day picked in the calendar stays selected when picked again`() = runTest(dispatcher) {
        val vm = viewModel()
        val day = LocalDate.of(2026, 9, 12)

        vm.onAction(HistoryAction.OnCalendarDayClick(day))
        vm.onAction(HistoryAction.OnCalendarDayClick(day))

        assertEquals(day, vm.state.value.selectedDate)
    }

    @Test
    fun `the selected day survives the app being killed`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        viewModel(handle).onAction(HistoryAction.OnDayClick(LocalDate.of(2026, 9, 30)))

        assertEquals(LocalDate.of(2026, 9, 30), viewModel(handle).state.value.selectedDate)
    }

    @Test
    fun `marking a day rest, then tapping it again, clears it`() = runTest(dispatcher) {
        val marks = FakeDayMarkRepository()
        val vm = viewModel(marks = marks)
        val day = LocalDate.of(2026, 10, 1)

        vm.onAction(HistoryAction.OnMarkDay(day, DayStatus.Rest))
        assertEquals(mapOf(day to DayStatus.Rest), marks.marks.value)

        vm.onAction(HistoryAction.OnMarkDay(day, null))
        assertEquals(emptyMap<LocalDate, DayStatus>(), marks.marks.value)
    }

    @Test
    fun `log workout starts one at 6 PM on that day`() = runTest(dispatcher) {
        val recording = object : StubWorkoutRepository() {
            override suspend fun startWorkout(templateId: Long?, startedAt: Instant, discardRunningWorkoutId: Long?): Long {
                started = startedAt
                return 7
            }
        }
        val vm = viewModel(workouts = recording)

        vm.onAction(HistoryAction.OnLogWorkoutClick(LocalDate.of(2026, 10, 1)))

        assertEquals(HistoryEvent.WorkoutStarted(7), vm.events.first())
        assertEquals(LocalDate.of(2026, 10, 1).atTime(18, 0).atZone(FixedDateProvider.zone).toInstant(), started)
    }

    @Test
    fun `log workout doesn't start one while another is running`() = runTest(dispatcher) {
        val running = object : StubWorkoutRepository() {
            override fun observeActiveWorkout(): Flow<ActiveWorkout?> =
                flowOf(ActiveWorkout(1, "Push Day", FixedDateProvider.now(), completedSets = 3))
            override suspend fun startWorkout(templateId: Long?, startedAt: Instant, discardRunningWorkoutId: Long?): Long =
                error("must not start")
        }
        val vm = viewModel(workouts = running)

        vm.onAction(HistoryAction.OnLogWorkoutClick(LocalDate.of(2026, 10, 1)))

        assertEquals(HistoryEvent.WorkoutAlreadyRunning, vm.events.first())
    }

    private var started: Instant? = null

    /** Saturday 3 Oct 2026. */
    private companion object {
        val SQUAT = Exercise(1, "Back Squat (Barbell)", ExerciseType.STRENGTH, "Quads", "Barbell", 120, false, false, null, null, null)
    }

    private object FixedDateProvider : DateProvider {
        override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        override fun now(): Instant = LocalDate.of(2026, 10, 3).atTime(18, 30).atZone(zone).toInstant()
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 3))
    }
}
