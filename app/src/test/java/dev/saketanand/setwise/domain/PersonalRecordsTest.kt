package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PersonalBests
import dev.saketanand.setwise.domain.model.PersonalRecords
import dev.saketanand.setwise.domain.model.PrKind
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalRecordsTest {

    private val bench = exercise(ExerciseType.STRENGTH)
    private val pullUp = exercise(ExerciseType.BODYWEIGHT)
    private val plank = exercise(ExerciseType.BODYWEIGHT, timed = true)
    private val treadmill = exercise(ExerciseType.CARDIO)

    private val benchHistory = PersonalBests.from(listOf(PreviousSet(60.0, 8), PreviousSet(57.5, 10), PreviousSet(60.0, 6)))

    @Test
    fun `bests come from the earlier sets`() {
        assertEquals(PreviousSet(60.0, 8), benchHistory.heaviest)
        // 57.5 × 10 → 76.7 beats 60 × 8 → 76.0
        assertEquals(PreviousSet(57.5, 10), benchHistory.bestEstimatedOneRepMax)
    }

    @Test
    fun `a heavier weight than ever is a weight record`() {
        val pr = PersonalRecords.find(bench, listOf(set(1, 60.0, 8), set(2, 62.5, 6)), benchHistory)!!

        assertEquals(PrKind.Weight, pr.kind)
        assertEquals(2L, pr.set.id)
        assertEquals(PreviousSet(60.0, 8), pr.previousBest)
    }

    @Test
    fun `more reps at the same weight is an estimated 1RM record`() {
        val pr = PersonalRecords.find(bench, listOf(set(1, 60.0, 10)), benchHistory)!!

        assertEquals(PrKind.EstimatedOneRepMax, pr.kind)
        assertEquals(PreviousSet(57.5, 10), pr.previousBest)
    }

    @Test
    fun `matching the best isn't a record`() {
        assertNull(PersonalRecords.find(bench, listOf(set(1, 60.0, 8)), benchHistory))
    }

    @Test
    fun `sets that aren't ticked off don't count`() {
        assertNull(PersonalRecords.find(bench, listOf(set(1, 100.0, 5, done = false)), benchHistory))
    }

    @Test
    fun `the first time an exercise is done is not a record`() {
        assertNull(PersonalRecords.find(bench, listOf(set(1, 100.0, 5)), PersonalBests.None))
    }

    @Test
    fun `bodyweight records are most reps, timed ones the longest hold`() {
        val pullUps = PersonalRecords.find(pullUp, listOf(set(1, null, 12)), PersonalBests.from(listOf(PreviousSet(null, 10))))!!
        assertEquals(PrKind.Reps, pullUps.kind)

        val hold = PersonalRecords.find(
            plank,
            listOf(set(1, null, null).copy(durationSec = 90)),
            PersonalBests.from(listOf(PreviousSet(null, null, durationSec = 75))),
        )!!
        assertEquals(PrKind.Duration, hold.kind)
    }

    @Test
    fun `cardio has no records yet`() {
        assertNull(PersonalRecords.find(treadmill, listOf(set(1, null, null).copy(durationSec = 1800)), PersonalBests.from(listOf(PreviousSet(null, null, 600)))))
    }

    private fun set(id: Long, weightKg: Double?, reps: Int?, done: Boolean = true) =
        WorkoutSet(id = id, setNumber = id.toInt(), weightKg = weightKg, reps = reps, durationSec = null, isCompleted = done, isPr = false)

    private fun exercise(type: ExerciseType, timed: Boolean = false) = Exercise(
        id = 1, name = "x", type = type, muscleGroup = "Chest", equipment = "Barbell", defaultRestSec = 90,
        isTimed = timed, isCustom = false, metrics = null, calorieMethod = null, met = null,
    )
}
