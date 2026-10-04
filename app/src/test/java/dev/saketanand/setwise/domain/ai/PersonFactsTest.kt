package dev.saketanand.setwise.domain.ai

import dev.saketanand.setwise.domain.model.BestSetFact
import dev.saketanand.setwise.domain.model.Intensity
import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.domain.model.WeekFacts
import dev.saketanand.setwise.domain.model.WorkoutFacts
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonFactsTest {

    private val week = WeekFacts(
        LocalDate.of(2026, 9, 28), 3, Duration.ofMinutes(190), 1, 21_400.0, 6,
        BestSetFact("Back Squat", 100.0, 5, null, isPr = true), null, null,
    )

    @Test
    fun `the profile gives the first name and planned days, when set`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
        assertEquals(PersonFacts("Saket", 4), PersonFacts.from(UserSettings(name = "  Saket Anand ", trainingDays = days)))
        assertEquals(PersonFacts(), PersonFacts.from(UserSettings(name = "   ")))
    }

    @Test
    fun `the weekly recap's facts start with the name and the plan`() {
        assertEquals(
            listOf(
                "Name: Saket",
                "Plan: 3 of 4 planned training days done (1 missed)",
                "Week: 3 workouts, 3 h 10 min trained, 1 personal records",
            ),
            WeeklyRecapWriter.factLines(week, PersonFacts("Saket", 4)).lines().take(3),
        )
        // Without a profile, the facts are as before.
        assertTrue(WeeklyRecapWriter.factLines(week).startsWith("Week: "))
    }

    @Test
    fun `a recap that compares the week with the plan is kept`() = runTest {
        val recap = "Saket, you trained 3 of your 4 planned days, 1 missed, with a 100 kg squat record. Volume was up 6% on the week before."
        val model = FakeOnDeviceModel(ModelAvailability.Ready, answer = { recap })

        assertEquals(recap, WeeklyRecapWriter(model).write(week, PersonFacts("Saket", 4)))
        assertTrue(model.requests.single().prompt.contains("Plan: 3 of 4 planned training days done (1 missed)"))
        assertTrue(model.requests.single().system.contains("never write about them by name"))
    }

    @Test
    fun `the week against the plan is worked out in code`() {
        assertEquals("Plan: all 4 planned training days done", WeeklyRecapWriter.planLine(4, 4))
        assertEquals("Plan: 2 of 4 planned training days done (2 missed)", WeeklyRecapWriter.planLine(2, 4))
        assertEquals("Plan: 5 workouts, 1 more than the 4 planned", WeeklyRecapWriter.planLine(5, 4))
    }

    @Test
    fun `the workout insight's facts start with the name`() {
        val facts = WorkoutFacts(
            workoutName = "Push Day", minutes = 60, intensity = Intensity.Moderate, medianRestSec = 90,
            changes = emptyList(), records = emptyList(), volumeKg = 5_000.0, previous = null,
        )
        assertEquals("Name: Saket", WorkoutInsightWriter.factLines(facts, PersonFacts("Saket")).lines().first())
        assertTrue(WorkoutInsightWriter.factLines(facts).startsWith("Workout: Push Day"))
    }
}
