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

        val result = interpreter.interpret("ohp 3x6 at 40", session, openWorkoutExerciseId = null, library)

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
            interpreter.interpret("60 for 8", session, openWorkoutExerciseId = 11, library),
        )
        val rowResult = interpreter.interpret("barbell row 3x10 at 50", session, openWorkoutExerciseId = 11, library) as QuickLogResult.Sets
        assertEquals(QuickLogTarget(row, null, "barbell row"), rowResult.target) // not in today's workout
    }

    @Test
    fun `same as last time, a timed hold, and cardio`() = runTest {
        assertEquals(
            listOf(SetFact(60.0, 8, null), SetFact(62.5, 6, null)),
            (interpreter.interpret("bench same as last time", session, null, library) as QuickLogResult.Sets).sets,
        )
        assertEquals(
            List(3) { SetFact(null, null, 45) }, // "3x45" on a timed hold: seconds
            (interpreter.interpret("plank 3x45", session, null, library) as QuickLogResult.Sets).sets,
        )
        assertEquals(
            QuickLogResult.Cardio(QuickLogTarget(treadmill, 14, "treadmill"), CardioValues(1_800, inclinePct = 6.0)),
            interpreter.interpret("treadmill 30 min 6% incline", session, null, library),
        )
    }

    @Test
    fun `a line with words the parser can't place goes to the model, held to its numbers`() = runTest {
        model.availability = ModelAvailability.Ready
        model.answer = { setsJson(40.0 to 6, 40.0 to 6, 37.5 to 8) }

        val result = interpreter.interpret("ohp 3 sets of 6 at 40, last one 37.5 for 8", session, null, library) as QuickLogResult.Sets

        assertEquals(listOf(SetFact(40.0, 6, null), SetFact(40.0, 6, null), SetFact(37.5, 8, null)), result.sets)
        assertEquals(SuggestionSource.Model, result.source)
        assertTrue(model.requests.single().prompt.endsWith("<exercise>Overhead Press (Barbell), weight and reps</exercise>\n<line>ohp 3 sets of 6 at 40, last one 37.5 for 8</line>"))

        // An invented number (42.5): the parser's reading instead.
        model.answer = { setsJson(40.0 to 6, 42.5 to 6) }
        val fallback = interpreter.interpret("ohp 3 sets of 6 at 40, last one 37.5 for 8", session, null, library) as QuickLogResult.Sets
        assertEquals(SuggestionSource.Keywords, fallback.source)
        assertEquals(4, fallback.sets.size) // 3 × 40 × 6, then 37.5 × 8: the user checks before adding
    }

    @Test
    fun `nothing to log, or no exercise, is said so`() = runTest {
        assertEquals(QuickLogResult.NotUnderstood(QuickLogResult.Reason.NothingToLog), interpreter.interpret("bench", session, null, library))
        assertEquals(QuickLogResult.NotUnderstood(QuickLogResult.Reason.NoExercise), interpreter.interpret("zercher squat 3x5 at 80", session, null, library))
        assertEquals(QuickLogResult.NotUnderstood(QuickLogResult.Reason.NoLastTime), interpreter.interpret("ohp same as last time", session, null, library))
    }

    @Test
    fun `the model's sets must be numbers from the line`() {
        assertNull(QuickLogInterpreter.acceptSets(ModelQuickLog(listOf(ModelLoggedSet(41.0, 6, 0))), "ohp 40 for 6"))
        assertNull(QuickLogInterpreter.acceptSets(ModelQuickLog(listOf(ModelLoggedSet(40.0, 0, 0))), "ohp 40 for 6")) // no reps or time
        assertEquals(listOf(SetFact(40.0, 6, null)), QuickLogInterpreter.acceptSets(ModelQuickLog(listOf(ModelLoggedSet(40.0, 6, 0))), "ohp 40 for 6"))
    }

    private fun setsJson(vararg sets: Pair<Double, Int>) =
        """{"sets": [${sets.joinToString { (kg, reps) -> """{"weightKg": $kg, "reps": $reps, "seconds": 0}""" }}]}"""

    private fun exercise(id: Long, name: String, type: ExerciseType = ExerciseType.STRENGTH, timed: Boolean = false) =
        Exercise(id, name, type, "Chest", "Barbell", 90, timed, false, if (type == ExerciseType.CARDIO) listOf(CardioMetric.DURATION) else null, null, null)

    private fun item(id: Long, exercise: Exercise, previous: List<PreviousSet> = emptyList()) =
        SessionExercise(id, exercise, listOf(WorkoutSet(id * 10, 1, null, null, null, isCompleted = false, isPr = false)), previous)
}
