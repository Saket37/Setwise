package dev.saketanand.setwise.domain.ai

import dev.saketanand.setwise.domain.model.CreateExerciseResult
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.NewExercise
import dev.saketanand.setwise.domain.model.RecentExercise
import dev.saketanand.setwise.domain.model.SharedSet
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.testing.StubWorkoutRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutImporterTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val curl = exercise(1, "Barbell Curl", "Biceps")
    private val pushdown = exercise(2, "Triceps Pushdown (Rope)", "Triceps")
    private val library = listOf(curl, pushdown)

    private val text = """
        Evening Workout
        Wednesday, 30 September 2026 at 8:01 pm

        Barbell Curl
        Set 1: 10 kg × 15 reps
        Set 2: 15 kg × 15 reps

        Zottman Curl (Dumbbell)
        Set 1: 7.5 kg × 12 reps

        Morning Workout
        Tuesday, 29 September 2026 at 7:00 am

        Zottman Curl (Dumbbell)
        Set 1: 7.5 kg × 10 reps
    """.trimIndent()

    private val workouts = object : StubWorkoutRepository() {
        val imported = mutableListOf<Triple<String, Instant, List<Pair<Long, List<SharedSet>>>>>()
        var existingStart: Instant? = null
        override suspend fun importWorkout(name: String, startedAt: Instant, endedAt: Instant, exercises: List<Pair<Long, List<SharedSet>>>): Long {
            imported += Triple(name, startedAt, exercises)
            return imported.size.toLong()
        }
        override suspend fun hasWorkoutStartedAt(startedAt: Instant) = startedAt == existingStart
    }

    private val exercises = object : ExerciseRepository {
        val created = mutableListOf<NewExercise>()
        override fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>> = flowOf(library)
        override fun observeMuscleGroups(): Flow<List<String>> = flowOf(emptyList())
        override fun observeRecentExercises(limit: Int): Flow<List<RecentExercise>> = flowOf(emptyList())
        override fun observeExerciseCount(): Flow<Int> = flowOf(library.size)
        override suspend fun createExercise(exercise: NewExercise): CreateExerciseResult {
            created += exercise
            return CreateExerciseResult.Created(50)
        }
        override suspend fun getExercises(ids: List<Long>): List<Exercise> = emptyList()
        override fun observeExercise(id: Long): Flow<Exercise?> = flowOf(null)
    }

    private val model = FakeOnDeviceModel()
    private val importer = WorkoutImporter(ExerciseAssistant(model), exercises, workouts)

    @Test
    fun `matched and new exercises, saved oldest first, a new exercise made once`() = runTest {
        val plan = importer.plan(text, library, done = emptyList(), zone)

        assertEquals(curl, plan.workouts[0].exercises[0].match)
        assertEquals(null, plan.workouts[0].exercises[1].match)
        assertEquals(listOf("Zottman Curl (Dumbbell)"), plan.newExercises)

        val result = importer.import(plan, zone)

        assertEquals(WorkoutImporter.Result(workouts = 2, sets = 4, newExercises = 1), result)
        assertEquals(listOf("Morning Workout", "Evening Workout"), workouts.imported.map { it.first })
        assertEquals(LocalDateTime.of(2026, 9, 30, 20, 1).atZone(zone).toInstant(), workouts.imported[1].second)
        assertEquals(listOf(1L, 50L), workouts.imported[1].third.map { it.first })
        val zottman = exercises.created.single()
        assertEquals(ExerciseType.STRENGTH, zottman.type)
        assertEquals("Dumbbell", zottman.equipment)
    }

    @Test
    fun `a workout imported before is skipped`() = runTest {
        workouts.existingStart = LocalDateTime.of(2026, 9, 30, 20, 1).atZone(zone).toInstant()

        val plan = importer.plan(text, library, done = emptyList(), zone)

        assertTrue(plan.workouts[0].alreadyImported)
        assertEquals(listOf("Morning Workout"), plan.toImport.map { it.shared.name })
        assertEquals(1, importer.import(plan, zone).workouts)
    }

    private fun exercise(id: Long, name: String, muscle: String) =
        Exercise(id, name, ExerciseType.STRENGTH, muscle, "Barbell", 90, false, false, null, null, null)
}
