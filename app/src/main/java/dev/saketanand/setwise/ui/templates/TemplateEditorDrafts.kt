package dev.saketanand.setwise.ui.templates

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.Template
import dev.saketanand.setwise.domain.model.TemplateDraft
import dev.saketanand.setwise.domain.model.TemplateDraftExercise

/** Edits to a [TemplateEditorDraft]. Plain functions, unit-tested without Android. */

fun Template.toEditorDraft() = TemplateEditorDraft(
    name = name,
    category = category,
    exercises = exercises.map {
        TemplateEditorExercise(
            it.exerciseId, it.name, it.muscleGroup, if (it.isCardio) 1 else it.targetSets, it.isCardio,
            targetReps = it.targetReps.takeIf { _ -> !it.isCardio }, isTimed = it.isTimed,
        )
    },
)

fun TemplateEditorDraft.toDraft(templateId: Long) = TemplateDraft(
    id = templateId,
    name = name,
    category = category,
    exercises = exercises.map { TemplateDraftExercise(it.exerciseId, if (it.isCardio) 1 else it.targetSets, it.targetReps.takeIf { _ -> !it.isCardio }) },
)

fun TemplateEditorDraft.withCategoryToggled(option: String) = copy(category = if (category == option) null else option)

fun TemplateEditorDraft.withSetsChanged(exerciseId: Long, delta: Int) = copy(
    exercises = exercises.map {
        if (it.exerciseId == exerciseId && !it.isCardio) {
            it.copy(targetSets = (it.targetSets + delta).coerceIn(TemplateEditorUiState.SET_RANGE))
        } else {
            it
        }
    },
)

/**
 * One step more or fewer target reps ([delta] = +1 / -1); seconds in steps of 15 for a timed
 * exercise. From none, + starts at a first value; − below the smallest is none again.
 */
fun TemplateEditorDraft.withRepsChanged(exerciseId: Long, delta: Int) = copy(
    exercises = exercises.map { exercise ->
        if (exercise.exerciseId != exerciseId || exercise.isCardio) return@map exercise
        val (range, first, step) = if (exercise.isTimed) {
            Triple(TemplateEditorUiState.SECONDS_RANGE, TemplateEditorUiState.FIRST_SECONDS, TemplateEditorUiState.SECONDS_STEP)
        } else {
            Triple(TemplateEditorUiState.REP_RANGE, TemplateEditorUiState.FIRST_REPS, 1)
        }
        val current = exercise.targetReps
        val next = when {
            current == null -> if (delta > 0) first else null
            else -> (current + delta * step).takeIf { it >= range.first }?.coerceAtMost(range.last)
        }
        exercise.copy(targetReps = next)
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

/** Appends [picked] (in order) that aren't in the template yet, with the default set count (cardio: 1 block). */
fun TemplateEditorDraft.withAdded(picked: List<Exercise>): TemplateEditorDraft {
    val present = exercises.mapTo(HashSet()) { it.exerciseId }
    val added = picked.distinctBy { it.id }.filter { it.id !in present }
        .map {
            val isCardio = it.type == ExerciseType.CARDIO
            TemplateEditorExercise(it.id, it.name, it.muscleGroup, if (isCardio) 1 else TemplateEditorUiState.DEFAULT_SETS, isCardio, isTimed = it.isTimed)
        }
    return copy(exercises = exercises + added)
}
