package dev.saketanand.setwise.domain.ai

import dev.saketanand.setwise.domain.model.AnsweredSet
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.HistoryAnswer
import dev.saketanand.setwise.domain.model.HistoryQuestion
import dev.saketanand.setwise.domain.model.LoggedSetRecord
import dev.saketanand.setwise.domain.model.PeriodName
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryAssistantTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 5) // a Monday

    private val squat = exercise(1, "Back Squat (Barbell)", "Quads")
    private val bench = exercise(2, "Bench Press (Barbell)", "Chest")
    private val benchDb = exercise(3, "Bench Press (Dumbbell)", "Chest")
    private val curl = exercise(4, "Barbell Curl", "Biceps")
    private val library = listOf(squat, bench, benchDb, curl)

    // Newest workout first, sets in order.
    private val log = listOf(
        set(10, "Leg Day", LocalDate.of(2026, 9, 28), squat, 1, 95.0, 5),
        set(10, "Leg Day", LocalDate.of(2026, 9, 28), squat, 2, 100.0, 5, pr = true),
        set(9, "Push Day", LocalDate.of(2026, 9, 25), bench, 1, 62.5, 8, pr = true),
        set(8, "Leg Day", LocalDate.of(2026, 9, 21), squat, 1, 97.5, 5),
        set(7, "Push Day", LocalDate.of(2026, 9, 18), bench, 1, 60.0, 8),
        set(6, "Pull Day", LocalDate.of(2026, 8, 30), curl, 1, 30.0, 10),
    )
    private val model = FakeOnDeviceModel()
    private val assistant = HistoryAssistant(model, ExerciseAssistant(model))

    private suspend fun ask(question: String) = assistant.ask(question, log, library, today, zone)

    @Test
    fun `when did I last squat 100 kg`() = runTest {
        val reply = ask("When did I last squat 100 kg?") as HistoryReply.Answered
        val lifted = reply.answer as HistoryAnswer.Lifted

        assertEquals(HistoryQuestion.LastLifted(squat, 100.0), lifted.question)
        assertEquals(LocalDate.of(2026, 9, 28), lifted.workout.date)
        assertEquals(AnsweredSet("Back Squat (Barbell)", 2, 100.0, 5, null, isPr = true), lifted.set)
        assertTrue(lifted.isBest)
        assertEquals(false, reply.byModel)
    }

    @Test
    fun `the design's suggestions are read in code`() = runTest {
        val best = (ask("What’s my best bench press?") as HistoryReply.Answered).answer as HistoryAnswer.Lifted
        assertEquals(bench, (best.question as HistoryQuestion.BestSet).exercise) // the one they've done
        assertEquals(62.5, best.set.weightKg)

        val volume = (ask("How much volume did I do in September?") as HistoryReply.Answered).answer as HistoryAnswer.Total
        assertEquals(PeriodName.InMonth(YearMonth.of(2026, 9)), (volume.question as HistoryQuestion.Volume).period.name)
        assertEquals(95.0 * 5 + 100.0 * 5 + 62.5 * 8 + 97.5 * 5 + 60.0 * 8, volume.volumeKg, 0.01)
        assertEquals(4, volume.workouts)

        val legs = (ask("How many times did I train legs this month?") as HistoryReply.Answered).answer
        assertTrue(legs is HistoryAnswer.NoneFound) // October has none yet
        val legsInSeptember = (ask("How many times did I train legs in September?") as HistoryReply.Answered).answer as HistoryAnswer.Count
        assertEquals(2, legsInSeptember.count)

        val prs = (ask("Show workouts where I hit a PR") as HistoryReply.Answered).answer as HistoryAnswer.Count
        assertEquals(listOf(10L, 9L), prs.workouts.map { it.id })
        assertTrue(model.requests.isEmpty())
    }

    @Test
    fun `spoken forms, and an exercise never done`() = runTest {
        val squatted = (ask("when did I last squat a hundred kilos") as HistoryReply.Answered).answer as HistoryAnswer.Lifted
        assertEquals(100.0, squatted.set.weightKg)

        assertEquals(HistoryReply.UnknownExercise("zercher"), ask("what's my best zercher"))
        // Contractions and filler around the exercise.
        val heaviest = (ask("what’s the heaviest I've squatted") as HistoryReply.Answered).answer as HistoryAnswer.Lifted
        assertEquals(squat, (heaviest.question as HistoryQuestion.BestSet).exercise)
        val strong = (ask("how strong is my bench these days") as HistoryReply.Answered).answer as HistoryAnswer.Lifted
        assertEquals(bench, (strong.question as HistoryQuestion.BestSet).exercise)
        assertEquals(HistoryReply.NotUnderstood, ask("should I eat more protein")) // no model here
    }

    @Test
    fun `when code can't tell the lookup, the model picks one from the list`() = runTest {
        model.availability = ModelAvailability.Ready
        model.answer = { """{"lookup": "best set", "period": "all time", "kg": 0}""" }

        val reply = ask("tell me about squat") as HistoryReply.Answered

        assertTrue(reply.byModel)
        assertEquals(squat, ((reply.answer as HistoryAnswer.Lifted).question as HistoryQuestion.BestSet).exercise)
    }

    @Test
    fun `the model's made-up weight is dropped, and an answer off the list isn't used`() = runTest {
        model.availability = ModelAvailability.Ready
        model.answer = { """{"lookup": "last time lifted", "period": "all time", "kg": 140}""" }
        val lifted = (ask("tell me about squat") as HistoryReply.Answered).answer as HistoryAnswer.Lifted
        assertEquals(HistoryQuestion.LastLifted(squat, null), lifted.question) // 140 isn't in the question

        model.answer = { """{"lookup": "calories burned", "period": "all time", "kg": 0}""" }
        assertEquals(HistoryReply.NotUnderstood, ask("tell me about squat"))

        model.answer = { error("Busy") }
        assertEquals(HistoryReply.NotUnderstood, ask("tell me about squat"))
    }

    private fun set(workoutId: Long, name: String, day: LocalDate, exercise: Exercise, number: Int, kg: Double, reps: Int, pr: Boolean = false) =
        LoggedSetRecord(workoutId, name, day.atTime(18, 0).atZone(zone).toInstant(), exercise.id, exercise.name, exercise.muscleGroup, number, kg, reps, null, null, pr)

    private fun exercise(id: Long, name: String, muscle: String) =
        Exercise(id, name, ExerciseType.STRENGTH, muscle, "Barbell", 90, false, false, null, null, null)
}
