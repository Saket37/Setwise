package dev.saketanand.setwise.ui.history

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.model.DayCheckIn
import dev.saketanand.setwise.domain.model.DayStatus
import dev.saketanand.setwise.domain.repository.DayMarkRepository
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Screen: [HistoryScreenRoot]. Live: a finished workout, a deleted one, corrected times or a day
 * marked rest / missed show up at once, and the day strip moves on at midnight. The selected day
 * survives rotation and the app being killed in the background (SavedStateHandle).
 */
class HistoryViewModel(
    private val workoutRepository: WorkoutRepository,
    private val dayMarkRepository: DayMarkRepository,
    userSettingsRepository: UserSettingsRepository,
    private val dateProvider: DateProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Epoch day of the selected day (LocalDate isn't a SavedStateHandle type); null = none. */
    private val selectedEpochDay = savedStateHandle.getStateFlow<Long?>(KEY_SELECTED_DAY, null)

    private var isStartingWorkout = false
    private val eventChannel = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = eventChannel.receiveAsFlow()

    val state: StateFlow<HistoryUiState> = combine(
        dateProvider.today(),
        workoutRepository.observeHistory(),
        dayMarkRepository.observeMarks(),
        userSettingsRepository.settings.map { it.trainingDays },
        selectedEpochDay.map { day -> day?.let(LocalDate::ofEpochDay) },
    ) { today, history, marks, trainingDays, selected ->
        historyUi(history, today, dateProvider.zone, selected, marks, trainingDays)
    }
        .catch { e ->
            Log.e(TAG, "Loading the history failed", e)
            emit(HistoryUiState(isLoading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.OnDayClick -> {
                val day = action.date.toEpochDay()
                savedStateHandle[KEY_SELECTED_DAY] = if (selectedEpochDay.value == day) null else day
            }
            is HistoryAction.OnLogWorkoutClick -> logWorkout(action.date)
            is HistoryAction.OnMarkDay -> markDay(action.date, action.status)
            // Navigation: HistoryScreenRoot handles it.
            is HistoryAction.OnWorkoutClick -> Unit
        }
    }

    /** Starts an empty workout on [day]; not while another one runs (that one would be lost). */
    private fun logWorkout(day: LocalDate) {
        if (isStartingWorkout) return // ignore double taps
        isStartingWorkout = true
        viewModelScope.launch {
            runCatching {
                if (workoutRepository.observeActiveWorkout().first() != null) {
                    eventChannel.send(HistoryEvent.WorkoutAlreadyRunning)
                } else {
                    val id = workoutRepository.startWorkout(null, DayCheckIn.backfillStartedAt(day, dateProvider.zone))
                    eventChannel.send(HistoryEvent.WorkoutStarted(id))
                }
            }.onFailure { e ->
                Log.e(TAG, "Logging a workout for $day failed", e)
                eventChannel.send(HistoryEvent.SaveFailed)
            }
            isStartingWorkout = false
        }
    }

    private fun markDay(day: LocalDate, status: DayStatus?) {
        viewModelScope.launch {
            runCatching { dayMarkRepository.mark(listOf(day), status, dateProvider.now()) }
                .onFailure { e ->
                    Log.e(TAG, "Marking $day as $status failed", e)
                    eventChannel.send(HistoryEvent.SaveFailed)
                }
        }
    }

    private companion object {
        const val TAG = "HistoryViewModel"
        const val KEY_SELECTED_DAY = "selected_day"
    }
}
