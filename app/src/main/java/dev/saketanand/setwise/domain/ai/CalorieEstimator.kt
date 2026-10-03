package dev.saketanand.setwise.domain.ai

import android.util.Log
import dev.saketanand.setwise.domain.model.CalorieEstimate
import dev.saketanand.setwise.domain.model.CalorieFormula
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.WorkoutSession
import java.time.Duration
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

/**
 * An estimate and where it came from ([CalorieFormula.SOURCE] or [MODEL_SOURCE]).
 * [modelFailed]: the model was asked and threw (busy, over quota…): the caller may stop asking.
 */
data class SourcedCalorieEstimate(val estimate: CalorieEstimate, val source: String, val modelFailed: Boolean = false)

/**
 * Calories for a finished workout: the on-device model's judgement when it's there, from the
 * workout's real data (sets, weights, the rest between sets, cardio), checked against
 * [CalorieFormula]; the formula itself otherwise. The model's number is used only if it's
 * well-formed and within [PLAUSIBLE_RANGE] of the formula's. The intensity always comes from
 * the formula's rule (sets per hour): the model is asked one focused thing, the calories.
 */
class CalorieEstimator(private val model: OnDeviceModel) {

    /** @param useModel false: the formula only (e.g. an old workout, or the model just failed). */
    suspend fun estimate(session: WorkoutSession, bodyWeightKg: Double?, useModel: Boolean = true): SourcedCalorieEstimate? {
        val formula = CalorieFormula.estimate(session, bodyWeightKg) ?: return null
        val fallback = SourcedCalorieEstimate(formula, CalorieFormula.SOURCE)
        if (!useModel || model.availability() != ModelAvailability.Ready) return fallback

        val startedAt = System.nanoTime()
        fun outcome(text: String) = Log.d(
            TAG,
            "Workout ${session.id}: formula ${formula.kcal} kcal ${formula.intensity.storedName}; $text " +
                "(${(System.nanoTime() - startedAt) / 1_000_000} ms)",
        )

        val parsed = try {
            model.generate(prompt(session, bodyWeightKg!!, formula), ModelCalorieAnswer.OUTPUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Busy, over quota, blocked in the background…: the formula's number, so there's always one.
            Log.w(TAG, "The model couldn't estimate workout ${session.id}", e)
            outcome("model failed (${e.javaClass.simpleName}: ${e.message}) → formula")
            return fallback.copy(modelFailed = true)
        }
        if (parsed == null) {
            outcome("model gave no usable answer → formula")
            return fallback
        }
        val low = formula.kcal * PLAUSIBLE_RANGE.start
        val high = formula.kcal * PLAUSIBLE_RANGE.endInclusive
        if (parsed.kcal < low || parsed.kcal > high) {
            outcome("model said ${parsed.kcal} kcal, outside ${low.toInt()}–${high.toInt()} → formula")
            return fallback
        }
        outcome("model said ${parsed.kcal} kcal → used")
        // Intensity is a rule (sets per hour), so it stays the formula's.
        return SourcedCalorieEstimate(CalorieEstimate(parsed.kcal, formula.intensity), MODEL_SOURCE)
    }

    companion object {
        const val MODEL_SOURCE = "on_device_model"

        /** The model may adjust the formula's number by this much (×), not replace it. */
        val PLAUSIBLE_RANGE = 0.6..1.5

        private const val TAG = "CalorieEstimator"

        /**
         * Shaped for Gemini Nano (ML Kit prompt guide): a short system instruction, two worked
         * examples, then this workout's log in `<workout>` tags, one fact per line. The fixed
         * part stays under 200 words, so it needs no prefix caching. The answer's shape comes
         * from [ModelCalorieAnswer] (structured output), not from the prompt.
         */
        fun prompt(session: WorkoutSession, bodyWeightKg: Double, formula: CalorieEstimate): ModelRequest =
            ModelRequest(
                system = SYSTEM,
                prompt = "## Examples\n$EXAMPLES\n\n## Workout\n<workout>\n${workoutLog(session, bodyWeightKg, formula)}\n</workout>",
                temperature = 0.2f,
                maxOutputTokens = 80,
            )

        /** Facts, one per line, kept short for a small model's context. */
        fun workoutLog(session: WorkoutSession, bodyWeightKg: Double, formula: CalorieEstimate): String {
            val minutes = session.endedAt?.let { Duration.between(session.startedAt, it).toMinutes() } ?: 0
            val lines = buildList {
                add("Body weight: ${bodyWeightKg.format()} kg")
                add("Length: $minutes min")
                strengthDensity(session, minutes)?.let { add(it) }
                session.exercises.forEach { exercise ->
                    val done = exercise.sets.filter { it.isCompleted }
                    if (done.isEmpty()) return@forEach
                    if (exercise.exercise.type == ExerciseType.CARDIO) {
                        done.mapNotNull { it.cardio }.forEach { cardio ->
                            val parts = listOfNotNull(
                                cardio.durationSec?.let { "${it / 60} min" },
                                cardio.distanceKm?.let { "${it.format()} km" },
                                cardio.inclinePct?.let { "incline ${it.format()}%" },
                                listOfNotNull(cardio.speedMinKmh, cardio.speedMaxKmh).takeIf { it.isNotEmpty() }
                                    ?.joinToString("-") { it.format() }?.let { "$it km/h" },
                                cardio.level?.let { "level $it" },
                            )
                            add("${exercise.exercise.name} (cardio): ${parts.joinToString(", ")}")
                        }
                    } else {
                        val sets = done.take(MAX_SETS_PER_EXERCISE).joinToString(", ") { set ->
                            when {
                                set.durationSec != null && set.reps == null -> "${set.durationSec}s"
                                set.weightKg != null && set.weightKg > 0 -> "${set.weightKg.format()}kg x${set.reps ?: 0}"
                                else -> "x${set.reps ?: 0}"
                            }
                        }
                        add("${exercise.exercise.name}: $sets")
                    }
                }
                restSummary(session)?.let { add(it) }
                add("Formula estimate: ${formula.kcal} kcal, ${formula.intensity.storedName}")
            }
            return lines.joinToString("\n")
        }

        private const val SYSTEM =
            "You estimate the gross calories burned in one gym workout from its log. " +
                "Short rests, many sets and heavy compound lifts raise the estimate; long rests and few sets lower it. " +
                "Stay within 30% of the formula estimate unless the log clearly justifies more."

        /** In-context examples, worked with the same formula (4 sets / 45 min = light; 18 / 40 = vigorous). */
        private val EXAMPLES = """
            <workout>
            Body weight: 80 kg
            Length: 45 min
            Completed sets: 4 (5 per hour)
            Back Squat: 100kg x5, 100kg x5, 100kg x5, 100kg x5
            Rest between sets: median 3:30 (3 rests)
            Formula estimate: 210 kcal, light
            </workout>
            {"kcal": 200}

            <workout>
            Body weight: 65 kg
            Length: 40 min
            Completed sets: 18 (27 per hour)
            Walking Lunge: 20kg x12, 20kg x12, 20kg x12, 20kg x12, 20kg x12, 20kg x12
            Kettlebell Swing: 16kg x20, 16kg x20, 16kg x20, 16kg x20, 16kg x20, 16kg x20
            Burpee: x15, x15, x15, x15, x15, x15
            Rest between sets: median 0:40 (17 rests)
            Formula estimate: 260 kcal, vigorous
            </workout>
            {"kcal": 290}
        """.trimIndent()

        private const val MAX_SETS_PER_EXERCISE = 10

        /**
         * "Completed sets: 18 (18 per hour)": lifting sets per hour of lifting time (the workout
         * minus its cardio), the density the formula's intensity comes from. Null without sets.
         */
        private fun strengthDensity(session: WorkoutSession, minutes: Long): String? {
            val sets = session.exercises
                .filter { it.exercise.type != ExerciseType.CARDIO }
                .sumOf { exercise -> exercise.sets.count { it.isCompleted } }
            if (sets == 0) return null
            val cardioMinutes = session.exercises
                .filter { it.exercise.type == ExerciseType.CARDIO }
                .flatMap { exercise -> exercise.sets.filter { it.isCompleted }.mapNotNull { it.cardio?.durationSec } }
                .sum() / 60.0
            val liftingHours = (minutes - cardioMinutes) / 60.0
            if (liftingHours <= 0) return "Completed sets: $sets"
            return "Completed sets: $sets (${(sets / liftingHours).roundToInt()} per hour)"
        }

        /** "Rest between sets: median 1:30 (8 rests)" from when sets were ticked off. */
        private fun restSummary(session: WorkoutSession): String? {
            val times = session.exercises
                .filter { it.exercise.type != ExerciseType.CARDIO }
                .flatMap { exercise -> exercise.sets.mapNotNull { if (it.isCompleted) it.completedAt else null } }
                .sorted()
            val rests = times.zipWithNext { a, b -> Duration.between(a, b).seconds }.filter { it in 1..MAX_REST_SEC }
            if (rests.isEmpty()) return null
            val median = rests.sorted()[rests.size / 2]
            return "Rest between sets: median %d:%02d (%d rests)".format(Locale.ROOT, median / 60, median % 60, rests.size)
        }

        /** Gaps longer than this are breaks (or a past day logged afterwards), not rests. */
        private const val MAX_REST_SEC = 15 * 60L

        private fun Double.format(): String =
            if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.ROOT, this)
    }
}
