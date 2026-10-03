package dev.saketanand.setwise.domain.ai

import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import kotlinx.serialization.Serializable

/**
 * [ExerciseAssistant]'s answers. @Generable: structured output keeps the model to these shapes
 * and choices (KSP generates the schemas); @Serializable: read from JSON where the phone has no
 * structured output. The choices match ExerciseCatalog (a test checks the generated schema).
 */

@Generable("Which library exercise is the same as the typed one")
@Serializable
data class ModelMatchAnswer(
    @Guide(description = "The number of the same exercise in the library list, or 0 if none is the same", minimum = 0.0, maximum = 5.0)
    val choice: Int,
) {
    companion object {
        val OUTPUT = ModelOutput(ModelMatchAnswer::class, serializer(), """{"choice": <0 to 5>}""")
    }
}

@Generable("A gym exercise's details")
@Serializable
data class ModelExerciseDetails(
    @Guide(
        description = "The main muscle group it works",
        enumValues = [
            "Chest", "Back", "Shoulders", "Biceps", "Triceps", "Forearms", "Core",
            "Quads", "Hamstrings", "Glutes", "Calves", "Full Body", "Cardio",
        ],
    )
    val muscleGroup: String,
    @Guide(description = "How a set is logged", enumValues = ["weight and reps", "bodyweight reps", "timed hold", "cardio"])
    val logging: String,
    @Guide(
        description = "The equipment it uses",
        enumValues = ["Barbell", "Dumbbell", "Machine", "Cable", "Kettlebell", "Smith Machine", "EZ Bar", "Bodyweight", "None"],
    )
    val equipment: String,
) {
    companion object {
        val OUTPUT = ModelOutput(
            ModelExerciseDetails::class,
            serializer(),
            """{"muscleGroup": "<muscle group>", "logging": "weight and reps" | "bodyweight reps" | "timed hold" | "cardio", "equipment": "<equipment>"}""",
        )
    }
}
