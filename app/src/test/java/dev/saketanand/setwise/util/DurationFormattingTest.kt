package dev.saketanand.setwise.util

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DurationFormattingTest {

    @Test
    fun `short duration label`() {
        assertEquals("0m", 0.minutes.toShortDurationLabel())
        assertEquals("45m", 45.minutes.toShortDurationLabel())
        assertEquals("2h", 2.hours.toShortDurationLabel())
        assertEquals("3h 24m", (3.hours + 24.minutes).toShortDurationLabel())
    }

    @Test
    fun `clock label`() {
        assertEquals("0:00", 0.seconds.toClockLabel())
        assertEquals("4:05", (4.minutes + 5.seconds).toClockLabel())
        assertEquals("12:34", (12.minutes + 34.seconds).toClockLabel())
        assertEquals("1:02:03", (1.hours + 2.minutes + 3.seconds).toClockLabel())
    }
}
