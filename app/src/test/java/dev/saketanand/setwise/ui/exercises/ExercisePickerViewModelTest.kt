package dev.saketanand.setwise.ui.exercises

import androidx.lifecycle.SavedStateHandle
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.RecentExercise
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class ExercisePickerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeExerciseRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    /** state only runs while collected (WhileSubscribed), like on screen. */
    private fun TestScope.viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        ExercisePickerViewModel(repository, savedStateHandle).also { vm ->
            backgroundScope.launch { vm.state.collect {} }
        }

    @Test
    fun `loads the library, recent exercises and chips`() = runTest(dispatcher) {
        val state = viewModel().state.value

        assertFalse(state.isLoading)
        assertEquals(listOf("Bench Press", "Plank", "Pull-up"), state.exercises.map { it.name })
        assertEquals(listOf("Back", "Chest", "Core"), state.muscleGroups)
        assertEquals(LastSetUi(weight = "60", reps = 8), state.recent.single().lastSet)
        assertTrue(state.showRecent)
    }

    @Test
    fun `selection keeps tap order and toggles off`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(ExercisePickerAction.OnExerciseToggle(3))
        vm.onAction(ExercisePickerAction.OnExerciseToggle(1))
        assertEquals(listOf(3L, 1L), vm.state.value.selectedIds)
        assertTrue(vm.state.value.exercises.first { it.id == 1L }.isSelected)
        // Selected in the library = selected in Recent too.
        assertTrue(vm.state.value.recent.first { it.id == 1L }.isSelected)

        vm.onAction(ExercisePickerAction.OnExerciseToggle(3))
        assertEquals(listOf(1L), vm.state.value.selectedIds)
    }

    @Test
    fun `selection and chip survive process death`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.onAction(ExercisePickerAction.OnExerciseToggle(2))
        first.onAction(ExercisePickerAction.OnMuscleGroupClick("Core"))

        val restored = viewModel(handle)

        assertEquals(listOf(2L), restored.state.value.selectedIds)
        assertEquals("Core", restored.state.value.selectedMuscleGroup)
    }

    @Test
    fun `chip filters the list, hides Recent, and tapping it again clears it`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(ExercisePickerAction.OnMuscleGroupClick("Back"))
        assertEquals(listOf("Pull-up"), vm.state.value.exercises.map { it.name })
        assertFalse(vm.state.value.showRecent)

        vm.onAction(ExercisePickerAction.OnMuscleGroupClick("Back"))
        assertEquals(null, vm.state.value.selectedMuscleGroup)
        assertEquals(3, vm.state.value.exercises.size)
    }

    @Test
    fun `search waits for typing to pause`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(ExercisePickerAction.OnQueryChange("pla"))
        assertEquals("query shows at once", "pla", vm.state.value.query)
        assertEquals("but the list isn't re-queried yet", 3, vm.state.value.exercises.size)

        advanceTimeBy(200)
        assertEquals(listOf("Plank"), vm.state.value.exercises.map { it.name })
        assertEquals(listOf("pla"), repository.searches.filter { it.isNotEmpty() })
    }

    @Test
    fun `starting a search covers every group`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(ExercisePickerAction.OnMuscleGroupClick("Chest"))

        // Plank is a Core exercise: found although Chest was selected.
        vm.onAction(ExercisePickerAction.OnQueryChange("plank"))
        advanceTimeBy(200)

        assertEquals(null, vm.state.value.selectedMuscleGroup)
        assertEquals(listOf("Plank"), vm.state.value.exercises.map { it.name })
    }

    @Test
    fun `a chip tapped while searching narrows it and survives more typing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(ExercisePickerAction.OnQueryChange("p"))
        vm.onAction(ExercisePickerAction.OnMuscleGroupClick("Back"))

        vm.onAction(ExercisePickerAction.OnQueryChange("pu"))
        advanceTimeBy(200)

        assertEquals("Back", vm.state.value.selectedMuscleGroup)
        assertEquals(listOf("Pull-up"), vm.state.value.exercises.map { it.name })
    }

    @Test
    fun `restoring after process death keeps the chip picked during a search`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        first.onAction(ExercisePickerAction.OnQueryChange("p"))
        first.onAction(ExercisePickerAction.OnMuscleGroupClick("Back"))

        // The restored screen's TextFieldState sends its text again.
        val restored = viewModel(handle)
        restored.onAction(ExercisePickerAction.OnQueryChange("p"))

        assertEquals("Back", restored.state.value.selectedMuscleGroup)
    }

    @Test
    fun `no results is reported`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onAction(ExercisePickerAction.OnQueryChange("zercher"))
        advanceTimeBy(200)

        assertTrue(vm.state.value.showNoResults)
    }

    private class FakeExerciseRepository : ExerciseRepository {
        private val library = MutableStateFlow(
            listOf(
                exercise(1, "Bench Press", "Chest"),
                exercise(2, "Plank", "Core"),
                exercise(3, "Pull-up", "Back"),
            )
        )
        override fun observeExercise(id: Long): Flow<Exercise?> = library.map { list -> list.firstOrNull { it.id == id } }
        val searches = mutableListOf<String>()

        override fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>> {
            searches += query
            return library.map { all ->
                all.filter { (muscleGroup == null || it.muscleGroup == muscleGroup) && it.name.contains(query, ignoreCase = true) }
            }
        }

        override fun observeMuscleGroups(): Flow<List<String>> = flowOf(listOf("Back", "Chest", "Core"))

        override fun observeRecentExercises(limit: Int): Flow<List<RecentExercise>> =
            flowOf(listOf(RecentExercise(exercise(1, "Bench Press", "Chest"), PreviousSet(60.0, 8))))

        override fun observeExerciseCount(): Flow<Int> = library.map { it.size }
    }

    private companion object {
        fun exercise(id: Long, name: String, muscleGroup: String) = Exercise(
            id = id, name = name, type = ExerciseType.STRENGTH, muscleGroup = muscleGroup, equipment = "Barbell",
            defaultRestSec = 90, isTimed = false, isCustom = false, metrics = null, calorieMethod = null, met = null,
        )
    }
}
