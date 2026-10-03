package dev.saketanand.setwise.domain.ai

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseCatalog
import dev.saketanand.setwise.domain.model.ExerciseGuess
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseAssistantTest {

    private val library = listOf(
        "Bench Press (Barbell)", "Bench Press (Dumbbell)", "Incline Dumbbell Press", "Chest Fly (Dumbbell)", "Plank",
    ).mapIndexed { i, name -> Exercise(i + 1L, name, ExerciseType.STRENGTH, "Chest", "Barbell", 90, false, false, null, null, null) }

    @Test
    fun `the same words in any order match without asking the model`() = runTest {
        val model = FakeOnDeviceModel(ModelAvailability.Ready)

        assertEquals("Bench Press (Dumbbell)", ExerciseAssistant(model).findMatch("dumbbell bench press", library)?.name)
        assertTrue(model.requests.isEmpty())
    }

    @Test
    fun `a close candidate matches without asking the model`() = runTest {
        val model = FakeOnDeviceModel(ModelAvailability.Ready, answer = { """{"choice": 0}""" })

        assertEquals("Bench Press (Dumbbell)", ExerciseAssistant(model).findMatch("db flat bench", library)?.name)
        assertTrue(model.requests.isEmpty())
    }

    @Test
    fun `in the uncertain middle the model picks from the shortlist, or none`() = runTest {
        val model = FakeOnDeviceModel(ModelAvailability.Ready, answer = { """{"choice": 2}""" })
        val assistant = ExerciseAssistant(model)

        assertEquals("Chest Fly (Dumbbell)", assistant.findMatch("incline db fly", library)?.name)
        val prompt = model.requests.single().prompt
        assertTrue(prompt, prompt.endsWith("<typed>incline db fly</typed>\n<library>\n1. Incline Dumbbell Press\n2. Chest Fly (Dumbbell)\n3. Bench Press (Dumbbell)\n</library>"))

        model.answer = { """{"choice": 0}""" }
        assertNull(assistant.findMatch("incline db fly", library))
        model.answer = { """{"choice": 9}""" } // out of range: as if no answer
        assertNull(assistant.findMatch("incline db fly", library))
    }

    @Test
    fun `without the model only a close candidate counts`() = runTest {
        val assistant = ExerciseAssistant(FakeOnDeviceModel(ModelAvailability.Unavailable))

        assertEquals("Chest Fly (Dumbbell)", assistant.findMatch("dumbbell flys", library)?.name)
        assertNull(assistant.findMatch("incline db fly", library)) // shares words, but not close enough
        assertEquals("Bench Press (Barbell)", assistant.findMatch("bench", library)?.name) // a name containing it, as before
    }

    @Test
    fun `keywords win on what they recognise and the model fills the rest`() = runTest {
        val model = FakeOnDeviceModel(
            ModelAvailability.Ready,
            answer = { """{"muscleGroup": "Back", "logging": "weight and reps", "equipment": "Barbell"}""" },
        )
        val assistant = ExerciseAssistant(model)

        // Keywords know Quads only: the model adds weight × reps and the barbell, not its "Back".
        assertEquals(
            ExerciseSuggestion(ExerciseGuess("Quads", ExerciseType.STRENGTH, false, "Barbell"), SuggestionSource.Model),
            assistant.suggestDetails("zercher squat"),
        )
        // Keywords know everything: the model isn't asked.
        model.requests.clear()
        assertEquals(SuggestionSource.Keywords, assistant.suggestDetails("flat db press")?.source)
        assertTrue(model.requests.isEmpty())

        model.answer = { """{"muscleGroup": "Legs", "logging": "weight and reps", "equipment": "Barbell"}""" } // not a choice
        assertEquals(ExerciseSuggestion(ExerciseGuess("Quads"), SuggestionSource.Keywords), assistant.suggestDetails("zercher squat"))

        model.availability = ModelAvailability.Downloadable
        assertEquals(
            ExerciseSuggestion(ExerciseGuess("Core", ExerciseType.BODYWEIGHT, true, "Bodyweight"), SuggestionSource.Keywords),
            assistant.suggestDetails("side plank"),
        )
        assertNull(assistant.suggestDetails("jefferson thing")) // nothing recognised, no model
    }

    @Test
    fun `the structured output's choices are the app's`() {
        val guides = ModelExerciseDetails_GeneratedProvider.generableDetail.guideDetails.associateBy { it.name }
        assertEquals(ExerciseCatalog.MUSCLE_GROUPS, guides.getValue("muscleGroup").enumValues?.toList())
        assertEquals(ExerciseCatalog.EQUIPMENT, guides.getValue("equipment").enumValues?.toList())
    }
}
