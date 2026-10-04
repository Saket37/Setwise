package dev.saketanand.setwise.testing

import dev.saketanand.setwise.domain.ai.Heard
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.ai.ModelDownload
import dev.saketanand.setwise.domain.ai.SpeechInput
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Speech input that "hears" [script] (each after [stepMs] of virtual time), then keeps
 * listening until [stop], as the real one does.
 */
class FakeSpeechInput(
    var availability: ModelAvailability = ModelAvailability.Ready,
    var script: List<Heard> = emptyList(),
    var stepMs: Long = 300,
    var failure: Exception? = null,
) : SpeechInput {
    var stops = 0
    var downloads = 0
    private var stopped = CompletableDeferred<Unit>()

    override suspend fun availability() = availability

    override fun download(): Flow<ModelDownload> = flowOf(ModelDownload.Done).also { downloads++ }

    override fun listen(recording: File?): Flow<Heard> = flow {
        stopped = CompletableDeferred()
        failure?.let { throw it }
        script.forEach { heard ->
            delay(stepMs)
            emit(heard)
        }
        stopped.await()
    }

    override suspend fun stop() {
        stops++
        stopped.complete(Unit)
    }
}
