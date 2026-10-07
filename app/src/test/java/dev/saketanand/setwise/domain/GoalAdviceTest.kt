package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.GoalAdvice
import dev.saketanand.setwise.domain.model.GoalTarget
import dev.saketanand.setwise.domain.model.GoalTargetReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalAdviceTest {

    @Test
    fun `a weight and a time are read from the goal, however it's typed`() {
        // As reported (#125): "loose", "12kg", a double comma.
        assertEquals(GoalTarget(12.0, losing = true, weeks = 8.7, timeLabel = "2 months"), GoalTargetReader.read("I need to loose 12kg weight,, how can I do in 2 months?"))
        assertEquals(GoalTarget(5.0, losing = false), GoalTargetReader.read("gain 5 kg of muscle"))
        assertEquals(GoalTarget(9.1, losing = true, weeks = 10.0, timeLabel = "10 weeks"), GoalTargetReader.read("drop 20 lbs in 10 weeks"))
        assertEquals("a month", "1 month", GoalTargetReader.read("lose 3 kg in a month")?.timeLabel)
    }

    @Test
    fun `no weight, or no clear way, is no target`() {
        assertNull(GoalTargetReader.read("get stronger at squat, 3 days a week"))
        assertNull(GoalTargetReader.read("lose fat and build 4 kg of muscle")) // both ways
        assertNull(GoalTargetReader.read("squat 100 kg")) // a lift, not body weight
    }

    @Test
    fun `the pace is set against a steady one`() {
        val fast = GoalAdvice.of("I need to loose 12kg weight in 2 months", bodyWeightKg = null)!!
        assertEquals(1.4, fast.kgPerWeek!!, 1e-9) // 12 kg / 8.7 weeks
        assertTrue(fast.isFast)
        assertEquals(0.5 to 1.0, fast.steadyLowKg to fast.steadyHighKg)
        assertEquals(12 to 24, fast.steadyWeeksMin to fast.steadyWeeksMax)

        // With a body weight: 0.5 to 1% of it a week.
        val known = GoalAdvice.of("lose 12 kg in 2 months", bodyWeightKg = 80.0)!!
        assertEquals(0.4 to 0.8, known.steadyLowKg to known.steadyHighKg)
        assertEquals(15 to 30, known.steadyWeeksMin to known.steadyWeeksMax)

        val steady = GoalAdvice.of("lose 6 kg in 3 months", bodyWeightKg = null)!!
        assertFalse(steady.isFast)

        // Gaining muscle: 0.25 to 0.5 kg a week; no time given.
        val gain = GoalAdvice.of("gain 5 kg", bodyWeightKg = 70.0)!!
        assertNull(gain.kgPerWeek)
        assertEquals(10 to 20, gain.steadyWeeksMin to gain.steadyWeeksMax)
    }

    @Test
    fun `the facts hold every number the note may use, written once`() {
        val facts = GoalAdvice.of("lose 12 kg in 2 months", bodyWeightKg = 80.0)!!.factLines()
        assertEquals(
            """
            Goal: lose 12 kg in 2 months
            Pace it asks for: about 1.4 kg a week, faster than steady
            Steady pace that usually lasts: 0.4 to 0.8 kg a week (from 80 kg body weight)
            At a steady pace it takes: about 15 to 30 weeks
            These workouts: keep muscle while losing fat; most of the loss comes from eating a little less
            """.trimIndent(),
            facts,
        )
    }
}
