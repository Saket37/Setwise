package dev.saketanand.setwise.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One set of a [WorkoutExerciseEntity]. Strength/bodyweight sets use weight + reps
 * (weight is optional added load for bodyweight); cardio sets use the cardio columns.
 */
@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workoutExerciseId"])],
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val setNumber: Int,
    // Strength / bodyweight
    val weightKg: Double? = null,
    val reps: Int? = null,
    // Cardio (durationSec also used by timed exercises like Plank)
    val durationSec: Int? = null,
    val inclinePct: Double? = null,
    val speedMinKmh: Double? = null,
    val speedMaxKmh: Double? = null,
    val distanceKm: Double? = null,
    /** Machine resistance level (bike, elliptical, rower, stairs). */
    val level: Int? = null,
    val isCompleted: Boolean = false,
    /** Epoch millis when the set was ticked off. */
    val completedAt: Long? = null,
    val isPr: Boolean = false,
)
