package dev.saketanand.setwise.domain.ai

import dev.saketanand.setwise.domain.model.CardioMetric
import dev.saketanand.setwise.domain.model.CardioValues
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.SetFact
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutSet
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickLogInterpreterTest {

    private val ohp = exercise(1, "Overhead Press (Barbell)")
    private val bench = exercise(2, "Bench Press (Barbell)")
    private val plank = exercise(3, "Plank", ExerciseType.BODYWEIGHT, timed = true)
    private val treadmill = exercise(4, "Treadmill", ExerciseType.CARDIO)
    private val row = exercise(5, "Bent-over Row (Barbell)")
    private val library = listOf(ohp, bench, plank, treadmill, row)
    private val recent = listOf(bench, ohp)

    private val session = WorkoutSession(
        7, "Push Day", null, Instant.EPOCH, null,
        listOf(
            item(11, bench, previous = listOf(PreviousSet(60.0, 8), PreviousSet(62.5, 6))),
            item(12, ohp),
            item(13, plank),
            item(14, treadmill),
        ),
    )
    private val model = FakeOnDeviceModel()
    private val interpreter = QuickLogInterpreter(model, ExerciseAssistant(model))

    @Test
    fun `a line the parser reads fully needs no model`() = runTest {
        model.availability = ModelAvailability.Ready

        val result = interpreter.interpret("ohp 3x6 at 40", session, openWorkoutExerciseId = null, recent, library)

        assertEquals(
            QuickLogResult.Sets(QuickLogTarget(ohp, 12, "ohp"), List(3) { SetFact(40.0, 6, null) }, SuggestionSource.Keywords),
            result,
        )
        assertTrue(model.requests.isEmpty())
    }

    @Test
    fun `no exercise named means the open one, and a library exercise can be added`() = runTest {
        assertEquals(
            QuickLogResult.Sets(QuickLogTarget(bench, 11, null), listOf(SetFact(60.0, 8, null)), SuggestionSource.Keywords),
            interpreter.interpret("60 for 8", session, openWorkoutExerciseId = 11, recent, library),
        )
        val rowResult = interpreter.interpret("barbell row 3x10 at 50", session, openWorkoutExerciseId = 11, recent, library) as QuickLogResult.Sets
        assertEquals(QuickLogTarget(row, null, "barbell row"), rowResult.target) // not in today's workout
    }

    @Test
    fun `same as last time, a timed hold, and cardio`() = runTest {
        assertEquals(
            listOf(SetFact(60.0, 8, null), SetFact(62.5, 6, null)),
            (interpreter.interpret("bench same as last time", session, null, recent, library) as QuickLogResult.Sets).sets,
        )
        assertEquals(
            List(3) { SetFact(null, null, 45) }, // "3x45" on a timed hold: seconds
            (interpreter.interpret("plank 3x45", session, null, recent, library) as QuickLogResult.Sets).sets,
        )
        assertEquals(
            QuickLogResult.Cardio(QuickLogTarget(treadmill, 14, "treadmill"), CardioValues(1_800, inclinePct = 6.0)),
            interpreter.interpret("treadmill 30 min 6% incline", session, null, recent, library),
        )
    }

    @Test
    fun `a line with words the parser can't place goes to the model, held to its numbers`() = runTest {
        model.availability = ModelAvailability.Ready
        model.answer = { setsJson(60.0 to 8, 60.0 to 8, 60.0 to 7) }

        val result = interpreter.interpret("bench 60 for 8, 8, 7", session, null, recent, library) as QuickLogResult.Sets

        assertEquals(listOf(SetFact(60.0, 8, null), SetFact(60.0, 8, null), SetFact(60.0, 7, null)), result.sets)
        assertEquals(SuggestionSource.Model, result.source)
        assertTrue(model.requests.single().prompt.endsWith("<exercise>Bench Press (Barbell), weight and reps</exercise>\n<line>bench 60 for 8, 8, 7</line>"))

        // An invented number (62.5): the parser's reading instead.
        model.answer = { setsJson(60.0 to 8, 62.5 to 8) }
        val fallback = interpreter.interpret("bench 60 for 8, 8, 7", session, null, recent, library) as QuickLogResult.Sets
        assertEquals(SuggestionSource.Keywords, fallback.source)
        assertEquals(listOf(SetFact(60.0, 8, null)), fallback.sets) // the user checks before adding
    }

    @Test
    fun `an exercise not in the workout is one done before, then any in the library`() = runTest {
        val frontSquat = exercise(6, "Front Squat (Barbell)")
        val backSquat = exercise(7, "Back Squat (Barbell)")
        val all = library + frontSquat + backSquat

        val done = interpreter.interpret("squat 3x5 at 100", session, null, recent = listOf(backSquat), all) as QuickLogResult.Sets
        assertEquals(QuickLogTarget(backSquat, null, "squat"), done.target)
        // Never done: without the model, the first library name with "squat", shown to check.
        val library = interpreter.interpret("squat 3x5 at 100", session, null, recent = emptyList(), all) as QuickLogResult.Sets
        assertEquals(frontSquat, library.target.exercise)
    }

    @Test
    fun `a misheard name and bare numbers still log, for bodyweight`() = runTest {
        val pullUp = exercise(8, "Pull-up", ExerciseType.BODYWEIGHT)

        val reps = interpreter.interpret("Full Ops 10, 8, 6.", session, null, recent, library + pullUp) as QuickLogResult.Sets
        assertEquals(QuickLogTarget(pullUp, null, "full ops"), reps.target)
        assertEquals(listOf(SetFact(null, 10, null), SetFact(null, 8, null), SetFact(null, 6, null)), reps.sets)

        val holds = interpreter.interpret("flank 45, 40", session, null, recent, library) as QuickLogResult.Sets
        assertEquals(plank, holds.target.exercise)
        assertEquals(listOf(SetFact(null, null, 45), SetFact(null, null, 40)), holds.sets)
        assertTrue(model.requests.isEmpty())
    }

    @Test
    fun `nothing to log, or no exercise, is said so`() = runTest {
        assertEquals(QuickLogResult.NotUnderstood(QuickLogResult.Reason.NothingToLog), interpreter.interpret("bench", session, null, recent, library))
        // A name it doesn't know: said so, with the sets kept for when one is picked (#138).
        assertEquals(
            QuickLogResult.NotUnderstood(QuickLogResult.Reason.UnknownExercise, exerciseWords = "zercher squat", rest = "3x5 at 80"),
            interpreter.interpret("zercher squat 3x5 at 80", session, null, recent, library),
        )
        assertEquals(QuickLogResult.NotUnderstood(QuickLogResult.Reason.NoLastTime), interpreter.interpret("ohp same as last time", session, null, recent, library))
    }

    @Test
    fun `the model's sets must be numbers from the line, keeping what the parser read`() {
        assertNull(QuickLogInterpreter.acceptSets(ModelQuickLog(listOf(ModelLoggedSet(41.0, 6, 0))), "ohp 40 for 6", emptyList()))
        assertNull(QuickLogInterpreter.acceptSets(ModelQuickLog(listOf(ModelLoggedSet(40.0, 0, 0))), "ohp 40 for 6", emptyList())) // no reps or time
        assertEquals(listOf(SetFact(40.0, 6, null)), QuickLogInterpreter.acceptSets(ModelQuickLog(listOf(ModelLoggedSet(40.0, 6, 0))), "ohp 40 for 6", emptyList()))
        // "3 sets" stated, one set answered: rejected.
        assertNull(QuickLogInterpreter.acceptSets(ModelQuickLog(listOf(ModelLoggedSet(16.0, 15, 0))), "3 sets of bench 15 16", emptyList(), statedSets = 3))
        assertEquals(3, QuickLogInterpreter.statedSets("3 sets of bench at 15 reps 16 kg."))
        assertNull(QuickLogInterpreter.statedSets("3 sets of 6 at 40, last one 37.5 for 8"))
        // Dropping the 40 × 8 the parser read: rejected.
        val parsed = listOf(SetFact(50.0, 10, null), SetFact(40.0, 8, null))
        assertNull(QuickLogInterpreter.acceptSets(ModelQuickLog(listOf(ModelLoggedSet(50.0, 10, 0))), "row 3x10 at 50 then dropped to 40 for 8", parsed))
    }

    private fun setsJson(vararg sets: Pair<Double, Int>) =
        """{"sets": [${sets.joinToString { (kg, reps) -> """{"weightKg": $kg, "reps": $reps, "seconds": 0}""" }}]}"""

    private fun exercise(id: Long, name: String, type: ExerciseType = ExerciseType.STRENGTH, timed: Boolean = false) =
        Exercise(id, name, type, "Chest", "Barbell", 90, timed, false, if (type == ExerciseType.CARDIO) listOf(CardioMetric.DURATION) else null, null, null)

    private fun item(id: Long, exercise: Exercise, previous: List<PreviousSet> = emptyList()) =
        SessionExercise(id, exercise, listOf(WorkoutSet(id * 10, 1, null, null, null, isCompleted = false, isPr = false)), previous)
}
