package dev.saketanand.setwise.ui.exercises

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.model.CreateExerciseResult
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.NewExercise
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.ui.navigation.Route
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Screen: [CreateExerciseScreenRoot]. A custom exercise: name (pre-filled with the picker's
 * search), how it's logged, muscle group, equipment and rest. While typing, a library exercise
 * with a similar name is offered instead ("Use this"); an exact name can't be created twice.
 * (On-device suggestions for the details come with milestone 11.)
 *
 * @param initialName from [Route.CreateExercise]: the picker's search text.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class) // flatMapLatest, debounce
class CreateExerciseViewModel(
    initialName: String,
    private val exerciseRepository: ExerciseRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** What the user picked; in SavedStateHandle via [Form] so it survives the app being killed. */
    private val form = MutableStateFlow(
        Form(
            name = savedStateHandle[KEY_NAME] ?: initialName,
            kind = savedStateHandle.get<String>(KEY_KIND)?.let(ExerciseKindOption::valueOf) ?: ExerciseKindOption.WeightReps,
            muscleGroup = savedStateHandle[KEY_MUSCLE_GROUP],
            equipment = savedStateHandle[KEY_EQUIPMENT],
            restSec = savedStateHandle[KEY_REST],
        )
    )

    private val isSaving = MutableStateFlow(false)
    private val eventChannel = Channel<CreateExerciseEvent>(Channel.BUFFERED)
    val events: Flow<CreateExerciseEvent> = eventChannel.receiveAsFlow()

    /** A library exercise whose name contains what's typed (3+ letters), exact match first. */
    private val match: Flow<Exercise?> = form.map { it.name.trim() }
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { name ->
            if (name.length < MIN_MATCH_LENGTH) {
                flowOf(null)
            } else {
                exerciseRepository.observeExercises(name, null).map { found ->
                    found.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: found.firstOrNull()
                }
            }
        }

    val state: StateFlow<CreateExerciseUiState> = combine(
        form,
        match,
        exerciseRepository.observeMuscleGroups(),
        isSaving,
    ) { form, match, muscleGroups, saving ->
        CreateExerciseUiState(
            name = form.name,
            kind = form.kind,
            muscleGroup = form.muscleGroup,
            equipment = form.equipment ?: form.kind.defaultEquipment,
            restSec = form.restSec ?: form.kind.defaultRestSec,
            muscleGroups = muscleGroups.filterNot { it == CARDIO },
            match = match,
            isNameTaken = match != null && match.name.equals(form.name.trim(), ignoreCase = true),
            isSaving = saving,
        )
    }
        .catch { e ->
            Log.e(TAG, "Loading the create-exercise form failed", e)
            emit(CreateExerciseUiState(name = form.value.name))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreateExerciseUiState(name = form.value.name))

    fun onAction(action: CreateExerciseAction) {
        when (action) {
            is CreateExerciseAction.OnNameChange -> update { it.copy(name = action.name) }
            // A new kind brings its usual equipment and rest (they can still be changed).
            is CreateExerciseAction.OnKindClick -> update { it.copy(kind = action.kind, equipment = null, restSec = null) }
            is CreateExerciseAction.OnMuscleGroupClick ->
                update { it.copy(muscleGroup = action.muscleGroup.takeUnless { group -> group == it.muscleGroup }) }
            is CreateExerciseAction.OnEquipmentClick -> update { it.copy(equipment = action.equipment) }
            is CreateExerciseAction.OnRestChange -> update {
                val rest = state.value.restSec + action.steps * CreateExerciseUiState.REST_STEP_SEC
                it.copy(restSec = rest.coerceIn(CreateExerciseUiState.REST_RANGE_SEC))
            }
            CreateExerciseAction.OnUseMatchClick -> state.value.match?.let { eventChannel.trySend(CreateExerciseEvent.Done(it.id)) }
            CreateExerciseAction.OnCreateClick -> create()
            // Navigation: CreateExerciseScreenRoot handles it.
            CreateExerciseAction.OnCloseClick -> Unit
        }
    }

    private fun create() {
        val state = state.value
        if (!state.canCreate) return
        isSaving.value = true
        viewModelScope.launch {
            val exercise = NewExercise(
                name = state.name,
                type = state.kind.type,
                isTimed = state.kind.isTimed,
                muscleGroup = state.muscleGroup.orEmpty(),
                equipment = state.equipment,
                restSec = state.restSec,
            )
            runCatching { exerciseRepository.createExercise(exercise) }
                .onSuccess { result ->
                    val id = when (result) {
                        is CreateExerciseResult.Created -> result.exerciseId
                        // Created elsewhere meanwhile: use that one.
                        is CreateExerciseResult.NameTaken -> result.existing.id
                    }
                    eventChannel.send(CreateExerciseEvent.Done(id))
                }
                .onFailure { e ->
                    Log.e(TAG, "Creating exercise ${state.name} failed", e)
                    isSaving.value = false
                    eventChannel.send(CreateExerciseEvent.SaveFailed)
                }
        }
    }

    private fun update(change: (Form) -> Form) {
        val next = change(form.value)
        form.value = next
        savedStateHandle[KEY_NAME] = next.name
        savedStateHandle[KEY_KIND] = next.kind.name
        savedStateHandle[KEY_MUSCLE_GROUP] = next.muscleGroup
        savedStateHandle[KEY_EQUIPMENT] = next.equipment
        savedStateHandle[KEY_REST] = next.restSec
    }

    /** null equipment / rest = the kind's default. */
    private data class Form(
        val name: String,
        val kind: ExerciseKindOption,
        val muscleGroup: String?,
        val equipment: String?,
        val restSec: Int?,
    )

    private companion object {
        const val TAG = "CreateExerciseViewModel"
        const val KEY_NAME = "name"
        const val KEY_KIND = "kind"
        const val KEY_MUSCLE_GROUP = "muscle_group"
        const val KEY_EQUIPMENT = "equipment"
        const val KEY_REST = "rest"
        const val CARDIO = "Cardio"
        const val SEARCH_DEBOUNCE_MS = 200L
        const val MIN_MATCH_LENGTH = 3
    }
}
