package dev.saketanand.setwise.llm

import android.util.Log
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
import dev.saketanand.setwise.domain.ai.DownloadFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * [OnDeviceModel] on Gemini Nano via the ML Kit GenAI Prompt API (AICore). On phones without
 * AICore every call reports [ModelAvailability.Unavailable] instead of throwing.
 *
 * One app-wide object (a Koin single), shared by every feature, so its ML Kit client isn't
 * closed per screen. Instead the client is created on first use and closed when the app goes
 * to the background, where AICore won't run inference anyway: right away, or once the calls
 * (or download) in progress finish ([withClient]). What the phone's Gemini Nano supports
 * (system instructions, structured output) is checked on first use and remembered once AICore
 * answers; callers check [availability] first.
 */
class GeminiNanoModel : OnDeviceModel {

    private val lock = Any()
    private var client: GenerativeModel? = null

    /** Calls and downloads using [client] right now. */
    private var users = 0

    /** Starts as background: a process started for a notification or service holds no client. */
    private var inBackground = true

    override fun onAppInBackground(inBackground: Boolean) = synchronized(lock) {
        this.inBackground = inBackground
        closeIfUnused()
    }

    /** Runs [block] with the client, created if needed and kept open until [block] ends. */
    private suspend fun <T> withClient(block: suspend (GenerativeModel) -> T): T {
        // Counted only once there is a client: if getting one throws, nothing is left in use.
        val client = synchronized(lock) {
            (client ?: Generation.getClient().also { client = it }).also { users++ }
        }
        try {
            return block(client)
        } finally {
            synchronized(lock) {
                users--
                closeIfUnused()
            }
        }
    }

    /** Frees the ML Kit client while the app is hidden and nothing uses it (call with [lock] held). */
    private fun closeIfUnused() {
        if (!inBackground || users > 0) return
        client?.let {
            it.close()
            Log.d(TAG, "Closed the client (app in the background)")
        }
        client = null
    }

    // Checked once, when the model is first used (null = not yet).
    private var supportsSystemInstruction: Boolean? = null
    private var supportsStructuredOutput: Boolean? = null

    override suspend fun availability(): ModelAvailability =
        runCatching {
            withClient { it.checkStatus() }.toModelAvailability()
        }
            .onFailure { e -> Log.i(TAG, "Gemini Nano isn't available here", e) }
            .getOrDefault(ModelAvailability.Unavailable)

    override fun download(): Flow<ModelDownload> =
        // Inside flow {}: if AICore throws while starting (or getting the client), it reaches
        // catch below instead of escaping to the caller.
        flow { withClient { emitAll(it.download().toModelDownloads()) } }
            .catch { e ->
                Log.w(TAG, "Downloading Gemini Nano failed", e)
                emit(ModelDownload.Failed(e.message, (e as? GenAiException)?.toDownloadFailure() ?: DownloadFailure.Other))
            }

    override suspend fun generate(request: ModelRequest): String {
        val response = withClient { it.generateContent(request.toMlKit()) }
        return checkNotNull(response.candidates.firstOrNull()?.text) { "Gemini Nano gave no answer" }
    }

    override suspend fun <T : Any> generate(request: ModelRequest, output: ModelOutput<T>): T? {
        if (supportsStructuredOutput()) {
            return try {
                val typed = withClient { it.generateContent(generateTypedContentRequest(request.toMlKit(), output.type)) }
                typed.candidates.firstOrNull()?.response.also { answer ->
                    if (answer == null) Log.w(TAG, "No structured answer (finish reason ${typed.candidates.firstOrNull()?.finishReason})")
                    else Log.d(TAG, "Structured answer: $answer")
                }
            } catch (e: GenAiException) {
                // The answer broke the schema (or ran out of tokens): no answer, the caller falls back.
                Log.w(TAG, "Structured output failed (error ${e.errorCode})", e)
                if (e.errorCode in INVALID_STRUCTURED_ANSWER) null else throw e
            }
        }
        // No structured output on this phone: ask for the JSON in words and read it.
        val answer = generate(request.copy(system = request.system + " Answer with JSON only: " + output.textFormat))
        Log.d(TAG, "Text answer: ${answer.take(MAX_LOGGED_ANSWER)}")
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

    private suspend fun supportsSystemInstruction(): Boolean =
        supportsSystemInstruction ?: askOnce("system instructions") { withClient { it.isSystemPromptAvailable() } }
            ?.also { supportsSystemInstruction = it } ?: false

    private suspend fun supportsStructuredOutput(): Boolean =
        supportsStructuredOutput ?: askOnce("structured output") { withClient { it.isStructuredOutputFeatureAvailable() } }
            ?.also { supportsStructuredOutput = it } ?: false

    /** AICore's answer, or null if asking failed: then it's asked again next time, not remembered as "no". */
    private suspend fun askOnce(feature: String, check: suspend () -> Boolean): Boolean? =
        try {
            check().also { Log.d(TAG, "Supports $feature: $it") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't check $feature support", e)
            null
        }

    private companion object {
        const val TAG = "GeminiNanoModel"
        const val MAX_LOGGED_ANSWER = 300
        val INVALID_STRUCTURED_ANSWER = setOf(
            GenAiException.ErrorCode.STRUCTURED_OUTPUT_RESPONSE_ERROR,
            GenAiException.ErrorCode.STRUCTURED_OUTPUT_MAX_TOKENS_ERROR,
        )
    }
}
