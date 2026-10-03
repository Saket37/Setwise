package dev.saketanand.setwise.ui.designsystem

import dev.saketanand.setwise.ui.designsystem.components.barFractions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarFractionsTest {

    @Test
    fun `the highest value is full height and the lowest still shows`() {
        val fractions = barFractions(listOf(40.0, null, 48.0))

        assertEquals(1f, fractions[2]!!, 0.001f)
        assertNull(fractions[1])
        // Axis starts at 32 (40 - the 8 kg spread): 40 → 8/16.
        assertEquals(0.5f, fractions[0]!!, 0.001f)
    }

    @Test
    fun `a tiny difference stays level, so a plateau doesn't look like a sawtooth`() {
        val fractions = barFractions(listOf(48.0, 48.17, 48.0))

        // Axis starts at 80% of 48.17 (not 47.83): 48 → 0.98, not 0.5.
        assertEquals(0.98f, fractions[0]!!, 0.01f)
        assertEquals(1f, fractions[1]!!, 0.001f)
    }

    @Test
    fun `equal values are full height, nothing logged is all empty`() {
        assertEquals(listOf(1f, 1f), barFractions(listOf(5.0, 5.0)))
        assertEquals(listOf(null, null), barFractions(listOf(null, null)))
    }
}
