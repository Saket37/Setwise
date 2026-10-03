package dev.saketanand.setwise.domain.ai

import dev.saketanand.setwise.domain.model.ExerciseChange
import dev.saketanand.setwise.domain.model.Intensity
import dev.saketanand.setwise.domain.model.Measure
import dev.saketanand.setwise.domain.model.PrKind
import dev.saketanand.setwise.domain.model.PreviousWorkout
import dev.saketanand.setwise.domain.model.RecordFact
import dev.saketanand.setwise.domain.model.SetFact
import dev.saketanand.setwise.domain.model.WorkoutFacts
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutInsightWriterTest {

    private val facts = WorkoutFacts(
        workoutName = "Push Day",
        minutes = 69,
        intensity = Intensity.Moderate,
        medianRestSec = 90,
        changes = listOf(ExerciseChange("Bench Press (Barbell)", Measure.Weight, SetFact(62.5, 8, null), SetFact(60.0, 8, null))),
        records = listOf(RecordFact("Bench Press (Barbell)", PrKind.Weight)),
        volumeKg = 8_420.0,
        previous = PreviousWorkout("Push Day", 7_900.0),
    )

    @Test
    fun `the facts are one per line, with every number the model may use`() {
        assertEquals(
            listOf(
                "Workout: Push Day, 69 min, intensity moderate, rest about 1:30",
                "Bench Press (Barbell): best 62.5 kg x 8, last time 60 kg x 8 (+2.5 kg)",
                "Personal records: 1",
                "Volume: 8420 kg, last Push Day 7900 kg (+7%)",
            ),
            WorkoutInsightWriter.factLines(facts).lines(),
        )
    }

    @Test
    fun `a short recap using only the facts' numbers is kept`() = runTest {
        val recap = "Bench Press went up 2.5 kg to 62.5 kg x 8, your heaviest yet. Volume was 7% above last Push Day."
        val model = FakeOnDeviceModel(ModelAvailability.Ready, answer = { "\"$recap\"\n" })

        assertEquals(recap, WorkoutInsightWriter(model).write(facts))
    }

    @Test
    fun `an invented number, a list, or a long answer is rejected`() {
        val lines = WorkoutInsightWriter.factLines(facts)

        assertNull(WorkoutInsightWriter.accept("Bench Press went up to 65 kg.", lines)) // 65 isn't a fact
        assertNull(WorkoutInsightWriter.accept("- Bench up 2.5 kg\n- Volume +7%", lines))
        assertNull(WorkoutInsightWriter.accept("**Great** session!", lines))
        assertNull(WorkoutInsightWriter.accept("A. B. C. D. E.", lines)) // too many sentences
        assertNull(WorkoutInsightWriter.accept("x".repeat(400), lines))
        assertTrue(WorkoutInsightWriter.accept("A moderate 69 min session with about 1:30 rest.", lines) != null)
    }

    @Test
    fun `no model, or a failing one, writes nothing`() = runTest {
        assertNull(WorkoutInsightWriter(FakeOnDeviceModel(ModelAvailability.Downloadable)).write(facts))
        assertNull(WorkoutInsightWriter(FakeOnDeviceModel(ModelAvailability.Ready, answer = { error("BUSY") })).write(facts))
    }

    @Test
    fun `the prompt has one example and the facts in tags`() {
        val request = WorkoutInsightWriter.prompt(WorkoutInsightWriter.factLines(facts))

        assertTrue(request.prompt.startsWith("## Example\n<facts>"))
        assertTrue(request.prompt.endsWith("## Facts\n<facts>\n${WorkoutInsightWriter.factLines(facts)}\n</facts>"))
        assertTrue(request.system.split(" ").size < 150)
    }
}
