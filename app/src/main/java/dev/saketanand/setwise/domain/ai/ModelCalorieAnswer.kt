package dev.saketanand.setwise.domain.ai

import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import kotlinx.serialization.Serializable

/**
 * What [CalorieEstimator] asks the model for: the calories only. Intensity is a rule (sets per
 * hour), so code decides it; on a real phone Gemini Nano answered "moderate" whatever the
 * density, even with the rule spelled out.
 *
 * @Generable: on phones with structured output the model must fill exactly this (KSP generates
 * the schema); @Serializable: elsewhere it's read from the JSON answer. CalorieEstimator still
 * checks it against the formula.
 */
@Generable("Calories burned in one gym workout")
@Serializable
data class ModelCalorieAnswer(
    @Guide(description = "Gross calories burned, resting included", minimum = 0.0, maximum = 5000.0)
    val kcal: Int,
) {
    companion object {
        val OUTPUT = ModelOutput(
            type = ModelCalorieAnswer::class,
            json = serializer(),
            textFormat = """{"kcal": <whole number>}""",
        )
    }
}
