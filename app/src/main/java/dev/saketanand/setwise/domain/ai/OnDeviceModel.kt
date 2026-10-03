package dev.saketanand.setwise.domain.ai

import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.DeserializationStrategy

/**
 * The on-device language model (Gemini Nano through AICore), behind an interface so features
 * can be tested with a fake and fall back cleanly where there's no model. Every feature that
 * uses it has a non-model fallback; nothing waits on it to work.
 */
interface OnDeviceModel {

    /** Whether it can answer now. Cheap; check again before each use (it can be downloaded meanwhile). */
    suspend fun availability(): ModelAvailability

    /** Starts (or follows) the model download; completes after [ModelDownload.Done] or [ModelDownload.Failed]. */
    fun download(): Flow<ModelDownload>

    /** One answer to [request]. Throws if the model fails or isn't available. */
    suspend fun generate(request: ModelRequest): String

    /**
     * A typed answer. Uses the model's structured output where the phone supports it (the
     * answer then matches [output]'s @Generable schema), else asks for [ModelOutput.textFormat]
     * and reads it with [ModelJson]. Null if neither gives a valid [T]; throws if the model fails.
     */
    suspend fun <T : Any> generate(request: ModelRequest, output: ModelOutput<T>): T?
}

/**
 * The shape of a typed answer: [type] is a class annotated @Generable (structured output) and
 * @Serializable ([json], for phones without structured output, with [textFormat] as the
 * instruction to answer in that JSON).
 */
class ModelOutput<T : Any>(
    val type: KClass<T>,
    val json: DeserializationStrategy<T>,
    val textFormat: String,
)

enum class ModelAvailability {
    /** This phone can't run it (no AICore / unsupported device). */
    Unavailable,

    /** Supported, but the model isn't on the phone yet. */
    Downloadable,

    Downloading,

    Ready,
}

sealed interface ModelDownload {
    data class Progress(val bytesDownloaded: Long, val totalBytes: Long?) : ModelDownload
    data object Done : ModelDownload
    data class Failed(val reason: String?) : ModelDownload
}

/**
 * A prompt, shaped for Gemini Nano: [system] is a short instruction (under ~150 words) on how
 * to answer; [prompt] holds `##` sections with the data in tags (e.g. `<workout>…</workout>`).
 * Low temperature by default (0.2): features ask for facts, not prose.
 */
data class ModelRequest(
    val system: String,
    val prompt: String,
    val temperature: Float = 0.2f,
    val maxOutputTokens: Int = 256,
)
