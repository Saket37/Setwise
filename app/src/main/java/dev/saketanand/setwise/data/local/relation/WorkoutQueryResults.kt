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
