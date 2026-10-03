package dev.saketanand.setwise.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.data.local.entity.SetEntity
import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.local.entity.WorkoutExerciseEntity

/**
 * A workout with its exercises and their sets; Room fills the lists from the @Relation
 * annotations. Relations come back unordered: sort by position / setNumber when mapping.
 */
data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(
        entity = WorkoutExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "workoutId",
    )
    val items: List<WorkoutExerciseWithSets>,
)

/** One exercise of a workout: the exercise itself (name, type) and the sets logged for it. */
data class WorkoutExerciseWithSets(
    @Embedded val item: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<SetEntity>,
)
