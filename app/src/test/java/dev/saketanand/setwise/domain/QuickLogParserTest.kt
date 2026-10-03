package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.CardioValues
import dev.saketanand.setwise.domain.model.QuickLogParse
import dev.saketanand.setwise.domain.model.QuickLogParser
import dev.saketanand.setwise.domain.model.SetFact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickLogParserTest {

    private fun kg(weight: Double, reps: Int, times: Int = 1) = List(times) { SetFact(weight, reps, null) }
    private fun reps(vararg reps: Int) = reps.map { SetFact(null, it, null) }
    private fun secs(seconds: Int, times: Int) = List(times) { SetFact(null, null, seconds) }

    private fun check(text: String, phrase: String?, sets: List<SetFact>) {
        val parse = QuickLogParser.parse(text)
        assertEquals(text, QuickLogParse(phrase, sets), parse)
        assertTrue(text, parse.isComplete)
    }

    @Test
    fun `sets × reps at a weight`() {
        check("bench 3x8 at 60", "bench", kg(60.0, 8, 3))
        check("Bench 3 x 8 @ 60kg", "bench", kg(60.0, 8, 3))
        check("row 3 sets of 10 with 50", "row", kg(50.0, 10, 3))
        check("ohp 3 sets of 6 at 40", "ohp", kg(40.0, 6, 3))
        check("squat 4x5 at 102,5", "squat", kg(102.5, 5, 4))
    }

    @Test
    fun `weight × reps, one set or several`() {
        check("deadlift 140x5x3", "deadlift", kg(140.0, 5, 3))
        check("curls 12.5 x 10 x 3", "curls", kg(12.5, 10, 3))
        check("bench 60x8", "bench", kg(60.0, 8))
        check("squat 100 for 5, 105 for 3", "squat", kg(100.0, 5) + kg(105.0, 3))
        check("press 40 for 6 x 3", "press", kg(40.0, 6, 3))
        check("press 40kg for 6 for 3 sets", "press", kg(40.0, 6, 3))
        check("lateral raise 8kg 15 reps", "lateral raise", kg(8.0, 15))
        check("leg curl 12 reps at 40", "leg curl", kg(40.0, 12))
        check("60 for 8", null, kg(60.0, 8)) // no exercise: the open one
    }

    @Test
    fun `bodyweight reps and timed holds`() {
        check("pullups 3x10", "pullups", reps(10, 10, 10))
        check("dips x12 x12 x10", "dips", reps(12, 12, 10))
        check("push ups 25 reps", "push ups", reps(25))
        check("plank 3x45s", "plank", secs(45, 3))
        check("side plank 3 sets of 30 seconds", "side plank", secs(30, 3))
        check("dead hang 60 sec", "dead hang", secs(60, 1))
    }

    @Test
    fun `cardio and same as last time`() {
        assertEquals(
            QuickLogParse("treadmill", cardio = CardioValues(1_800, inclinePct = 6.0)),
            QuickLogParser.parse("treadmill 30 min 6% incline"),
        )
        assertEquals(
            QuickLogParse("run", cardio = CardioValues(1_500, distanceKm = 5.0)),
            QuickLogParser.parse("run 5 km in 25 minutes"),
        )
        assertEquals(
            QuickLogParse("bike", cardio = CardioValues(1_200, level = 8)),
            QuickLogParser.parse("bike 20min level 8"),
        )
        assertEquals(QuickLogParse(null, sameAsLastTime = true), QuickLogParser.parse("same as last time"))
        assertEquals(QuickLogParse("bench", sameAsLastTime = true), QuickLogParser.parse("Bench, same as last time"))
    }

    @Test
    fun `anything it can't place is left over for the model`() {
        val parse = QuickLogParser.parse("ohp 3 sets of 6 at 40, last one 37.5 for 8")
        assertEquals("ohp", parse.exercisePhrase)
        assertEquals(listOf("last", "one"), parse.leftover)
        assertFalse(parse.isComplete)

        assertFalse(QuickLogParser.parse("ohp 40 for 6 twice").isComplete)
        assertFalse(QuickLogParser.parse("bench").isComplete) // nothing to log
        assertFalse(QuickLogParser.parse("bench 3x8 at 900").isComplete) // 900 kg: not believed
    }

    @Test
    fun `lines as speech-to-text writes them`() {
        check("bench three sets of eight at sixty", "bench", kg(60.0, 8, 3))
        check("Squat 3 by 5 at 100 kilos", "squat", kg(100.0, 5, 3))
        check("curls 12 and a half for 10", "curls", kg(12.5, 10))
        check("ohp thirty seven point five for eight", "ohp", kg(37.5, 8))
        check("deadlift a hundred and forty for five", "deadlift", kg(140.0, 5))
        check("plank three by forty-five seconds", "plank", secs(45, 3))
        check("Bench 60 for 8.", "bench", kg(60.0, 8))
        assertEquals(
            CardioValues(1_200, inclinePct = 6.0, distanceKm = 2.0),
            QuickLogParser.parse("treadmill twenty minutes six percent incline two kilometers").cardio,
        )
    }

    @Test
    fun `number words that aren't counts stay words`() {
        assertEquals("one arm dumbbell row 3 x 10", QuickLogParser.withDigits("One arm dumbbell row three x ten"))
        assertEquals("ohp 40 for 6, last one 37.5 for 8", QuickLogParser.withDigits("ohp forty for six, last one thirty seven and a half for eight"))
    }
}
