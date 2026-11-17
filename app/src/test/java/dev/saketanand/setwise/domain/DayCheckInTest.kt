package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.DayCheckIn
import dev.saketanand.setwise.domain.model.DayState
import dev.saketanand.setwise.domain.model.DayStatus
import dev.saketanand.setwise.domain.model.TrainingPlan
import dev.saketanand.setwise.domain.model.UserSettings
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayCheckInTest {

    private val saturday = LocalDate.of(2026, 10, 3)
    private fun day(d: Int) = LocalDate.of(2026, 9, 30).plusDays(d - 30L) // day(30) = Wed 30 Sep, day(32) = Fri 2 Oct

    @Test
    fun `with training days set, only planned days without a workout or answer are asked about`() {
        val days = DayCheckIn.daysToAsk(
            today = saturday,
            trainedDays = setOf(day(30)), // Wed trained
            marks = emptyMap(),
            plan = TrainingPlan(setOf(MONDAY, WEDNESDAY, FRIDAY)),
            firstWorkoutDate = day(1),
        )
        // Last 7 days: Sat 26 Sep … Fri 2 Oct. Planned: Mon 28, Wed 30 (trained), Fri 2.
        assertEquals(listOf(day(32), day(28)), days)
    }

    @Test
    fun `without training days, every unlogged day of the last week is asked about, newest first`() {
        val days = DayCheckIn.daysToAsk(saturday, setOf(day(30)), mapOf(day(29) to DayStatus.Rest), TrainingPlan(), firstWorkoutDate = day(1))
        assertEquals(listOf(day(32), day(31), day(28), day(27), day(26)), days)
    }

    @Test
    fun `nothing is asked about before the first workout, or with no workouts at all`() {
        assertEquals(listOf(day(32)), DayCheckIn.daysToAsk(saturday, emptySet(), emptyMap(), TrainingPlan(), firstWorkoutDate = day(32)))
        assertTrue(DayCheckIn.daysToAsk(saturday, emptySet(), emptyMap(), TrainingPlan(), firstWorkoutDate = null).isEmpty())
    }

    @Test
    fun `asks at most once a day, not during a workout, not when switched off`() {
        val days = listOf(day(32))
        val on = UserSettings(askAboutUnloggedDays = true)
        assertTrue(DayCheckIn.shouldAsk(on, saturday, lastAskedOn = day(32), hasActiveWorkout = false, daysToAsk = days))
        assertFalse(DayCheckIn.shouldAsk(on, saturday, lastAskedOn = saturday, hasActiveWorkout = false, daysToAsk = days))
        assertFalse(DayCheckIn.shouldAsk(on, saturday, lastAskedOn = null, hasActiveWorkout = true, daysToAsk = days))
        assertFalse(DayCheckIn.shouldAsk(on.copy(askAboutUnloggedDays = false), saturday, null, false, days))
        assertFalse(DayCheckIn.shouldAsk(on, saturday, null, false, daysToAsk = emptyList()))
    }

    @Test
    fun `day states for the strip`() {
        val training = TrainingPlan(setOf(MONDAY, WEDNESDAY, FRIDAY))
        fun state(d: LocalDate) = DayCheckIn.stateOf(d, saturday, setOf(day(30)), mapOf(day(28) to DayStatus.Missed), training, firstWorkoutDate = day(26))

        assertEquals(DayState.Trained, state(day(30)))
        assertEquals(DayState.Missed, state(day(28)))
        assertEquals("not a training day", DayState.Rest, state(day(29)))
        assertEquals("planned, no answer", DayState.Unanswered, state(day(32)))
        assertEquals(DayState.None, state(saturday))
        assertEquals("before the first workout", DayState.None, state(day(25)))
    }

    @Test
    fun `with no fixed days, only a run of 4 unanswered quiet days is asked about`() {
        fun ask(trained: LocalDate, marks: Map<LocalDate, DayStatus> = emptyMap()) =
            DayCheckIn.daysToAsk(saturday, setOf(trained), marks, TrainingPlan(noFixedDays = true), firstWorkoutDate = day(1))

        // Trained Tue 29: Wed 30 – Fri 2 is 3 quiet days, not enough.
        assertEquals(emptyList<LocalDate>(), ask(trained = day(29)))
        // Trained Mon 28: Tue 29 – Fri 2 is 4, asked about newest first.
        assertEquals(listOf(day(32), day(31), day(30), day(29)), ask(trained = day(28)))
        // An answer resets the run: Wed 30 marked rest leaves Thu 1 – Fri 2.
        assertEquals(emptyList<LocalDate>(), ask(trained = day(28), marks = mapOf(day(30) to DayStatus.Rest)))
    }

    @Test
    fun `with no fixed days, History marks a quiet day only as part of a long enough run`() {
        fun state(trained: LocalDate) = DayCheckIn.stateOf(day(30), saturday, setOf(trained), emptyMap(), TrainingPlan(noFixedDays = true), day(1))
        assertEquals(DayState.None, state(trained = day(29)))
        assertEquals(DayState.Unanswered, state(trained = day(28)))
    }
}
