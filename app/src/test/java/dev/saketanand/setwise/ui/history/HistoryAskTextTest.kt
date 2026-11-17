package dev.saketanand.setwise.ui.history

import dev.saketanand.setwise.domain.model.AnsweredSet
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryAskTextTest {

    private fun set(weightKg: Double? = null, reps: Int? = null, seconds: Int? = null) =
        AnsweredSet("Treadmill", 1, weightKg, reps, seconds, isPr = false)

    @Test
    fun `a cardio time reads like the app's other times, a short hold in seconds`() {
        assertEquals("30:05", set(seconds = 1_805).short(Locale.UK)) // was "1805 s" (#135)
        assertEquals("1:02:00", set(seconds = 3_720).short(Locale.UK))
        assertEquals("45 s", set(seconds = 45).short(Locale.UK))
        assertEquals("100 × 5", set(100.0, 5).short(Locale.UK))
        assertEquals("12 reps", set(reps = 12).short(Locale.UK))
    }
}
