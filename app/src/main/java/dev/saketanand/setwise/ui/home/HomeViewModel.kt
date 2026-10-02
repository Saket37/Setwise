package dev.saketanand.setwise.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** Screen: [HomeScreenRoot]. */
class HomeViewModel(
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** One-off events (navigate after a workout is created, show an error). Each is handled once. */
    private val eventChannel = Channel<HomeEvent>()
    val events: Flow<HomeEvent> = eventChannel.receiveAsFlow()

    init {
        // TODO (milestones 2–3): observe workouts and templates from their repositories and
        //  fill lastWorkout, activeWorkout, weekStats and templates.
        //  Until then there is no data, so content = FirstRun.
        _state.update { it.copy(isLoading = false, today = LocalDate.now()) }

        // Live library size for the first-run footnote; updates when seeding finishes.
        exerciseRepository.observeExerciseCount()
            .onEach { count -> _state.update { it.copy(exerciseCount = count) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.OnStartWorkoutClick ->
                _state.update { it.copy(isStartSheetVisible = true) }

            HomeAction.OnStartSheetDismiss ->
                _state.update { it.copy(isStartSheetVisible = false, customStartTime = null) }

            is HomeAction.OnStartTimeChange ->
                _state.update { it.copy(customStartTime = action.startTime) }

            HomeAction.OnStartEmptyWorkout -> startWorkout(templateId = null)

            is HomeAction.OnStartFromTemplate -> startWorkout(templateId = action.templateId)

            is HomeAction.OnSaveLastWorkoutAsTemplate -> saveWorkoutAsTemplate(action.workoutId)

            HomeAction.OnWeeklySummaryDismiss ->
                // TODO (milestone 14): remember the dismissal (DataStore) so it stays hidden.
                _state.update { it.copy(weeklySummary = null) }

            // Navigation actions: HomeScreenRoot sends these straight to its nav callbacks.
            is HomeAction.OnResumeWorkout,
            is HomeAction.OnTemplateClick,
            HomeAction.OnCreateTemplateClick,
            HomeAction.OnCreateTemplateFromGoalClick,
            is HomeAction.OnPlateauExerciseClick -> Unit
        }
    }

    /**
     * Creates the workout row, then tells the screen to open it via [HomeEvent.WorkoutStarted].
     * @param templateId null for an empty workout.
     */
    private fun startWorkout(templateId: Long?) {
        if (_state.value.isStartingWorkout) return // ignore double taps
        // TODO (milestone 2): needs WorkoutRepository. Roughly:
        //  _state.update { it.copy(isStartingWorkout = true) }
        //  viewModelScope.launch {
        //      val startedAt = customStartTime (today) or now, as epoch millis
        //      runCatching { workoutRepository.startWorkout(templateId, startedAt) }
        //          .onSuccess { id -> eventChannel.send(HomeEvent.WorkoutStarted(id)) }
        //          .onFailure { eventChannel.send(HomeEvent.StartWorkoutFailed) }
        //      _state.update { it.copy(isStartingWorkout = false, isStartSheetVisible = false, customStartTime = null) }
        //  }
    }

    /** Copies a finished workout's exercises (and set counts) into a new template. */
    private fun saveWorkoutAsTemplate(workoutId: Long) {
        // TODO (milestone 3): needs TemplateRepository. Roughly:
        //  viewModelScope.launch {
        //      runCatching { templateRepository.createFromWorkout(workoutId) }
        //          .onSuccess { id -> eventChannel.send(HomeEvent.TemplateCreated(id)) }
        //          .onFailure { eventChannel.send(HomeEvent.SaveTemplateFailed) }
        //  }
    }
}
