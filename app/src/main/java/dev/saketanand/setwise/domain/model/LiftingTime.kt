package dev.saketanand.setwise.domain.model

import java.time.Duration

/** A set's work, as the goal planner counts it. */
private const val WORK_SECONDS_PER_SET = 40

/** A set with the shortest real rest: a clock under this many seconds per set wasn't timing them. */
private const val SHORTEST_SECONDS_PER_SET = 70

private const val SECONDS_PER_HOUR = 3600.0

/**
 * Hours spent lifting: the workout's time less its cardio entries'. When that's shorter than its
 * sets could have taken (they were logged afterwards, or it was saved straight after entering
 * them), the time they typically take instead: each set's work plus its exercise's rest (#196).
 * 0 while the workout is running.
 */
fun WorkoutSession.liftingHours(): Double {
    val end = endedAt ?: return 0.0
    val cardioSeconds = cardioEntries().sumOf { (_, values) -> values.durationSec ?: 0 }
    val clockSeconds = (Duration.between(startedAt, end).seconds - cardioSeconds).coerceAtLeast(0)
    val sets = liftingSets()
    if (clockSeconds >= sets.sumOf { (_, count) -> count } * SHORTEST_SECONDS_PER_SET) return clockSeconds / SECONDS_PER_HOUR
    return sets.sumOf { (exercise, count) -> count * (WORK_SECONDS_PER_SET + exercise.defaultRestSec) } / SECONDS_PER_HOUR
}

/** Completed cardio entries, each with its exercise. */
internal fun WorkoutSession.cardioEntries(): List<Pair<Exercise, CardioValues>> = exercises
    .filter { it.exercise.type == ExerciseType.CARDIO }
    .flatMap { exercise -> exercise.sets.filter { it.isCompleted }.mapNotNull { set -> set.cardio?.let { exercise.exercise to it } } }

/** Each lifting exercise with its completed sets. */
internal fun WorkoutSession.liftingSets(): List<Pair<Exercise, Int>> = exercises
    .filter { it.exercise.type != ExerciseType.CARDIO }
    .map { exercise -> exercise.exercise to exercise.sets.count { it.isCompleted } }
