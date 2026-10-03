package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseSession
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.LoggedSet
import dev.saketanand.setwise.domain.model.Measure
import dev.saketanand.setwise.domain.model.NextSession
import dev.saketanand.setwise.domain.model.Progression
import dev.saketanand.setwise.domain.model.ProgressionRule
import dev.saketanand.setwise.domain.model.SetFact
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionTest {

    private val now = Instant.parse("2026-10-04T18:00:00Z")
    private val bench = exercise("Bench Press (Barbell)", "Barbell", "Chest")
    private val ohp = exercise("Overhead Press (Barbell)", "Barbell", "Shoulders")

    @Test
    fun `every set to its reps twice at one weight adds the step, warm-ups aside`() {
        val history = listOf(
            session(2, warmUp() + kg(60.0, 8, 8, 8)),
            session(5, warmUp() + kg(60.0, 8, 8, 8)),
            session(9, kg(57.5, 8, 8, 8)),
        )

        assertEquals(
            NextSession(62.5, 8, null, 3, ProgressionRule.AddWeight, SetFact(60.0, 8, null), stepKg = 2.5),
            Progression.next(bench, history, now),
        )
    }

    @Test
    fun `reps short, or the first time at a weight, is the same again`() {
        val short = listOf(session(2, kg(60.0, 8, 8, 6)), session(5, kg(60.0, 8, 8, 8)))
        assertEquals(NextSession(60.0, 8, null, 3, ProgressionRule.Repeat, SetFact(60.0, 8, null)), Progression.next(bench, short, now))

        val newWeight = listOf(session(2, kg(62.5, 8, 8, 8)), session(5, kg(60.0, 8, 8, 8)))
        assertEquals(ProgressionRule.Repeat, Progression.next(bench, newWeight, now)?.rule)

        // A first set above the rest still aims for what most sets did.
        val pyramid = listOf(session(2, kg(60.0, 10, 8, 8)), session(5, kg(60.0, 8, 8, 8)))
        assertEquals(ProgressionRule.AddWeight, Progression.next(bench, pyramid, now)?.rule)
    }

    @Test
    fun `the step is their own smallest jump, else usual for the equipment`() {
        val micro = listOf(session(2, kg(61.25, 5, 5)), session(5, kg(60.0, 5, 5)))
        assertEquals(1.25, Progression.stepKg(bench, micro), 0.0)

        val dumbbell = exercise("Bench Press (Dumbbell)", "Dumbbell", "Chest")
        val once = listOf(session(2, kg(22.0, 10, 10)), session(5, kg(22.0, 10, 10)))
        assertEquals(24.0, Progression.next(dumbbell, once, now)?.weightKg)

        val squat = exercise("Back Squat (Barbell)", "Barbell", "Quads")
        assertEquals(5.0, Progression.stepKg(squat, emptyList()), 0.0)
    }

    @Test
    fun `bodyweight adds a rep, a hold adds time, cardio has none`() {
        val pullUp = exercise("Pull-up", "Bodyweight", "Back", ExerciseType.BODYWEIGHT)
        val reps = listOf(session(2, bodyweight(10, 10, 10)), session(5, bodyweight(10, 10, 10)))
        assertEquals(NextSession(null, 11, null, 3, ProgressionRule.AddRep, SetFact(null, 10, null)), Progression.next(pullUp, reps, now))

        val plank = exercise("Plank", "Bodyweight", "Core", ExerciseType.BODYWEIGHT, timed = true)
        val holds = listOf(session(2, held(45, 45)), session(5, held(45, 45)))
        assertEquals(NextSession(null, null, 50, 2, ProgressionRule.AddTime, SetFact(null, null, 45)), Progression.next(plank, holds, now))

        val bike = exercise("Bike", "Machine", "Cardio", ExerciseType.CARDIO)
        assertNull(Progression.next(bike, listOf(session(2, held(1_200))), now))
        assertNull(Progression.next(bench, emptyList(), now))
    }

    @Test
    fun `weeks at about the same best is a plateau, and the next session is lighter with more reps`() {
        // Twice a week for 4 weeks at an estimated 1RM of about 48 kg, never every rep.
        val history = (1..9).map { i -> session(i * 3 + 1, if (i % 2 == 0) kg(42.5, 4, 4, 3) else kg(40.0, 6, 6, 5)) }

        val plateau = Progression.plateau(ohp, history, now)!!
        assertEquals(Measure.Weight, plateau.measure)
        assertEquals(history.last().startedAt, plateau.since)
        assertEquals(9, plateau.sessions)
        assertEquals(3, plateau.weeks)
        assertEquals(48.17, plateau.best, 0.01)

        // From 40 × 6 (the last session): 5% lighter on their 2.5 kg step, 2 more reps.
        assertEquals(
            NextSession(37.5, 8, null, 3, ProgressionRule.Lighter, SetFact(40.0, 6, null)),
            Progression.next(ohp, history, now),
        )
    }

    @Test
    fun `steady gains, a break, or few sessions aren't a plateau`() {
        val rising = (1..8).map { i -> session(i * 4, kg(50.0 - i * 2.5, 6, 6, 5)) }
        assertNull(Progression.plateau(ohp, rising, now))

        // About 0.75% a week (39 → 40 kg over 3½ weeks): slow, but a new best every week.
        val slow = (1..9).map { i -> session(i * 3 + 1, kg(40.0 - (i - 1) * 0.125, 6, 6, 5)) }
        assertNull(Progression.plateau(ohp, slow, now))

        val flat = (1..6).map { i -> session(i * 4, kg(40.0, 6, 6, 5)) }
        assertNull(Progression.plateau(ohp, flat.map { it.copy(startedAt = it.startedAt.minus(Duration.ofDays(30))) }, now))
        assertNull(Progression.plateau(ohp, flat.take(3), now))
    }

    private fun warmUp() = listOf(LoggedSet(40.0, 10, null, null))
    private fun kg(weight: Double, vararg reps: Int) = reps.map { LoggedSet(weight, it, null, null) }
    private fun bodyweight(vararg reps: Int) = reps.map { LoggedSet(null, it, null, null) }
    private fun held(vararg seconds: Int) = seconds.map { LoggedSet(null, null, it, null) }

    private fun session(daysAgo: Int, sets: List<LoggedSet>) =
        ExerciseSession(daysAgo.toLong(), now.minus(Duration.ofDays(daysAgo.toLong())), sets)

    private fun exercise(name: String, equipment: String, muscle: String, type: ExerciseType = ExerciseType.STRENGTH, timed: Boolean = false) =
        Exercise(1, name, type, muscle, equipment, 90, timed, false, null, null, null)
}
