package dev.saketanand.setwise.llm

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.ai.ModelDownload
import dev.saketanand.setwise.domain.ai.ModelJson
import dev.saketanand.setwise.domain.ai.ModelOutput
import dev.saketanand.setwise.domain.ai.ModelRequest
import dev.saketanand.setwise.domain.ai.OnDeviceModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import dev.saketanand.setwise.domain.ai.DownloadFailure

/**
 * [OnDeviceModel] on Gemini Nano via the ML Kit GenAI Prompt API (AICore). On phones without
 * AICore every call reports [ModelAvailability.Unavailable] instead of throwing.
 *
 * One client for the app's lifetime (a Koin single), so it isn't closed per screen; it's
 * released with the process. What the phone's Gemini Nano supports (system instructions,
 * structured output) is checked once, on first use; callers check [availability] first.
 */
class GeminiNanoModel : OnDeviceModel {

    private val model: GenerativeModel by lazy { Generation.getClient() }

    /** Download size, from DownloadStarted, for progress. */
    private var totalBytes: Long? = null

    // Checked once, when the model is first used (null = not yet).
    private var supportsSystemInstruction: Boolean? = null
    private var supportsStructuredOutput: Boolean? = null

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
                    is DownloadStatus.DownloadFailed -> ModelDownload.Failed(status.e.message, status.e.toDownloadFailure())
                    DownloadStatus.DownloadCompleted -> ModelDownload.Done
                    else -> ModelDownload.Progress(0, totalBytes)
                }
            }
            .catch { e ->
                Log.w(TAG, "Downloading Gemini Nano failed", e)
                emit(ModelDownload.Failed(e.message, (e as? GenAiException)?.toDownloadFailure() ?: DownloadFailure.Other))
            }

    override suspend fun generate(request: ModelRequest): String {
        val response = model.generateContent(request.toMlKit())
        return checkNotNull(response.candidates.firstOrNull()?.text) { "Gemini Nano gave no answer" }
    }

    override suspend fun <T : Any> generate(request: ModelRequest, output: ModelOutput<T>): T? {
        if (supportsStructuredOutput()) {
            return try {
                val typed = model.generateContent(generateTypedContentRequest(request.toMlKit(), output.type))
                typed.candidates.firstOrNull()?.response.also { answer ->
                    if (answer == null) Log.w(TAG, "No structured answer (finish reason ${typed.candidates.firstOrNull()?.finishReason})")
                }
            } catch (e: GenAiException) {
                // The answer broke the schema (or ran out of tokens): no answer, the caller falls back.
                if (e.errorCode in INVALID_STRUCTURED_ANSWER) null else throw e
            }
        }
        // No structured output on this phone: ask for the JSON in words and read it.
        val answer = generate(request.copy(system = request.system + " Answer with JSON only: " + output.textFormat))
        return ModelJson.decode(answer, output.json)
    }

    /**
     * System instruction where supported (Gemini Nano V3+); otherwise the same text as the
     * first `##` section of the prompt.
     */
    private suspend fun ModelRequest.toMlKit(): GenerateContentRequest {
        val configure: GenerateContentRequest.Builder.() -> Unit = {
            temperature = this@toMlKit.temperature
            maxOutputTokens = this@toMlKit.maxOutputTokens
        }
        return if (supportsSystemInstruction()) {
            generateContentRequest(SystemInstruction(system), TextPart(prompt), configure)
        } else {
            generateContentRequest(TextPart("## Instructions\n$system\n\n$prompt"), configure)
        }
    }

    private fun GenAiException.toDownloadFailure() = when (errorCode) {
        GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> DownloadFailure.NotEnoughSpace
        GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE, GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> DownloadFailure.NeedsSystemUpdate
        else -> DownloadFailure.Other
    }

    private suspend fun supportsSystemInstruction(): Boolean =
        supportsSystemInstruction ?: runCatching { model.isSystemPromptAvailable() }.getOrDefault(false)
            .also { supportsSystemInstruction = it }

    private suspend fun supportsStructuredOutput(): Boolean =
        supportsStructuredOutput ?: runCatching { model.isStructuredOutputFeatureAvailable() }.getOrDefault(false)
            .also { supportsStructuredOutput = it }

    private companion object {
        const val TAG = "GeminiNanoModel"
        val INVALID_STRUCTURED_ANSWER = setOf(
            GenAiException.ErrorCode.STRUCTURED_OUTPUT_RESPONSE_ERROR,
            GenAiException.ErrorCode.STRUCTURED_OUTPUT_MAX_TOKENS_ERROR,
        )
    }
}
