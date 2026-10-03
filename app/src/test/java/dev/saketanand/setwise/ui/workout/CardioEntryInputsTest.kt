package dev.saketanand.setwise.ui.workout

import dev.saketanand.setwise.domain.model.CardioMetric
import dev.saketanand.setwise.domain.model.CardioValues
import org.junit.Assert.assertEquals
import org.junit.Test

class CardioEntryInputsTest {

    private val treadmill = setOf(CardioMetric.DURATION, CardioMetric.INCLINE, CardioMetric.SPEED, CardioMetric.DISTANCE)
    private val elliptical = setOf(CardioMetric.DURATION, CardioMetric.DISTANCE, CardioMetric.LEVEL)
    private val last = CardioValues(1_800, inclinePct = 5.0, speedMinKmh = 5.5, speedMaxKmh = 7.5, distanceKm = 3.9)

    @Test
    fun `typed values are logged, only for the exercise's metrics`() {
        val result = parseCardio(
            CardioInputs(minutes = "32", seconds = "15", speedFrom = "6", speedTo = "8,5", distance = "4.2"),
            treadmill, inclinePct = 6.0, level = 9, lastTime = null,
        )

        assertEquals(CardioParseResult.Valid(CardioValues(32 * 60 + 15, 6.0, 6.0, 8.5, 4.2, level = null)), result)
    }

    @Test
    fun `empty fields use last time's values`() {
        val result = parseCardio(CardioInputs(distance = "4"), treadmill, inclinePct = 5.0, level = 1, lastTime = last)

        assertEquals(CardioParseResult.Valid(last.copy(distanceKm = 4.0)), result)
    }

    @Test
    fun `seconds alone or minutes alone are a duration`() {
        assertEquals(90, (parseCardio(CardioInputs(seconds = "90"), elliptical, 0.0, 5, null) as CardioParseResult.Valid).values.durationSec)
        val twenty = (parseCardio(CardioInputs(minutes = "20"), elliptical, 0.0, 5, null) as CardioParseResult.Valid).values
        assertEquals(CardioValues(1_200, distanceKm = null, level = 5), twenty)
    }

    @Test
    fun `no duration, or speeds the wrong way round, can't be logged`() {
        assertEquals(CardioParseResult.Invalid(CardioInputError.MissingDuration), parseCardio(CardioInputs(), elliptical, 0.0, 5, null))
        assertEquals(CardioParseResult.Invalid(CardioInputError.MissingDuration), parseCardio(CardioInputs(minutes = "0"), elliptical, 0.0, 5, last))
        assertEquals(
            CardioParseResult.Invalid(CardioInputError.SpeedOrder),
            parseCardio(CardioInputs(minutes = "30", speedFrom = "9", speedTo = "6"), treadmill, 0.0, 1, null),
        )
    }
}
