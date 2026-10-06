package dev.saketanand.setwise.ui.summary

import dev.saketanand.setwise.domain.ai.CalorieEstimator
import dev.saketanand.setwise.domain.model.CalorieFormula
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutSet
import dev.saketanand.setwise.ui.workout.SetKind
import dev.saketanand.setwise.ui.workout.label
import dev.saketanand.setwise.ui.workout.setKind
import java.time.Duration as JavaDuration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.toKotlinDuration
import kotlinx.collections.immutable.toImmutableList

/** Domain → UI for the summary. Plain functions, unit-tested without Android. */

fun WorkoutSession.toSummaryUi(zone: ZoneId): WorkoutSummaryUiState {
    val start = startedAt.atZone(zone)
    val end = (endedAt ?: startedAt).atZone(zone)
    val done = exercises.map { it to it.sets.filter(WorkoutSet::isCompleted) }.filter { (_, sets) -> sets.isNotEmpty() }
    return WorkoutSummaryUiState(
        isLoading = false,
        name = name,
        date = start.toLocalDate(),
        startTime = start.toLocalTime(),
        endTime = end.toLocalTime(),
        duration = JavaDuration.between(startedAt, endedAt ?: startedAt).coerceAtLeast(JavaDuration.ZERO).toKotlinDuration(),
        volumeKg = done.sumOf { (_, sets) -> sets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) } },
        // Cardio is one entry, not sets.
        completedSets = done.filter { (exercise, _) -> exercise.exercise.type != ExerciseType.CARDIO }.sumOf { (_, sets) -> sets.size },
        exerciseCount = done.size,
        caloriesKcal = calories,
        caloriesSource = when (caloriesSource) {
            CalorieEstimator.MODEL_SOURCE -> CaloriesSourceUi.OnDevice
            CalorieFormula.SOURCE -> CaloriesSourceUi.Formula
            else -> null
        },
        records = exercises.mapNotNull { exercise ->
            exercise.personalRecord?.let { record ->
                RecordUi(
                    exerciseName = exercise.exercise.name,
                    kind = record.kind,
                    setKind = exercise.exercise.setKind,
                    achieved = record.set.toPreviousSet(),
                    previousBest = record.previousBest,
                )
            }
        }.toImmutableList(),
        exercises = done.map { (exercise, sets) -> exercise.toSummaryUi(sets) }.toImmutableList(),
        canSaveAsTemplate = templateId == null && done.isNotEmpty(),
    )
}

private fun SessionExercise.toSummaryUi(done: List<WorkoutSet>): SummaryExerciseUi {
    val kind = exercise.setKind
    val best = when (kind) {
        SetKind.WeightReps -> done.maxWithOrNull(compareBy<WorkoutSet>({ it.weightKg ?: 0.0 }, { it.reps ?: 0 }))
        SetKind.Bodyweight -> done.maxWithOrNull(compareBy<WorkoutSet>({ it.reps ?: 0 }, { it.weightKg ?: 0.0 }))
        SetKind.Duration -> done.maxByOrNull { it.durationSec ?: 0 }
        SetKind.Cardio -> null
    }
    return SummaryExerciseUi(
        id = id,
        exerciseId = exercise.id,
        name = exercise.name,
        setCount = done.size,
        best = best?.toPreviousSet()?.label(kind),
        cardio = done.firstOrNull()?.cardio,
    )
}

private fun WorkoutSet.toPreviousSet() = PreviousSet(weightKg = weightKg, reps = reps, durationSec = durationSec)

/**
 * New start and end for a finished workout from times picked on the clock, on the workout's
 * [date]. An end at or before the start means it ran past midnight, but only if that makes a
 * workout of at most [MAX_PAST_MIDNIGHT] that has already ended: "6:30 pm to 6:00 pm" is a typo,
 * not a 23½-hour workout (#132). A same-day end in the future becomes [now]. Null when the
 * times don't make a workout: the dialog says so.
 */
fun editedTimes(date: LocalDate, start: LocalTime, end: LocalTime, zone: ZoneId, now: Instant): Pair<Instant, Instant>? {
    val startAt = date.atTime(start).atZone(zone).toInstant()
    val sameDay = date.atTime(end).atZone(zone).toInstant()
    val endAt = if (sameDay.isAfter(startAt)) {
        sameDay.coerceAtMost(now)
    } else {
        date.plusDays(1).atTime(end).atZone(zone).toInstant()
            .takeIf { !it.isAfter(now) && JavaDuration.between(startAt, it) <= MAX_PAST_MIDNIGHT } ?: return null
    }
    return if (endAt.isAfter(startAt)) startAt to endAt else null
}

/** The longest workout taken to have run past midnight. */
private val MAX_PAST_MIDNIGHT: JavaDuration = JavaDuration.ofHours(6)
