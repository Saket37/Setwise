package dev.saketanand.setwise.domain.ai

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/**
 * Reads the JSON object a model was asked for. Small models often wrap it (```json fences, a
 * sentence before or after), so the first {...} in the answer is taken. Anything that doesn't
 * parse into [T] is null: callers then use their fallback. Unknown keys are ignored, missing
 * required ones are not.
 */
object ModelJson {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun <T> decode(answer: String, deserializer: DeserializationStrategy<T>): T? {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.decodeFromString(deserializer, answer.substring(start, end + 1)) }.getOrNull()
    }
}
