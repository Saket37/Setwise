package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.ai.WeeklyRecapWriter
import dev.saketanand.setwise.domain.model.BestSetFact
import dev.saketanand.setwise.domain.model.BodyPart
import dev.saketanand.setwise.domain.model.BodyPartChange
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.LoggedSetRecord
import dev.saketanand.setwise.domain.model.PlateauFact
import dev.saketanand.setwise.domain.model.WeeklySummaryRules
import dev.saketanand.setwise.domain.model.WorkoutHistoryItem
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekFactsTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val week = LocalDate.of(2026, 9, 28)
    private val squat = Exercise(1, "Back Squat (Barbell)", ExerciseType.STRENGTH, "Quads", "Barbell", 180, false, false, null, null, null)
    private val ohp = Exercise(2, "Overhead Press (Barbell)", ExerciseType.STRENGTH, "Shoulders", "Barbell", 120, false, false, null, null, null)

    @Test
    fun `the week's numbers, best set, biggest body-part change and a stalled lift`() {
        // OHP twice a week for 4+ weeks at an estimated 1RM of about 48 kg; squats up this week.
        val ohpDays = listOf(30, 26, 23, 19, 16, 12, 9).map { LocalDate.of(2026, 9, it) } + LocalDate.of(2026, 10, 2)
        val log = (
            listOf(set(20, LocalDate.of(2026, 10, 1), squat, 100.0, 5, pr = true), set(21, LocalDate.of(2026, 9, 24), squat, 90.0, 5)) +
                ohpDays.mapIndexed { i, day -> set(100L + i, day, ohp, if (i % 2 == 0) 40.0 else 42.5, if (i % 2 == 0) 6 else 4) }
            ).sortedByDescending { it.startedAt }
        val history = listOf(
            item(20, LocalDate.of(2026, 10, 1), minutes = 70, prs = 1),
            item(101, LocalDate.of(2026, 10, 2), minutes = 50, prs = 0),
            item(21, LocalDate.of(2026, 9, 24), minutes = 60, prs = 0),
        )

        val facts = WeeklySummaryRules.facts(week, log, history, listOf(squat, ohp), zone)!!

        assertEquals(2, facts.workouts)
        assertEquals(Duration.ofMinutes(120), facts.timeTrained)
        assertEquals(1, facts.prs)
        assertEquals(100.0 * 5 + 40.0 * 6 + 42.5 * 4, facts.volumeKg, 0.01) // squat 1 Oct, OHP 30 Sep and 2 Oct
        assertEquals(BestSetFact("Back Squat (Barbell)", 100.0, 5, null, isPr = true), facts.bestSet)
        assertEquals(BodyPartChange(BodyPart.Legs, 11), facts.bodyPartChange) // 500 vs 450
        assertEquals(PlateauFact(2, "Overhead Press (Barbell)", 3), facts.plateau) // 9 Sep – 2 Oct
    }

    @Test
    fun `a week without workouts has no summary`() {
        assertNull(WeeklySummaryRules.facts(week, emptyList(), emptyList(), emptyList(), zone))
        assertEquals(LocalDate.of(2026, 9, 28), WeeklySummaryRules.lastWeekStart(LocalDate.of(2026, 10, 5)))
        assertEquals(LocalDate.of(2026, 9, 21), WeeklySummaryRules.lastWeekStart(LocalDate.of(2026, 10, 4)))
    }

    @Test
    fun `the recap's facts are one per line`() {
        val facts = dev.saketanand.setwise.domain.model.WeekFacts(
            week, 4, Duration.ofMinutes(274), 3, 38_200.0, 8,
            BestSetFact("Back Squat", 100.0, 5, null, isPr = true), BodyPartChange(BodyPart.Legs, 18), PlateauFact(2, "Overhead Press", 4),
        )
        assertEquals(
            listOf(
                "Week: 4 workouts, 4 h 34 min trained, 3 personal records",
                "Volume: 38200 kg, up 8% on the week before",
                "Best set: Back Squat 100 kg x 5 (a record)",
                "Legs volume: up 18% on the week before",
                "Stalled: Overhead Press, flat for 4 weeks (its plan: lighter weight, more reps)",
            ),
            WeeklyRecapWriter.factLines(facts).lines(),
        )
    }

    private fun set(workoutId: Long, day: LocalDate, exercise: Exercise, kg: Double, reps: Int, pr: Boolean = false) =
        LoggedSetRecord(workoutId, "W", day.atTime(18, 0).atZone(zone).toInstant(), exercise.id, exercise.name, exercise.muscleGroup, 1, kg, reps, null, null, pr)

    private fun item(id: Long, day: LocalDate, minutes: Long, prs: Int): WorkoutHistoryItem {
        val start = day.atTime(18, 0).atZone(zone).toInstant()
        return WorkoutHistoryItem(id, "W", start, start.plusSeconds(minutes * 60), 5, 0.0, 0.0, prs, null)
    }
}
