package dev.saketanand.setwise.ui.history

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Screen: [HistoryScreenRoot]. Live: a finished workout, a deleted one or corrected times show
 * up at once, and the day strip moves on at midnight. The selected day survives rotation and
 * the app being killed in the background (SavedStateHandle).
 */
class HistoryViewModel(
    workoutRepository: WorkoutRepository,
    private val dateProvider: DateProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Epoch day of the selected day (LocalDate isn't a SavedStateHandle type); null = none. */
    private val selectedEpochDay = savedStateHandle.getStateFlow<Long?>(KEY_SELECTED_DAY, null)

    val state: StateFlow<HistoryUiState> = combine(
        dateProvider.today(),
        workoutRepository.observeHistory(),
        selectedEpochDay.map { day -> day?.let(LocalDate::ofEpochDay) },
    ) { today, history, selected -> historyUi(history, today, dateProvider.zone, selected) }
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
            // Navigation: HistoryScreenRoot handles it.
            is HistoryAction.OnWorkoutClick -> Unit
        }
    }

    private companion object {
        const val TAG = "HistoryViewModel"
        const val KEY_SELECTED_DAY = "selected_day"
    }
}
