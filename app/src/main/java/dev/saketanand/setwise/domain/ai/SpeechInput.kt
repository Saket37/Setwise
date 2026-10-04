package dev.saketanand.setwise.domain.ai

import java.io.File
import kotlinx.coroutines.flow.Flow

/** What has been heard while listening. */
sealed interface Heard {
    /** So far: may still change. */
    data class Partial(val text: String) : Heard

    /** A finished stretch of speech. */
    data class Final(val text: String) : Heard
}

/**
 * On-device speech to text, in English: the quick log's mic. [availability] and [download]
 * work like [OnDeviceModel]'s; where it isn't available the phone's own recognizer is used.
 */
interface SpeechInput {

    suspend fun availability(): ModelAvailability

    fun download(): Flow<ModelDownload>

    /**
     * Listens to the microphone (RECORD_AUDIO must be granted), or to [recording] (raw 16 kHz
     * mono 16-bit PCM) if given, until it ends, [stop] is called or the flow is cancelled.
     * Throws if recognition fails.
     */
    fun listen(recording: File? = null): Flow<Heard>

    /** Stops listening; what was heard still arrives. */
    suspend fun stop()
}
