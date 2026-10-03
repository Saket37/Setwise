package dev.saketanand.setwise.domain.ai

import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelJsonTest {

    @Serializable
    private data class Answer(val kcal: Int, val intensity: String)

    @Test
    fun `reads the object even inside fences or chatter`() {
        val expected = Answer(320, "moderate")
        assertEquals(expected, ModelJson.decode("""{"kcal": 320, "intensity": "moderate"}""", Answer.serializer()))
        assertEquals(expected, ModelJson.decode("```json\n{\"kcal\":320,\"intensity\":\"moderate\"}\n```", Answer.serializer()))
        assertEquals(expected, ModelJson.decode("Sure! {kcal: 320, intensity: moderate, note: \"x\"} Hope that helps.", Answer.serializer()))
    }

    @Test
    fun `anything else is null, so the caller falls back`() {
        assertNull(ModelJson.decode("About 320 kcal.", Answer.serializer()))
        assertNull(ModelJson.decode("""{"kcal": "lots"}""", Answer.serializer()))
        assertNull(ModelJson.decode("""{"intensity": "moderate"}""", Answer.serializer())) // kcal missing
        assertNull(ModelJson.decode("}{", Answer.serializer()))
    }
}
