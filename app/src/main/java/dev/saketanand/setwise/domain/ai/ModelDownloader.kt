package dev.saketanand.setwise.domain.ai

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Follows the model download app-wide, not per screen: AICore keeps downloading when Settings
 * closes, and this keeps the progress, so coming back shows where it is. One per app (Koin
 * single, application scope).
 */
class ModelDownloader(
    private val model: OnDeviceModel,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var job: Job? = null

    /** Starts the download, or does nothing if one is already being followed. */
    fun start() {
        if (job?.isActive == true) return
        // AICore may wait (network, storage…) before the first byte: not "0%" yet.
        _state.value = DownloadState.WaitingToStart
        job = scope.launch {
            model.download().collect { step ->
                _state.value = when (step) {
                    is ModelDownload.Progress -> when {
                        step.bytesDownloaded <= 0 -> DownloadState.WaitingToStart
                        else -> DownloadState.Downloading(
                            percent = step.totalBytes?.let { total -> (step.bytesDownloaded * 100 / total).toInt().coerceIn(0, 100) },
                        )
                    }
                    ModelDownload.Done -> DownloadState.Done
                    is ModelDownload.Failed -> {
                        Log.w(TAG, "Model download failed (${step.kind}): ${step.reason}")
                        DownloadState.Failed(step.kind)
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "ModelDownloader"
    }
}

sealed interface DownloadState {
    /** Not started from this app (AICore may still be downloading on its own). */
    data object Idle : DownloadState

    /** Asked for; no bytes yet. */
    data object WaitingToStart : DownloadState

    /** [percent] null when AICore didn't say how big it is. */
    data class Downloading(val percent: Int?) : DownloadState

    data object Done : DownloadState

    data class Failed(val kind: DownloadFailure) : DownloadState
}
