package dev.saketanand.setwise.ui.workout

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.model.BodyRules
import dev.saketanand.setwise.domain.model.CalorieFormula
import dev.saketanand.setwise.domain.model.CardioMetric
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.repository.BodyRepository
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.DateProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen: [CardioEntryScreenRoot]. Logs (or corrects) one cardio exercise of the running workout.
 * Steppers start from this workout's entry, else last time's; they survive the app being killed.
 *
 * @param workoutExerciseId from [Route.CardioEntry], passed in by SetwiseNavHost (Koin parametersOf).
 */
class CardioEntryViewModel(
    private val workoutExerciseId: Long,
    private val workoutRepository: WorkoutRepository,
    private val dateProvider: DateProvider,
    private val savedStateHandle: SavedStateHandle,
    userSettingsRepository: UserSettingsRepository,
    bodyRepository: BodyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CardioEntryUiState())
    val state: StateFlow<CardioEntryUiState> = _state.asStateFlow()

    private val eventChannel = Channel<CardioEntryEvent>(Channel.BUFFERED)
    val events: Flow<CardioEntryEvent> = eventChannel.receiveAsFlow()

    private val exercise = MutableStateFlow<Exercise?>(null)

    init {
        workoutRepository.observeCardioEntry(workoutExerciseId)
            .onEach { entry ->
                if (entry == null) {
                    eventChannel.send(CardioEntryEvent.Closed) // removed from the workout meanwhile
                    return@onEach
                }
                val start = entry.logged ?: entry.lastTime
                exercise.value = entry.exercise
                _state.update {
                    it.copy(
                        isLoading = false,
                        name = entry.exercise.name,
                        metrics = entry.exercise.metrics.orEmpty().toSet() + CardioMetric.DURATION,
                        logged = entry.logged,
                        lastTime = entry.lastTime,
                        inclinePct = savedStateHandle[KEY_INCLINE] ?: start?.inclinePct ?: 0.0,
                        level = savedStateHandle[KEY_LEVEL] ?: start?.level ?: CardioEntryUiState.LEVEL_RANGE.first,
                    )
                }
            }
            .catch { e ->
                Log.e(TAG, "Loading cardio entry $workoutExerciseId failed", e)
                eventChannel.send(CardioEntryEvent.Closed)
            }
            .launchIn(viewModelScope)

        // The calorie line's basis (#145), as the workout's own estimate works it out (CalorieSync).
        combine(
            exercise.filterNotNull(),
            userSettingsRepository.settings,
            bodyRepository.observeMeasurements(),
            dateProvider.today(),
        ) { exercise, settings, measurements, today ->
            settings.bodyWeightKg?.let { weightKg ->
                val bmr = BodyRules.bmr(measurements, settings, today)?.kcal
                CardioCalorieBasis(
                    method = exercise.calorieMethod,
                    met = exercise.met,
                    kcalPerMetHour = CalorieFormula.kcalPerMetHour(weightKg, bmr),
                    weightKg = weightKg,
                    fromBmr = bmr != null && bmr in BodyRules.BMR_KCAL,
                )
            }
        }
            .catch { e ->
                Log.e(TAG, "Loading the calorie basis failed", e)
                emit(null)
            }
            .onEach { basis -> _state.update { it.copy(calories = basis) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: CardioEntryAction) {
        when (action) {
            is CardioEntryAction.OnInclineChange -> {
                val incline = (_state.value.inclinePct + action.steps * CardioEntryUiState.INCLINE_STEP)
                    .coerceIn(0.0, CardioEntryUiState.MAX_INCLINE)
                savedStateHandle[KEY_INCLINE] = incline
                _state.update { it.copy(inclinePct = incline) }
            }
            is CardioEntryAction.OnLevelChange -> {
                val level = (_state.value.level + action.delta).coerceIn(CardioEntryUiState.LEVEL_RANGE)
                savedStateHandle[KEY_LEVEL] = level
                _state.update { it.copy(level = level) }
            }
            CardioEntryAction.OnInputEdited -> _state.update { it.copy(error = null) }
            is CardioEntryAction.OnLogClick -> log(action.inputs)
            // Navigation: CardioEntryScreenRoot handles it.
            CardioEntryAction.OnBackClick -> Unit
        }
    }

    private fun log(inputs: CardioInputs) {
        val state = _state.value
        if (state.isLoading || state.isSaving) return
        when (val result = parseCardio(inputs, state.metrics, state.inclinePct, state.level, state.lastTime)) {
            is CardioParseResult.Invalid -> _state.update { it.copy(error = result.error) }
            is CardioParseResult.Valid -> {
                _state.update { it.copy(isSaving = true, error = null) }
                viewModelScope.launch {
                    runCatching { workoutRepository.logCardio(workoutExerciseId, result.values, dateProvider.now()) }
                        .onSuccess { eventChannel.send(CardioEntryEvent.Logged) }
                        .onFailure { e ->
                            Log.e(TAG, "Logging cardio $workoutExerciseId failed", e)
                            _state.update { it.copy(isSaving = false) }
                            eventChannel.send(CardioEntryEvent.SaveFailed)
                        }
                }
            }
        }
    }

    private companion object {
        const val TAG = "CardioEntryViewModel"
        const val KEY_INCLINE = "incline"
        const val KEY_LEVEL = "level"
    }
}
