package dev.saketanand.setwise.domain.model

/**
 * Personal records: a set that beats everything done before in that exercise.
 *
 * Rules (one record per exercise per workout, the strongest kind first):
 * - Weight × reps exercises: a heavier weight than ever before ([PrKind.Weight]); otherwise a
 *   better estimated 1-rep max ([PrKind.EstimatedOneRepMax], Epley), so 60 × 10 beats 60 × 8.
 * - Bodyweight exercises: more reps in one set ([PrKind.Reps]).
 * - Timed exercises (Plank): a longer hold ([PrKind.Duration]).
 * - Cardio: none yet (cardio logging comes later).
 *
 * Only completed sets count, and there must be an earlier best to beat: the first time an
 * exercise is done is not a record (otherwise every new exercise would "set a PR").
 */
object PersonalRecords {

    fun find(exercise: Exercise, sets: List<WorkoutSet>, before: PersonalBests): PersonalRecord? {
        val done = sets.filter { it.isCompleted }
        return when {
            exercise.type == ExerciseType.CARDIO -> null
            exercise.isTimed -> longestHold(done, before)
            exercise.type == ExerciseType.BODYWEIGHT -> mostReps(done, before)
            else -> heaviest(done, before) ?: bestEstimatedOneRepMax(done, before)
        }
    }

    private fun heaviest(sets: List<WorkoutSet>, before: PersonalBests): PersonalRecord? {
        val previous = before.heaviest ?: return null
        val best = sets.filter { it.weightKg != null && (it.reps ?: 0) > 0 }
            .maxWithOrNull(compareBy<WorkoutSet>({ it.weightKg }, { it.reps })) ?: return null
        val bestKg = best.weightKg ?: return null
        val previousKg = previous.weightKg ?: return null
        return if (bestKg > previousKg) PersonalRecord(PrKind.Weight, best, previous) else null
    }

    private fun bestEstimatedOneRepMax(sets: List<WorkoutSet>, before: PersonalBests): PersonalRecord? {
        val previous = before.bestEstimatedOneRepMax ?: return null
        val (best, bestMax) = sets.bestBy { estimatedOneRepMax(it.weightKg, it.reps) } ?: return null
        val previousMax = estimatedOneRepMax(previous.weightKg, previous.reps) ?: return null
        return if (bestMax - previousMax > EPSILON) PersonalRecord(PrKind.EstimatedOneRepMax, best, previous) else null
    }

    private fun mostReps(sets: List<WorkoutSet>, before: PersonalBests): PersonalRecord? {
        val previous = before.mostReps ?: return null
        val (best, reps) = sets.bestBy { it.reps } ?: return null
        val previousReps = previous.reps ?: return null
        return if (reps > previousReps) PersonalRecord(PrKind.Reps, best, previous) else null
    }

    private fun longestHold(sets: List<WorkoutSet>, before: PersonalBests): PersonalRecord? {
        val previous = before.longestHold ?: return null
        val (best, seconds) = sets.bestBy { it.durationSec } ?: return null
        val previousSeconds = previous.durationSec ?: return null
        return if (seconds > previousSeconds) PersonalRecord(PrKind.Duration, best, previous) else null
    }

    /** Epley: weight × (1 + reps / 30). Null without a weight or reps. */
    fun estimatedOneRepMax(weightKg: Double?, reps: Int?): Double? =
        if (weightKg == null || weightKg <= 0 || reps == null || reps <= 0) null else weightKg * (1 + reps / 30.0)

    private const val EPSILON = 1e-6
}

/** Best sets of one exercise in earlier workouts; each null if never done that way. */
data class PersonalBests(
    /** Heaviest weight (most reps at that weight). */
    val heaviest: PreviousSet?,
    val bestEstimatedOneRepMax: PreviousSet?,
    val mostReps: PreviousSet?,
    val longestHold: PreviousSet?,
) {
    companion object {
        val None = PersonalBests(null, null, null, null)

        /** From every completed set of the exercise in earlier workouts. */
        fun from(sets: List<PreviousSet>): PersonalBests = PersonalBests(
            heaviest = sets.filter { it.weightKg != null && (it.reps ?: 0) > 0 }
                .maxWithOrNull(compareBy<PreviousSet>({ it.weightKg }, { it.reps })),
            bestEstimatedOneRepMax = sets.bestBy { PersonalRecords.estimatedOneRepMax(it.weightKg, it.reps) }?.first,
            mostReps = sets.bestBy { it.reps }?.first,
            longestHold = sets.bestBy { it.durationSec }?.first,
        )
    }
}

enum class PrKind { Weight, EstimatedOneRepMax, Reps, Duration }

/** [set] beat [previousBest] in [kind]. */
data class PersonalRecord(
    val kind: PrKind,
    val set: WorkoutSet,
    val previousBest: PreviousSet,
)

/** The item with the highest non-null [value] (the first, on a tie), and that value; null if none has one. */
private inline fun <T, V : Comparable<V>> List<T>.bestBy(value: (T) -> V?): Pair<T, V>? =
    mapNotNull { item -> value(item)?.let { item to it } }.maxByOrNull { it.second }
