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
        return if (best.weightKg!! > previous.weightKg!!) PersonalRecord(PrKind.Weight, best, previous) else null
    }

    private fun bestEstimatedOneRepMax(sets: List<WorkoutSet>, before: PersonalBests): PersonalRecord? {
        val previous = before.bestEstimatedOneRepMax ?: return null
        val best = sets.filter { estimatedOneRepMax(it.weightKg, it.reps) != null }
            .maxByOrNull { estimatedOneRepMax(it.weightKg, it.reps)!! } ?: return null
        val gained = estimatedOneRepMax(best.weightKg, best.reps)!! - estimatedOneRepMax(previous.weightKg, previous.reps)!!
        return if (gained > EPSILON) PersonalRecord(PrKind.EstimatedOneRepMax, best, previous) else null
    }

    private fun mostReps(sets: List<WorkoutSet>, before: PersonalBests): PersonalRecord? {
        val previous = before.mostReps ?: return null
        val best = sets.filter { it.reps != null }.maxByOrNull { it.reps!! } ?: return null
        return if (best.reps!! > previous.reps!!) PersonalRecord(PrKind.Reps, best, previous) else null
    }

    private fun longestHold(sets: List<WorkoutSet>, before: PersonalBests): PersonalRecord? {
        val previous = before.longestHold ?: return null
        val best = sets.filter { it.durationSec != null }.maxByOrNull { it.durationSec!! } ?: return null
        return if (best.durationSec!! > previous.durationSec!!) PersonalRecord(PrKind.Duration, best, previous) else null
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
            bestEstimatedOneRepMax = sets.filter { PersonalRecords.estimatedOneRepMax(it.weightKg, it.reps) != null }
                .maxByOrNull { PersonalRecords.estimatedOneRepMax(it.weightKg, it.reps)!! },
            mostReps = sets.filter { it.reps != null }.maxByOrNull { it.reps!! },
            longestHold = sets.filter { it.durationSec != null }.maxByOrNull { it.durationSec!! },
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
