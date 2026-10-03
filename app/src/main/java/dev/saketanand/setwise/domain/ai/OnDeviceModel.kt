package dev.saketanand.setwise.domain.ai

import kotlinx.coroutines.flow.Flow

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
}

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
 * A prompt. Low temperature by default: features ask for facts and small JSON, not prose.
 * @param system how to answer (role, rules, output format).
 */
data class ModelRequest(
    val system: String,
    val prompt: String,
    val temperature: Float = 0.2f,
    val maxOutputTokens: Int = 256,
)
