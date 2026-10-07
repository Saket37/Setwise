package dev.saketanand.setwise.ui.body

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.ai.BodyTipsWriter
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.BodyTip
import dev.saketanand.setwise.domain.model.BodyTips
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
    /** What this report (and the one before) give reason to do (#124); empty for none. */
    val tips: List<BodyTip> = emptyList(),
    /** The on-device model's wording of [tips], one sentence each; null: the screen words them. */
    val modelTips: List<String>? = null,
)

/** Screen: [BodyReportScreenRoot]. One body check, everything its report said (design 17). */
class BodyReportViewModel(
    private val measurementId: Long,
    private val bodyRepository: BodyRepository,
    private val tipsWriter: BodyTipsWriter,
) : ViewModel() {

    private val confirming = MutableStateFlow(false)
    private val modelTips = MutableStateFlow<Pair<List<BodyTip>, List<String>>?>(null)
    private var asked: List<BodyTip>? = null

    val state: StateFlow<BodyReportUiState> = combine(bodyRepository.observeMeasurements(), confirming, modelTips) { checks, confirming, written ->
        val index = checks.indexOfFirst { it.id == measurementId }
        val check = checks.getOrNull(index)
        // Newest first: the report before this one is the next with report values.
        val previous = if (index < 0) null else checks.drop(index + 1).firstOrNull { it.source == BodyMeasurement.Source.Report }
        val tips = check?.let { BodyTips.of(it, previous) }.orEmpty()
        if (tips.isNotEmpty() && tips != asked) writeTips(tips)
        BodyReportUiState(
            isLoading = false,
            check = check,
            isMissing = check == null,
            isConfirmingDelete = confirming && check != null,
            tips = tips,
            modelTips = written?.takeIf { it.first == tips }?.second,
        )
    }
        .catch { e ->
            Log.e(TAG, "Loading body check $measurementId failed", e)
            emit(BodyReportUiState(isLoading = false, isMissing = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyReportUiState())

    /** The model words the tips once per set of tips, after the screen shows the code's wording. */
    private fun writeTips(tips: List<BodyTip>) {
        asked = tips
        viewModelScope.launch {
            tipsWriter.write(tips)?.let { modelTips.value = tips to it }
        }
    }

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
