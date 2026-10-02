package dev.saketanand.setwise.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.TemplateRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Screen: [HomeScreenRoot]. */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class HomeViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val templateRepository: TemplateRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    private val zone: ZoneId get() = dateProvider.zone

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** One-off events (navigate after a workout is created, show an error). Each is handled once. */
    // BUFFERED: if the screen isn't collecting at that moment (e.g. app just backgrounded),
    // send() doesn't wait; the event is delivered when the screen is visible again.
    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = eventChannel.receiveAsFlow()

    init {
        // Everything the Workout tab shows, live: any change in the database re-emits, and so
        // does the date at midnight (header date, "2 days ago", and this week's window).
        // isLoading turns false with the first emission, so the first-run layout can't flash
        // before the data is known.
        dateProvider.today()
            .flatMapLatest { today ->
                val (weekStart, weekEnd) = weekRange(today, zone)
                combine(
                    workoutRepository.observeLastFinishedWorkout(),
                    workoutRepository.observeActiveWorkout(),
                    workoutRepository.observeStats(weekStart, weekEnd),
                    templateRepository.observeTemplates(),
                    exerciseRepository.observeExerciseCount(),
                ) { lastWorkout, activeWorkout, weekStats, templates, exerciseCount ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            today = today,
                            lastWorkout = lastWorkout?.toLastWorkoutUi(today, zone),
                            activeWorkout = activeWorkout?.toUi(),
                            weekStats = weekStats.toUi(),
                            templates = templates.map { template -> template.toUi(today, zone) },
                            exerciseCount = exerciseCount,
                            // TODO (milestone 14): weeklySummary from the LLM recap.
                        )
                    }
                }
            }
            // A failing query must not crash the app (launchIn would rethrow); log and show
            // what we have.
            .catch { e ->
                Log.e(TAG, "Loading the Workout tab failed", e)
                _state.update { it.copy(isLoading = false) }
            }
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

            HomeAction.OnStartEmptyWorkout -> requestStart(templateId = null)

            is HomeAction.OnStartFromTemplate -> requestStart(templateId = action.templateId)

            HomeAction.OnConfirmDiscardAndStart -> {
                val dialog = _state.value.discardDialog ?: return
                _state.update { it.copy(discardDialog = null) }
                startWorkout(templateId = dialog.templateIdToStart, discardRunningWorkoutId = dialog.runningWorkoutId)
            }

            HomeAction.OnDismissDiscardDialog -> _state.update { it.copy(discardDialog = null) }

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
     * Starts a workout, unless one is already running: then asks first (discard dialog), because
     * only one workout can be in progress at a time.
     */
    private fun requestStart(templateId: Long?) {
        val running = _state.value.activeWorkout
        if (running == null) {
            startWorkout(templateId)
        } else {
            _state.update {
                it.copy(
                    discardDialog = DiscardDialogUi(
                        runningWorkoutId = running.workoutId,
                        runningWorkoutName = running.name,
                        runningCompletedSets = running.completedSets,
                        templateIdToStart = templateId,
                    )
                )
            }
        }
    }

    /**
     * Creates the workout row, then tells the screen to open it via [HomeEvent.WorkoutStarted].
     * @param templateId null for an empty workout.
     * @param discardRunningWorkoutId running workout to delete in the same transaction.
     */
    private fun startWorkout(templateId: Long?, discardRunningWorkoutId: Long? = null) {
        if (_state.value.isStartingWorkout) return // ignore double taps
        _state.update { it.copy(isStartingWorkout = true) }
        viewModelScope.launch {
            // "Change" in the sheet back-dates the start to that time today; otherwise now.
            val startedAt = _state.value.customStartTime
                ?.atDate(_state.value.today)?.atZone(zone)?.toInstant()
                ?: dateProvider.now()
            runCatching { workoutRepository.startWorkout(templateId, startedAt, discardRunningWorkoutId) }
                .onSuccess { id -> eventChannel.send(HomeEvent.WorkoutStarted(id)) }
                .onFailure { eventChannel.send(HomeEvent.StartWorkoutFailed) }
            _state.update {
                it.copy(isStartingWorkout = false, isStartSheetVisible = false, customStartTime = null)
            }
        }
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

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
