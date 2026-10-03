package dev.saketanand.setwise.ui.summary

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.Template
import dev.saketanand.setwise.domain.model.TemplateDraft
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutSet
import dev.saketanand.setwise.domain.repository.TemplateRepository
import dev.saketanand.setwise.testing.StubWorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class WorkoutSummaryViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val workouts = FakeWorkoutRepository()
    private val templates = FakeTemplateRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = WorkoutSummaryViewModel(WORKOUT_ID, workouts, templates, FixedDateProvider).also { vm ->
        backgroundScope.launch { vm.state.collect {} }
    }

    @Test
    fun `save as template creates it once`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(WorkoutSummaryAction.OnSaveAsTemplateClick)
        vm.onAction(WorkoutSummaryAction.OnSaveAsTemplateClick)

        assertEquals(listOf(WORKOUT_ID), templates.created)
        assertTrue(vm.state.value.isTemplateSaved)
        assertEquals(WorkoutSummaryEvent.TemplateSaved, vm.events.first())
    }

    @Test
    fun `edited times are saved when they make sense`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(WorkoutSummaryAction.OnEditTimesClick)
        vm.onAction(WorkoutSummaryAction.OnPickTime(TimeField.Start))
        vm.onAction(WorkoutSummaryAction.OnTimePicked(LocalTime.of(18, 0)))
        vm.onAction(WorkoutSummaryAction.OnSaveTimes)

        assertNull(vm.state.value.editTimes)
        assertEquals(listOf(at(18, 0) to at(19, 51)), workouts.timeUpdates)
    }

    @Test
    fun `times that end before they start are rejected with a message`() = runTest(dispatcher) {
        // The workout is today; an end at 18:00 (before the 18:42 start) would mean tomorrow,
        // which is in the future, so it becomes now (18:30): still before the start.
        workouts.session.value = session(startedAt = today(18, 0), endedAt = today(18, 20))
        val vm = viewModel()

        vm.onAction(WorkoutSummaryAction.OnEditTimesClick)
        vm.onAction(WorkoutSummaryAction.OnPickTime(TimeField.Start))
        vm.onAction(WorkoutSummaryAction.OnTimePicked(LocalTime.of(18, 42)))
        vm.onAction(WorkoutSummaryAction.OnSaveTimes)

        assertTrue(vm.state.value.editTimes!!.isInvalid)
        assertTrue(workouts.timeUpdates.isEmpty())
    }

    @Test
    fun `a workout that isn't finished closes the screen`() = runTest(dispatcher) {
        workouts.session.value = session(endedAt = null)
        val vm = viewModel()

        assertEquals(WorkoutSummaryEvent.Closed, vm.events.first())
    }

    private class FakeWorkoutRepository : StubWorkoutRepository() {
        val session = MutableStateFlow<WorkoutSession?>(session())
        val timeUpdates = mutableListOf<Pair<Instant, Instant>>()
        override fun observeSession(workoutId: Long): Flow<WorkoutSession?> = session
        override suspend fun updateFinishedTimes(workoutId: Long, startedAt: Instant, endedAt: Instant): Boolean {
            timeUpdates += startedAt to endedAt
            return true
        }
    }

    private class FakeTemplateRepository : TemplateRepository {
        val created = mutableListOf<Long>()
        override fun observeTemplates(): Flow<List<Template>> = flowOf(emptyList())
        override suspend fun getTemplate(templateId: Long): Template? = null
        override suspend fun saveTemplate(draft: TemplateDraft, now: Instant): Long = 0
        override suspend fun deleteTemplate(templateId: Long) = Unit
        override suspend fun createFromWorkout(workoutId: Long, createdAt: Instant): Long {
            created += workoutId
            return 1
        }
    }

    /** Saturday 3 Oct 2026, 18:30 in India. */
    private object FixedDateProvider : DateProvider {
        override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        override fun now(): Instant = today(18, 30)
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 3))
    }

    private companion object {
        const val WORKOUT_ID = 7L

        fun at(h: Int, m: Int): Instant = LocalDateTime.of(2026, 10, 2, h, m).atZone(ZoneId.of("Asia/Kolkata")).toInstant()
        fun today(h: Int, m: Int): Instant = LocalDateTime.of(2026, 10, 3, h, m).atZone(ZoneId.of("Asia/Kolkata")).toInstant()

        fun session(startedAt: Instant = at(18, 42), endedAt: Instant? = at(19, 51)) = WorkoutSession(
            id = WORKOUT_ID, name = "Workout", templateId = null, startedAt = startedAt, endedAt = endedAt,
            exercises = listOf(
                SessionExercise(
                    id = 1,
                    exercise = Exercise(1, "Bench", ExerciseType.STRENGTH, "Chest", "Barbell", 90, false, false, null, null, null),
                    sets = listOf(WorkoutSet(1, 1, 60.0, 8, null, isCompleted = true, isPr = false)),
                    previousSets = emptyList(),
                ),
            ),
        )
    }
}
