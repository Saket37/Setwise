package dev.saketanand.setwise.util

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DateFormattingTest {

    @Test
    fun `short day label matches the design`() {
        assertEquals("Fri, 2 Oct", LocalDate.of(2026, 10, 2).toShortDayLabel(Locale.ENGLISH))
    }

    @Test
    fun `single digit day has no leading zero, double digit day keeps both`() {
        assertEquals("Mon, 5 Oct", LocalDate.of(2026, 10, 5).toShortDayLabel(Locale.ENGLISH))
        assertEquals("Wed, 30 Sep", LocalDate.of(2026, 9, 30).toShortDayLabel(Locale.ENGLISH))
    }
}
