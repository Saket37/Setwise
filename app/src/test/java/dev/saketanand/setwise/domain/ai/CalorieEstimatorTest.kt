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
    fun `a plausible answer from the model is used`() = runTest {
        val model = FakeOnDeviceModel(ModelAvailability.Ready, answer = { "```json\n{\"kcal\": 410, \"intensity\": \"Vigorous\"}\n```" })

        assertEquals(
            SourcedCalorieEstimate(CalorieEstimate(410, Intensity.Vigorous), CalorieEstimator.MODEL_SOURCE),
            CalorieEstimator(model).estimate(session, 70.0),
        )
    }

    @Test
    fun `an implausible, malformed or failed answer falls back to the formula`() = runTest {
        val model = FakeOnDeviceModel(ModelAvailability.Ready)
        val estimator = CalorieEstimator(model)

        model.answer = { """{"kcal": 1200, "intensity": "vigorous"}""" } // over 1.5 × 350
        assertEquals(formula, estimator.estimate(session, 70.0))
        model.answer = { """{"kcal": 150, "intensity": "light"}""" } // under 0.6 × 350
        assertEquals(formula, estimator.estimate(session, 70.0))
        model.answer = { """{"kcal": 330, "intensity": "extreme"}""" }
        assertEquals(formula, estimator.estimate(session, 70.0))
        model.answer = { "About 330 calories!" }
        assertEquals(formula, estimator.estimate(session, 70.0))
        model.answer = { error("AICore busy") }
        assertEquals(formula, estimator.estimate(session, 70.0))
    }

    @Test
    fun `no body weight, no estimate`() = runTest {
        assertNull(CalorieEstimator(FakeOnDeviceModel(ModelAvailability.Ready, answer = { "{}" })).estimate(session, null))
    }

    @Test
    fun `the prompt gives the model the sets, rest times and the formula's number`() {
        val prompt = CalorieEstimator.prompt(session, 70.0, formula.estimate).prompt

        assertTrue(prompt, "Body weight: 70 kg" in prompt)
        assertTrue(prompt, "Workout length: 60 min" in prompt)
        assertTrue(prompt, "Bench Press: 60kg x8, 60kg x8" in prompt)
        assertTrue(prompt, "Rest between sets: median 1:30 (17 rests)" in prompt)
        assertTrue(prompt, "Formula estimate: 350 kcal, moderate" in prompt)
    }
}
