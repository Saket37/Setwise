package dev.saketanand.setwise.ui.templates

import app.cash.turbine.test
import dev.saketanand.setwise.domain.ai.GoalPlanAssistant
import dev.saketanand.setwise.domain.model.CreateExerciseResult
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.GoalType
import dev.saketanand.setwise.domain.model.NewExercise
import dev.saketanand.setwise.domain.model.RecentExercise
import dev.saketanand.setwise.domain.model.Template
import dev.saketanand.setwise.domain.model.TemplateDraft
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.TemplateRepository
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.testing.FakeUserSettingsRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

class TemplateFromGoalViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val templates = Templates()
    private fun viewModel() = TemplateFromGoalViewModel(templates, Library, FakeUserSettingsRepository(), GoalPlanAssistant(FakeOnDeviceModel()), Dates)

    @Test
    fun `a goal with a weight to lose gets a note, and reads as fat loss`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(TemplateFromGoalAction.OnGoalChange("I need to loose 12kg weight,, how can I do in 2 months?"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(GoalType.FatLoss, state.understood?.type) // was Muscle (#125)
        assertEquals(12.0, state.advice?.advice?.target?.kg)
        assertEquals(null, state.advice?.modelText) // no model on this phone: the screen words it

        vm.onAction(TemplateFromGoalAction.OnGoalChange("get stronger at squat"))
        advanceUntilIdle()
        assertEquals(null, vm.state.value.advice)
    }

    @Test
    fun `a typed goal is read and drafted after a pause`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(TemplateFromGoalAction.OnGoalChange("get stronger at squat, 2 days a week"))
        assertTrue(vm.state.value.templates.isEmpty()) // still typing
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(GoalType.Strength, state.understood?.type)
        assertEquals(2, state.understood?.daysPerWeek)
        assertEquals(listOf("Strength A", "Strength B"), state.templates.map { it.name })
        assertEquals("Back Squat (Barbell)", state.templates.first().exercises.first().name)
        assertFalse(state.isChoosing) // no model on this phone
        assertTrue(state.canSave)
    }

    @Test
    fun `clearing the goal clears the draft`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(TemplateFromGoalAction.OnGoalChange("build muscle"))
        advanceUntilIdle()
        vm.onAction(TemplateFromGoalAction.OnGoalChange(" "))
        advanceUntilIdle()
        assertEquals(TemplateFromGoalUiState(), vm.state.value)
    }

    @Test
    fun `saving creates every template with target reps and opens the first`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(TemplateFromGoalAction.OnGoalChange("get stronger at squat, 2 days a week"))
        advanceUntilIdle()

        vm.events.test {
            vm.onAction(TemplateFromGoalAction.OnSaveClick)
            advanceUntilIdle()
            assertEquals(TemplateFromGoalEvent.Saved(1), awaitItem())
        }
        assertEquals(listOf("Strength A", "Strength B"), templates.saved.map { it.name })
        val squat = templates.saved.first().exercises.first()
        assertEquals(5, squat.targetReps)
        assertTrue(squat.targetSets >= 4)
    }

    @Test
    fun `regenerate keeps the named lift and changes the rest`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(TemplateFromGoalAction.OnGoalChange("get stronger at squat, 2 days a week"))
        advanceUntilIdle()
        val before = vm.state.value.templates

        vm.onAction(TemplateFromGoalAction.OnRegenerateClick)
        advanceUntilIdle()

        val after = vm.state.value.templates
        assertEquals(before.first().exercises.first(), after.first().exercises.first())
        assertTrue(before != after)
    }

    @Test
    fun `a failed save says so and saves nothing more`() = runTest(dispatcher) {
        templates.fail = true
        val vm = viewModel()
        vm.onAction(TemplateFromGoalAction.OnGoalChange("build muscle"))
        advanceUntilIdle()

        vm.events.test {
            vm.onAction(TemplateFromGoalAction.OnSaveClick)
            advanceUntilIdle()
            assertEquals(TemplateFromGoalEvent.SaveFailed, awaitItem())
        }
        assertFalse(vm.state.value.isSaving)
        assertNull(templates.saved.firstOrNull())
    }

    private class Templates : TemplateRepository {
        val saved = mutableListOf<TemplateDraft>()
        var fail = false
        override fun observeTemplates(): Flow<List<Template>> = flowOf(emptyList())
        override suspend fun getTemplate(templateId: Long): Template? = null
        override suspend fun saveTemplate(draft: TemplateDraft, now: Instant): Long {
            if (fail) error("Disk full")
            saved += draft
            return saved.size.toLong()
        }
        override suspend fun deleteTemplate(templateId: Long) = Unit
        override suspend fun createFromWorkout(workoutId: Long, createdAt: Instant): Long = 0
    }

    /** A small library: enough for strength full-body days. */
    private object Library : ExerciseRepository {
        private var id = 0L
        private fun e(name: String, muscle: String, equipment: String, timed: Boolean = false) =
            Exercise(++id, name, ExerciseType.STRENGTH, muscle, equipment, 90, timed, false, null, null, null)
        private val all = listOf(
            e("Back Squat (Barbell)", "Quads", "Barbell"), e("Front Squat (Barbell)", "Quads", "Barbell"),
            e("Bench Press (Barbell)", "Chest", "Barbell"), e("Bench Press (Dumbbell)", "Chest", "Dumbbell"),
            e("Bent-over Row (Barbell)", "Back", "Barbell"), e("Single-arm Row (Dumbbell)", "Back", "Dumbbell"),
            e("Deadlift (Barbell)", "Back", "Barbell"), e("Romanian Deadlift (Barbell)", "Hamstrings", "Barbell"),
            e("Overhead Press (Barbell)", "Shoulders", "Barbell"), e("Lat Pulldown (Wide Grip)", "Back", "Cable"),
            e("Incline Bench Press (Dumbbell)", "Chest", "Dumbbell"), e("Plank", "Core", "Bodyweight", timed = true),
        )
        override fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>> = flowOf(all)
        override fun observeMuscleGroups(): Flow<List<String>> = flowOf(emptyList())
        override fun observeRecentExercises(limit: Int): Flow<List<RecentExercise>> = flowOf(emptyList())
        override fun observeExerciseCount(): Flow<Int> = flowOf(all.size)
        override suspend fun createExercise(exercise: NewExercise): CreateExerciseResult = CreateExerciseResult.Created(0)
        override suspend fun getExercises(ids: List<Long>): List<Exercise> = emptyList()
        override fun observeExercise(id: Long): Flow<Exercise?> = flowOf(null)
    }

    private object Dates : DateProvider {
        override val zone: ZoneId = ZoneId.of("UTC")
        override fun now(): Instant = Instant.parse("2026-10-05T09:00:00Z")
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 5))
    }
}
