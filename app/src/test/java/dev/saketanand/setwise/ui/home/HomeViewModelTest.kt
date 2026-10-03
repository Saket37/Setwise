package dev.saketanand.setwise.ui.home

import dev.saketanand.setwise.domain.model.ActiveWorkout
import dev.saketanand.setwise.domain.model.DayStatus
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.FinishedWorkout
import dev.saketanand.setwise.domain.model.RecentExercise
import dev.saketanand.setwise.domain.model.Template
import dev.saketanand.setwise.domain.model.WorkoutHistoryItem
import dev.saketanand.setwise.domain.model.WorkoutStats
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.TemplateRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import dev.saketanand.setwise.testing.FakeDayMarkRepository
import dev.saketanand.setwise.testing.FakeUserSettingsRepository
import dev.saketanand.setwise.testing.StubWorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val workouts = FakeWorkoutRepository()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val marks = FakeDayMarkRepository()
    private val settings = FakeUserSettingsRepository()

    private fun viewModel() =
        HomeViewModel(FakeExerciseRepository, workouts, FakeTemplateRepository, marks, settings, FixedDateProvider)

    // Day check-in. Today is Sat 3 Oct; the only workout was Wed 30 Sep.

    @Test
    fun `check-in asks about the unlogged days since the first workout, once a day`() = runTest {
        workouts.history.value = listOf(historyItem(LocalDate.of(2026, 9, 30)))

        val vm = viewModel()

        assertEquals(
            listOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)),
            vm.state.value.checkIn?.days?.map { it.date },
        )
        assertEquals(LocalDate.of(2026, 10, 3), settings.settings.value.checkInLastAskedOn)

        vm.onAction(HomeAction.OnCheckInDismiss)
        assertNull(vm.state.value.checkIn)
        assertNull(viewModel().state.value.checkIn) // asked today already
    }

    @Test
    fun `check-in isn't shown during a workout, when switched off, or before the first workout`() = runTest {
        assertNull(viewModel().state.value.checkIn) // no workout yet

        workouts.history.value = listOf(historyItem(LocalDate.of(2026, 9, 30)))
        settings.setAskAboutUnloggedDays(false)
        assertNull(viewModel().state.value.checkIn)

        settings.setAskAboutUnloggedDays(true)
        workouts.active.value = ActiveWorkout(1, "Push Day", Instant.EPOCH, completedSets = 0)
        assertNull(viewModel().state.value.checkIn)
    }

    @Test
    fun `check-in answers show in the sheet, and mark all as rest fills the rest`() = runTest {
        workouts.history.value = listOf(historyItem(LocalDate.of(2026, 9, 30)))
        val vm = viewModel()

        vm.onAction(HomeAction.OnCheckInMark(LocalDate.of(2026, 10, 2), DayStatus.Missed))
        assertEquals(listOf(DayStatus.Missed, null), vm.state.value.checkIn?.days?.map { it.status })

        vm.onAction(HomeAction.OnCheckInMarkAllRest)
        assertNull(vm.state.value.checkIn)
        assertEquals(
            mapOf(LocalDate.of(2026, 10, 2) to DayStatus.Missed, LocalDate.of(2026, 10, 1) to DayStatus.Rest),
            marks.marks.value,
        )
    }

    @Test
    fun `check-in log workout starts one at 6 PM on that day`() = runTest {
        workouts.history.value = listOf(historyItem(LocalDate.of(2026, 9, 30)))
        val vm = viewModel()

        vm.onAction(HomeAction.OnCheckInLogWorkout(LocalDate.of(2026, 10, 1)))

        assertNull(vm.state.value.checkIn)
        assertEquals(HomeEvent.WorkoutStarted(100), vm.events.first())
        assertEquals(LocalDate.of(2026, 10, 1).atTime(18, 0).atZone(FixedDateProvider.zone).toInstant(), workouts.lastStartedAt)
    }

    private fun historyItem(day: LocalDate): WorkoutHistoryItem {
        val start = day.atTime(18, 0).atZone(FixedDateProvider.zone).toInstant()
        return WorkoutHistoryItem(1, "Push Day", start, start.plusSeconds(3600), 10, 1000.0, 0.0, 0, null)
    }

    @Test
    fun `no running workout - starting from a template starts it directly`() = runTest {
        val vm = viewModel()
        vm.onAction(HomeAction.OnStartFromTemplate(templateId = 3))

        assertNull(vm.state.value.discardDialog)
        assertEquals(listOf(StartCall(templateId = 3, discard = null)), workouts.startCalls)
        assertEquals(HomeEvent.WorkoutStarted(100), vm.events.first())
    }

    @Test
    fun `running workout - starting from a template asks first and starts nothing`() {
        workouts.active.value = ActiveWorkout(id = 7, name = "Pull Day", startedAt = Instant.EPOCH, completedSets = 4)
        val vm = viewModel()

        vm.onAction(HomeAction.OnStartFromTemplate(templateId = 3))

        val dialog = vm.state.value.discardDialog
        assertNotNull(dialog)
        assertEquals(7L, dialog!!.runningWorkoutId)
        assertEquals("Pull Day", dialog.runningWorkoutName)
        assertEquals(3L, dialog.templateIdToStart)
        assertTrue("nothing may start before confirming", workouts.startCalls.isEmpty())
    }

    @Test
    fun `starting takes the sheet out of the state, and cancelling the discard forgets the picked time`() {
        workouts.active.value = ActiveWorkout(id = 7, name = "Pull Day", startedAt = Instant.EPOCH, completedSets = 4)
        val vm = viewModel()
        vm.onAction(HomeAction.OnStartWorkoutClick)
        vm.onAction(HomeAction.OnStartTimeChange(LocalTime.of(17, 0)))

        vm.onAction(HomeAction.OnStartFromTemplate(templateId = 3))
        assertEquals(false, vm.state.value.isStartSheetVisible)
        assertEquals("kept for the start after confirming", LocalTime.of(17, 0), vm.state.value.customStartTime)

        vm.onAction(HomeAction.OnDismissDiscardDialog)
        assertNull(vm.state.value.customStartTime)
    }

    @Test
    fun `saving the last workout as a template creates it and reports it`() = runTest {
        FakeTemplateRepository.created.clear()
        val vm = viewModel()

        vm.onAction(HomeAction.OnSaveLastWorkoutAsTemplate(workoutId = 5))

        assertEquals(listOf(5L), FakeTemplateRepository.created)
        assertEquals(HomeEvent.TemplateCreated(40), vm.events.first())
    }

    @Test
    fun `confirm discards exactly the running workout and starts the new one`() = runTest {
        workouts.active.value = ActiveWorkout(id = 7, name = "Pull Day", startedAt = Instant.EPOCH, completedSets = 4)
        val vm = viewModel()
        vm.onAction(HomeAction.OnStartFromTemplate(templateId = 3))

        vm.onAction(HomeAction.OnConfirmDiscardAndStart)

        assertNull(vm.state.value.discardDialog)
        assertEquals(listOf(StartCall(templateId = 3, discard = 7)), workouts.startCalls)
        assertEquals(HomeEvent.WorkoutStarted(100), vm.events.first())
    }

    @Test
    fun `cancel keeps the running workout`() {
        workouts.active.value = ActiveWorkout(id = 7, name = "Pull Day", startedAt = Instant.EPOCH, completedSets = 4)
        val vm = viewModel()
        vm.onAction(HomeAction.OnStartEmptyWorkout)

        vm.onAction(HomeAction.OnDismissDiscardDialog)

        assertNull(vm.state.value.discardDialog)
        assertTrue(workouts.startCalls.isEmpty())
    }

    @Test
    fun `confirm without an open dialog does nothing`() {
        val vm = viewModel()
        vm.onAction(HomeAction.OnConfirmDiscardAndStart)
        assertTrue(workouts.startCalls.isEmpty())
    }

    @Test
    fun `a failed start reports an error and re-enables the buttons`() = runTest {
        workouts.failStart = true
        val vm = viewModel()

        vm.onAction(HomeAction.OnStartEmptyWorkout)

        assertEquals(HomeEvent.StartWorkoutFailed, vm.events.first())
        assertEquals(false, vm.state.value.isStartingWorkout)
    }

    // Fakes

    private data class StartCall(val templateId: Long?, val discard: Long?)

    private class FakeWorkoutRepository : StubWorkoutRepository() {
        val active = MutableStateFlow<ActiveWorkout?>(null)
        val history = MutableStateFlow<List<WorkoutHistoryItem>>(emptyList())
        var lastStartedAt: Instant? = null
        val startCalls = mutableListOf<StartCall>()
        var failStart = false

        override fun observeLastFinishedWorkout(): Flow<FinishedWorkout?> = flowOf(null)
        override fun observeActiveWorkout(): Flow<ActiveWorkout?> = active
        override fun observeHistory(): Flow<List<WorkoutHistoryItem>> = history
        override fun observeStats(from: Instant, to: Instant): Flow<WorkoutStats> =
            flowOf(WorkoutStats(workouts = 0, timeTrained = Duration.ZERO, prs = 0))

        override suspend fun startWorkout(templateId: Long?, startedAt: Instant, discardRunningWorkoutId: Long?): Long {
            if (failStart) error("database is full")
            startCalls += StartCall(templateId, discardRunningWorkoutId)
            lastStartedAt = startedAt
            return 100
        }
    }

    private object FakeTemplateRepository : TemplateRepository {
        val created = mutableListOf<Long>()
        override fun observeTemplates(): Flow<List<Template>> = flowOf(emptyList())
        override suspend fun createFromWorkout(workoutId: Long, createdAt: Instant): Long {
            created += workoutId
            return 40
        }
    }

    /** Always Saturday 3 Oct 2026; today() emits once (no midnight loop in tests). */
    private object FixedDateProvider : DateProvider {
        override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        override fun now(): Instant = LocalDate.of(2026, 10, 3).atTime(18, 30).atZone(zone).toInstant()
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 3))
    }

    private object FakeExerciseRepository : ExerciseRepository {
        override fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>> = flowOf(emptyList())
        override fun observeMuscleGroups(): Flow<List<String>> = flowOf(emptyList())
        override fun observeRecentExercises(limit: Int): Flow<List<RecentExercise>> = flowOf(emptyList())
        override fun observeExerciseCount(): Flow<Int> = flowOf(128)
        override fun observeExercise(id: Long): Flow<Exercise?> = flowOf(null)
    }
}
