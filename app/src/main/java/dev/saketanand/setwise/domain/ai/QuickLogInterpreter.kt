package dev.saketanand.setwise.domain.ai

import android.util.Log
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import dev.saketanand.setwise.domain.model.CardioValues
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.QuickLogParser
import dev.saketanand.setwise.domain.model.SetFact
import dev.saketanand.setwise.domain.model.WorkoutSession
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** The exercise a quick-log line is about: in the workout already ([workoutExerciseId]) or not. */
data class QuickLogTarget(
    val exercise: Exercise,
    val workoutExerciseId: Long?,
    /** The words it was matched from ("ohp"); null when it's the open exercise. */
    val matchedFrom: String?,
)

/** What a quick-log line means, for the "Understood as" card; nothing is logged until confirmed. */
sealed interface QuickLogResult {
    data class Sets(val target: QuickLogTarget, val sets: List<SetFact>, val source: SuggestionSource) : QuickLogResult
    data class Cardio(val target: QuickLogTarget, val values: CardioValues) : QuickLogResult
    data class NotUnderstood(val reason: Reason) : QuickLogResult

    enum class Reason {
        /** No exercise named, none open, or none like it in the library. */
        NoExercise,

        /** An exercise, but no sets, cardio or "same as last time" in it. */
        NothingToLog,

        /** "Same as last time" for an exercise not done before. */
        NoLastTime,
    }
}

/**
 * Reads a quick-log line ("ohp 3 sets of 6 at 40, last one 37.5 for 8"). Code first:
 * [QuickLogParser] reads the usual forms, [ExerciseAssistant] finds the exercise (today's workout
 * first, then the library). Only a line the parser can't fully read goes to the on-device model,
 * which lists the sets; every weight, rep count and time it gives must be one of the line's
 * numbers ([acceptSets]).
 */
class QuickLogInterpreter(
    private val model: OnDeviceModel,
    private val assistant: ExerciseAssistant,
) {

    /**
     * @param openWorkoutExerciseId the exercise card that's open: the target when no exercise is
     *   named ("60 for 8").
     */
    suspend fun interpret(text: String, session: WorkoutSession, openWorkoutExerciseId: Long?, library: List<Exercise>): QuickLogResult {
        val parse = QuickLogParser.parse(text)
        val target = target(parse.exercisePhrase, session, openWorkoutExerciseId, library)
            ?: return QuickLogResult.NotUnderstood(QuickLogResult.Reason.NoExercise)
        val exercise = target.exercise

        if (exercise.type == ExerciseType.CARDIO) {
            val cardio = parse.cardio ?: return QuickLogResult.NotUnderstood(QuickLogResult.Reason.NothingToLog)
            return QuickLogResult.Cardio(target, cardio)
        }
        if (parse.sameAsLastTime) {
            val previous = session.exercises.firstOrNull { it.id == target.workoutExerciseId }?.previousSets.orEmpty()
            if (previous.isEmpty()) return QuickLogResult.NotUnderstood(QuickLogResult.Reason.NoLastTime)
            return QuickLogResult.Sets(target, previous.map { SetFact(it.weightKg, it.reps, it.durationSec) }, SuggestionSource.Keywords)
        }
        if (parse.isComplete) return QuickLogResult.Sets(target, parse.sets.fitTo(exercise), SuggestionSource.Keywords)

        // Words the parser couldn't place ("last one", "twice"): the model, if it's there.
        modelSets(text, exercise)?.let { return QuickLogResult.Sets(target, it.fitTo(exercise), SuggestionSource.Model) }
        if (parse.sets.isNotEmpty()) return QuickLogResult.Sets(target, parse.sets.fitTo(exercise), SuggestionSource.Keywords)
        return QuickLogResult.NotUnderstood(QuickLogResult.Reason.NothingToLog)
    }

    private suspend fun target(phrase: String?, session: WorkoutSession, openId: Long?, library: List<Exercise>): QuickLogTarget? {
        if (phrase == null) {
            val open = session.exercises.firstOrNull { it.id == openId }
                ?: session.exercises.firstOrNull { exercise -> exercise.sets.any { !it.isCompleted } }
                ?: return null
            return QuickLogTarget(open.exercise, open.id, matchedFrom = null)
        }
        // Today's workout first ("in today's template"), then the whole library.
        val inWorkout = assistant.findMatch(phrase, session.exercises.map { it.exercise })
        if (inWorkout != null) {
            val item = session.exercises.filter { it.exercise.id == inWorkout.id }
                .let { same -> same.firstOrNull { exercise -> exercise.sets.any { !it.isCompleted } } ?: same.first() }
            return QuickLogTarget(inWorkout, item.id, matchedFrom = phrase)
        }
        val fromLibrary = assistant.findMatch(phrase, library) ?: return null
        return QuickLogTarget(fromLibrary, workoutExerciseId = null, matchedFrom = phrase)
    }

    private suspend fun modelSets(text: String, exercise: Exercise): List<SetFact>? {
        if (model.availability() != ModelAvailability.Ready) return null
        val startedAt = System.nanoTime()
        val answer = try {
            model.generate(prompt(text, exercise), ModelQuickLog.OUTPUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The model couldn't read '$text'", e)
            null
        }
        val sets = answer?.let { acceptSets(it, text) }
        Log.d(TAG, "'$text': ${if (sets != null) "used" else "rejected"} $answer (${(System.nanoTime() - startedAt) / 1_000_000} ms)")
        return sets
    }

    companion object {
        private const val TAG = "QuickLogInterpreter"

        /**
         * The model's sets, or null if any number in them isn't in [text] (no invented weights or
         * reps), there are none, or a set has neither reps nor seconds.
         */
        fun acceptSets(answer: ModelQuickLog, text: String): List<SetFact>? {
            val numbers = Regex("\\d+(?:[.,]\\d+)?").findAll(text).map { it.value.replace(',', '.').toDouble() }.toSet()
            val sets = answer.sets.map { set ->
                SetFact(
                    weightKg = set.weightKg.takeIf { it > 0 },
                    reps = set.reps.takeIf { it > 0 },
                    seconds = set.seconds.takeIf { it > 0 },
                )
            }
            if (sets.isEmpty()) return null
            val ok = sets.all { set ->
                (set.reps != null || set.seconds != null) &&
                    listOfNotNull(set.weightKg, set.reps?.toDouble(), set.seconds?.toDouble()).all { it in numbers }
            }
            return sets.takeIf { ok }
        }

        fun prompt(text: String, exercise: Exercise): ModelRequest = ModelRequest(
            system = SYSTEM,
            prompt = "## Examples\n$EXAMPLES\n\n## Log\n<exercise>${exercise.name}, ${exercise.logging()}</exercise>\n<line>$text</line>",
            temperature = 0.1f,
            maxOutputTokens = 200,
        )

        private fun Exercise.logging() = when {
            isTimed -> "timed hold"
            type == ExerciseType.BODYWEIGHT -> "bodyweight reps"
            else -> "weight and reps"
        }

        private const val SYSTEM =
            "You turn one gym log line into the list of sets done, in order. Use only numbers from the line. " +
                "\"Last one\" or \"last set\" changes the final set; \"twice\" means two sets. Use 0 for what a set doesn't have."

        private val EXAMPLES = """
            <exercise>Barbell Row, weight and reps</exercise>
            <line>row 4 sets of 10 at 50, last one 45 for 12</line>
            {"sets": [{"weightKg": 50, "reps": 10, "seconds": 0}, {"weightKg": 50, "reps": 10, "seconds": 0}, {"weightKg": 50, "reps": 10, "seconds": 0}, {"weightKg": 45, "reps": 12, "seconds": 0}]}

            <exercise>Barbell Curl, weight and reps</exercise>
            <line>curl 30 for 10 twice then 25 for 12</line>
            {"sets": [{"weightKg": 30, "reps": 10, "seconds": 0}, {"weightKg": 30, "reps": 10, "seconds": 0}, {"weightKg": 25, "reps": 12, "seconds": 0}]}
        """.trimIndent()
    }
}

/** Sets fitted to how the exercise is logged: a timed hold's lone number is seconds, not reps. */
private fun List<SetFact>.fitTo(exercise: Exercise): List<SetFact> =
    if (exercise.isTimed) map { if (it.seconds == null && it.reps != null) SetFact(it.weightKg, null, it.reps) else it } else this

/** The model's answer: structured output on phones with it (KSP schema), else read from JSON. */
@Generable("The sets someone did, in order")
@Serializable
data class ModelQuickLog(
    @Guide(description = "Each set in the order it was done", minItems = 1, maxItems = 20)
    val sets: List<ModelLoggedSet>,
) {
    companion object {
        val OUTPUT = ModelOutput(
            ModelQuickLog::class,
            serializer(),
            """{"sets": [{"weightKg": <kg or 0>, "reps": <reps or 0>, "seconds": <seconds or 0>}]}""",
        )
    }
}

@Generable("One set")
@Serializable
data class ModelLoggedSet(
    @Guide(description = "Weight in kg, 0 if none", minimum = 0.0, maximum = 500.0)
    val weightKg: Double,
    @Guide(description = "Reps, 0 for a timed hold", minimum = 0.0, maximum = 100.0)
    val reps: Int,
    @Guide(description = "Seconds held, 0 unless it's a timed hold", minimum = 0.0, maximum = 3600.0)
    val seconds: Int,
)
