package dev.saketanand.setwise.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** An exercise performed within a workout, in display order. */
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        // RESTRICT: an exercise used in any workout can't be deleted, so history is never
        // silently wiped. Catch SQLiteConstraintException and tell the user instead
        // (or add soft delete via an isArchived flag later).
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["workoutId"]), Index(value = ["exerciseId"])],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
    /** From the template it was started from: the reps (or seconds) each set is hinted with. */
    val targetReps: Int? = null,
)
