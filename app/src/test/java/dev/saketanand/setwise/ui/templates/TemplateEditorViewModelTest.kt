package dev.saketanand.setwise.ui.templates

import androidx.lifecycle.SavedStateHandle
import dev.saketanand.setwise.domain.model.CreateExerciseResult
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.NewExercise
import dev.saketanand.setwise.domain.model.RecentExercise
import dev.saketanand.setwise.domain.model.Template
import dev.saketanand.setwise.domain.model.TemplateDraft
import dev.saketanand.setwise.domain.model.TemplateExercise
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.TemplateRepository
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateEditorViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val templates = FakeTemplateRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(templateId: Long = Route.NEW_TEMPLATE_ID, handle: SavedStateHandle = SavedStateHandle()) =
        TemplateEditorViewModel(templateId, templates, FakeExerciseRepository, FixedDateProvider, handle)

    @Test
    fun `a new template saves once it has a name and an exercise`() = runTest(dispatcher) {
        val vm = viewModel()
        assertFalse(vm.state.value.canSave)

        vm.onAction(TemplateEditorAction.OnNameChange("Legs"))
        assertFalse(vm.state.value.canSave) // no exercise yet
        vm.onAction(TemplateEditorAction.OnExercisesPicked(listOf(2, 1)))
        assertTrue(vm.state.value.canSave)
        vm.onAction(TemplateEditorAction.OnSaveClick)

        assertEquals(TemplateEditorEvent.Saved, vm.events.first())
        val saved = templates.saved.single()
        assertEquals(0L, saved.id)
        assertEquals(listOf(2L, 1L), saved.exercises.map { it.exerciseId })
    }

    @Test
    fun `an existing template opens with its exercises, and back without changes just closes`() = runTest(dispatcher) {
        val vm = viewModel(templateId = 5)

        assertEquals("Push Day", vm.state.value.draft.name)
        assertEquals(listOf(1L), vm.state.value.draft.exercises.map { it.exerciseId })
        assertFalse(vm.state.value.hasChanges)

        vm.onAction(TemplateEditorAction.OnBackClick)
        assertEquals(TemplateEditorEvent.Closed, vm.events.first())
    }

    @Test
    fun `back with changes asks first, and changing it back counts as no change`() = runTest(dispatcher) {
        val vm = viewModel(templateId = 5)

        vm.onAction(TemplateEditorAction.OnSetsChange(1, +1))
        vm.onAction(TemplateEditorAction.OnBackClick)
        assertEquals(TemplateEditorDialog.DiscardChanges, vm.state.value.dialog)

        vm.onAction(TemplateEditorAction.OnDialogDismiss)
        vm.onAction(TemplateEditorAction.OnSetsChange(1, -1))
        assertFalse(vm.state.value.hasChanges)
    }

    @Test
    fun `the draft survives the app being killed`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        viewModel(templateId = 5, handle = handle).onAction(TemplateEditorAction.OnNameChange("Push Day v2"))

        val restored = viewModel(templateId = 5, handle = handle).state.value

        assertEquals("Push Day v2", restored.draft.name)
        assertTrue(restored.hasChanges)
    }

    @Test
    fun `delete asks first, then deletes and closes`() = runTest(dispatcher) {
        val vm = viewModel(templateId = 5)

        vm.onAction(TemplateEditorAction.OnDeleteClick)
        assertEquals(TemplateEditorDialog.Delete, vm.state.value.dialog)
        vm.onAction(TemplateEditorAction.OnConfirmDelete)

        assertEquals(TemplateEditorEvent.Closed, vm.events.first())
        assertEquals(listOf(5L), templates.deleted)
    }

    private class FakeTemplateRepository : TemplateRepository {
        val saved = mutableListOf<TemplateDraft>()
        val deleted = mutableListOf<Long>()

        override fun observeTemplates(): Flow<List<Template>> = flowOf(emptyList())
        override suspend fun getTemplate(templateId: Long): Template? =
            if (templateId == 5L) {
                Template(5, "Push Day", "Push", listOf(TemplateExercise(1, "Bench Press", 3, 90, "Chest")), lastUsedAt = null)
            } else {
                null
            }
        override suspend fun saveTemplate(draft: TemplateDraft, now: Instant): Long = 7L.also { saved += draft }
        override suspend fun deleteTemplate(templateId: Long) { deleted += templateId }
        override suspend fun createFromWorkout(workoutId: Long, createdAt: Instant): Long = 0
    }

    private object FakeExerciseRepository : ExerciseRepository {
        private fun exercise(id: Long) =
            Exercise(id, "Exercise $id", ExerciseType.STRENGTH, "Legs", "Barbell", 90, isTimed = false, isCustom = false, metrics = null, calorieMethod = null, met = null)

        override fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>> = flowOf(emptyList())
        override fun observeMuscleGroups(): Flow<List<String>> = flowOf(emptyList())
        override fun observeRecentExercises(limit: Int): Flow<List<RecentExercise>> = flowOf(emptyList())
        override fun observeExerciseCount(): Flow<Int> = flowOf(0)
        override suspend fun getExercises(ids: List<Long>): List<Exercise> = ids.map(::exercise)
        override suspend fun createExercise(exercise: NewExercise): CreateExerciseResult = CreateExerciseResult.Created(0)
        override fun observeExercise(id: Long): Flow<Exercise?> = flowOf(exercise(id))
    }

    private object FixedDateProvider : DateProvider {
        override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        override fun now(): Instant = LocalDate.of(2026, 10, 3).atTime(18, 30).atZone(zone).toInstant()
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 3))
    }
}
