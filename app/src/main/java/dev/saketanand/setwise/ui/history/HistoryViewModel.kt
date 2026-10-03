package dev.saketanand.setwise.ui.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Screen: [HistoryScreenRoot]. Live: a finished workout, a deleted one or corrected times show
 * up at once, and the week strip moves on at midnight.
 */
class HistoryViewModel(
    workoutRepository: WorkoutRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    val state: StateFlow<HistoryUiState> = combine(
        dateProvider.today(),
        workoutRepository.observeHistory(),
    ) { today, history -> historyUi(history, today, dateProvider.zone) }
        .catch { e ->
            Log.e(TAG, "Loading the history failed", e)
            emit(HistoryUiState(isLoading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private companion object {
        const val TAG = "HistoryViewModel"
    }
}
