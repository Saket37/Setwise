package dev.saketanand.setwise.ui.workout

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.WorkoutSet
import dev.saketanand.setwise.util.toWeightLabel

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
            weight = set.weightKg?.toWeightLabel().orEmpty(),
            reps = set.amount(kind)?.toString().orEmpty(),
            weightHint = previous?.weightKg?.toWeightLabel() ?: weightAbove,
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
