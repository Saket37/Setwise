package dev.saketanand.setwise.ui.exercises

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseSession
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.LoggedSet
import dev.saketanand.setwise.domain.model.ProgressionRule
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseDetailUiMappersTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 3) // a Saturday; this week starts Mon 28 Sep
    private val now = today.atTime(18, 0).atZone(zone).toInstant()

    @Test
    fun `strength shows the best estimated 1RM per week, last 8 weeks, oldest first`() {
        val ui = exerciseDetailUi(
            exercise(ExerciseType.STRENGTH),
            listOf(
                session(1, LocalDate.of(2026, 10, 2), LoggedSet(60.0, 6, null, null), LoggedSet(50.0, 12, null, null)),
                session(2, LocalDate.of(2026, 9, 29), LoggedSet(60.0, 3, null, null)),
                session(3, LocalDate.of(2026, 8, 12), LoggedSet(40.0, 10, null, null)), // week of 10 Aug: the first bar
                session(4, LocalDate.of(2026, 7, 1), LoggedSet(100.0, 1, null, null)), // older: not charted, still listed
            ),
            today,
            zone,
            now,
        )
        val progress = ui.progress!!

        assertEquals(ProgressMetric.EstimatedOneRepMax, progress.metric)
        assertEquals(LocalDate.of(2026, 8, 10), progress.firstWeek)
        assertEquals(8, progress.weeks.size)
        assertEquals(40.0 * (1 + 10 / 30.0), progress.weeks.first()!!, 0.001)
        // This week: 60 × 6 = 72 kg beats 50 × 12 = 70 and 60 × 3 = 66.
        assertEquals(72.0, progress.weeks.last()!!, 0.001)
        assertNull(progress.weeks[1])
        assertEquals(72.0, progress.latest!!, 0.001)
        assertEquals(listOf(1L, 2L, 3L, 4L), ui.sessions.map { it.workoutId })
    }

    @Test
    fun `bodyweight charts reps, timed charts the longest hold, cardio totals distance or minutes`() {
        val day = LocalDate.of(2026, 10, 1)
        fun metricOf(type: ExerciseType, isTimed: Boolean = false, vararg sets: LoggedSet) =
            exerciseDetailUi(exercise(type, isTimed), listOf(session(1, day, *sets)), today, zone, now).progress!!

        metricOf(ExerciseType.BODYWEIGHT, sets = arrayOf(LoggedSet(null, 10, null, null), LoggedSet(5.0, 12, null, null))).let {
            assertEquals(ProgressMetric.Reps, it.metric)
            assertEquals(12.0, it.latest!!, 0.001)
        }
        metricOf(ExerciseType.BODYWEIGHT, isTimed = true, sets = arrayOf(LoggedSet(null, null, 45, null), LoggedSet(null, null, 60, null))).let {
            assertEquals(ProgressMetric.Duration, it.metric)
            assertEquals(60.0, it.latest!!, 0.001)
        }
        metricOf(ExerciseType.CARDIO, sets = arrayOf(LoggedSet(null, null, 1_800, 5.0), LoggedSet(null, null, 600, 1.5))).let {
            assertEquals(ProgressMetric.Distance, it.metric)
            assertEquals(6.5, it.latest!!, 0.001)
        }
        metricOf(ExerciseType.CARDIO, sets = arrayOf(LoggedSet(null, null, 1_800, null))).let {
            assertEquals(ProgressMetric.Minutes, it.metric)
            assertEquals(30.0, it.latest!!, 0.001)
        }
    }

    @Test
    fun `a stalled lift shows its plateau and a lighter next session`() {
        // Twice a week since 9 Sep, never above an estimated 1RM of about 48 kg.
        val days = listOf(30, 26, 23, 19, 16, 12, 9).map { LocalDate.of(2026, 9, it) }
        val sessions = days.mapIndexed { index, day ->
            if (index % 2 == 0) session(index + 1L, day, *kg(40.0, 6, 6, 5)) else session(index + 1L, day, *kg(42.5, 4, 4, 3))
        }

        val ui = exerciseDetailUi(exercise(ExerciseType.STRENGTH), sessions, today, zone, now)

        assertEquals(PlateauUi(weeks = 3, sessions = 7, since = LocalDate.of(2026, 9, 9), best = 48, metric = ProgressMetric.EstimatedOneRepMax), ui.plateau)
        assertEquals(ProgressionRule.Lighter, ui.nextSession!!.rule)
        assertEquals(37.5 to 8, ui.nextSession!!.weightKg to ui.nextSession!!.reps)
    }

    @Test
    fun `never done has no chart, and done only long ago has an empty one`() {
        assertNull(exerciseDetailUi(exercise(ExerciseType.STRENGTH), emptyList(), today, zone, now).progress)

        val old = exerciseDetailUi(
            exercise(ExerciseType.STRENGTH),
            listOf(session(1, LocalDate.of(2026, 5, 1), LoggedSet(60.0, 5, null, null))),
            today,
            zone,
            now,
        )
        assertNull(old.progress!!.latest)
        assertEquals(List(8) { null }, old.progress!!.weeks)
    }

    private fun exercise(type: ExerciseType, isTimed: Boolean = false) =
        Exercise(7, "Overhead Press", type, "Shoulders", "Barbell", 90, isTimed, isCustom = false, metrics = null, calorieMethod = null, met = null)

    private fun kg(weight: Double, vararg reps: Int) = reps.map { LoggedSet(weight, it, null, null) }.toTypedArray()

    private fun session(workoutId: Long, day: LocalDate, vararg sets: LoggedSet) =
        ExerciseSession(workoutId, day.atTime(18, 0).atZone(zone).toInstant(), sets.toList())
}
