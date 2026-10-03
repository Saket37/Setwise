package dev.saketanand.setwise.llm

import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import dev.saketanand.setwise.domain.ai.DownloadFailure
import dev.saketanand.setwise.domain.ai.Heard
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.ai.ModelDownload
import dev.saketanand.setwise.domain.ai.SpeechInput
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile

/**
 * [SpeechInput] on ML Kit GenAI Speech Recognition, US English (the locale both of its modes
 * have). Advanced mode (Gemini, Pixel 10 and later) where the phone has it, else basic (the
 * on-device recognizer). Android 12+ (its microphone source); older phones use their own
 * recognizer. A client per use, closed when done: nothing is held while the mic isn't in use.
 */
class GenAiSpeechInput : SpeechInput {

    /** The mode the phone has, found on first use. */
    @Volatile
    private var mode: Int? = null

    /** The client listening now, for [stop]. */
    @Volatile
    private var listening: SpeechRecognizer? = null

    override suspend fun availability(): ModelAvailability =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) ModelAvailability.Unavailable
        else runCatching { pickMode().second.toModelAvailability() }
            .onFailure { e -> Log.i(TAG, "Speech recognition isn't available here", e) }
            .getOrDefault(ModelAvailability.Unavailable)

    override fun download(): Flow<ModelDownload> =
        flow {
            client(pickMode().first).use { emitAll(it.download().toModelDownloads()) }
        }.catch { e ->
            Log.w(TAG, "Downloading speech recognition failed", e)
            emit(ModelDownload.Failed(e.message, (e as? GenAiException)?.toDownloadFailure() ?: DownloadFailure.Other))
        }

    override fun listen(recording: File?): Flow<Heard> = flow {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { "Speech recognition needs Android 12" }
        val (mode, status) = pickMode()
        check(status == FeatureStatus.AVAILABLE) { "Speech recognition isn't ready (status $status)" }
        val file = recording?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
        try {
            client(mode).use { client ->
                listening = client
                val source = when {
                    file != null -> AudioSource.fromPfd(file)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> AudioSource.fromMic()
                    else -> error("Speech recognition needs Android 12")
                }
                val request = speechRecognizerRequest { audioSource = source }
                client.startRecognition(request)
                    .transformWhile { response ->
                        when (response) {
                            is SpeechRecognizerResponse.PartialTextResponse -> emit(Heard.Partial(response.text))
                            is SpeechRecognizerResponse.FinalTextResponse -> emit(Heard.Final(response.text))
                            is SpeechRecognizerResponse.ErrorResponse -> throw response.e
                            else -> Unit
                        }
                        response !is SpeechRecognizerResponse.CompletedResponse
                    }
                    .collect { emit(it) }
            }
        } finally {
            listening = null
            file?.close()
        }
    }

    override suspend fun stop() {
        listening?.stopRecognition()
    }

    private fun client(mode: Int): SpeechRecognizer =
        SpeechRecognition.getClient(
            speechRecognizerOptions {
                locale = Locale.US
                preferredMode = mode
            },
        )

    /** Advanced if the phone has it, else basic; with its status. */
    private suspend fun pickMode(): Pair<Int, Int> {
        mode?.let { known -> return known to client(known).use { it.checkStatus() } }
        for (candidate in listOf(SpeechRecognizerOptions.Mode.MODE_ADVANCED, SpeechRecognizerOptions.Mode.MODE_BASIC)) {
            val status = client(candidate).use { it.checkStatus() }
            Log.d(TAG, "${candidate.modeName()} mode: status $status")
            if (status != FeatureStatus.UNAVAILABLE) {
                mode = candidate
                return candidate to status
            }
        }
        return SpeechRecognizerOptions.Mode.MODE_BASIC to FeatureStatus.UNAVAILABLE
    }

    private fun Int.modeName() = if (this == SpeechRecognizerOptions.Mode.MODE_ADVANCED) "Advanced" else "Basic"

    private companion object {
        const val TAG = "GenAiSpeechInput"
    }
}
