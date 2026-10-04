package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.SharedExercise
import dev.saketanand.setwise.domain.model.SharedSet
import dev.saketanand.setwise.domain.model.StrongShareParser
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrongShareParserTest {

    /** As shared from Strong (free plan), trimmed to three exercises. */
    private val evening = """
        Evening Workout
        Wednesday, 30 September 2026 at 8:01 pm

        Bicep Curl (Barbell)
        Set 1: 10 kg × 15 reps
        Set 2: 15 kg × 15 reps
        Set 3: 20 kg × 15 reps


        Hammer Curl (Dumbbell)
        Set 1: 7.5 kg × 15 reps
        Set 2: 7.5 kg × 15 reps
        Set 3: 10 kg × 10 reps


        Triceps Pushdown (Cable - Straight Bar)
        Set 1: 25 kg × 15 reps
        Set 2: 25 kg × 15 reps
        Set 3: 30 kg × 12 reps

        https://link.strong.app/xcrznaph
    """.trimIndent()

    @Test
    fun `a shared Strong workout`() {
        val workout = StrongShareParser.parse(evening).single()

        assertEquals("Evening Workout", workout.name)
        assertEquals(LocalDateTime.of(2026, 9, 30, 20, 1), workout.startedAt)
        assertEquals(listOf("Bicep Curl (Barbell)", "Hammer Curl (Dumbbell)", "Triceps Pushdown (Cable - Straight Bar)"), workout.exercises.map { it.name })
        assertEquals(
            listOf(SharedSet(7.5, 15), SharedSet(7.5, 15), SharedSet(10.0, 10)),
            workout.exercises[1].sets,
        )
        assertEquals(9, workout.setCount)
    }

    @Test
    fun `several pasted together, bodyweight, weighted, assisted, holds, cardio and pounds`() {
        val text = evening + "\n\n" + """
            Morning Workout
            Thursday, 1 October 2026 at 7:15 am

            Pull Up
            Set 1: 10 reps
            Set 2: +10 kg × 6 reps
            Set 3: -20 kg × 8 reps

            Plank
            Set 1: 1:30

            Treadmill
            Set 1: 2.5 km · 15:00

            Bench Press (Barbell)
            Set 1: 135 lb × 8 reps
        """.trimIndent()

        val workouts = StrongShareParser.parse(text)

        assertEquals(2, workouts.size)
        val morning = workouts[1]
        assertEquals(LocalDateTime.of(2026, 10, 1, 7, 15), morning.startedAt)
        assertEquals(
            listOf(
                SharedExercise("Pull Up", listOf(SharedSet(reps = 10), SharedSet(10.0, 6), SharedSet(null, 8))),
                SharedExercise("Plank", listOf(SharedSet(seconds = 90))),
                SharedExercise("Treadmill", listOf(SharedSet(seconds = 900, distanceKm = 2.5))),
                SharedExercise("Bench Press (Barbell)", listOf(SharedSet(61.2, 8))),
            ),
            morning.exercises,
        )
    }

    @Test
    fun `text that isn't a shared workout gives nothing`() {
        assertTrue(StrongShareParser.parse("bench 3x8 at 60").isEmpty())
        assertEquals(LocalDateTime.of(2026, 9, 30, 0, 5), StrongShareParser.dateTime("30 Sep 2026 at 12:05 am"))
    }
}
