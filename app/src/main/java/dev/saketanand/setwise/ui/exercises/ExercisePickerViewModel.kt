package dev.saketanand.setwise.ui.exercises

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.RecentExercise
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Screen: [ExercisePickerScreenRoot].
 *
 * The state is derived entirely from inputs (search text, chip, selection) combined with live
 * Room queries, so there's no state to keep in sync by hand. The chip and the selection live
 * in [SavedStateHandle]: they survive the app being killed in the background mid-pick.
 * The search text is saved too: the screen's TextFieldState sends it again after a restore,
 * and that must not count as "started a search" (which resets the chip).
 *
 * Search covers the whole library: starting one resets the chip to "All". Tapping a chip
 * while searching narrows the results, and typing more keeps that chip.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class) // flatMapLatest, debounce
class ExercisePickerViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val selectedMuscleGroup = savedStateHandle.getStateFlow<String?>(KEY_MUSCLE_GROUP, null)
    private val selectedIds = savedStateHandle.getStateFlow(KEY_SELECTED_IDS, LongArray(0))

    /**
     * The library for the current search + chip. Typing is debounced so a fast typist doesn't
     * run a query per letter; clearing the search applies at once. flatMapLatest drops the
     * previous query as soon as the filter changes.
     */
    private val filteredExercises: Flow<FilteredExercises> = combine(
        query
            .map { it.trim() }
            .distinctUntilChanged()
            .debounce { text -> if (text.isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
        selectedMuscleGroup,
    ) { text, muscleGroup -> text to muscleGroup }
        .distinctUntilChanged()
        .flatMapLatest { (text, muscleGroup) ->
            exerciseRepository.observeExercises(text, muscleGroup)
                .map { exercises -> FilteredExercises(filterKey(text, muscleGroup), exercises) }
        }

    /** Parts that don't depend on the search: recent exercises and the chip list. */
    private val library: Flow<Library> = combine(
        exerciseRepository.observeRecentExercises(RECENT_LIMIT),
        exerciseRepository.observeMuscleGroups(),
        ::Library,
    )

    val state: StateFlow<ExercisePickerUiState> = combine(
        query,
        selectedMuscleGroup,
        selectedIds,
        filteredExercises,
        library,
    ) { text, muscleGroup, ids, filtered, library ->
        val selected = ids.toSet()
        ExercisePickerUiState(
            isLoading = false,
            query = text,
            muscleGroups = library.muscleGroups,
            selectedMuscleGroup = muscleGroup,
            recent = library.recent.map { it.exercise.toRowUi(isSelected = it.exercise.id in selected, lastSet = it.lastSet) },
            exercises = filtered.exercises.map { it.toRowUi(isSelected = it.id in selected) },
            listFilter = filtered.key,
            selectedIds = ids.toList(),
        )
    }
        // A failing query must not crash the app; log it and show an empty list.
        .catch { e ->
            Log.e(TAG, "Loading exercises failed", e)
            emit(ExercisePickerUiState(isLoading = false))
        }
        // Queries run only while the screen is visible; the 5s grace keeps them alive across
        // a rotation instead of restarting them.
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExercisePickerUiState(
                selectedMuscleGroup = selectedMuscleGroup.value,
                selectedIds = selectedIds.value.toList(),
            ),
        )

    fun onAction(action: ExercisePickerAction) {
        when (action) {
            is ExercisePickerAction.OnQueryChange -> {
                val startedSearch = query.value.isBlank() && action.query.isNotBlank()
                if (startedSearch) savedStateHandle[KEY_MUSCLE_GROUP] = null
                savedStateHandle[KEY_QUERY] = action.query
            }

            is ExercisePickerAction.OnMuscleGroupClick ->
                // Tapping the selected chip again clears the filter.
                savedStateHandle[KEY_MUSCLE_GROUP] = action.muscleGroup.takeUnless { it == selectedMuscleGroup.value }

            is ExercisePickerAction.OnExerciseToggle -> toggle(action.exerciseId)

            // Back from "New exercise": select it (unless it already is).
            is ExercisePickerAction.OnExerciseCreated ->
                if (action.exerciseId !in selectedIds.value) toggle(action.exerciseId)

            // Navigation: ExercisePickerScreenRoot handles these.
            ExercisePickerAction.OnAddClick,
            ExercisePickerAction.OnCreateNewClick,
            ExercisePickerAction.OnBackClick -> Unit
        }
    }

    /** Adds to the end (tap order = order in the workout), or removes. */
    private fun toggle(exerciseId: Long) {
        val current = selectedIds.value
        savedStateHandle[KEY_SELECTED_IDS] =
            if (exerciseId in current) current.filter { it != exerciseId }.toLongArray() else current + exerciseId
    }

    private data class FilteredExercises(val key: String, val exercises: List<Exercise>)

    private data class Library(val recent: List<RecentExercise>, val muscleGroups: List<String>)

    private companion object {
        const val TAG = "ExercisePickerViewModel"
        const val KEY_QUERY = "query"
        const val KEY_MUSCLE_GROUP = "muscle_group"
        const val KEY_SELECTED_IDS = "selected_ids"
        const val SEARCH_DEBOUNCE_MS = 150L
        const val RECENT_LIMIT = 5

        fun filterKey(text: String, muscleGroup: String?) = "$text|${muscleGroup.orEmpty()}"
    }
}
