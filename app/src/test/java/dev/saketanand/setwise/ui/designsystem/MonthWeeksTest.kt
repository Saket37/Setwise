package dev.saketanand.setwise.ui.designsystem

import dev.saketanand.setwise.ui.designsystem.components.monthWeeks
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthWeeksTest {

    @Test
    fun `October 2026 starts on a Thursday, so a Monday-first grid has three blanks`() {
        val weeks = monthWeeks(YearMonth.of(2026, 10), DayOfWeek.MONDAY)

        assertEquals(5, weeks.size)
        assertEquals(listOf(null, null, null), weeks.first().take(3))
        assertEquals(LocalDate.of(2026, 10, 1), weeks.first()[3])
        assertEquals(LocalDate.of(2026, 10, 31), weeks.last()[5]) // a Saturday
        assertNull(weeks.last()[6])
        weeks.forEach { assertEquals(7, it.size) }
    }

    @Test
    fun `a month starting on the first day of the week has no leading blanks`() {
        // 1 Feb 2026 is a Sunday.
        val weeks = monthWeeks(YearMonth.of(2026, 2), DayOfWeek.SUNDAY)

        assertEquals(LocalDate.of(2026, 2, 1), weeks.first().first())
        assertEquals(4, weeks.size) // 28 days, exactly four weeks
    }
}
