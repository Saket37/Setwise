package dev.saketanand.setwise.ui.home

import dev.saketanand.setwise.domain.model.ActiveWorkout
import dev.saketanand.setwise.domain.model.FinishedWorkout
import dev.saketanand.setwise.domain.model.Template
import dev.saketanand.setwise.domain.model.WorkoutStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/*
 * Domain → Home UI models. Plain functions (no Android), so they're easy to unit test.
 */

/** How many exercise names to show on a template card before "+N". */
private const val PREVIEW_EXERCISES = 3

/** Rough working time per set, on top of the rest, for the "~65 min" estimate. */
private const val WORK_SECONDS_PER_SET = 40

/** Cardio exercises aren't done in sets; count each one as a 10-minute block. */
private const val CARDIO_SECONDS = 10 * 60

/** Calendar days between [instant] and [today] (0 = today, 1 = yesterday). */
fun daysAgo(instant: Instant, today: LocalDate, zone: ZoneId): Int =
    ChronoUnit.DAYS.between(instant.atZone(zone).toLocalDate(), today).toInt().coerceAtLeast(0)

/** "Bench Press (Barbell)" → "Bench Press": the equipment is noise on a one-line preview. */
fun shortExerciseName(name: String): String = name.substringBefore(" (").trim()

fun FinishedWorkout.toLastWorkoutUi(today: LocalDate, zone: ZoneId) = LastWorkoutUi(
    workoutId = id,
    name = name,
    daysAgo = daysAgo(endedAt, today, zone),
)

fun ActiveWorkout.toUi() = ActiveWorkoutUi(
    workoutId = id,
    name = name,
    startedAtMillis = startedAt.toEpochMilli(),
    completedSets = completedSets,
    cardioEntries = cardioEntries,
)

fun WorkoutStats.toUi() = WeekStatsUi(workouts = workouts, timeTrained = timeTrained, newPrs = prs)

fun Template.toUi(today: LocalDate, zone: ZoneId): TemplateUi {
    val estimatedSeconds = exercises.sumOf { exercise ->
        if (exercise.isCardio) CARDIO_SECONDS else exercise.targetSets * (exercise.restSec + WORK_SECONDS_PER_SET)
    }
    return TemplateUi(
        id = id,
        name = name,
        category = category,
        exercisePreview = exercises.take(PREVIEW_EXERCISES).map { shortExerciseName(it.name) },
        moreExerciseCount = (exercises.size - PREVIEW_EXERCISES).coerceAtLeast(0),
        exerciseCount = exercises.size,
        // Cardio isn't done in sets.
        setCount = exercises.filterNot { it.isCardio }.sumOf { it.targetSets },
        estimatedMinutes = if (estimatedSeconds > 0) (estimatedSeconds / 60.0).roundToInt() else null,
        lastUsedDaysAgo = lastUsedAt?.let { daysAgo(it, today, zone) },
    )
}
