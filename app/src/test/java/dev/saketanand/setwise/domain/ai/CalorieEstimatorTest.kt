package dev.saketanand.setwise.domain.ai

import dev.saketanand.setwise.domain.model.CalorieEstimate
import dev.saketanand.setwise.domain.model.CalorieFormula
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.Intensity
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutSet
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieEstimatorTest {

    private val start = Instant.parse("2026-10-03T12:00:00Z")
    // 60 min, 18 sets, 90 s apart → formula: 350 kcal, moderate (70 kg).
    private val session = WorkoutSession(
        1, "Push Day", null, start, start.plusSeconds(3_600),
        listOf(
            SessionExercise(
                id = 1,
                exercise = Exercise(1, "Bench Press", ExerciseType.STRENGTH, "Chest", "Barbell", 120, false, false, null, null, null),
                sets = List(18) { i ->
                    WorkoutSet(i.toLong(), i + 1, 60.0, 8, null, isCompleted = true, isPr = false, completedAt = start.plusSeconds(90L * (i + 1)))
                },
                previousSets = emptyList(),
            ),
        ),
    )
    private val formula = SourcedCalorieEstimate(CalorieEstimate(350, Intensity.Moderate), CalorieFormula.SOURCE)

    @Test
    fun `without a model, the formula`() = runTest {
        val model = FakeOnDeviceModel(ModelAvailability.Downloadable)

        assertEquals(formula, CalorieEstimator(model).estimate(session, 70.0))
        assertTrue(model.requests.isEmpty())
    }

    @Test
    fun `a plausible number from the model is used, with the formula's intensity`() = runTest {
        // Extra keys (an intensity the model wasn't asked for) are ignored: intensity is the rule's.
        val model = FakeOnDeviceModel(ModelAvailability.Ready, answer = { "```json\n{\"kcal\": 410, \"intensity\": \"Vigorous\"}\n```" })

        assertEquals(
            SourcedCalorieEstimate(CalorieEstimate(410, Intensity.Moderate), CalorieEstimator.MODEL_SOURCE),
            CalorieEstimator(model).estimate(session, 70.0),
        )
    }

    @Test
    fun `an implausible, malformed or failed answer falls back to the formula`() = runTest {
        val model = FakeOnDeviceModel(ModelAvailability.Ready)
        val estimator = CalorieEstimator(model)

        model.answer = { """{"kcal": 1200}""" } // over 1.5 × 350
        assertEquals(formula, estimator.estimate(session, 70.0))
        model.answer = { """{"kcal": 150}""" } // under 0.6 × 350
        assertEquals(formula, estimator.estimate(session, 70.0))
        model.answer = { """{"kcal": "lots"}""" }
        assertEquals(formula, estimator.estimate(session, 70.0))
        model.answer = { "About 330 calories!" }
        assertEquals(formula, estimator.estimate(session, 70.0))
        model.answer = { error("AICore busy") }
        assertEquals(formula.copy(modelFailed = true), estimator.estimate(session, 70.0)) // so the caller can stop asking
    }

    @Test
    fun `no body weight, no estimate`() = runTest {
        assertNull(CalorieEstimator(FakeOnDeviceModel(ModelAvailability.Ready, answer = { "{}" })).estimate(session, null))
    }

    @Test
    fun `a known BMR is in the prompt and in the formula's number`() {
        val request = CalorieEstimator.prompt(session, 70.0, formula.estimate, bmrKcal = 1_649)
        assertTrue(request.prompt, "Body weight: 70 kg\nResting burn (BMR): 1649 kcal/day\n" in request.prompt)
    }

    @Test
    fun `the prompt gives the model the sets, rest times and the formula's number`() {
        val request = CalorieEstimator.prompt(session, 70.0, formula.estimate)
        val log = request.prompt.substringAfter("## Workout\n<workout>\n").substringBefore("\n</workout>")

        assertTrue(request.prompt, request.prompt.startsWith("## Examples\n<workout>"))
        assertEquals(
            listOf(
                "Body weight: 70 kg",
                "Length: 60 min",
                "Completed sets: 18 (18 per hour)",
                "Bench Press: " + List(10) { "60kg x8" }.joinToString(", "), // capped at 10 sets
                "Rest between sets: median 1:30 (17 rests)",
                "Formula estimate: 350 kcal, moderate",
            ),
            log.lines(),
        )
        // Short enough for a system instruction (the guide: under ~150 words), at low temperature.
        assertTrue(request.system.split(" ").size < 150)
        assertEquals(0.2f, request.temperature)
    }
}
