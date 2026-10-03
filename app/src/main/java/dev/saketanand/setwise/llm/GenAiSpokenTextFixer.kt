package dev.saketanand.setwise.llm

import android.content.Context
import android.util.Log
import androidx.concurrent.futures.await
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.proofreading.Proofreader
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.ai.ModelDownload
import dev.saketanand.setwise.domain.ai.SpokenTextFixer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [SpokenTextFixer] on ML Kit GenAI Proofreading, set for voice input (it expects words that
 * sound alike rather than typos) in English. A client per use, closed when done.
 */
class GenAiSpokenTextFixer(context: Context) : SpokenTextFixer {

    private val options = ProofreaderOptions.builder(context)
        .setInputType(ProofreaderOptions.InputType.VOICE)
        .setLanguage(ProofreaderOptions.Language.ENGLISH)
        .build()

    override suspend fun availability(): ModelAvailability =
        runCatching { withClient { it.checkFeatureStatus().await().toModelAvailability() } }
            .onFailure { e -> Log.i(TAG, "Proofreading isn't available here", e) }
            .getOrDefault(ModelAvailability.Unavailable)

    override fun download(): Flow<ModelDownload> = callbackFlow {
        val client = Proofreading.getClient(options)
        var totalBytes: Long? = null
        client.downloadFeature(
            object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) {
                    totalBytes = bytesToDownload.takeIf { it > 0 }
                    trySend(ModelDownload.Progress(0, totalBytes))
                }

                override fun onDownloadProgress(totalBytesDownloaded: Long) {
                    trySend(ModelDownload.Progress(totalBytesDownloaded, totalBytes))
                }

                override fun onDownloadCompleted() {
                    trySend(ModelDownload.Done)
                    close()
                }

                override fun onDownloadFailed(e: GenAiException) {
                    trySend(ModelDownload.Failed(e.message, e.toDownloadFailure()))
                    close()
                }
            },
        )
        awaitClose { client.close() }
    }

    override suspend fun fix(text: String): String? = try {
        withClient { client ->
            client.runInference(ProofreadingRequest.builder(text).build()).await().results.firstOrNull()?.text
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Proofreading failed", e)
        null
    }

    private suspend fun <T> withClient(block: suspend (Proofreader) -> T): T {
        val client = Proofreading.getClient(options)
        try {
            return block(client)
        } finally {
            client.close()
        }
    }

    private companion object {
        const val TAG = "GenAiSpokenTextFixer"
    }
}
