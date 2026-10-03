package dev.saketanand.setwise.ui.summary

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
        completedSets = done.sumOf { (_, sets) -> sets.size },
        exerciseCount = done.size,
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
        },
        exercises = done.map { (exercise, sets) -> exercise.toSummaryUi(sets) },
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
 * [date]. An end at or before the start means it ran past midnight (next day); an end in the
 * future becomes [now]. Null if that still doesn't leave the end after the start.
 */
fun editedTimes(date: LocalDate, start: LocalTime, end: LocalTime, zone: ZoneId, now: Instant): Pair<Instant, Instant>? {
    val startAt = date.atTime(start).atZone(zone).toInstant()
    var endAt = date.atTime(end).atZone(zone).toInstant()
    if (!endAt.isAfter(startAt)) endAt = date.plusDays(1).atTime(end).atZone(zone).toInstant()
    if (endAt.isAfter(now)) endAt = now
    return if (endAt.isAfter(startAt)) startAt to endAt else null
}
