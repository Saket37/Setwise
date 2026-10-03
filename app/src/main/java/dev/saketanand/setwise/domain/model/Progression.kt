package dev.saketanand.setwise.domain.model

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** The rule behind a [NextSession]. */
enum class ProgressionRule {
    /** Every set at the same weight reached its reps two sessions in a row: more weight, same reps. */
    AddWeight,

    /** Bodyweight: every set reached its reps twice in a row: one more rep. */
    AddRep,

    /** Timed hold: every set reached its time twice in a row: a few seconds more. */
    AddTime,

    /** A [Plateau]: about 5% lighter with 2 more reps, to get it moving again. */
    Lighter,

    /** Not yet: the same again, every set to its reps (or time). */
    Repeat,
}

/**
 * What to aim for next session, decided from the logs by [Progression] (never by the model):
 * [weightKg] × [reps] (or [seconds]) for [sets] sets.
 */
data class NextSession(
    /** Null for bodyweight with no added weight. */
    val weightKg: Double?,
    /** Null for a timed hold. */
    val reps: Int?,
    /** Timed holds only. */
    val seconds: Int?,
    val sets: Int,
    val rule: ProgressionRule,
    /** What the rule went by: the last session's working weight and the reps (or time) its sets aimed for. */
    val done: SetFact,
    /** [ProgressionRule.AddWeight]: the step added. */
    val stepKg: Double? = null,
) {
    /** Changes something (worth a hint during the workout). */
    val isChange: Boolean get() = rule != ProgressionRule.Repeat
}

/**
 * A stall: the exercise's best ([measure]: estimated 1RM, reps or seconds) has stayed about
 * [best] since [since], over [sessions] sessions in [weeks] weeks.
 */
data class Plateau(
    val since: Instant,
    val weeks: Int,
    val sessions: Int,
    val best: Double,
    val measure: Measure,
)

/**
 * Progression rules: what to try next session and whether an exercise has stalled, from its
 * finished sessions (newest first). Double progression: reps first, then weight. Plain
 * functions, unit-tested.
 */
object Progression {

    /** Null for cardio, or if it hasn't been done. */
    fun next(exercise: Exercise, history: List<ExerciseSession>, now: Instant): NextSession? {
        val measure = measureOf(exercise) ?: return null
        val sessions = history.mapNotNull { working(it, measure) }
        val last = sessions.firstOrNull() ?: return null
        if (readyForMore(sessions)) {
            return when (measure) {
                Measure.Weight -> {
                    val step = stepKg(exercise, history)
                    NextSession(round(last.weightKg!! + step), last.target, null, last.sets, ProgressionRule.AddWeight, last.fact(measure), step)
                }
                Measure.Reps -> NextSession(last.weightKg, last.target + 1, null, last.sets, ProgressionRule.AddRep, last.fact(measure))
                Measure.Seconds -> NextSession(last.weightKg, null, last.target + TIME_STEP_SEC, last.sets, ProgressionRule.AddTime, last.fact(measure))
            }
        }
        if (measure == Measure.Weight && plateau(exercise, history, now) != null) {
            val step = stepKg(exercise, history)
            val weight = last.weightKg!!
            val lighter = floorTo(weight * DELOAD, step).let { if (it >= weight) weight - step else it }
            if (lighter > 0) return NextSession(round(lighter), last.target + DELOAD_EXTRA_REPS, null, last.sets, ProgressionRule.Lighter, last.fact(measure))
        }
        return when (measure) {
            Measure.Seconds -> NextSession(last.weightKg, null, last.target, last.sets, ProgressionRule.Repeat, last.fact(measure))
            else -> NextSession(last.weightKg, last.target, null, last.sets, ProgressionRule.Repeat, last.fact(measure))
        }
    }

    /**
     * A stall in the last [WINDOW_DAYS] days: the best hasn't moved more than [FLAT_TOLERANCE]
     * for [MIN_WEEKS]+ weeks over [MIN_SESSIONS]+ sessions, and it's still being trained. Not
     * when the last two sessions say it's ready for more (that's progress about to happen).
     */
    fun plateau(exercise: Exercise, history: List<ExerciseSession>, now: Instant): Plateau? {
        val measure = measureOf(exercise) ?: return null
        if (readyForMore(history.mapNotNull { working(it, measure) })) return null
        val values = history
            .filter { Duration.between(it.startedAt, now).toDays() <= WINDOW_DAYS }
            .mapNotNull { session -> best(session, measure)?.let { session.startedAt to it } }
        if (values.size < MIN_SESSIONS) return null
        val lastAt = values.first().first
        if (Duration.between(lastAt, now).toDays() > STILL_TRAINING_DAYS) return null
        val peak = values.maxOf { it.second }
        // Flat since the first session about as good as the best.
        val since = values.filter { it.second >= peak * (1 - FLAT_TOLERANCE) }.minOf { it.first }
        val weeks = Duration.between(since, lastAt).toDays() / 7
        val sessions = values.count { it.first >= since }
        if (weeks < MIN_WEEKS || sessions < MIN_SESSIONS) return null
        return Plateau(since, weeks.toInt(), sessions, peak, measure)
    }

    /**
     * The weight step for this exercise: the smallest jump between the working weights
     * they've used (their plates and dumbbells), else a usual one for the equipment.
     */
    fun stepKg(exercise: Exercise, history: List<ExerciseSession>): Double {
        val weights = history.mapNotNull { working(it, Measure.Weight)?.weightKg }.distinct().sorted()
        val smallest = weights.zipWithNext { a, b -> b - a }.filter { it > EPSILON }.minOrNull()
        if (smallest != null) return (Math.round(smallest * 4) / 4.0).coerceIn(MIN_STEP_KG, MAX_STEP_KG)
        return when (exercise.equipment) {
            "Barbell" -> if (exercise.muscleGroup in LOWER_BODY) 5.0 else 2.5
            "Dumbbell" -> 2.0
            "Kettlebell" -> 4.0
            else -> 2.5
        }
    }

    fun measureOf(exercise: Exercise): Measure? = when {
        exercise.type == ExerciseType.CARDIO -> null
        exercise.isTimed -> Measure.Seconds
        exercise.type == ExerciseType.BODYWEIGHT -> Measure.Reps
        else -> Measure.Weight
    }

    /** The last two sessions: same weight, every set to its reps (or time), not fewer than before. */
    private fun readyForMore(sessions: List<Working>): Boolean {
        val last = sessions.getOrNull(0) ?: return false
        val before = sessions.getOrNull(1) ?: return false
        return abs((last.weightKg ?: 0.0) - (before.weightKg ?: 0.0)) < EPSILON &&
            last.clean && before.clean && last.target >= before.target
    }

    /**
     * A session's working sets: those at its top weight (warm-ups and back-off sets left out).
     * [target]: the reps (or seconds) most of them aimed for, the higher on a tie; [clean]:
     * every one reached it.
     */
    private data class Working(val weightKg: Double?, val target: Int, val sets: Int, val clean: Boolean) {
        fun fact(measure: Measure) =
            if (measure == Measure.Seconds) SetFact(weightKg, null, target) else SetFact(weightKg, target, null)
    }

    private fun working(session: ExerciseSession, measure: Measure): Working? {
        val sets = session.sets.filter { set ->
            (amount(set, measure) ?: 0) > 0 && (measure != Measure.Weight || (set.weightKg ?: 0.0) > 0)
        }
        if (sets.isEmpty()) return null
        val top = sets.maxOf { it.weightKg ?: 0.0 }
        val amounts = sets.filter { abs((it.weightKg ?: 0.0) - top) < EPSILON }.map { amount(it, measure)!! }
        val target = amounts.groupingBy { it }.eachCount().entries
            .maxWith(compareBy<Map.Entry<Int, Int>>({ it.value }, { it.key })).key
        return Working(top.takeIf { it > 0 }, target, amounts.size, amounts.all { it >= target })
    }

    private fun amount(set: LoggedSet, measure: Measure): Int? =
        if (measure == Measure.Seconds) set.durationSec else set.reps

    /** A session's best: estimated 1RM, most reps, or longest hold. */
    private fun best(session: ExerciseSession, measure: Measure): Double? = when (measure) {
        Measure.Weight -> session.sets.mapNotNull { PersonalRecords.estimatedOneRepMax(it.weightKg, it.reps) }.maxOrNull()
        Measure.Reps -> session.sets.mapNotNull { it.reps }.maxOrNull()?.toDouble()
        Measure.Seconds -> session.sets.mapNotNull { it.durationSec }.maxOrNull()?.toDouble()
    }

    private fun floorTo(value: Double, step: Double) = floor(value / step + EPSILON) * step

    /** No float dust: 57.49999 → 57.5. */
    private fun round(kg: Double) = (kg * 100).roundToLong() / 100.0

    private const val TIME_STEP_SEC = 5
    private const val DELOAD = 0.95
    private const val DELOAD_EXTRA_REPS = 2
    private const val MIN_STEP_KG = 0.5
    private const val MAX_STEP_KG = 5.0
    private val LOWER_BODY = setOf("Quads", "Hamstrings", "Glutes")

    private const val WINDOW_DAYS = 84L
    private const val STILL_TRAINING_DAYS = 21L
    private const val MIN_SESSIONS = 4
    private const val MIN_WEEKS = 3L
    private const val FLAT_TOLERANCE = 0.025
    private const val EPSILON = 1e-6
}
