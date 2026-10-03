package dev.saketanand.setwise.ui.summary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.repository.TemplateRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.DateProvider
import java.time.LocalTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen: [WorkoutSummaryScreenRoot]. Shown after Finish and when opening a workout from History.
 *
 * @param workoutId from [Route.WorkoutSummary], passed in by SetwiseNavHost (Koin parametersOf).
 */
class WorkoutSummaryViewModel(
    private val workoutId: Long,
    private val workoutRepository: WorkoutRepository,
    private val templateRepository: TemplateRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    /** Screen-only state on top of the workout: the edit-times dialog, the saved template. */
    private val overlays = MutableStateFlow(Overlays())

    private var isClosing = false
    private val eventChannel = Channel<WorkoutSummaryEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutSummaryEvent> = eventChannel.receiveAsFlow()

    val state: StateFlow<WorkoutSummaryUiState> = combine(
        workoutRepository.observeSession(workoutId).onEach { session ->
            // Deleted, or somehow still running: there's nothing to summarise.
            if (session == null || session.endedAt == null) close()
        },
        overlays,
    ) { session, overlays ->
        if (session == null || session.endedAt == null) return@combine WorkoutSummaryUiState(isLoading = false)
        session.toSummaryUi(dateProvider.zone).copy(
            isTemplateSaved = overlays.isTemplateSaved,
            editTimes = overlays.editTimes,
        )
    }
        .catch { e ->
            Log.e(TAG, "Loading the summary of workout $workoutId failed", e)
            emit(WorkoutSummaryUiState(isLoading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSummaryUiState())

    fun onAction(action: WorkoutSummaryAction) {
        when (action) {
            WorkoutSummaryAction.OnSaveAsTemplateClick -> saveAsTemplate()

            WorkoutSummaryAction.OnEditTimesClick -> {
                val state = state.value
                val start = state.startTime ?: return
                val end = state.endTime ?: return
                overlays.update { it.copy(editTimes = EditTimesUi(start, end)) }
            }
            is WorkoutSummaryAction.OnPickTime -> updateEditTimes { it.copy(picking = action.field) }
            is WorkoutSummaryAction.OnTimePicked -> updateEditTimes { it.withPicked(action.time) }
            WorkoutSummaryAction.OnTimePickerDismiss -> updateEditTimes { it.copy(picking = null) }
            WorkoutSummaryAction.OnSaveTimes -> saveTimes()
            WorkoutSummaryAction.OnEditTimesDismiss -> overlays.update { it.copy(editTimes = null) }

            // Navigation: WorkoutSummaryScreenRoot handles these.
            WorkoutSummaryAction.OnDoneClick, is WorkoutSummaryAction.OnExerciseClick -> Unit
        }
    }

    private fun saveAsTemplate() {
        val current = overlays.value
        if (current.isTemplateSaved || current.isSavingTemplate || !state.value.canSaveAsTemplate) return
        overlays.update { it.copy(isSavingTemplate = true) }
        viewModelScope.launch {
            runCatching { templateRepository.createFromWorkout(workoutId, dateProvider.now()) }
                .onSuccess {
                    overlays.update { it.copy(isTemplateSaved = true, isSavingTemplate = false) }
                    eventChannel.send(WorkoutSummaryEvent.TemplateSaved)
                }
                .onFailure { e ->
                    Log.e(TAG, "Saving workout $workoutId as a template failed", e)
                    overlays.update { it.copy(isSavingTemplate = false) }
                    eventChannel.send(WorkoutSummaryEvent.SaveFailed)
                }
        }
    }

    private fun saveTimes() {
        val edit = overlays.value.editTimes ?: return
        val date = state.value.date ?: return
        val times = editedTimes(date, edit.start, edit.end, dateProvider.zone, dateProvider.now())
        if (times == null) {
            updateEditTimes { it.copy(isInvalid = true) }
            return
        }
        overlays.update { it.copy(editTimes = null) }
        viewModelScope.launch {
            val (startedAt, endedAt) = times
            val saved = runCatching { workoutRepository.updateFinishedTimes(workoutId, startedAt, endedAt) }
                .onFailure { e -> Log.e(TAG, "Saving the times of workout $workoutId failed", e) }
                .getOrDefault(false)
            if (!saved) eventChannel.send(WorkoutSummaryEvent.SaveFailed)
        }
    }

    private fun updateEditTimes(change: (EditTimesUi) -> EditTimesUi) =
        overlays.update { overlays -> overlays.copy(editTimes = overlays.editTimes?.let(change)) }

    private fun EditTimesUi.withPicked(time: LocalTime): EditTimesUi = when (picking) {
        TimeField.Start -> copy(start = time, picking = null, isInvalid = false)
        TimeField.End -> copy(end = time, picking = null, isInvalid = false)
        null -> this
    }

    private fun close() {
        if (isClosing) return
        isClosing = true
        eventChannel.trySend(WorkoutSummaryEvent.Closed)
    }

    private data class Overlays(
        val editTimes: EditTimesUi? = null,
        val isSavingTemplate: Boolean = false,
        val isTemplateSaved: Boolean = false,
    )

    private companion object {
        const val TAG = "WorkoutSummaryViewModel"
    }
}
