package dev.saketanand.setwise.ui.exercises

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseSession
import dev.saketanand.setwise.domain.model.LoggedSet
import dev.saketanand.setwise.domain.model.PersonalRecords
import dev.saketanand.setwise.ui.workout.SetKind
import dev.saketanand.setwise.ui.workout.setKind
import dev.saketanand.setwise.util.mondayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import dev.saketanand.setwise.domain.model.Plateau
import dev.saketanand.setwise.domain.model.Progression
import java.time.Instant
import kotlin.math.roundToInt

/** Domain → UI for Exercise detail. Plain functions, unit-tested without Android. */

/** Weeks in the progress chart, this week included. */
const val PROGRESS_WEEKS = 8

/**
 * @param sessions newest first.
 * @param now for the plateau rule ([Progression.plateau]: still being trained?).
 */
fun exerciseDetailUi(exercise: Exercise, sessions: List<ExerciseSession>, today: LocalDate, zone: ZoneId, now: Instant): ExerciseDetailUiState {
    val sessionsUi = sessions.map { ExerciseSessionUi(it.workoutId, it.startedAt.atZone(zone).toLocalDate(), it.sets) }
    val progress = if (sessionsUi.isEmpty()) null else progress(exercise.setKind, sessionsUi, today)
    return ExerciseDetailUiState(
        isLoading = false,
        name = exercise.name,
        muscleGroup = exercise.muscleGroup,
        equipment = exercise.equipment,
        kind = exercise.setKind,
        progress = progress,
        plateau = progress?.let { Progression.plateau(exercise, sessions, now)?.toUi(it.metric, zone) },
        nextSession = Progression.next(exercise, sessions, now),
        sessions = sessionsUi,
    )
}

private fun Plateau.toUi(metric: ProgressMetric, zone: ZoneId) = PlateauUi(
    weeks = weeks,
    sessions = sessions,
    since = since.atZone(zone).toLocalDate(),
    best = best.roundToInt(),
    metric = metric,
)

private fun progress(kind: SetKind, sessions: List<ExerciseSessionUi>, today: LocalDate): ProgressUi {
    val metric = when (kind) {
        SetKind.WeightReps -> ProgressMetric.EstimatedOneRepMax
        SetKind.Bodyweight -> ProgressMetric.Reps
        SetKind.Duration -> ProgressMetric.Duration
        SetKind.Cardio ->
            if (sessions.any { session -> session.sets.any { (it.distanceKm ?: 0.0) > 0 } }) ProgressMetric.Distance else ProgressMetric.Minutes
    }
    val firstWeek = today.mondayOfWeek().minusWeeks(PROGRESS_WEEKS - 1L)
    val byWeek = sessions.groupBy { it.date.mondayOfWeek() }
    val weeks = (0L until PROGRESS_WEEKS).map { offset ->
        val sets = byWeek[firstWeek.plusWeeks(offset)].orEmpty().flatMap { it.sets }
        weekValue(metric, sets)
    }
    return ProgressUi(metric, weeks, firstWeek, latest = weeks.lastOrNull { it != null })
}

/** A week's bar: its best set (strength), or its total (cardio); null if nothing counts. */
private fun weekValue(metric: ProgressMetric, sets: List<LoggedSet>): Double? = when (metric) {
    ProgressMetric.EstimatedOneRepMax -> sets.mapNotNull { PersonalRecords.estimatedOneRepMax(it.weightKg, it.reps) }.maxOrNull()
    ProgressMetric.Reps -> sets.mapNotNull { it.reps }.maxOrNull()?.toDouble()
    ProgressMetric.Duration -> sets.mapNotNull { it.durationSec }.maxOrNull()?.toDouble()
    ProgressMetric.Distance -> sets.mapNotNull { it.distanceKm }.takeIf { it.isNotEmpty() }?.sum()
    ProgressMetric.Minutes -> sets.mapNotNull { it.durationSec }.takeIf { it.isNotEmpty() }?.sum()?.div(60.0)
}
