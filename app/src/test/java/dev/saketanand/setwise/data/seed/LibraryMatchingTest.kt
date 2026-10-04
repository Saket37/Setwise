package dev.saketanand.setwise.data.seed

import dev.saketanand.setwise.data.mapper.toDomain
import dev.saketanand.setwise.data.mapper.toEntity
import dev.saketanand.setwise.domain.ai.ExerciseAssistant
import dev.saketanand.setwise.domain.ai.QuickLogInterpreter
import dev.saketanand.setwise.domain.ai.QuickLogResult
import dev.saketanand.setwise.domain.ai.WorkoutImporter
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.testing.StubWorkoutRepository
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The real library (assets/exercises.json) against names as people bring them: Strong's (from
 * a shared workout) and as typed in the quick log. Without the model: code's matching only.
 */
class LibraryMatchingTest {

    private val library: List<Exercise> = Json { ignoreUnknownKeys = true }
        .decodeFromString<ExerciseSeedFile>(File("src/main/assets/exercises.json").readText())
        .exercises.mapIndexed { i, dto -> dto.toEntity().copy(id = i + 1L).toDomain() }

    private val model = FakeOnDeviceModel()
    private val assistant = ExerciseAssistant(model)

    @Test
    fun `a workout shared from Strong maps onto the library's own names`() = runTest {
        val shared = """
            Evening Workout
            Wednesday, 30 September 2026 at 8:01 pm

            Bicep Curl (Barbell)
            Set 1: 10 kg × 15 reps
            Preacher Curl (Barbell)
            Set 1: 10 kg × 15 reps
            Hammer Curl (Dumbbell)
            Set 1: 7.5 kg × 15 reps
            Triceps Extension (Dumbbell)
            Set 1: 10 kg × 15 reps
            Triceps Pushdown (Cable - Straight Bar)
            Set 1: 25 kg × 15 reps
            Skullcrusher (Barbell)
            Set 1: 20 kg × 14 reps
            Lat Pulldown (Cable)
            Set 1: 50 kg × 12 reps
            Seated Row (Cable)
            Set 1: 50 kg × 12 reps
            Incline Bench Press (Dumbbell)
            Set 1: 22 kg × 10 reps
        """.trimIndent()
        val importer = WorkoutImporter(assistant, NoRepository, StubWorkoutRepository())

        val plan = importer.plan(shared, library, done = emptyList(), ZoneId.of("Asia/Kolkata"))

        assertEquals(
            listOf(
                "Bicep Curl (Barbell)", "Preacher Curl (Barbell)", "Hammer Curl (Dumbbell)",
                "Overhead Triceps Extension (Dumbbell)", "Triceps Pushdown (Cable - Straight Bar)", "Skull Crusher (Barbell)",
                "Lat Pulldown (Wide Grip)", "Seated Cable Row (V-Bar)", "Incline Bench Press (Dumbbell)",
            ),
            plan.workouts.single().exercises.map { it.match?.name },
        )
    }

    @Test
    fun `quick-log names pick the variant that was said`() = runTest {
        val interpreter = QuickLogInterpreter(model, assistant)
        val empty = WorkoutSession(0, "", null, Instant.EPOCH, null, emptyList())
        suspend fun matched(line: String) = (interpreter.interpret(line, empty, null, emptyList(), library) as QuickLogResult.Sets).target.exercise.name

        assertEquals("Preacher Curl (Dumbbell)", matched("dumbbell preacher curl 3x10 at 12"))
        assertEquals("Triceps Pushdown (Cable - Rope)", matched("rope pushdown 3x12 at 25"))
        assertEquals("Bicep Curl (Barbell)", matched("barbell curl 3x10 at 30"))
    }

    @Test
    fun `every renamed library name still finds the exercise it became`() = runTest {
        val seed = Json { ignoreUnknownKeys = true }.decodeFromString<ExerciseSeedFile>(File("src/main/assets/exercises.json").readText())
        val misses = seed.renames.mapNotNull { rename ->
            assistant.findMatch(rename.from, library)?.name.takeIf { it != rename.to }.let { found ->
                if (assistant.findMatch(rename.from, library)?.name == rename.to) null else "${rename.from} → $found"
            }
        }
        assertEquals(emptyList<String>(), misses)
    }

    private object NoRepository : ExerciseRepository by unused()
}

@Suppress("UNCHECKED_CAST")
private fun <T> unused(): T = java.lang.reflect.Proxy.newProxyInstance(
    ExerciseRepository::class.java.classLoader, arrayOf(ExerciseRepository::class.java),
) { _, method, _ -> error("not used: ${method.name}") } as T
