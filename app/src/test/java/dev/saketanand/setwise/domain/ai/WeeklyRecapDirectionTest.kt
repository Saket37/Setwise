package dev.saketanand.setwise.domain.ai

import dev.saketanand.setwise.domain.model.BestSetFact
import dev.saketanand.setwise.domain.model.BodyPart
import dev.saketanand.setwise.domain.model.BodyPartChange
import dev.saketanand.setwise.domain.model.PlateauFact
import dev.saketanand.setwise.domain.model.WeekFacts
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The recap can't say a lift went the wrong way (#144). */
class WeeklyRecapDirectionTest {

    private val up = WeekFacts(
        LocalDate.of(2026, 9, 28), 4, Duration.ofMinutes(274), 2, 38_200.0, 8,
        BestSetFact("Deadlift (Barbell)", 140.0, 5, null, isPr = true), BodyPartChange(BodyPart.Back, 12), PlateauFact(2, "Overhead Press (Barbell)", 4),
    )

    @Test
    fun `a best lift said to be lost, or a stalled one said to improve, isn't used`() {
        // As reported: "deadlift improved significantly … regain lost ground".
        assertFalse(WeeklyRecapWriter.directionsAgree("Your deadlift improved significantly to 140 kg; keep at it to regain lost ground.", up))
        assertFalse(WeeklyRecapWriter.directionsAgree("Overhead press is improving, so keep its plan.", up))
        // Nothing went down this week, so nothing may be said to.
        assertFalse(WeeklyRecapWriter.directionsAgree("Volume dropped a little, but you set 2 records.", up))

        assertTrue(
            WeeklyRecapWriter.directionsAgree(
                "A strong week: 4 sessions and 2 records, led by a 140 kg deadlift. Overhead press is still flat, so try its lighter, higher-rep plan.",
                up,
            ),
        )
    }

    @Test
    fun `when something did go down, the recap may say so`() {
        val down = up.copy(volumeChangePercent = -5, bodyPartChange = BodyPartChange(BodyPart.Back, -12))
        assertTrue(WeeklyRecapWriter.directionsAgree("Volume was down 5% on the week before, but your deadlift hit 140 kg.", down))
        assertFalse(WeeklyRecapWriter.directionsAgree("Your deadlift dropped this week.", down)) // the best lift still didn't
    }

    @Test
    fun `the facts give each change as a direction in words`() {
        val lines = WeeklyRecapWriter.factLines(up.copy(volumeChangePercent = -5)).lines()
        assertEquals("Volume: 38200 kg, down 5% on the week before", lines[1])
        assertEquals("Back volume: up 12% on the week before", lines[3])
    }

    @Test
    fun `a recap that contradicts itself falls back to the card's own wording`() = runTest {
        val model = FakeOnDeviceModel(ModelAvailability.Ready, answer = { "Your deadlift improved to 140 kg, so regain lost ground on overhead press." })
        assertNull(WeeklyRecapWriter(model).write(up))
    }
}
