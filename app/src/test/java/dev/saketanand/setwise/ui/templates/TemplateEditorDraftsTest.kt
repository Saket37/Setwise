package dev.saketanand.setwise.ui.templates

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
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
