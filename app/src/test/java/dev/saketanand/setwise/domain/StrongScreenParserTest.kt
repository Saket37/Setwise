package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.OcrLine
import dev.saketanand.setwise.domain.model.SharedSet
import dev.saketanand.setwise.domain.model.StrongScreenParser
import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Strong's workout screen as ML Kit reads it on a 1080 × 2424 screenshot: the positions are a real
 * capture's (#126), the workout is made up.
 */
class StrongScreenParserTest {

    private val screen = listOf(
        line("8:41", 93, 73, 164, 101),
        line("Morning Workout", 49, 420, 703, 504),
        line("Tuesday, 6 October 2026 at 7:10 am", 43, 605, 666, 640),
        line("Squat (Barbell)", 44, 693, 400, 732),
        line("1RM", 875, 699, 946, 726),
        line("1 60 kg x 8", 59, 768, 269, 804),
        line("75", 874, 771, 914, 798),
        line("2 70 kg x 6", 44, 821, 269, 854),
        line("84", 873, 822, 913, 849),
        line("? 1RM ? Weight", 58, 874, 419, 917), // record badges, one line
        line("3 70 kg x 5", 44, 935, 269, 969),
        line("82", 873, 937, 913, 963),
        line("Pull Up", 45, 1253, 181, 1289),
        line("1 12 reps ? Reps", 56, 1324, 396, 1362),
        line("2 9 reps", 42, 1373, 220, 1412),
        line("Leg Press (Machine)", 45, 1447, 375, 1484),
        line("1RM", 875, 1449, 947, 1476),
        line("1 120 kg x 10", 56, 1518, 244, 1551),
        line("160", 874, 1521, 913, 1548),
        line("Weight", 306, 1578, 419, 1616), // badges read as two lines
        line("1RM", 118, 1582, 188, 1609),
        line("2 110 kg x 12", 44, 1634, 246, 1667),
        line("154", 874, 1635, 913, 1662),
        line("2410 kg", 373, 2046, 492, 2082),
        line("1h 12m", 139, 2048, 230, 2084),
        line("3 PRS", 623, 2052, 717, 2079),
        line("PERFORM AGAIN", 380, 2211, 702, 2238),
    )

    @Test
    fun readsStrongsWorkoutScreen() {
        val workout = StrongScreenParser.parse(listOf(screen)).single()

        assertEquals("Morning Workout", workout.name)
        assertEquals(LocalDateTime.of(2026, 10, 6, 7, 10), workout.startedAt)
        assertEquals(Duration.ofMinutes(72), workout.duration) // from the footer, not estimated
        assertEquals(listOf("Squat (Barbell)", "Pull Up", "Leg Press (Machine)"), workout.exercises.map { it.name })
        assertEquals(listOf(SharedSet(60.0, 8), SharedSet(70.0, 6), SharedSet(70.0, 5)), workout.exercises[0].sets)
        assertEquals(listOf(SharedSet(reps = 12), SharedSet(reps = 9)), workout.exercises[1].sets)
        assertEquals(listOf(SharedSet(120.0, 10), SharedSet(110.0, 12)), workout.exercises[2].sets)
    }

    @Test
    fun aSecondScreenshotContinuesTheWorkoutWithoutCountingSetsTwice() {
        val top = screen.filter { it.top < 1500 } // up to Leg Press set 1
        val bottom = listOf(
            line("8:42", 93, 73, 164, 101),
            line("1 120 kg x 10", 56, 300, 244, 333), // shown on both screenshots
            line("160", 874, 303, 913, 330),
            line("2 110 kg x 12", 44, 380, 246, 413),
            line("154", 874, 381, 913, 408),
            line("Plank", 45, 480, 181, 516),
            line("1 1:30", 56, 560, 200, 596),
            line("1h 12m", 139, 900, 230, 936),
        )

        for (order in listOf(listOf(top, bottom), listOf(bottom, top))) {
            val workout = StrongScreenParser.parse(order).single()
            assertEquals(listOf("Squat (Barbell)", "Pull Up", "Leg Press (Machine)", "Plank"), workout.exercises.map { it.name })
            assertEquals(listOf(SharedSet(120.0, 10), SharedSet(110.0, 12)), workout.exercises[2].sets)
            assertEquals(listOf(SharedSet(seconds = 90)), workout.exercises[3].sets)
            assertEquals(Duration.ofMinutes(72), workout.duration)
        }
    }

    @Test
    fun strongsSharedTextIsLeftToTheTextReader() {
        val sharedText = listOf(
            line("Morning Workout", 0, 10, 300, 40),
            line("Tuesday, 6 October 2026 at 7:10 am", 0, 60, 500, 90),
            line("Squat (Barbell)", 0, 110, 300, 140),
            line("Set 1: 60 kg × 8 reps", 0, 160, 300, 190),
        )
        assertTrue(StrongScreenParser.parse(listOf(sharedText)).isEmpty())
    }

    private fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) = OcrLine(text, left, top, right, bottom)
}
