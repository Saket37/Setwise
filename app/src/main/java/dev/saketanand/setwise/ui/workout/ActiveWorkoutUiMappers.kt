package dev.saketanand.setwise.ui.workout

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.WorkoutSet
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
            isPr = set.isPr,
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
        sets = rows,
        lastTime = previousSets.mapNotNull { it.label(kind) }.takeIf { it.isNotEmpty() }?.joinToString(" · "),
    )
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

/** "62.5" and "62,5" both mean 62.5; empty or "." means no value. */
fun parseWeight(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

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
 * The start instant for a time picked on the clock. Today at that time, except:
 * - the workout began yesterday (it's running past midnight) and the time is still ahead
 *   today → yesterday at that time;
 * - otherwise a time still ahead → now (a workout can't start in the future).
 */
fun pickedStartTime(time: LocalTime, currentStart: Instant, now: Instant, zone: ZoneId): Instant {
    val today = now.atZone(zone).toLocalDate()
    val picked = today.atTime(time).atZone(zone).toInstant()
    if (!picked.isAfter(now)) return picked
    val startedBeforeToday = currentStart.atZone(zone).toLocalDate().isBefore(today)
    return if (startedBeforeToday) today.minusDays(1).atTime(time).atZone(zone).toInstant() else now
}
