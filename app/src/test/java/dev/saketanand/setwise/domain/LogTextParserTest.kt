package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.LogTextParser
import dev.saketanand.setwise.domain.model.SharedSet
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogTextParserTest {

    @Test
    fun `hand-written logs, as in a notes app`() {
        val text = """
            Leg day - Tue 29/09/2026 18:30
            Squat: 100kg 5,5,5
            Romanian deadlift 80kg x 8, 8, 7
            Plank 60s, 45s

            2 Oct 2026 push
            bench press 60x8 62.5x6 62.5x6
            incline db press 22kg - 10, 9, 8
            lateral raises 10 kg 15 reps 3 sets

            Saturday 3rd October 2026, morning
            Pullups 10 8 6
            Barbell row 3 sets of 10 at 60
            felt great today!
        """.trimIndent()

        val result = LogTextParser.parse(text)
        val (legs, push, pull) = result.workouts

        assertEquals("Leg day", legs.name)
        assertEquals(LocalDateTime.of(2026, 9, 29, 18, 30), legs.startedAt)
        assertEquals(listOf("Squat", "Romanian deadlift", "Plank"), legs.exercises.map { it.name })
        assertEquals(List(3) { SharedSet(100.0, 5) }, legs.exercises[0].sets)
        assertEquals(listOf(SharedSet(seconds = 60), SharedSet(seconds = 45)), legs.exercises[2].sets)

        assertEquals("Push", push.name)
        assertEquals(LocalDateTime.of(2026, 10, 2, 12, 0), push.startedAt)
        assertEquals(listOf(SharedSet(60.0, 8), SharedSet(62.5, 6), SharedSet(62.5, 6)), push.exercises[0].sets)
        assertEquals(List(3) { SharedSet(10.0, 15) }, push.exercises[2].sets)

        assertEquals("Workout", pull.name)
        assertEquals(LocalDateTime.of(2026, 10, 3, 8, 0), pull.startedAt)
        assertEquals(listOf(SharedSet(reps = 10), SharedSet(reps = 8), SharedSet(reps = 6)), pull.exercises[0].sets)
        assertEquals(List(3) { SharedSet(60.0, 10) }, pull.exercises[1].sets)

        assertEquals(1, result.unreadLines) // "felt great today!"
    }

    @Test
    fun `no dated lines, no workouts`() {
        assertTrue(LogTextParser.parse("bench 3x8 at 60\nsquat 5x5 at 100").workouts.isEmpty())
    }
}
