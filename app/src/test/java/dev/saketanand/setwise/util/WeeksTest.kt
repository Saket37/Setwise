package dev.saketanand.setwise.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeksTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val saturday = LocalDate.of(2026, 10, 3)

    @Test
    fun `week runs from Monday 00_00 to the next Monday`() {
        val (start, end) = weekRange(saturday, zone)
        assertEquals(LocalDateTime.of(2026, 9, 28, 0, 0), start.atZone(zone).toLocalDateTime())
        assertEquals(LocalDateTime.of(2026, 10, 5, 0, 0), end.atZone(zone).toLocalDateTime())
    }

    @Test
    fun `on a Monday the week starts that day`() {
        val monday = LocalDate.of(2026, 9, 28)
        assertEquals(monday.atStartOfDay(zone).toInstant(), weekRange(monday, zone).first)
    }

    @Test
    fun `week days are Monday to Sunday`() {
        val days = weekDays(LocalDate.of(2026, 10, 3)) // a Saturday
        assertEquals(LocalDate.of(2026, 9, 28), days.first())
        assertEquals(LocalDate.of(2026, 10, 4), days.last())
        assertEquals(DayOfWeek.MONDAY, days.first().dayOfWeek)
    }
}
