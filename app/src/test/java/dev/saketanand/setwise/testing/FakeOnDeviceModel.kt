package dev.saketanand.setwise.testing

import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.ai.ModelDownload
import dev.saketanand.setwise.domain.ai.ModelRequest
import dev.saketanand.setwise.domain.ai.OnDeviceModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A model that answers with [answer] (or throws it), and records what it was asked. */
class FakeOnDeviceModel(
    var availability: ModelAvailability = ModelAvailability.Unavailable,
    /** How long an answer takes (virtual time in tests). */
    var thinkingMs: Long = 0,
    var answer: () -> String = { error("not set") },
) : OnDeviceModel {
    val requests = mutableListOf<ModelRequest>()

    override suspend fun availability(): ModelAvailability = availability

    override fun download(): Flow<ModelDownload> = flowOf(ModelDownload.Progress(0, 100), ModelDownload.Done)

    override suspend fun generate(request: ModelRequest): String {
        requests += request
        if (thinkingMs > 0) delay(thinkingMs)
        return answer()
    }
}
