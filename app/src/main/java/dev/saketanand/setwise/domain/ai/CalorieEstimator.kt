package dev.saketanand.setwise.domain.ai

import android.util.Log
import dev.saketanand.setwise.domain.model.CalorieEstimate
import dev.saketanand.setwise.domain.model.CalorieFormula
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.Intensity
import dev.saketanand.setwise.domain.model.WorkoutSession
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** An estimate and where it came from ([CalorieFormula.SOURCE] or [MODEL_SOURCE]). */
data class SourcedCalorieEstimate(val estimate: CalorieEstimate, val source: String)

/**
 * Calories for a finished workout: the on-device model's judgement when it's there, from the
 * workout's real data (sets, weights, the rest between sets, cardio), checked against
 * [CalorieFormula]; the formula itself otherwise. The model's answer is used only if it's
 * well-formed and within [PLAUSIBLE_RANGE] of the formula's number.
 */
class CalorieEstimator(private val model: OnDeviceModel) {

    suspend fun estimate(session: WorkoutSession, bodyWeightKg: Double?): SourcedCalorieEstimate? {
        val formula = CalorieFormula.estimate(session, bodyWeightKg) ?: return null
        val fallback = SourcedCalorieEstimate(formula, CalorieFormula.SOURCE)
        if (model.availability() != ModelAvailability.Ready) return fallback

        val answer = try {
            model.generate(prompt(session, bodyWeightKg!!, formula))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The model couldn't estimate workout ${session.id}", e)
            return fallback
        }
        val parsed = ModelJson.decode(answer, ModelAnswer.serializer()) ?: return fallback
        val intensity = Intensity.fromStored(parsed.intensity.lowercase(Locale.ROOT)) ?: return fallback
        val low = formula.kcal * PLAUSIBLE_RANGE.start
        val high = formula.kcal * PLAUSIBLE_RANGE.endInclusive
        if (parsed.kcal < low || parsed.kcal > high) return fallback
        return SourcedCalorieEstimate(CalorieEstimate(parsed.kcal, intensity), MODEL_SOURCE)
    }

    @Serializable
    private data class ModelAnswer(val kcal: Int, val intensity: String)

    companion object {
        const val MODEL_SOURCE = "on_device_model"

        /** The model may adjust the formula's number by this much (×), not replace it. */
        val PLAUSIBLE_RANGE = 0.6..1.5

        private const val TAG = "CalorieEstimator"

        /** Facts, one per line, kept short for a small model's context. */
        fun prompt(session: WorkoutSession, bodyWeightKg: Double, formula: CalorieEstimate): ModelRequest {
            val minutes = session.endedAt?.let { Duration.between(session.startedAt, it).toMinutes() } ?: 0
            val lines = buildList {
                add("Body weight: ${bodyWeightKg.format()} kg")
                add("Workout length: $minutes min")
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
            return ModelRequest(
                system = SYSTEM,
                prompt = lines.joinToString("\n"),
                temperature = 0.1f,
                maxOutputTokens = 64,
            )
        }

        private const val SYSTEM =
            "You estimate the calories a person burned in one gym workout. " +
                "Use the sets, weights, rest between sets and cardio given. Count gross calories (resting included). " +
                "Short rests and heavy compound lifts mean more; long rests mean less. " +
                "Stay close to the formula estimate unless the details clearly say otherwise. " +
                "Answer with JSON only: {\"kcal\": <whole number>, \"intensity\": \"light\" | \"moderate\" | \"vigorous\"}"

        private const val MAX_SETS_PER_EXERCISE = 10

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
