package dev.saketanand.setwise.domain.ai

import android.util.Log
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.Goal
import dev.saketanand.setwise.domain.model.GoalPlanner
import dev.saketanand.setwise.domain.model.PlannedTemplate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/**
 * Plans templates for a typed goal ([GoalPlanner], in code), and lets the on-device model choose,
 * for each planned exercise, among the slot's best few library exercises: so what the goal says
 * beyond what code reads ("my knees hurt", "I love split squats") can count. The model only
 * picks option numbers; anything else (a wrong count, a number out of range) keeps code's choice.
 */
class GoalPlanAssistant(private val model: OnDeviceModel) {

    suspend fun canChoose(): Boolean = model.availability() == ModelAvailability.Ready

    /** [templates] for the goal; [byModel] when the model's choices were used. */
    data class Plan(val templates: List<PlannedTemplate>, val byModel: Boolean)

    /**
     * @param goalText what was typed, for the model.
     * @param variation 0: the model may choose; more: code's later candidates ("Regenerate").
     */
    suspend fun plan(goalText: String, goal: Goal, library: List<Exercise>, doneIds: Set<Long>, variation: Int = 0): Plan {
        // Code's plan, noting each planned exercise's options.
        val options = linkedMapOf<Pair<Int, Int>, List<Exercise>>()
        val byCode = GoalPlanner.plan(goal, library, doneIds, variation) { day, position, _, candidates ->
            options[day to position] = candidates.take(MAX_OPTIONS)
            null
        }
        if (variation != 0 || goalText.isBlank() || model.availability() != ModelAvailability.Ready) return Plan(byCode, byModel = false)

        // Only slots that made it into the plan (time can cut the last ones), with a real choice.
        val names = GoalPlanner.days(goal).map { it.name }
        val planned = byCode.flatMapIndexed { day, template -> template.exercises.map { day to it.position } }.toSet()
        val slots = options.filter { (key, list) -> key in planned && list.size > 1 }
        if (slots.isEmpty()) return Plan(byCode, byModel = false)
        // Any failure of the model keeps code's plan; OnDeviceModel doesn't narrow what it throws.
        @Suppress("TooGenericExceptionCaught")
        val picks = try {
            model.generate(prompt(goalText, slots.map { (key, list) -> names[key.first] to list.map { it.name } }), ModelGoalPicks.OUTPUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The model couldn't choose exercises for the goal", e)
            null
        }
        Log.d(TAG, "Model picks: $picks")
        val chosen = accept(picks, slots) ?: return Plan(byCode, byModel = false)
        val byModel = GoalPlanner.plan(goal, library, doneIds) { day, position, _, candidates ->
            chosen[day to position]?.takeIf { it in candidates }
        }
        return Plan(byModel, byModel = byModel != byCode)
    }

    companion object {
        private const val TAG = "GoalPlanAssistant"
        private const val MAX_OPTIONS = 4

        /** The model's picks as an exercise per slot, if they're one valid option number for each slot. */
        fun accept(picks: ModelGoalPicks?, slots: Map<Pair<Int, Int>, List<Exercise>>): Map<Pair<Int, Int>, Exercise>? {
            val numbers = picks?.picks ?: return null
            if (numbers.size != slots.size) return null
            return slots.entries.zip(numbers).associate { (entry, number) ->
                entry.key to (entry.value.getOrNull(number - 1) ?: return null)
            }
        }

        /** @param slots each planned slot: its template's name and its options, in plan order. */
        fun prompt(goalText: String, slots: List<Pair<String, List<String>>>): ModelRequest = ModelRequest(
            system = "You help plan gym workouts. For each slot, give the number of the option that best fits the person's goal: " +
                "follow what they say about injuries, likes, dislikes and equipment. If the goal says nothing that matters for a slot, give 1.",
            prompt = "## Example\n$EXAMPLE\n\n## Goal\n<goal>$goalText</goal>\n\n## Slots\n" +
                slots.mapIndexed { i, (template, options) ->
                    "Slot ${i + 1} ($template): " + options.mapIndexed { n, name -> "${n + 1}) $name" }.joinToString(" ")
                }.joinToString("\n"),
            temperature = 0.1f,
            maxOutputTokens = 80,
        )

        private val EXAMPLE = """
            <goal>Build muscle, my knees don't like deep squats</goal>
            Slot 1 (Lower): 1) Back Squat (Barbell) 2) Leg Press 3) Goblet Squat (Dumbbell)
            Slot 2 (Lower): 1) Romanian Deadlift (Barbell) 2) Hip Thrust (Barbell)
            {"picks": [2, 1]}
        """.trimIndent()
    }
}

/** The model's choice for each slot: option numbers, in slot order. */
@Generable("The option chosen for each slot of a workout plan")
@Serializable
data class ModelGoalPicks(
    @Guide(description = "One option number per slot, in slot order", minimum = 1.0, maximum = 4.0)
    val picks: List<Int>,
) {
    companion object {
        val OUTPUT = ModelOutput(ModelGoalPicks::class, serializer(), """{"picks": [<option number for each slot>]}""")
    }
}
