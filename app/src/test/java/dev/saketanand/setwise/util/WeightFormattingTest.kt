package dev.saketanand.setwise.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightFormattingTest {

    @Test
    fun `whole numbers have no decimals`() {
        assertEquals("60", 60.0.toWeightLabel(Locale.US))
        assertEquals("0", 0.0.toWeightLabel(Locale.US))
    }

    @Test
    fun `fractions keep only the digits they need`() {
        assertEquals("62.5", 62.5.toWeightLabel(Locale.US))
        assertEquals("1.25", 1.25.toWeightLabel(Locale.US))
    }

    @Test
    fun `no thousands separator`() {
        assertEquals("1200", 1200.0.toWeightLabel(Locale.US))
    }

    @Test
    fun `uses the locale's decimal separator`() {
        assertEquals("62,5", 62.5.toWeightLabel(Locale.GERMANY))
    }
}
