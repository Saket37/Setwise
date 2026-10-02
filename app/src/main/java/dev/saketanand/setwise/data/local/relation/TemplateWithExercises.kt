package dev.saketanand.setwise.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.data.local.entity.TemplateEntity
import dev.saketanand.setwise.data.local.entity.TemplateExerciseEntity

/** A template with its exercise rows; Room fills the lists from the @Relation annotations. */
data class TemplateWithExercises(
    @Embedded val template: TemplateEntity,
    @Relation(
        entity = TemplateExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "templateId",
    )
    val items: List<TemplateExerciseWithExercise>,
)

/** One template row plus the exercise it points to (for the name and rest time). */
data class TemplateExerciseWithExercise(
    @Embedded val item: TemplateExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)
