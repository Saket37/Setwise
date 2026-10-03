package dev.saketanand.setwise.ui.exercises

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PreviousSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExercisePickerUiMappersTest {

    @Test
    fun `initials use the first two words and ignore the variant in brackets`() {
        assertEquals("BP", exerciseInitials("Bench Press (Barbell)"))
        assertEquals("ID", exerciseInitials("Incline Dumbbell Press"))
    }

    @Test
    fun `initials of a one-word name are its first two letters`() {
        assertEquals("PL", exerciseInitials("Plank"))
    }

    @Test
    fun `hyphens split words`() {
        assertEquals("PU", exerciseInitials("Pull-up"))
    }

    @Test
    fun `blank name gives no initials`() {
        assertEquals("", exerciseInitials("  "))
    }

    @Test
    fun `equipment None is hidden`() {
        assertNull(exercise(equipment = "None").toRowUi(isSelected = false).equipment)
        assertEquals("Barbell", exercise(equipment = "Barbell").toRowUi(isSelected = false).equipment)
    }

    @Test
    fun `last set weight is formatted without needless decimals`() {
        val row = exercise().toRowUi(isSelected = true, lastSet = PreviousSet(weightKg = 60.0, reps = 8))
        assertEquals(LastSetUi(weight = "60", reps = 8), row.lastSet)
        assertEquals(true, row.isSelected)
    }

    @Test
    fun `bodyweight last set has reps only`() {
        assertEquals(LastSetUi(weight = null, reps = 10), PreviousSet(weightKg = null, reps = 10).toUi())
    }

    @Test
    fun `a set with neither value shows nothing`() {
        assertNull(PreviousSet(weightKg = null, reps = null).toUi())
    }

    private fun exercise(equipment: String = "Barbell") = Exercise(
        id = 1, name = "Bench Press (Barbell)", type = ExerciseType.STRENGTH, muscleGroup = "Chest",
        equipment = equipment, defaultRestSec = 120, isTimed = false, isCustom = false,
        metrics = null, calorieMethod = null, met = null,
    )
}
