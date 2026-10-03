package dev.saketanand.setwise.ui.workout

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.WorkoutSet
import dev.saketanand.setwise.timer.NextUp
import dev.saketanand.setwise.timer.RestTimerState
import dev.saketanand.setwise.util.parseWeight
import dev.saketanand.setwise.util.toWeightInput
import dev.saketanand.setwise.util.toWeightLabel
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Domain → UI for the active workout. Plain functions, unit-tested without Android. */

val Exercise.setKind: SetKind
    get() = when {
        type == ExerciseType.CARDIO -> SetKind.Cardio
        isTimed -> SetKind.Duration
        type == ExerciseType.BODYWEIGHT -> SetKind.Bodyweight
        else -> SetKind.WeightReps
    }

fun SessionExercise.toUi(): WorkoutExerciseUi {
    val kind = exercise.setKind
    // Hints: the same set last time; for extra sets beyond last time's count, the row above
    // (what was typed there, or its hint), so set 5 suggests what set 4 was done with.
    // 🏆 as soon as a ticked-off set beats the earlier best (stored when the workout finishes).
    val recordSetId = personalRecord?.set?.id
    var weightAbove = ""
    var repsAbove = ""
    val rows = sets.mapIndexed { index, set ->
        val previous = previousSets.getOrNull(index)
        val row = SetUi(
            id = set.id,
            number = index + 1,
            previous = previous?.label(kind),
            // Field text and hints are parsed back, so they use the locale-independent format.
            weight = set.weightKg?.toWeightInput().orEmpty(),
            reps = set.amount(kind)?.toString().orEmpty(),
            weightHint = previous?.weightKg?.toWeightInput() ?: weightAbove,
            repsHint = previous?.amount(kind)?.toString() ?: repsAbove,
            isCompleted = set.isCompleted,
            isPr = set.id == recordSetId,
        )
        weightAbove = row.weight.ifEmpty { row.weightHint }
        repsAbove = row.reps.ifEmpty { row.repsHint }
        row
    }
    return WorkoutExerciseUi(
        id = id,
        exerciseId = exercise.id,
        name = exercise.name,
        kind = kind,
        restSec = exercise.defaultRestSec,
        sets = rows,
        lastTime = previousSets.mapNotNull { it.label(kind) }.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        cardio = sets.firstOrNull { it.isCompleted }?.cardio,
    )
}

fun RestTimerState.toUi(): RestUi = RestUi(
    endsAtElapsed = endsAtElapsed,
    totalMillis = totalMillis,
    nextSetNumber = (next as? NextUp.Set)?.number,
    nextExerciseName = (next as? NextUp.Exercise)?.name,
)

/**
 * What comes after ticking off [setId] in [exercise]: its next open set, else the next exercise
 * (after this one, then from the top) that still has sets to do, else nothing.
 */
fun nextUpAfter(setId: Long, exercise: WorkoutExerciseUi, exercises: List<WorkoutExerciseUi>): NextUp {
    exercise.sets.firstOrNull { !it.isCompleted && it.id != setId }?.let { return NextUp.Set(it.number) }
    val index = exercises.indexOfFirst { it.id == exercise.id }
    val others = exercises.drop(index + 1) + exercises.take(index.coerceAtLeast(0))
    others.firstOrNull { it.sets.any { set -> !set.isCompleted } }?.let { return NextUp.Exercise(it.name) }
    return NextUp.Nothing
}

/** "60 × 8", bodyweight "10" or "+5 × 10", timed "45s"; null if there's nothing to show. */
fun PreviousSet.label(kind: SetKind): String? = when (kind) {
    SetKind.WeightReps -> if (weightKg != null && reps != null) "${weightKg.toWeightLabel()} × $reps" else reps?.toString()
    SetKind.Bodyweight -> when {
        reps == null -> null
        weightKg != null && weightKg > 0 -> "+${weightKg.toWeightLabel()} × $reps"
        else -> reps.toString()
    }
    SetKind.Duration -> durationSec?.let { "${it}s" }
    SetKind.Cardio -> null
}

/** Reps, or seconds for timed exercises. */
private fun WorkoutSet.amount(kind: SetKind): Int? = if (kind == SetKind.Duration) durationSec else reps

private fun PreviousSet.amount(kind: SetKind): Int? = if (kind == SetKind.Duration) durationSec else reps


fun parseAmount(text: String): Int? = text.trim().toIntOrNull()

/**
 * Reps (or seconds) a set is ticked off with: what's typed, else the hint. Null when there's
 * nothing to log; 0 doesn't count, a set of 0 reps wasn't done.
 */
fun loggedAmount(typed: String, hint: String): Int? =
    parseAmount(typed)?.takeIf { it > 0 } ?: parseAmount(hint)?.takeIf { it > 0 }

/** Whether ✓ can be tapped: there's a reps (or seconds) value to log. Same rule as [loggedAmount]. */
fun canCompleteSet(typedReps: String, repsHint: String): Boolean = loggedAmount(typedReps, repsHint) != null

/**
 * The start instant for a time picked on the clock: that time on whichever day (the workout's
 * own day, the day before or after) is closest to the current start. That keeps a workout
 * logged for a past day on its day, and handles midnight (started 23:30, pick 00:10 → the next
 * day). A start in the future becomes now.
 */
fun pickedStartTime(time: LocalTime, currentStart: Instant, now: Instant, zone: ZoneId): Instant {
    val startDay = currentStart.atZone(zone).toLocalDate()
    val picked = (-1L..1L)
        .map { startDay.plusDays(it).atTime(time).atZone(zone).toInstant() }
        .minBy { java.time.Duration.between(it, currentStart).abs() }
    return if (picked.isAfter(now)) now else picked
}

/** How long a workout logged afterwards is assumed to last; editable on the summary. */
val BACKFILL_DURATION: java.time.Duration = java.time.Duration.ofHours(1)

/** Longer ago than any real session: a workout started this long ago is being logged afterwards. */
private val LOGGED_AFTERWARDS_AFTER: java.time.Duration = java.time.Duration.ofHours(6)

/**
 * Whether the workout is being logged afterwards (e.g. a past day from the day check-in) rather
 * than live. Not "started on an earlier day": a live session can run past midnight.
 */
fun isLoggedAfterwards(startedAt: Instant, now: Instant): Boolean =
    java.time.Duration.between(startedAt, now) > LOGGED_AFTERWARDS_AFTER

/**
 * When Finish ends a workout: now, except for one logged afterwards: then start +
 * [BACKFILL_DURATION], so it isn't days long.
 */
fun finishTime(startedAt: Instant, now: Instant): Instant =
    if (isLoggedAfterwards(startedAt, now)) startedAt.plus(BACKFILL_DURATION) else now
