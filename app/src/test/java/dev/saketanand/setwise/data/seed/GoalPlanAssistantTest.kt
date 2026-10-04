package dev.saketanand.setwise.data.seed

import dev.saketanand.setwise.data.mapper.toDomain
import dev.saketanand.setwise.data.mapper.toEntity
import dev.saketanand.setwise.domain.ai.GoalPlanAssistant
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.GoalPlanner
import dev.saketanand.setwise.domain.model.GoalReader
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The model choosing among each slot's options, on the real library. */
class GoalPlanAssistantTest {

    private val library: List<Exercise> = Json { ignoreUnknownKeys = true }
        .decodeFromString<ExerciseSeedFile>(File("src/main/assets/exercises.json").readText())
        .exercises.mapIndexed { i, dto -> dto.toEntity().copy(id = i + 1L).toDomain() }

    private val text = "build muscle 4 days a week, an hour, my knees don't like deep squats"
    private val goal = GoalReader.read(text)
    private val byCode = GoalPlanner.plan(goal, library)
    private val model = FakeOnDeviceModel(ModelAvailability.Ready)
    private val assistant = GoalPlanAssistant(model)

    /** The slots the model is asked about (from the prompt, after its example). */
    private fun slotLines() = model.requests.single().prompt.substringAfter("## Slots\n").lines().filter { it.startsWith("Slot ") }

    private fun slotCount() = slotLines().size

    @Test
    fun `the model's option numbers choose the exercises`() = runTest {
        model.answer = { """{"picks": [${List(40) { 2 }.joinToString()}]}""" } // too many: see below
        assistant.plan(text, goal, library, emptySet())
        val count = slotCount()
        // Lower day's squat (its first slot in the prompt): option 2 instead of 1, everything else 1.
        val lower = byCode[1]
        val firstLowerSlot = slotLines().indexOfFirst { "(Lower)" in it }
        model.requests.clear()
        model.answer = { """{"picks": [${List(count) { if (it == firstLowerSlot) 2 else 1 }.joinToString()}]}""" }

        val plan = assistant.plan(text, goal, library, emptySet())

        assertTrue(plan.byModel)
        val squat = plan.templates[1].exercises.first().exercise
        assertTrue(squat != lower.exercises.first().exercise)
        assertTrue(model.requests.single().prompt.contains("<goal>$text</goal>"))
    }

    @Test
    fun `only slots the goal talks about take the model's choice`() = runTest {
        assistant.plan(text, goal, library, emptySet())
        val count = slotCount()
        model.requests.clear()
        model.answer = { """{"picks": [${List(count) { 2 }.joinToString()}]}""" } // option 2 everywhere

        val plan = assistant.plan(text, goal, library, emptySet())

        // "knees … squats": the squat and lunge slots change; presses and pulls (not mentioned) don't.
        val changed = plan.templates.zip(byCode).flatMap { (a, b) -> a.exercises.zip(b.exercises) }.filter { (a, b) -> a.exercise != b.exercise }
        assertTrue(changed.isNotEmpty())
        assertTrue(changed.all { (_, code) -> code.exercise.muscleGroup in setOf("Quads", "Hamstrings", "Glutes") })
        assertEquals(byCode[0].exercises, plan.templates[0].exercises) // the upper day is untouched
    }

    @Test
    fun `a wrong count or an out-of-range number keeps code's plan`() = runTest {
        model.answer = { """{"picks": [1, 2]}""" }
        assertEquals(GoalPlanAssistant.Plan(byCode, byModel = false), assistant.plan(text, goal, library, emptySet()))

        val count = slotCount()
        model.requests.clear()
        model.answer = { """{"picks": [${List(count) { 9 }.joinToString()}]}""" }
        assertFalse(assistant.plan(text, goal, library, emptySet()).byModel)
    }

    @Test
    fun `no model, or regenerating, is code alone`() = runTest {
        val plan = GoalPlanAssistant(FakeOnDeviceModel()).plan(text, goal, library, emptySet())
        assertEquals(GoalPlanAssistant.Plan(byCode, byModel = false), plan)

        assistant.plan(text, goal, library, emptySet(), variation = 1)
        assertTrue(model.requests.isEmpty())
    }
}
