package dev.saketanand.setwise.ui.summary

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PersonalBests
import dev.saketanand.setwise.domain.model.PrKind
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutSet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSummaryUiMappersTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    @Test
    fun `stats count ticked-off sets only`() {
        val ui = session(
            exercise(1, "Bench", ExerciseType.STRENGTH, listOf(set(1, 60.0, 8), set(2, 62.5, 6), set(3, 100.0, 1, done = false))),
            exercise(2, "Pull-up", ExerciseType.BODYWEIGHT, listOf(set(4, null, 10))),
        ).toSummaryUi(zone)

        assertEquals(60 * 8 + 62.5 * 6, ui.volumeKg, 0.001)
        assertEquals(3, ui.completedSets)
        assertEquals(2, ui.exerciseCount)
        assertEquals(69.minutes, ui.duration)
        assertEquals(LocalTime.of(18, 42), ui.startTime)
        assertEquals(LocalTime.of(19, 51), ui.endTime)
    }

    @Test
    fun `the calorie estimate says where it came from`() {
        fun source(stored: String?) = session().copy(calories = 320, caloriesSource = stored).toSummaryUi(zone).caloriesSource
        assertEquals(CaloriesSourceUi.OnDevice, source("on_device_model"))
        assertEquals(CaloriesSourceUi.Formula, source("formula"))
        assertEquals(null, source("heart_rate")) // not one the app labels yet
        assertEquals(null, source(null))
    }

    @Test
    fun `a cardio entry isn't counted as a set`() {
        val ui = session(
            exercise(1, "Bench", ExerciseType.STRENGTH, listOf(set(1, 60.0, 8), set(2, 62.5, 6))),
            exercise(2, "Treadmill", ExerciseType.CARDIO, listOf(set(3, null, null))),
        ).toSummaryUi(zone)

        assertEquals(2, ui.completedSets)
        assertEquals(2, ui.exerciseCount)
    }

    @Test
    fun `each exercise shows its set count and best set`() {
        val ui = session(
            exercise(1, "Bench", ExerciseType.STRENGTH, listOf(set(1, 60.0, 8), set(2, 62.5, 6))),
            exercise(2, "Pull-up", ExerciseType.BODYWEIGHT, listOf(set(3, null, 10), set(4, null, 12))),
            exercise(3, "Row", ExerciseType.STRENGTH, listOf(set(5, 50.0, 8, done = false))), // nothing done: left out
        ).toSummaryUi(zone)

        assertEquals(
            listOf(SummaryExerciseUi(1, 1, "Bench", 2, "62.5 × 6"), SummaryExerciseUi(2, 2, "Pull-up", 2, "12")),
            ui.exercises,
        )
    }

    @Test
    fun `records list what was beaten`() {
        val ui = session(
            exercise(1, "Bench", ExerciseType.STRENGTH, listOf(set(1, 62.5, 8)), bests = PersonalBests.from(listOf(PreviousSet(60.0, 8)))),
        ).toSummaryUi(zone)

        val record = ui.records.single()
        assertEquals(PrKind.Weight, record.kind)
        assertEquals(PreviousSet(62.5, 8), record.achieved)
        assertEquals(PreviousSet(60.0, 8), record.previousBest)
    }

    @Test
    fun `only workouts not started from a template can be saved as one`() {
        val bench = exercise(1, "Bench", ExerciseType.STRENGTH, listOf(set(1, 60.0, 8)))
        assertTrue(session(bench).toSummaryUi(zone).canSaveAsTemplate)
        assertFalse(session(bench, templateId = 3).toSummaryUi(zone).canSaveAsTemplate)
    }

    @Test
    fun `edited times stay on the workout's day, an earlier end means past midnight`() {
        val date = LocalDate.of(2026, 10, 2)
        val now = at(2026, 10, 3, 12, 0)

        assertEquals(at(2026, 10, 2, 18, 30) to at(2026, 10, 2, 19, 45), editedTimes(date, LocalTime.of(18, 30), LocalTime.of(19, 45), zone, now))
        assertEquals(at(2026, 10, 2, 23, 30) to at(2026, 10, 3, 0, 40), editedTimes(date, LocalTime.of(23, 30), LocalTime.of(0, 40), zone, now))
    }

    @Test
    fun `an end in the future becomes now, and must still be after the start`() {
        val today = LocalDate.of(2026, 10, 3)
        val now = at(2026, 10, 3, 18, 0)

        assertEquals(at(2026, 10, 3, 17, 0) to now, editedTimes(today, LocalTime.of(17, 0), LocalTime.of(19, 0), zone, now))
        assertNull(editedTimes(today, LocalTime.of(18, 30), LocalTime.of(19, 0), zone, now))
    }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Instant = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant()

    private fun session(vararg exercises: SessionExercise, templateId: Long? = null) = WorkoutSession(
        id = 7, name = "Push Day", templateId = templateId,
        startedAt = at(2026, 10, 2, 18, 42), endedAt = at(2026, 10, 2, 19, 51),
        exercises = exercises.toList(),
    )

    private fun exercise(id: Long, name: String, type: ExerciseType, sets: List<WorkoutSet>, bests: PersonalBests = PersonalBests.None) =
        SessionExercise(
            id = id,
            exercise = Exercise(id, name, type, "Chest", "Barbell", 90, isTimed = false, isCustom = false, metrics = null, calorieMethod = null, met = null),
            sets = sets,
            previousSets = emptyList(),
            bestsBefore = bests,
        )

    private fun set(id: Long, weightKg: Double?, reps: Int?, done: Boolean = true) =
        WorkoutSet(id, id.toInt(), weightKg, reps, durationSec = null, isCompleted = done, isPr = false)
}
