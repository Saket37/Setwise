package dev.saketanand.setwise.domain.model

import java.time.Duration
import kotlin.math.roundToInt

/**
 * What a finished workout's insight is built from, worked out in code: the on-device model only
 * puts these into words (it's unreliable at arithmetic and rules), and the fallback template uses
 * the same facts. Built by [WorkoutFacts.of].
 */
data class WorkoutFacts(
    val workoutName: String,
    val minutes: Long,
    val intensity: Intensity?,
    /** Median gap between ticked-off lifting sets; null with fewer than two. */
    val medianRestSec: Long?,
    /** Exercises done before too, most notable first (improvements, then the rest). */
    val changes: List<ExerciseChange>,
    val records: List<RecordFact>,
    val volumeKg: Double,
    /** The last earlier workout with the same name; null if there's none (or it had no volume). */
    val previous: PreviousWorkout?,
) {
    /** Anything for the insight card: a comparison with last time, or how the session went. */
    val hasSomethingToSay: Boolean
        get() = changes.isNotEmpty() || previous != null || intensity != null

    /** Volume against [previous], in whole percent (e.g. 7 or -12); null without one. */
    val volumeChangePercent: Int?
        get() = previous?.let { ((volumeKg - it.volumeKg) / it.volumeKg * 100).roundToInt() }

    companion object {

        /**
         * @param history finished workouts (any order); the last earlier one named like this
         *   workout is its [previous].
         */
        fun of(session: WorkoutSession, history: List<WorkoutHistoryItem>): WorkoutFacts {
            val minutes = session.endedAt?.let { Duration.between(session.startedAt, it).toMinutes() } ?: 0
            val lifts = session.exercises.filter { it.exercise.type != ExerciseType.CARDIO }
            val sets = lifts.sumOf { exercise -> exercise.sets.count { it.isCompleted } }
            val liftingHours = (minutes - cardioMinutes(session)) / 60.0
            val volume = lifts.sumOf { exercise ->
                exercise.sets.filter { it.isCompleted }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
            }
            val previous = history
                .filter { it.id != session.id && it.startedAt < session.startedAt && it.name.equals(session.name, ignoreCase = true) }
                .maxByOrNull { it.startedAt }
                ?.takeIf { it.volumeKg > 0 && volume > 0 }
                ?.let { PreviousWorkout(it.name, it.volumeKg) }
            return WorkoutFacts(
                workoutName = session.name,
                minutes = minutes,
                intensity = session.intensity ?: if (sets > 0) CalorieFormula.strengthIntensity(sets, liftingHours) else null,
                medianRestSec = medianRestSec(session),
                changes = lifts.mapNotNull { it.change() }.sortedBy { it.rank },
                records = session.exercises.mapNotNull { exercise ->
                    exercise.personalRecord?.let { RecordFact(exercise.exercise.name, it.kind) }
                },
                volumeKg = volume,
                previous = previous,
            )
        }

        /**
         * Median gap between ticked-off lifting sets. Only real rests count: gaps under 15 s are
         * sets ticked off together (e.g. logged afterwards), over 15 min are breaks.
         */
        fun medianRestSec(session: WorkoutSession): Long? {
            val times = session.exercises
                .filter { it.exercise.type != ExerciseType.CARDIO }
                .flatMap { exercise -> exercise.sets.mapNotNull { if (it.isCompleted) it.completedAt else null } }
                .sorted()
            val rests = times.zipWithNext { a, b -> Duration.between(a, b).seconds }.filter { it in MIN_REST_SEC..MAX_REST_SEC }
            if (rests.isEmpty()) return null
            return rests.sorted()[rests.size / 2]
        }

        /** Number of rests behind [medianRestSec]. */
        fun restCount(session: WorkoutSession): Int {
            val times = session.exercises
                .filter { it.exercise.type != ExerciseType.CARDIO }
                .flatMap { exercise -> exercise.sets.mapNotNull { if (it.isCompleted) it.completedAt else null } }
                .sorted()
            return times.zipWithNext { a, b -> Duration.between(a, b).seconds }.count { it in MIN_REST_SEC..MAX_REST_SEC }
        }

        private fun cardioMinutes(session: WorkoutSession): Double = session.exercises
            .filter { it.exercise.type == ExerciseType.CARDIO }
            .flatMap { exercise -> exercise.sets.filter { it.isCompleted }.mapNotNull { it.cardio?.durationSec } }
            .sum() / 60.0

        /** This workout's best set against last time's, by how the exercise is measured. */
        private fun SessionExercise.change(): ExerciseChange? {
            val done = sets.filter { it.isCompleted }
            if (done.isEmpty() || previousSets.isEmpty()) return null
            val measure = when {
                exercise.isTimed -> Measure.Seconds
                exercise.type == ExerciseType.BODYWEIGHT -> Measure.Reps
                else -> Measure.Weight
            }
            val now = done.map { SetFact(it.weightKg, it.reps, it.durationSec) }.best(measure) ?: return null
            val before = previousSets.map { SetFact(it.weightKg, it.reps, it.durationSec) }.best(measure) ?: return null
            return ExerciseChange(exercise.name, measure, now, before)
        }

        private fun List<SetFact>.best(measure: Measure): SetFact? = when (measure) {
            Measure.Weight -> filter { it.reps != null && it.reps > 0 }.maxWithOrNull(compareBy({ it.weightKg ?: 0.0 }, { it.reps ?: 0 }))
            Measure.Reps -> filter { it.reps != null && it.reps > 0 }.maxWithOrNull(compareBy({ it.reps ?: 0 }, { it.weightKg ?: 0.0 }))
            Measure.Seconds -> filter { it.seconds != null && it.seconds > 0 }.maxByOrNull { it.seconds ?: 0 }
        }

        private const val MIN_REST_SEC = 15L
        private const val MAX_REST_SEC = 15 * 60L
    }
}

/** How an exercise's best set is judged. */
enum class Measure { Weight, Reps, Seconds }

data class SetFact(val weightKg: Double?, val reps: Int?, val seconds: Int?)

/** This workout's best set of an exercise against last time's. */
data class ExerciseChange(
    val exercise: String,
    val measure: Measure,
    val now: SetFact,
    val before: SetFact,
) {
    /** Heavier (Weight), more reps at that weight, or longer: positive; less: negative; same: 0. */
    val weightDeltaKg: Double get() = (now.weightKg ?: 0.0) - (before.weightKg ?: 0.0)
    val repsDelta: Int get() = (now.reps ?: 0) - (before.reps ?: 0)
    val secondsDelta: Int get() = (now.seconds ?: 0) - (before.seconds ?: 0)

    val isImprovement: Boolean
        get() = when (measure) {
            Measure.Weight -> weightDeltaKg > 0 || (weightDeltaKg == 0.0 && repsDelta > 0)
            Measure.Reps -> repsDelta > 0
            Measure.Seconds -> secondsDelta > 0
        }

    val isSame: Boolean
        get() = when (measure) {
            Measure.Weight -> weightDeltaKg == 0.0 && repsDelta == 0
            Measure.Reps -> repsDelta == 0
            Measure.Seconds -> secondsDelta == 0
        }

    /** Improvements first, then the same, then drops. */
    internal val rank: Int get() = if (isImprovement) 0 else if (isSame) 1 else 2
}

data class RecordFact(val exercise: String, val kind: PrKind)

data class PreviousWorkout(val name: String, val volumeKg: Double)
