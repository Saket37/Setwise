package dev.saketanand.setwise.ui.history

import dev.saketanand.setwise.domain.model.WorkoutHistoryItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
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
        assertEquals(listOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 9, 30)), ui.days.filter { it.trained }.map { it.date })

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
    fun `jumping to a day finds its workout, or the nearest earlier one`() {
        // Items: [Oct header, W1 (2 Oct), Sep header, W2 (30 Sep), W3 (24 Sep)]
        val months = historyUi(listOf(workout(1, 2026, 10, 2), workout(2, 2026, 9, 30), workout(3, 2026, 9, 24)), today, zone).months

        assertEquals(1, listIndexFor(LocalDate.of(2026, 10, 2), months))
        assertEquals(3, listIndexFor(LocalDate.of(2026, 9, 30), months))
        assertEquals("rest day → the nearest earlier workout", 4, listIndexFor(LocalDate.of(2026, 9, 27), months))
        assertEquals("before the first workout → nothing", null, listIndexFor(LocalDate.of(2026, 9, 1), months))
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

    private fun workout(id: Long, y: Int, m: Int, d: Int, hour: Int = 18, minute: Int = 0): WorkoutHistoryItem {
        val start: Instant = LocalDateTime.of(y, m, d, hour, minute).atZone(zone).toInstant()
        return WorkoutHistoryItem(
            id = id, name = "W$id", startedAt = start, endedAt = start.plusSeconds(69 * 60),
            completedSets = 10, volumeKg = 5_000.0, distanceKm = 0.0, personalRecords = 0, calories = null,
        )
    }
}
