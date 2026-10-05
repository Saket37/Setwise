package dev.saketanand.setwise.ui.body

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.repository.BodyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class BodyReportUiState(
    val isLoading: Boolean = true,
    val check: BodyMeasurement? = null,
    /** Gone (deleted, or never there): the screen closes. */
    val isMissing: Boolean = false,
    val isConfirmingDelete: Boolean = false,
)

/** Screen: [BodyReportScreenRoot]. One body check, everything its report said (design 17). */
class BodyReportViewModel(
    private val measurementId: Long,
    private val bodyRepository: BodyRepository,
) : ViewModel() {

    private val confirming = MutableStateFlow(false)

    val state: StateFlow<BodyReportUiState> = combine(bodyRepository.observeMeasurements(), confirming) { checks, confirming ->
        val check = checks.firstOrNull { it.id == measurementId }
        BodyReportUiState(isLoading = false, check = check, isMissing = check == null, isConfirmingDelete = confirming && check != null)
    }
        .catch { e ->
            Log.e(TAG, "Loading body check $measurementId failed", e)
            emit(BodyReportUiState(isLoading = false, isMissing = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyReportUiState())

    fun onDeleteClick() = confirming.update { true }

    fun onDeleteDismiss() = confirming.update { false }

    /** Deleted: the check is gone, so the screen closes. */
    fun onConfirmDelete() {
        confirming.value = false
        viewModelScope.launch {
            runCatching { bodyRepository.delete(measurementId) }.onFailure { e -> Log.e(TAG, "Deleting body check $measurementId failed", e) }
        }
    }

    private companion object {
        const val TAG = "BodyReportViewModel"
    }
}
