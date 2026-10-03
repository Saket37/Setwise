package dev.saketanand.setwise.ui.exercises

import androidx.lifecycle.SavedStateHandle
import dev.saketanand.setwise.domain.model.CreateExerciseResult
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.NewExercise
import dev.saketanand.setwise.domain.model.RecentExercise
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
class CreateExerciseViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeExerciseRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(name: String = "", handle: SavedStateHandle = SavedStateHandle()) =
        CreateExerciseViewModel(name, repository, handle).also { vm ->
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()
        }

    @Test
    fun `starts with the picker's search, and needs a muscle group`() = runTest(dispatcher) {
        val vm = viewModel(name = "zercher squat")
        assertEquals("zercher squat", vm.state.value.name)
        assertFalse(vm.state.value.canCreate)

        vm.onAction(CreateExerciseAction.OnMuscleGroupClick("Quads"))
        advanceUntilIdle()
        assertTrue(vm.state.value.canCreate)

        vm.onAction(CreateExerciseAction.OnCreateClick)
        advanceUntilIdle()
        assertEquals(CreateExerciseEvent.Done(99), vm.events.first())
        assertEquals(NewExercise("zercher squat", ExerciseType.STRENGTH, false, "Quads", "Barbell", 120), repository.created.single())
    }

    @Test
    fun `a similar library exercise is offered, and the same name can't be created`() = runTest(dispatcher) {
        val vm = viewModel(name = "bench")
        assertEquals("Bench Press (Barbell)", vm.state.value.match?.name)
        assertFalse(vm.state.value.isNameTaken)

        vm.onAction(CreateExerciseAction.OnNameChange("bench press (barbell)"))
        advanceUntilIdle()
        vm.onAction(CreateExerciseAction.OnMuscleGroupClick("Chest"))
        advanceUntilIdle()
        assertTrue(vm.state.value.isNameTaken)
        assertFalse(vm.state.value.canCreate)

        vm.onAction(CreateExerciseAction.OnUseMatchClick)
        assertEquals(CreateExerciseEvent.Done(1), vm.events.first())
    }

    @Test
    fun `changing how it's logged brings its usual equipment and rest`() = runTest(dispatcher) {
        val vm = viewModel(name = "Dead hang")

        vm.onAction(CreateExerciseAction.OnKindClick(ExerciseKindOption.Timed))
        advanceUntilIdle()
        assertEquals("Bodyweight", vm.state.value.equipment)
        assertEquals(60, vm.state.value.restSec)

        vm.onAction(CreateExerciseAction.OnRestChange(+2))
        advanceUntilIdle()
        assertEquals(90, vm.state.value.restSec)

        vm.onAction(CreateExerciseAction.OnKindClick(ExerciseKindOption.Cardio))
        advanceUntilIdle()
        assertTrue(vm.state.value.canCreate) // cardio needs no muscle group
    }

    private class FakeExerciseRepository : ExerciseRepository {
        private val library = listOf(exercise(1, "Bench Press (Barbell)"), exercise(2, "Bench Press (Dumbbell)"))
        val created = mutableListOf<NewExercise>()

        override fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>> =
            flowOf(library.filter { it.name.contains(query, ignoreCase = true) })
        override fun observeMuscleGroups(): Flow<List<String>> = flowOf(listOf("Chest", "Quads", "Cardio"))
        override fun observeRecentExercises(limit: Int): Flow<List<RecentExercise>> = flowOf(emptyList())
        override fun observeExerciseCount(): Flow<Int> = flowOf(library.size)
        override suspend fun createExercise(exercise: NewExercise): CreateExerciseResult {
            created += exercise
            return CreateExerciseResult.Created(99)
        }
        override suspend fun getExercises(ids: List<Long>): List<Exercise> = emptyList()
        override fun observeExercise(id: Long): Flow<Exercise?> = flowOf(null)

        private companion object {
            fun exercise(id: Long, name: String) =
                Exercise(id, name, ExerciseType.STRENGTH, "Chest", "Barbell", 120, false, false, null, null, null)
        }
    }
}
