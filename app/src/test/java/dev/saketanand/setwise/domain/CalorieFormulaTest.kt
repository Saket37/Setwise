package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.CalorieEstimate
import dev.saketanand.setwise.domain.model.CalorieFormula
import dev.saketanand.setwise.domain.model.CalorieMethod
import dev.saketanand.setwise.domain.model.CardioMetric
import dev.saketanand.setwise.domain.model.CardioValues
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.Intensity
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutSet
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalorieFormulaTest {

    private val start = Instant.parse("2026-10-03T12:00:00Z")

    @Test
    fun `strength is time × a MET by how dense the session was`() {
        // 60 min, 18 sets → 18 / h: moderate, MET 5 × 70 kg × 1 h.
        assertEquals(CalorieEstimate(350, Intensity.Moderate), CalorieFormula.estimate(session(60, strength(18)), 70.0))
        // 8 sets in an hour: light, MET 3.5.
        assertEquals(CalorieEstimate(245, Intensity.Light), CalorieFormula.estimate(session(60, strength(8)), 70.0))
        // 30 sets in an hour: vigorous, MET 6.
        assertEquals(CalorieEstimate(420, Intensity.Vigorous), CalorieFormula.estimate(session(60, strength(30)), 70.0))
    }

    @Test
    fun `treadmill uses the ACSM walking equation with speed and incline`() {
        // 6 km/h = 100 m/min, 5%: VO2 = 10 + 9 + 3.5 = 22.5 → MET 6.43 × 70 kg × 0.5 h ≈ 225.
        val treadmill = cardio(CalorieMethod.ACSM_TREADMILL, met = null, CardioValues(1_800, inclinePct = 5.0, speedMinKmh = 5.0, speedMaxKmh = 7.0))
        assertEquals(CalorieEstimate(225, Intensity.Moderate), CalorieFormula.estimate(session(30, treadmill), 70.0))
    }

    @Test
    fun `an outdoor run uses its pace`() {
        // 5 km in 30 min = 10 km/h = 166.7 m/min: VO2 = 33.3 + 3.5 = 36.8 → MET 10.5 × 70 × 0.5 ≈ 368.
        val run = cardio(CalorieMethod.ACSM_RUN_FROM_PACE, met = null, CardioValues(1_800, distanceKm = 5.0))
        assertEquals(CalorieEstimate(368, Intensity.Vigorous), CalorieFormula.estimate(session(30, run), 70.0))
    }

    @Test
    fun `cardio time is taken out of the lifting time`() {
        // Elliptical MET 5 for 20 min = 116.7; lifting 12 sets in the other 40 min (18 / h,
        // moderate) = 5 × 70 × 0.667 = 233.3; together 350.
        val elliptical = cardio(CalorieMethod.MET, met = 5.0, CardioValues(1_200, level = 8))
        assertEquals(CalorieEstimate(350, Intensity.Moderate), CalorieFormula.estimate(session(60, strength(12), elliptical), 70.0))
    }

    @Test
    fun `no estimate without a body weight, or for an unfinished workout`() {
        assertNull(CalorieFormula.estimate(session(60, strength(18)), null))
        assertNull(CalorieFormula.estimate(session(60, strength(18)).copy(endedAt = null), 70.0))
    }

    private fun session(minutes: Long, vararg exercises: SessionExercise) =
        WorkoutSession(1, "W", null, start, start.plusSeconds(minutes * 60), exercises.toList())

    private fun strength(sets: Int) = SessionExercise(
        id = 1,
        exercise = Exercise(1, "Bench", ExerciseType.STRENGTH, "Chest", "Barbell", 120, false, false, null, null, null),
        sets = List(sets) { WorkoutSet(it.toLong(), it + 1, 60.0, 8, null, isCompleted = true, isPr = false) },
        previousSets = emptyList(),
    )

    private fun cardio(method: CalorieMethod, met: Double?, values: CardioValues) = SessionExercise(
        id = 2,
        exercise = Exercise(2, "Cardio", ExerciseType.CARDIO, "Cardio", "Machine", 0, false, false, listOf(CardioMetric.DURATION), method, met),
        sets = listOf(WorkoutSet(100, 1, null, null, values.durationSec, isCompleted = true, isPr = false, cardio = values)),
        previousSets = emptyList(),
    )
}
