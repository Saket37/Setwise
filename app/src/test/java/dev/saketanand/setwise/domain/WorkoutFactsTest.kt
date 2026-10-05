package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.Intensity
import dev.saketanand.setwise.domain.model.Measure
import dev.saketanand.setwise.domain.model.PersonalBests
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.PreviousWorkout
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.SetFact
import dev.saketanand.setwise.domain.model.WorkoutFacts
import dev.saketanand.setwise.domain.model.WorkoutHistoryItem
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutSet
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutFactsTest {

    private val start = Instant.parse("2026-10-03T12:00:00Z")

    @Test
    fun `each exercise's best set is compared with last time's, improvements first`() {
        val facts = WorkoutFacts.of(
            session(
                exercise(1, "Overhead Press", ExerciseType.STRENGTH, now = listOf(40.0 to 6, 40.0 to 6), before = listOf(40.0 to 7)),
                exercise(2, "Bench Press", ExerciseType.STRENGTH, now = listOf(60.0 to 8, 62.5 to 8), before = listOf(60.0 to 8)),
                exercise(3, "Pull-up", ExerciseType.BODYWEIGHT, now = listOf(null to 12), before = listOf(null to 10)),
                exercise(4, "Face Pull", ExerciseType.STRENGTH, now = listOf(20.0 to 12), before = emptyList()), // first time
            ),
            history = emptyList(),
        )

        assertEquals(listOf("Bench Press", "Pull-up", "Overhead Press"), facts.changes.map { it.exercise })
        val bench = facts.changes[0]
        assertEquals(Measure.Weight, bench.measure)
        assertEquals(SetFact(62.5, 8, null), bench.now)
        assertEquals(2.5, bench.weightDeltaKg, 0.0)
        assertEquals(2, facts.changes[1].repsDelta)
        assertEquals(-1, facts.changes[2].repsDelta) // same weight, a rep fewer: a drop
    }

    @Test
    fun `volume is compared with the last earlier workout of the same name`() {
        val facts = WorkoutFacts.of(
            session(exercise(1, "Bench Press", ExerciseType.STRENGTH, now = listOf(100.0 to 10), before = emptyList())),
            history = listOf(
                historyItem(5, "Push Day", start.minusSeconds(86_400 * 7), volume = 800.0),
                historyItem(6, "push day", start.minusSeconds(86_400 * 3), volume = 1_250.0), // the latest earlier one
                historyItem(7, "Pull Day", start.minusSeconds(86_400), volume = 3_000.0),
            ),
        )

        assertEquals(PreviousWorkout("push day", 1_250.0), facts.previous)
        assertEquals(-20, facts.volumeChangePercent) // 1000 vs 1250
    }

    @Test
    fun `intensity and rest come from the sets`() {
        // 18 sets in 60 min, 90 s apart: moderate, median rest 90 s.
        val sets = List(18) { 60.0 to 8 }
        val facts = WorkoutFacts.of(session(exercise(1, "Bench Press", ExerciseType.STRENGTH, now = sets, before = emptyList())), emptyList())

        assertEquals(Intensity.Moderate, facts.intensity)
        assertEquals(90L, facts.medianRestSec)
        assertNull(facts.previous)
    }

    private fun session(vararg exercises: SessionExercise) =
        WorkoutSession(1, "Push Day", null, start, start.plusSeconds(3_600), exercises.toList())

    private fun exercise(id: Long, name: String, type: ExerciseType, now: List<Pair<Double?, Int>>, before: List<Pair<Double?, Int>>) =
        SessionExercise(
            id = id,
            exercise = Exercise(id, name, type, "Chest", "Barbell", 90, false, false, null, null, null),
            sets = now.mapIndexed { i, (kg, reps) ->
                WorkoutSet(id * 100 + i, i + 1, kg, reps, null, isCompleted = true, isPr = false, completedAt = start.plusSeconds(90L * (i + 1)))
            },
            previousSets = before.map { (kg, reps) -> PreviousSet(kg, reps) },
            bestsBefore = PersonalBests.None,
        )

    private fun historyItem(id: Long, name: String, startedAt: Instant, volume: Double) =
        WorkoutHistoryItem(id, name, startedAt, startedAt.plusSeconds(3_600), 10, volume, 0.0, 0, null)
}
