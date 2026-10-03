package dev.saketanand.setwise.data.local.relation

import androidx.room.Embedded
import dev.saketanand.setwise.data.local.entity.ExerciseEntity

/*
 * Shapes returned by ExerciseDao queries that aren't whole entities.
 * Room maps each column alias in the SELECT to the property with the same name.
 */

/**
 * An exercise from a recent finished workout, with that session's top set
 * (heaviest weight, then most reps). The set columns are null if no set was completed.
 */
data class RecentExerciseRow(
    @Embedded val exercise: ExerciseEntity,
    val lastUsedAt: Long,
    val lastWeightKg: Double?,
    val lastReps: Int?,
)
