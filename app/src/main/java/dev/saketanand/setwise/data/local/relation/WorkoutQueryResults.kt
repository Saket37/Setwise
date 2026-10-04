package dev.saketanand.setwise.data.local.relation

/*
 * Shapes returned by WorkoutDao / TemplateDao queries that aren't whole entities.
 * Room maps each column alias in the SELECT to the property with the same name.
 */

/** A workout that was started but not finished. */
data class ActiveWorkoutRow(
    val id: Long,
    val name: String,
    val startedAt: Long,
    val completedSets: Int,
)

/** Totals for finished workouts in a time window (e.g. this week). */
data class WorkoutStatsRow(
    val workouts: Int,
    val totalMillis: Long,
    val prs: Int,
)

/** When a template was last used for a finished workout. */
data class TemplateLastUsedRow(
    val templateId: Long,
    val lastStartedAt: Long,
)

/**
 * A completed set from the last finished session of the exercise behind [workoutExerciseId]
 * (a row of the current workout): the "Previous" column.
 */
data class PreviousSetRow(
    val workoutExerciseId: Long,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
)

/** A completed set of [exerciseId] from an earlier finished workout (for personal bests). */
data class ExerciseHistorySetRow(
    val exerciseId: Long,
    val workoutId: Long,
    val startedAt: Long,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
)

/** A finished workout with its totals, for the History list. */
data class WorkoutHistoryRow(
    val id: Long,
    val name: String,
    val startedAt: Long,
    val endedAt: Long,
    val calories: Int?,
    val completedSets: Int,
    val volumeKg: Double,
    val distanceKm: Double,
    val personalRecords: Int,
)

/** A completed set of one exercise in a finished workout (Exercise detail: progress, sessions). */
data class ExerciseLogRow(
    val workoutId: Long,
    val startedAt: Long,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val distanceKm: Double?,
)

/** One completed set with its workout and exercise: the training log that history questions read. */
data class TrainingLogRow(
    val workoutId: Long,
    val workoutName: String,
    val startedAt: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val distanceKm: Double?,
    val isPr: Boolean,
)
