package dev.saketanand.setwise.domain.ai

import android.util.Log
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseCatalog
import dev.saketanand.setwise.domain.model.ExerciseGuess
import dev.saketanand.setwise.domain.model.ExerciseKeywords
import dev.saketanand.setwise.domain.model.ExerciseNames
import dev.saketanand.setwise.domain.model.ExerciseType
import kotlinx.coroutines.CancellationException

/** Where a suggestion came from: the on-device model, or the keyword rules. */
enum class SuggestionSource { Model, Keywords }

data class ExerciseSuggestion(val guess: ExerciseGuess, val source: SuggestionSource)

/**
 * Help on "New exercise": is what's typed already in the library ([findMatch]), and what are
 * its details ([suggestDetails]). Code decides what it's sure of; the on-device model only the
 * rest. Tested on a real Pixel: Gemini Nano was too cautious on clear matches ("ohp" → none)
 * and wrong where keywords are sure ("incline walk" as a weighted quads lift), but good at
 * what keywords don't know ("landmine press": shoulders, barbell).
 */
class ExerciseAssistant(private val model: OnDeviceModel) {

    /**
     * The library exercise that is the same as [typed], or null. Clear cases are code's: the
     * same words in any order, or a close candidate ([ExerciseNames.CLOSE_MATCH]). In the
     * uncertain middle the model picks from the code's shortlist (or says none); without the
     * model, a library name containing what's typed counts ("bench" → Bench Press), as before.
     */
    suspend fun findMatch(typed: String, library: List<Exercise>): Exercise? {
        library.firstOrNull { ExerciseNames.sameName(it.name, typed) }?.let { return it }
        val candidates = ExerciseNames.candidates(typed, library, MAX_CANDIDATES)
        candidates.firstOrNull()?.takeIf { (_, score) -> score >= ExerciseNames.CLOSE_MATCH }?.let { return it.first }
        val contained = library.firstOrNull { it.name.contains(typed.trim(), ignoreCase = true) }
        if (candidates.isEmpty() || model.availability() != ModelAvailability.Ready) return contained

        val answer = ask("match '$typed'") { model.generate(matchPrompt(typed, candidates.map { it.first }), ModelMatchAnswer.OUTPUT) }
            ?: return contained
        return when (answer.choice) {
            0 -> null
            in 1..candidates.size -> candidates[answer.choice - 1].first
            else -> contained // out of range: as if no answer
        }
    }

    /**
     * The keyword rules for every detail they recognise; the model (within the fixed choices)
     * for the ones they leave empty. [SuggestionSource.Model] when the model filled any.
     */
    suspend fun suggestDetails(typed: String): ExerciseSuggestion? {
        val keywords = ExerciseKeywords.guess(typed)
        val fallback = ExerciseSuggestion(keywords, SuggestionSource.Keywords).takeUnless { keywords.isEmpty }
        val complete = keywords.muscleGroup != null && keywords.type != null && keywords.equipment != null
        if (complete || model.availability() != ModelAvailability.Ready) return fallback
        val answer = ask("classify '$typed'") { model.generate(detailsPrompt(typed), ModelExerciseDetails.OUTPUT) }
            ?: return fallback
        val model = answer.toGuess() ?: return fallback
        val merged = ExerciseGuess(
            muscleGroup = keywords.muscleGroup ?: model.muscleGroup,
            type = keywords.type ?: model.type,
            isTimed = if (keywords.type != null) keywords.isTimed else model.isTimed,
            equipment = keywords.equipment ?: model.equipment,
        )
        return ExerciseSuggestion(merged, if (merged == keywords) SuggestionSource.Keywords else SuggestionSource.Model)
    }

    private suspend fun <T> ask(what: String, block: suspend () -> T?): T? {
        val startedAt = System.nanoTime()
        return try {
            block().also { Log.d(TAG, "$what: $it (${(System.nanoTime() - startedAt) / 1_000_000} ms)") }
        } catch (e: CancellationException) {
            throw e // typing again: this question is no longer needed
        } catch (e: Exception) {
            Log.w(TAG, "The model couldn't $what", e)
            null
        }
    }

    companion object {
        private const val TAG = "ExerciseAssistant"
        private const val MAX_CANDIDATES = 5

        /** Within the choices, or null (the text fallback isn't held to them). */
        fun ModelExerciseDetails.toGuess(): ExerciseGuess? {
            if (muscleGroup !in ExerciseCatalog.MUSCLE_GROUPS || equipment !in ExerciseCatalog.EQUIPMENT) return null
            val (type, timed) = when (logging) {
                "weight and reps" -> ExerciseType.STRENGTH to false
                "bodyweight reps" -> ExerciseType.BODYWEIGHT to false
                "timed hold" -> ExerciseType.BODYWEIGHT to true
                "cardio" -> ExerciseType.CARDIO to false
                else -> return null
            }
            return ExerciseGuess(muscleGroup = if (type == ExerciseType.CARDIO) "Cardio" else muscleGroup, type = type, isTimed = timed, equipment = equipment)
        }

        fun matchPrompt(typed: String, candidates: List<Exercise>): ModelRequest = ModelRequest(
            system = MATCH_SYSTEM,
            prompt = "## Examples\n$MATCH_EXAMPLES\n\n## Exercise\n<typed>$typed</typed>\n<library>\n" +
                candidates.mapIndexed { i, exercise -> "${i + 1}. ${exercise.name}" }.joinToString("\n") +
                "\n</library>",
            temperature = 0.1f,
            maxOutputTokens = 16,
        )

        fun detailsPrompt(typed: String): ModelRequest = ModelRequest(
            system = DETAILS_SYSTEM,
            prompt = "## Examples\n$DETAILS_EXAMPLES\n\n## Exercise\n<exercise>$typed</exercise>",
            temperature = 0.1f,
            maxOutputTokens = 48,
        )

        private const val MATCH_SYSTEM =
            "You decide whether the gym exercise someone typed is already in their library. " +
                "Shorthand counts as the same (db = dumbbell, bb = barbell). " +
                "A different variation, angle or piece of equipment is a different exercise: then answer 0."

        private val MATCH_EXAMPLES = """
            <typed>bb bench</typed>
            <library>
            1. Bench Press (Dumbbell)
            2. Bench Press (Barbell)
            3. Incline Bench Press (Barbell)
            </library>
            {"choice": 2}

            <typed>incline db fly</typed>
            <library>
            1. Incline Dumbbell Press
            2. Chest Fly (Dumbbell)
            </library>
            {"choice": 0}
        """.trimIndent()

        private const val DETAILS_SYSTEM =
            "You classify a gym exercise by its name: the main muscle group it works, how a set is logged, and its equipment."

        private val DETAILS_EXAMPLES = """
            <exercise>zercher squat</exercise>
            {"muscleGroup": "Quads", "logging": "weight and reps", "equipment": "Barbell"}

            <exercise>wall sit</exercise>
            {"muscleGroup": "Quads", "logging": "timed hold", "equipment": "Bodyweight"}

            <exercise>incline treadmill walk</exercise>
            {"muscleGroup": "Cardio", "logging": "cardio", "equipment": "Machine"}
        """.trimIndent()
    }
}
