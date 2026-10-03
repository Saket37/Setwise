package dev.saketanand.setwise.llm

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.ai.ModelDownload
import dev.saketanand.setwise.domain.ai.ModelRequest
import dev.saketanand.setwise.domain.ai.OnDeviceModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * [OnDeviceModel] on Gemini Nano via the ML Kit GenAI Prompt API (AICore). On phones without
 * AICore every call reports [ModelAvailability.Unavailable] instead of throwing.
 */
class GeminiNanoModel : OnDeviceModel {

    private val model: GenerativeModel by lazy { Generation.getClient() }

    /** Download size, from DownloadStarted, for progress. */
    private var totalBytes: Long? = null

    override suspend fun availability(): ModelAvailability =
        runCatching {
            when (model.checkStatus()) {
                FeatureStatus.AVAILABLE -> ModelAvailability.Ready
                FeatureStatus.DOWNLOADABLE -> ModelAvailability.Downloadable
                FeatureStatus.DOWNLOADING -> ModelAvailability.Downloading
                else -> ModelAvailability.Unavailable
            }
        }
            .onFailure { e -> Log.i(TAG, "Gemini Nano isn't available here", e) }
            .getOrDefault(ModelAvailability.Unavailable)

    override fun download(): Flow<ModelDownload> =
        model.download()
            .map { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> {
                        totalBytes = status.bytesToDownload.takeIf { it > 0 }
                        ModelDownload.Progress(0, totalBytes)
                    }
                    is DownloadStatus.DownloadProgress -> ModelDownload.Progress(status.totalBytesDownloaded, totalBytes)
                    is DownloadStatus.DownloadFailed -> ModelDownload.Failed(status.e.message)
                    DownloadStatus.DownloadCompleted -> ModelDownload.Done
                    else -> ModelDownload.Progress(0, totalBytes)
                }
            }
            .catch { e ->
                Log.w(TAG, "Downloading Gemini Nano failed", e)
                emit(ModelDownload.Failed(e.message))
            }

    override suspend fun generate(request: ModelRequest): String {
        val response = model.generateContent(
            generateContentRequest(SystemInstruction(request.system), TextPart(request.prompt)) {
                temperature = request.temperature
                maxOutputTokens = request.maxOutputTokens
            }
        )
        return checkNotNull(response.candidates.firstOrNull()?.text) { "Gemini Nano gave no answer" }
    }

    private companion object {
        const val TAG = "GeminiNanoModel"
    }
}
