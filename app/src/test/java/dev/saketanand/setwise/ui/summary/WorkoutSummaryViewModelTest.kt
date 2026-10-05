package dev.saketanand.setwise.ui.summary

import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.ai.WorkoutInsightWriter
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.Template
import dev.saketanand.setwise.domain.model.TemplateDraft
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutSet
import dev.saketanand.setwise.domain.repository.TemplateRepository
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.testing.FakeUserSettingsRepository
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val settings = FakeUserSettingsRepository()
    private val model = FakeOnDeviceModel()

    private fun TestScope.viewModel() = WorkoutSummaryViewModel(WORKOUT_ID, workouts, templates, settings, WorkoutInsightWriter(model), FixedDateProvider).also { vm ->
        backgroundScope.launch { vm.state.collect {} }
    }

    @Test
    fun `delete asks first, then deletes and closes the summary`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(WorkoutSummaryAction.OnDeleteClick)
        assertEquals(true, vm.state.value.isConfirmingDelete)
        vm.onAction(WorkoutSummaryAction.OnConfirmDelete)

        assertEquals(WorkoutSummaryEvent.Closed, vm.events.first())
    }

    @Test
    fun `without a body weight the calories tile asks for it, and saving it goes to settings`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(true, vm.state.value.needsBodyWeight)

        vm.onAction(WorkoutSummaryAction.OnAddBodyWeightClick)
        vm.onAction(WorkoutSummaryAction.OnSaveBodyWeight("abc"))
        assertEquals(true, vm.state.value.bodyWeightDialog?.isInvalid)

        vm.onAction(WorkoutSummaryAction.OnSaveBodyWeight("72,5"))
        assertEquals(null, vm.state.value.bodyWeightDialog)
        assertEquals(72.5, settings.settings.value.bodyWeightKg)
        assertEquals(false, vm.state.value.needsBodyWeight)
    }

    @Test
    fun `without the model the insight card uses the template, with the model its text is saved`() = runTest(dispatcher) {
        assertEquals(null, viewModel().state.value.insight?.modelText) // no model: template

        model.availability = ModelAvailability.Ready
        model.answer = { "A steady session." }
        viewModel()

        assertEquals(listOf(WORKOUT_ID to "A steady session."), workouts.insights)
        assertEquals(1, model.requests.size) // asked once
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

    @Test
    fun `the workout is renamed from its dialog`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(WorkoutSummaryAction.OnRenameClick)
        assertTrue(vm.state.value.isRenaming)
        vm.onAction(WorkoutSummaryAction.OnRenameDismiss)
        assertFalse(vm.state.value.isRenaming)

        vm.onAction(WorkoutSummaryAction.OnRenameClick)
        vm.onAction(WorkoutSummaryAction.OnRenameConfirm("Heavy push"))
        assertFalse(vm.state.value.isRenaming)
        assertEquals(listOf("Heavy push"), workouts.renames)
    }

    @Test
    fun `a rename or delete that fails says so`() = runTest(dispatcher) {
        workouts.failWrites = true
        val vm = viewModel()

        vm.onAction(WorkoutSummaryAction.OnRenameConfirm("Heavy push"))
        assertEquals(WorkoutSummaryEvent.SaveFailed, vm.events.first())

        vm.onAction(WorkoutSummaryAction.OnDeleteClick)
        vm.onAction(WorkoutSummaryAction.OnConfirmDelete)
        assertEquals(WorkoutSummaryEvent.SaveFailed, vm.events.first())
        assertFalse(vm.state.value.isConfirmingDelete)
    }

    @Test
    fun `a template save that fails says so and can be tried again`() = runTest(dispatcher) {
        templates.fail = true
        val vm = viewModel()

        vm.onAction(WorkoutSummaryAction.OnSaveAsTemplateClick)
        assertEquals(WorkoutSummaryEvent.SaveFailed, vm.events.first())

        templates.fail = false
        vm.onAction(WorkoutSummaryAction.OnSaveAsTemplateClick)
        assertEquals(WorkoutSummaryEvent.TemplateSaved, vm.events.first())
        assertEquals(listOf(WORKOUT_ID), templates.created)
    }

    @Test
    fun `a summary that fails to load shows the screen, not a spinner`() = runTest(dispatcher) {
        workouts.failReads = true
        assertFalse(viewModel().state.value.isLoading)
    }

    @Test
    fun `the end time is picked too, and a time with no picker open is ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(WorkoutSummaryAction.OnEditTimesClick)

        vm.onAction(WorkoutSummaryAction.OnTimePicked(LocalTime.of(20, 0))) // no picker open
        assertEquals(LocalTime.of(19, 51), vm.state.value.editTimes?.end)

        vm.onAction(WorkoutSummaryAction.OnPickTime(TimeField.End))
        vm.onAction(WorkoutSummaryAction.OnTimePicked(LocalTime.of(20, 0)))
        assertEquals(LocalTime.of(20, 0), vm.state.value.editTimes?.end)
        assertNull(vm.state.value.editTimes?.picking)
    }

    private class FakeWorkoutRepository : StubWorkoutRepository() {
        val session = MutableStateFlow<WorkoutSession?>(session())
        val timeUpdates = mutableListOf<Pair<Instant, Instant>>()
        val renames = mutableListOf<String>()
        var failReads = false
        var failWrites = false
        override fun observeSession(workoutId: Long): Flow<WorkoutSession?> =
            if (failReads) flow { error("Database closed") } else session
        override suspend fun renameWorkout(workoutId: Long, name: String) {
            if (failWrites) error("Disk full")
            renames += name
        }
        val insights = mutableListOf<Pair<Long, String>>()
        override suspend fun setInsight(workoutId: Long, insight: String) {
            insights += workoutId to insight
            session.value = session.value?.copy(insight = insight)
        }
        override suspend fun deleteFinishedWorkout(workoutId: Long): Boolean {
            if (failWrites) error("Disk full")
            session.value = null // like Room: the observed workout is gone
            return true
        }
        override suspend fun updateFinishedTimes(workoutId: Long, startedAt: Instant, endedAt: Instant): Boolean {
            timeUpdates += startedAt to endedAt
            return true
        }
    }

    private class FakeTemplateRepository : TemplateRepository {
        val created = mutableListOf<Long>()
        var fail = false
        override fun observeTemplates(): Flow<List<Template>> = flowOf(emptyList())
        override suspend fun getTemplate(templateId: Long): Template? = null
        override suspend fun saveTemplate(draft: TemplateDraft, now: Instant): Long = 0
        override suspend fun deleteTemplate(templateId: Long) = Unit
        override suspend fun createFromWorkout(workoutId: Long, createdAt: Instant): Long {
            if (fail) error("Disk full")
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
