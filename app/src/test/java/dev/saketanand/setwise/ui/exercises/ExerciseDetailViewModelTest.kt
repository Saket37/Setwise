package dev.saketanand.setwise.ui.exercises

import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.ai.PlateauNoteWriter
import dev.saketanand.setwise.domain.model.CreateExerciseResult
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseSession
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.ExerciseUsage
import dev.saketanand.setwise.domain.model.LoggedSet
import dev.saketanand.setwise.domain.model.NewExercise
import dev.saketanand.setwise.domain.model.ProgressionRule
import dev.saketanand.setwise.domain.model.RecentExercise
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.testing.FakeExerciseEditor
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.testing.StubWorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class ExerciseDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val model = FakeOnDeviceModel()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(exerciseId: Long = OHP.id, editor: FakeExerciseEditor = FakeExerciseEditor()) =
        ExerciseDetailViewModel(exerciseId, Library, Sessions, FixedDateProvider, PlateauNoteWriter(model), editor).also { vm ->
            backgroundScope.launch { vm.state.collect {} }
        }

    @Test
    fun `a plateau's note is written on-device, with a writing state while it thinks`() = runTest(dispatcher) {
        model.availability = ModelAvailability.Ready
        model.thinkingMs = 2_000
        model.answer = { NOTE }
        val vm = viewModel()

        runCurrent()
        assertTrue(vm.state.value.plateau!!.isWriting)
        advanceUntilIdle()

        val plateau = vm.state.value.plateau!!
        assertEquals(NOTE, plateau.note)
        assertEquals(false, plateau.isWriting)
        assertEquals(ProgressionRule.Lighter, vm.state.value.nextSession?.rule)
    }

    @Test
    fun `without the model the plateau shows its template`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val plateau = vm.state.value.plateau!!
        assertNull(plateau.note)
        assertEquals(false, plateau.isWriting)
        assertTrue(model.requests.isEmpty())
    }

    @Test
    fun `a built-in exercise has no edit or delete`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.state.value.isCustom)
    }

    @Test
    fun `a custom exercise in no workout is deleted after asking`() = runTest(dispatcher) {
        val editor = FakeExerciseEditor(ExerciseUsage(workouts = 0, templates = 2))
        val vm = viewModel(TYPO.id, editor)
        advanceUntilIdle()
        assertTrue(vm.state.value.isCustom)

        vm.onAction(ExerciseDetailAction.OnDeleteClick)
        advanceUntilIdle()
        assertEquals(ManageExerciseUi.ConfirmDelete(templates = 2), vm.state.value.manage)

        vm.onAction(ExerciseDetailAction.OnConfirmDelete)
        advanceUntilIdle()
        assertEquals(listOf(TYPO.id), editor.deleted)
    }

    @Test
    fun `with history it's merged into an exercise logged the same way`() = runTest(dispatcher) {
        val editor = FakeExerciseEditor(ExerciseUsage(workouts = 3, templates = 0))
        val vm = viewModel(TYPO.id, editor)
        advanceUntilIdle()

        vm.onAction(ExerciseDetailAction.OnDeleteClick)
        advanceUntilIdle()
        val merge = vm.state.value.manage as ManageExerciseUi.Merge
        assertEquals(3, merge.workouts)
        // Weight × reps like it, not itself, not the timed plank.
        assertEquals(listOf(OHP.name, BENCH.name), merge.candidates.map { it.name })

        vm.onAction(ExerciseDetailAction.OnMergeQueryChange("bench"))
        advanceUntilIdle()
        assertEquals(listOf(BENCH.name), (vm.state.value.manage as ManageExerciseUi.Merge).candidates.map { it.name })

        vm.onAction(ExerciseDetailAction.OnConfirmMerge) // nothing picked yet
        advanceUntilIdle()
        assertTrue(editor.merged.isEmpty())

        vm.onAction(ExerciseDetailAction.OnMergeTargetClick(BENCH.id))
        vm.onAction(ExerciseDetailAction.OnConfirmMerge)
        advanceUntilIdle()
        assertEquals(listOf(TYPO.id to BENCH.id), editor.merged)
        assertTrue(editor.deleted.isEmpty())
    }

    private object Library : ExerciseRepository {
        override fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>> =
            flowOf(listOf(OHP, TYPO, BENCH, PLANK).filter { it.name.contains(query, ignoreCase = true) })
        override fun observeMuscleGroups(): Flow<List<String>> = flowOf(emptyList())
        override fun observeRecentExercises(limit: Int): Flow<List<RecentExercise>> = flowOf(emptyList())
        override fun observeExerciseCount(): Flow<Int> = flowOf(1)
        override suspend fun createExercise(exercise: NewExercise): CreateExerciseResult = CreateExerciseResult.Created(0)
        override suspend fun getExercises(ids: List<Long>): List<Exercise> = emptyList()
        override fun observeExercise(id: Long): Flow<Exercise?> = flowOf(listOf(OHP, TYPO, BENCH, PLANK).find { it.id == id })
    }

    /** Twice a week since 9 Sep at an estimated 1RM of about 48 kg, never every rep. */
    private object Sessions : StubWorkoutRepository() {
        override fun observeExerciseSessions(exerciseId: Long): Flow<List<ExerciseSession>> = flowOf(
            listOf(30, 26, 23, 19, 16, 12, 9).mapIndexed { index, day ->
                val (kg, reps) = if (index % 2 == 0) 40.0 to listOf(6, 6, 5) else 42.5 to listOf(4, 4, 3)
                ExerciseSession(index + 1L, LocalDate.of(2026, 9, day).atTime(18, 0).atZone(ZONE).toInstant(), reps.map { LoggedSet(kg, it, null, null) })
            },
        )
    }

    private object FixedDateProvider : DateProvider {
        override val zone: ZoneId = ZONE
        override fun now(): Instant = LocalDate.of(2026, 10, 3).atTime(18, 30).atZone(zone).toInstant()
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 3))
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
        val OHP = Exercise(7, "Overhead Press (Barbell)", ExerciseType.STRENGTH, "Shoulders", "Barbell", 90, false, false, null, null, null)
        val TYPO = Exercise(20, "Bnech press", ExerciseType.STRENGTH, "Chest", "Dumbbell", 90, false, true, null, null, null)
        val BENCH = Exercise(21, "Bench Press (Dumbbell)", ExerciseType.STRENGTH, "Chest", "Dumbbell", 120, false, false, null, null, null)
        val PLANK = Exercise(22, "Plank", ExerciseType.BODYWEIGHT, "Core", "Bodyweight", 60, true, false, null, null, null)
        const val NOTE = "Your overhead press has held at about 48 kg for 3 weeks, even with 2 sessions a week. A lighter week, or 8 to 10 reps a set for a month, often gets it moving again."
    }
}
