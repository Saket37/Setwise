package dev.saketanand.setwise.ui.workout

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveWorkoutUiMappersTest {

    @Test
    fun `exercise type decides the set kind`() {
        assertEquals(SetKind.WeightReps, exercise(ExerciseType.STRENGTH).setKind)
        assertEquals(SetKind.Bodyweight, exercise(ExerciseType.BODYWEIGHT).setKind)
        assertEquals(SetKind.Duration, exercise(ExerciseType.BODYWEIGHT, timed = true).setKind)
        assertEquals(SetKind.Cardio, exercise(ExerciseType.CARDIO).setKind)
    }

    @Test
    fun `hints come from last time, then from the row above`() {
        val ui = SessionExercise(
            id = 1,
            exercise = exercise(ExerciseType.STRENGTH),
            sets = listOf(set(1), set(2, weightKg = 65.0, reps = 6), set(3)),
            previousSets = listOf(PreviousSet(60.0, 8), PreviousSet(62.5, 7)),
        ).toUi()

        val (first, second, third) = ui.sets
        assertEquals("60" to "8", first.weightHint to first.repsHint)
        assertEquals("62.5" to "7", second.weightHint to second.repsHint)
        // No set 3 last time: suggest what set 2 is being done with.
        assertEquals("65" to "6", third.weightHint to third.repsHint)
        assertNull(third.previous)
        assertEquals("60 × 8 · 62.5 × 7", ui.lastTime)
    }

    @Test
    fun `typed values show as text, empty ones as empty`() {
        val row = SessionExercise(1, exercise(ExerciseType.STRENGTH), listOf(set(1, weightKg = 62.5, reps = null)), emptyList())
            .toUi().sets.single()
        assertEquals("62.5", row.weight)
        assertEquals("", row.reps)
        assertEquals(1, row.number)
    }

    @Test
    fun `timed exercises use seconds`() {
        val ui = SessionExercise(
            1, exercise(ExerciseType.BODYWEIGHT, timed = true),
            listOf(set(1).copy(durationSec = 45)),
            listOf(PreviousSet(weightKg = null, reps = null, durationSec = 60)),
        ).toUi()
        assertEquals("45", ui.sets.single().reps)
        assertEquals("60", ui.sets.single().repsHint)
        assertEquals("60s", ui.lastTime)
    }

    @Test
    fun `previous labels per kind`() {
        assertEquals("60 × 8", PreviousSet(60.0, 8).label(SetKind.WeightReps))
        assertEquals("10", PreviousSet(null, 10).label(SetKind.Bodyweight))
        assertEquals("+5 × 10", PreviousSet(5.0, 10).label(SetKind.Bodyweight))
        assertEquals("45s", PreviousSet(null, null, 45).label(SetKind.Duration))
        assertNull(PreviousSet(null, null).label(SetKind.WeightReps))
    }

    @Test
    fun `numbers are parsed with either decimal separator`() {
        assertEquals(62.5, parseWeight("62,5"))
        assertEquals(62.5, parseWeight("62.5"))
        assertNull(parseWeight(""))
        assertNull(parseWeight("."))
        assertEquals(8, parseAmount("8"))
        assertNull(parseAmount(""))
    }

    private fun exercise(type: ExerciseType, timed: Boolean = false) = Exercise(
        id = 10, name = "Bench Press", type = type, muscleGroup = "Chest", equipment = "Barbell",
        defaultRestSec = 120, isTimed = timed, isCustom = false, metrics = null, calorieMethod = null, met = null,
    )

    private fun set(number: Int, weightKg: Double? = null, reps: Int? = null) = WorkoutSet(
        id = number.toLong(), setNumber = number, weightKg = weightKg, reps = reps,
        durationSec = null, isCompleted = false, isPr = false,
    )
}
