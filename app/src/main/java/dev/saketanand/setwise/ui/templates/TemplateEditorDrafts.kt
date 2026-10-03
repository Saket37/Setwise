package dev.saketanand.setwise.ui.templates

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.Template
import dev.saketanand.setwise.domain.model.TemplateDraft
import dev.saketanand.setwise.domain.model.TemplateDraftExercise

/** Edits to a [TemplateEditorDraft]. Plain functions, unit-tested without Android. */

fun Template.toEditorDraft() = TemplateEditorDraft(
    name = name,
    category = category,
    exercises = exercises.map { TemplateEditorExercise(it.exerciseId, it.name, it.muscleGroup, it.targetSets) },
)

fun TemplateEditorDraft.toDraft(templateId: Long) = TemplateDraft(
    id = templateId,
    name = name,
    category = category,
    exercises = exercises.map { TemplateDraftExercise(it.exerciseId, it.targetSets) },
)

fun TemplateEditorDraft.withCategoryToggled(option: String) = copy(category = if (category == option) null else option)

fun TemplateEditorDraft.withSetsChanged(exerciseId: Long, delta: Int) = copy(
    exercises = exercises.map {
        if (it.exerciseId == exerciseId) {
            it.copy(targetSets = (it.targetSets + delta).coerceIn(TemplateEditorUiState.SET_RANGE))
        } else {
            it
        }
    },
)

/** Moves the exercise one place ([by] = -1 up, +1 down); nothing at either end. */
fun TemplateEditorDraft.withMoved(exerciseId: Long, by: Int): TemplateEditorDraft {
    val from = exercises.indexOfFirst { it.exerciseId == exerciseId }
    val to = from + by
    if (from < 0 || to !in exercises.indices) return this
    return copy(exercises = exercises.toMutableList().apply { add(to, removeAt(from)) })
}

fun TemplateEditorDraft.withRemoved(exerciseId: Long) = copy(exercises = exercises.filterNot { it.exerciseId == exerciseId })

/** Appends [picked] (in order) that aren't in the template yet, with the default set count. */
fun TemplateEditorDraft.withAdded(picked: List<Exercise>): TemplateEditorDraft {
    val present = exercises.mapTo(HashSet()) { it.exerciseId }
    val added = picked.distinctBy { it.id }.filter { it.id !in present }
        .map { TemplateEditorExercise(it.id, it.name, it.muscleGroup, TemplateEditorUiState.DEFAULT_SETS) }
    return copy(exercises = exercises + added)
}
