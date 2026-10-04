package dev.saketanand.setwise.llm

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import dev.saketanand.setwise.domain.ai.DownloadFailure
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.ai.ModelDownload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Shared by the ML Kit GenAI features (prompt, speech recognition). */

fun Int.toModelAvailability(): ModelAvailability = when (this) {
    FeatureStatus.AVAILABLE -> ModelAvailability.Ready
    FeatureStatus.DOWNLOADABLE -> ModelAvailability.Downloadable
    FeatureStatus.DOWNLOADING -> ModelAvailability.Downloading
    else -> ModelAvailability.Unavailable
}

/** ML Kit's download events as [ModelDownload]s, with progress against the size it announced. */
fun Flow<DownloadStatus>.toModelDownloads(): Flow<ModelDownload> = flow {
    var totalBytes: Long? = null
    collect { status ->
        emit(
            when (status) {
                is DownloadStatus.DownloadStarted -> {
                    totalBytes = status.bytesToDownload.takeIf { it > 0 }
                    ModelDownload.Progress(0, totalBytes)
                }
                is DownloadStatus.DownloadProgress -> ModelDownload.Progress(status.totalBytesDownloaded, totalBytes)
                is DownloadStatus.DownloadFailed -> ModelDownload.Failed(status.e.message, status.e.toDownloadFailure())
                DownloadStatus.DownloadCompleted -> ModelDownload.Done
                else -> ModelDownload.Progress(0, totalBytes)
            },
        )
    }
}

fun GenAiException.toDownloadFailure(): DownloadFailure = when (errorCode) {
    GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> DownloadFailure.NotEnoughSpace
    GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE, GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> DownloadFailure.NeedsSystemUpdate
    else -> DownloadFailure.Other
}
