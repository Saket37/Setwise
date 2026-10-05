package dev.saketanand.setwise.ui.exercises

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.ai.ExerciseAssistant
import dev.saketanand.setwise.domain.ai.ExerciseSuggestion
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Screen: [CreateExerciseScreenRoot]. A custom exercise: name (pre-filled with the picker's
 * search), how it's logged, muscle group, equipment and rest. While typing ([ExerciseAssistant]:
 * the on-device model where it's there, code rules otherwise), the same exercise from the library
 * is offered instead ("Use this"), and the details are suggested from the name, never over a
 * field the user picked. An exact name can't be created twice.
 *
 * @param initialName from [Route.CreateExercise]: the picker's search text.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class) // mapLatest, debounce
class CreateExerciseViewModel(
    initialName: String,
    private val exerciseRepository: ExerciseRepository,
    private val assistant: ExerciseAssistant,
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
            picked = savedStateHandle.get<Array<String>>(KEY_PICKED)?.toSet().orEmpty(),
        )
    )

    private val typedName = form.map { it.name.trim() }.distinctUntilChanged()

    private val isSaving = MutableStateFlow(false)
    private val eventChannel = Channel<CreateExerciseEvent>(Channel.BUFFERED)
    val events: Flow<CreateExerciseEvent> = eventChannel.receiveAsFlow()

    /** The same exercise from the library (3+ letters typed); a new answer replaces one in progress. */
    private val match: Flow<Exercise?> = combine(typedName.debounce(MATCH_DEBOUNCE_MS), exerciseRepository.observeExercises("", null)) { name, library ->
        name to library
    }.mapLatest { (name, library) -> if (name.length < MIN_MATCH_LENGTH) null else assistant.findMatch(name, library) }

    /** Details suggested from the name, once typing pauses; applied in init to fields not picked. */
    private val suggestion = MutableStateFlow<ExerciseSuggestion?>(null)

    init {
        viewModelScope.launch {
            typedName.debounce(SUGGEST_DEBOUNCE_MS)
                .mapLatest { name -> if (name.length < MIN_MATCH_LENGTH) null else assistant.suggestDetails(name) }
                .collect { suggestion ->
                    this@CreateExerciseViewModel.suggestion.value = suggestion
                    suggestion?.let { apply(it) }
                }
        }
    }

    val state: StateFlow<CreateExerciseUiState> = combine(
        form,
        match,
        exerciseRepository.observeMuscleGroups(),
        isSaving,
        suggestion,
    ) { form, match, muscleGroups, saving, suggestion ->
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
            suggestionSource = suggestion?.source?.takeIf { form.picked.size < Field.entries.size },
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
            is CreateExerciseAction.OnKindClick ->
                update { it.copy(kind = action.kind, equipment = null, restSec = null, picked = it.picked + Field.Kind.name) }
            is CreateExerciseAction.OnMuscleGroupClick -> update {
                it.copy(muscleGroup = action.muscleGroup.takeUnless { group -> group == it.muscleGroup }, picked = it.picked + Field.Muscle.name)
            }
            is CreateExerciseAction.OnEquipmentClick -> update { it.copy(equipment = action.equipment, picked = it.picked + Field.Equipment.name) }
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

    /** The suggestion, into each field the user hasn't picked. */
    private fun apply(suggestion: ExerciseSuggestion) {
        val guess = suggestion.guess
        update { form ->
            var next = form
            val kind = guess.type?.let { type -> ExerciseKindOption.entries.firstOrNull { it.type == type && it.isTimed == guess.isTimed } }
            if (kind != null && Field.Kind.name !in form.picked && kind != form.kind) {
                next = next.copy(kind = kind, restSec = null).let { if (Field.Equipment.name !in form.picked) it.copy(equipment = null) else it }
            }
            if (guess.muscleGroup != null && guess.muscleGroup != CARDIO && Field.Muscle.name !in form.picked) {
                next = next.copy(muscleGroup = guess.muscleGroup)
            }
            if (guess.equipment != null && Field.Equipment.name !in form.picked) {
                next = next.copy(equipment = guess.equipment)
            }
            next
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
        savedStateHandle[KEY_PICKED] = next.picked.toTypedArray()
    }

    /** Fields the user set: suggestions leave them alone. */
    private enum class Field { Kind, Muscle, Equipment }

    /** null equipment / rest = the kind's default. */
    private data class Form(
        val name: String,
        val kind: ExerciseKindOption,
        val muscleGroup: String?,
        val equipment: String?,
        val restSec: Int?,
        /** [Field] names the user set. */
        val picked: Set<String> = emptySet(),
    )

    private companion object {
        const val TAG = "CreateExerciseViewModel"
        const val KEY_NAME = "name"
        const val KEY_KIND = "kind"
        const val KEY_MUSCLE_GROUP = "muscle_group"
        const val KEY_EQUIPMENT = "equipment"
        const val KEY_REST = "rest"
        const val KEY_PICKED = "picked"
        const val CARDIO = "Cardio"
        const val MATCH_DEBOUNCE_MS = 300L

        /** Longer than the match: the model is asked once typing has really paused. */
        const val SUGGEST_DEBOUNCE_MS = 600L
        const val MIN_MATCH_LENGTH = 3
    }
}
