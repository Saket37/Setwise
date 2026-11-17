package dev.saketanand.setwise.ui.history

import dev.saketanand.setwise.domain.model.DayState
import dev.saketanand.setwise.domain.model.DayStatus
import dev.saketanand.setwise.domain.model.TrainingPlan
import dev.saketanand.setwise.domain.model.WorkoutHistoryItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryUiMappersTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 3) // Saturday

    @Test
    fun `the day strip runs from today back to the first workout, at least four weeks`() {
        val ui = historyUi(listOf(workout(1, 2026, 10, 2), workout(2, 2026, 9, 30)), today, zone)

        assertEquals(today, ui.days.first().date)
        assertEquals(today.minusDays(MIN_STRIP_DAYS - 1L), ui.days.last().date)
        assertEquals(listOf(today), ui.days.filter { it.isToday }.map { it.date })
        assertEquals(listOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 9, 30)), ui.days.filter { it.state == DayState.Trained }.map { it.date })

        val old = historyUi(listOf(workout(1, 2026, 6, 1)), today, zone)
        assertEquals(LocalDate.of(2026, 6, 1), old.days.last().date)
    }

    @Test
    fun `the selected day is marked`() {
        val selected = LocalDate.of(2026, 9, 30)
        val ui = historyUi(listOf(workout(1, 2026, 9, 30)), today, zone, selectedDate = selected)

        assertEquals(listOf(selected), ui.days.filter { it.isSelected }.map { it.date })
        assertEquals(selected, ui.selectedDate)
    }

    @Test
    fun `jumping to a day finds its workout, a day without one doesn't move the list`() {
        // Items: [Oct header, W1 (2 Oct), Sep header, W2 (30 Sep), W3 (24 Sep)]
        val months = historyUi(listOf(workout(1, 2026, 10, 2), workout(2, 2026, 9, 30), workout(3, 2026, 9, 24)), today, zone).months

        assertEquals(1, listIndexOf(LocalDate.of(2026, 10, 2), months))
        assertEquals(4, listIndexOf(LocalDate.of(2026, 9, 24), months))
        assertEquals(null, listIndexOf(LocalDate.of(2026, 9, 27), months))
    }

    @Test
    fun `selecting a day without workouts is flagged for the card`() {
        val history = listOf(workout(1, 2026, 9, 30))
        assertTrue(historyUi(history, today, zone, selectedDate = LocalDate.of(2026, 9, 29)).isSelectedDayEmpty)
        assertEquals(false, historyUi(history, today, zone, selectedDate = LocalDate.of(2026, 9, 30)).isSelectedDayEmpty)
        assertEquals(false, historyUi(history, today, zone).isSelectedDayEmpty)
    }

    @Test
    fun `workouts are grouped by month, newest first`() {
        val ui = historyUi(listOf(workout(1, 2026, 10, 2), workout(2, 2026, 9, 30), workout(3, 2026, 9, 24)), today, zone)

        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9)), ui.months.map { it.month })
        assertEquals(listOf(2L, 3L), ui.months[1].workouts.map { it.id })
        assertEquals(69.minutes, ui.months[0].workouts.single().duration)
    }

    @Test
    fun `a workout late at night belongs to its local day`() {
        // 23:30 in India is still 18:00 UTC the same day; 00:30 India is the previous day in UTC.
        val ui = historyUi(listOf(workout(1, 2026, 10, 3, hour = 0, minute = 30)), today, zone)
        assertEquals(today, ui.months.single().workouts.single().date)
    }

    @Test
    fun `no workouts is empty, not loading`() {
        val ui = historyUi(emptyList(), today, zone)
        assertTrue(ui.isEmpty)
    }

    @Test
    fun `the strip marks rest, missed and unanswered days`() {
        // Sat 3 Oct; trains Mon / Wed / Fri; first workout Mon 28 Sep.
        val ui = historyUi(
            history = listOf(workout(1, 2026, 9, 28), workout(2, 2026, 9, 30)),
            today = today,
            zone = zone,
            marks = mapOf(LocalDate.of(2026, 10, 1) to DayStatus.Missed),
            plan = TrainingPlan(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
        )
        val state = ui.days.associate { it.date to it.state }

        assertEquals(DayState.None, state[today])
        assertEquals(DayState.Unanswered, state[LocalDate.of(2026, 10, 2)]) // a Friday, nothing said
        assertEquals(DayState.Missed, state[LocalDate.of(2026, 10, 1)]) // said missed on a Thursday
        assertEquals(DayState.Trained, state[LocalDate.of(2026, 9, 30)])
        assertEquals(DayState.Rest, state[LocalDate.of(2026, 9, 29)]) // not a training day
        assertEquals(DayState.None, state[LocalDate.of(2026, 9, 27)]) // before the first workout
    }

    @Test
    fun `an unanswered day offers the unanswered days around it, up to a workout or an answer`() {
        // Sat 3 Oct; no plan; workouts Thu 10 and Wed 30 Sep; Sun 20 Sep said missed.
        val history = listOf(workout(1, 2026, 9, 30), workout(2, 2026, 9, 10))
        val marks = mapOf(
            LocalDate.of(2026, 9, 20) to DayStatus.Missed,
            LocalDate.of(2026, 9, 22) to DayStatus.Rest,
            LocalDate.of(2026, 9, 24) to DayStatus.Rest,
        )
        fun run(selected: LocalDate) = historyUi(history, today, zone, selected, marks).unloggedRun

        val september = run(LocalDate.of(2026, 9, 15))!!
        assertEquals((11..19).map { LocalDate.of(2026, 9, it) }, september.days)
        assertEquals(LocalDate.of(2026, 9, 11), september.from)
        assertEquals(LocalDate.of(2026, 9, 19), september.to)
        // Up to yesterday: today isn't part of it.
        assertEquals(listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2)), run(LocalDate.of(2026, 10, 2))!!.days)
        // On its own between two answers: its own Rest / Missed are enough.
        assertNull(run(LocalDate.of(2026, 9, 23)))
        // A workout day, or an answered one: nothing to mark.
        assertNull(run(LocalDate.of(2026, 9, 30)))
        assertNull(run(LocalDate.of(2026, 9, 20)))
        assertNull(historyUi(history, today, zone, selectedDate = null, marks = marks).unloggedRun)
    }

    @Test
    fun `with training days a run passes rest days but only holds the planned ones`() {
        // Trains Mon / Wed / Fri; workouts Mon 7 and Wed 30 Sep; nothing said in between.
        val ui = historyUi(
            history = listOf(workout(1, 2026, 9, 30), workout(2, 2026, 9, 7)),
            today = today,
            zone = zone,
            selectedDate = LocalDate.of(2026, 9, 18),
            plan = TrainingPlan(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
        )

        assertEquals(listOf(9, 11, 14, 16, 18, 21, 23, 25, 28).map { LocalDate.of(2026, 9, it) }, ui.unloggedRun!!.days)
    }

    @Test
    fun `only an empty past day can be checked in`() {
        val history = listOf(workout(1, 2026, 9, 30))

        assertTrue(historyUi(history, today, zone, selectedDate = LocalDate.of(2026, 10, 1)).canCheckInSelectedDay)
        assertEquals(false, historyUi(history, today, zone, selectedDate = today).canCheckInSelectedDay)
        assertEquals(false, historyUi(history, today, zone, selectedDate = LocalDate.of(2026, 9, 30)).canCheckInSelectedDay)
    }

    @Test
    fun `month summary counts workouts and marked days, and the calendar spans the strip`() {
        val ui = historyUi(
            history = listOf(workout(1, 2026, 10, 1), workout(2, 2026, 9, 30), workout(3, 2026, 9, 28)),
            today = today,
            zone = zone,
            marks = mapOf(LocalDate.of(2026, 10, 2) to DayStatus.Missed, LocalDate.of(2026, 9, 29) to DayStatus.Rest),
        )

        assertEquals(MonthSummaryUi(workouts = 1, restDays = 0, missedDays = 1), monthSummary(YearMonth.of(2026, 10), ui))
        assertEquals(MonthSummaryUi(workouts = 2, restDays = 1, missedDays = 0), monthSummary(YearMonth.of(2026, 9), ui))
        assertEquals(YearMonth.of(2026, 9), ui.firstMonth) // 28 days back from 3 Oct
        assertEquals(YearMonth.of(2026, 10), ui.lastMonth)
    }

    private fun workout(id: Long, y: Int, m: Int, d: Int, hour: Int = 18, minute: Int = 0): WorkoutHistoryItem {
        val start: Instant = LocalDateTime.of(y, m, d, hour, minute).atZone(zone).toInstant()
        return WorkoutHistoryItem(
            id = id, name = "W$id", startedAt = start, endedAt = start.plusSeconds(69 * 60),
            completedSets = 10, volumeKg = 5_000.0, distanceKm = 0.0, personalRecords = 0, calories = null,
        )
    }
}
