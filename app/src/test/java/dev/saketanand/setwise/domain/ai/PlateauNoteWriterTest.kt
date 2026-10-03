package dev.saketanand.setwise.domain.ai

import dev.saketanand.setwise.domain.model.ExerciseSession
import dev.saketanand.setwise.domain.model.LoggedSet
import dev.saketanand.setwise.domain.model.Measure
import dev.saketanand.setwise.domain.model.Plateau
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlateauNoteWriterTest {

    private val plateau = Plateau(Instant.parse("2026-09-07T12:00:00Z"), weeks = 4, sessions = 8, best = 48.2, measure = Measure.Weight)
    private val last = ExerciseSession(
        9, Instant.parse("2026-10-02T12:00:00Z"),
        listOf(LoggedSet(20.0, 10, null, null), LoggedSet(40.0, 6, null, null), LoggedSet(40.0, 6, null, null), LoggedSet(40.0, 5, null, null)),
    )
    private val model = FakeOnDeviceModel(availability = ModelAvailability.Ready)
    private val writer = PlateauNoteWriter(model)

    @Test
    fun `the facts are one per line, the last session's working sets only`() {
        assertEquals(
            listOf(
                "Exercise: Overhead Press",
                "Best: estimated 1RM about 48 kg, flat for 4 weeks",
                "Sessions in those weeks: 8 (about 2 a week)",
                "Last session: 40 kg x 6, 6, 5",
                "Ideas: a lighter week, or 8 to 10 reps a set for a month",
            ),
            PlateauNoteWriter.factLines("Overhead Press (Barbell)", plateau, last).lines(),
        )
    }

    @Test
    fun `a note is kept for the same facts, and one with an invented number isn't used`() = runTest {
        val note = "Your overhead press has held at about 48 kg for 4 weeks, even with 2 sessions a week. A lighter week, or 8 to 10 reps a set for a month, often gets it moving again."
        model.answer = { note }

        assertEquals(note, writer.write("Overhead Press (Barbell)", plateau, last))
        assertEquals(note, writer.cached("Overhead Press (Barbell)", plateau, last))
        assertEquals(note, writer.write("Overhead Press (Barbell)", plateau, last))
        assertEquals(1, model.requests.size) // asked once

        model.answer = { "Your overhead press has held at about 50 kg for 4 weeks." }
        assertNull(writer.write("Bench Press (Barbell)", plateau, last))
    }

    @Test
    fun `without the model there's no note`() = runTest {
        model.availability = ModelAvailability.Downloadable
        assertNull(writer.write("Overhead Press (Barbell)", plateau, last))
        assertEquals(0, model.requests.size)
    }
}
