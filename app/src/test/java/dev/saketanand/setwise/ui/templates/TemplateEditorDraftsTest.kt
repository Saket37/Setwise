package dev.saketanand.setwise.ui.templates

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.TemplateDraftExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateEditorDraftsTest {

    private val draft = TemplateEditorDraft(
        name = "Push",
        exercises = listOf(row(1, sets = 3), row(2, sets = 1), row(3, sets = 10)),
    )

    @Test
    fun `sets stay between 1 and 10`() {
        assertEquals(4, draft.withSetsChanged(1, +1).exercises[0].targetSets)
        assertEquals(1, draft.withSetsChanged(2, -1).exercises[1].targetSets)
        assertEquals(10, draft.withSetsChanged(3, +1).exercises[2].targetSets)
    }

    @Test
    fun `target reps start at 8, step by 1, and go back to none below 1`() {
        val start = TemplateEditorDraft(name = "Push", exercises = listOf(row(1, 3)))
        fun TemplateEditorDraft.reps() = exercises.single().targetReps

        assertNull(start.withRepsChanged(1, -1).reps()) // none stays none
        val eight = start.withRepsChanged(1, +1)
        assertEquals(8, eight.reps())
        assertEquals(9, eight.withRepsChanged(1, +1).reps())
        val one = TemplateEditorDraft(name = "Push", exercises = listOf(row(1, 3).copy(targetReps = 1)))
        assertNull(one.withRepsChanged(1, -1).reps())
        val fifty = TemplateEditorDraft(name = "Push", exercises = listOf(row(1, 3).copy(targetReps = 50)))
        assertEquals(50, fifty.withRepsChanged(1, +1).reps())
    }

    @Test
    fun `a timed target is seconds in steps of 15, and cardio has none`() {
        val plank = TemplateEditorDraft(name = "Core", exercises = listOf(row(1, 3).copy(isTimed = true)))
        val thirty = plank.withRepsChanged(1, +1)
        assertEquals(30, thirty.exercises.single().targetReps)
        assertEquals(45, thirty.withRepsChanged(1, +1).exercises.single().targetReps)
        assertNull(thirty.withRepsChanged(1, -1).withRepsChanged(1, -1).exercises.single().targetReps) // 30 → 15 → none

        val run = TemplateEditorDraft(name = "Run", exercises = listOf(row(1, 1).copy(isCardio = true)))
        assertNull(run.withRepsChanged(1, +1).exercises.single().targetReps)
    }

    @Test
    fun `target reps are saved with the draft`() {
        val draft = TemplateEditorDraft(name = "Push", exercises = listOf(row(1, 4).copy(targetReps = 5)))
        assertEquals(TemplateDraftExercise(1, 4, 5), draft.toDraft(7).exercises.single())
    }

    @Test
    fun `moving swaps with the neighbour, and does nothing at the ends`() {
        assertEquals(listOf(2L, 1L, 3L), draft.withMoved(2, -1).exercises.map { it.exerciseId })
        assertEquals(listOf(1L, 3L, 2L), draft.withMoved(2, +1).exercises.map { it.exerciseId })
        assertEquals(draft, draft.withMoved(1, -1))
        assertEquals(draft, draft.withMoved(3, +1))
    }

    @Test
    fun `picked exercises are appended once, with 3 sets`() {
        val added = draft.withAdded(listOf(exercise(4), exercise(1), exercise(4), exercise(5)))

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), added.exercises.map { it.exerciseId })
        assertEquals(3, added.exercises.last().targetSets)
        assertEquals("Chest", added.exercises.last().muscleGroup)
    }

    @Test
    fun `tapping the selected category clears it`() {
        val push = draft.withCategoryToggled("Push")
        assertEquals("Push", push.category)
        assertEquals("Legs", push.withCategoryToggled("Legs").category)
        assertNull(push.withCategoryToggled("Push").category)
    }

    @Test
    fun `removing drops just that exercise, and the draft keeps the order for saving`() {
        val saved = draft.withRemoved(2).toDraft(templateId = 9)

        assertEquals(9, saved.id)
        assertEquals(listOf(1L to 3, 3L to 10), saved.exercises.map { it.exerciseId to it.targetSets })
    }

    @Test
    fun `cardio is one block with no set count`() {
        val withCardio = draft.withAdded(listOf(exercise(8, ExerciseType.CARDIO)))
        val cardio = withCardio.exercises.last()

        assertEquals(true, cardio.isCardio)
        assertEquals(1, cardio.targetSets)
        assertEquals(1, withCardio.withSetsChanged(8, +1).exercises.last().targetSets)
        // An older template that stored 3 for cardio still saves 1.
        val old = TemplateEditorDraft("Old", exercises = listOf(cardio.copy(targetSets = 3)))
        assertEquals(1, old.toDraft(templateId = 1).exercises.single().targetSets)
    }

    private fun row(id: Long, sets: Int) = TemplateEditorExercise(id, "Exercise $id", "Chest", sets)

    private fun exercise(id: Long, type: ExerciseType = ExerciseType.STRENGTH) =
        Exercise(id, "Exercise $id", type, "Chest", "Barbell", 90, isTimed = false, isCustom = false, metrics = null, calorieMethod = null, met = null)
}
