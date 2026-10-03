package dev.saketanand.setwise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.data.local.entity.SetEntity
import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.local.entity.WorkoutExerciseEntity
import dev.saketanand.setwise.data.repository.WorkoutRepositoryImpl
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The active-workout writes and reads, on a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {

    private lateinit var db: SetwiseDatabase
    private lateinit var repository: WorkoutRepository

    private var bench = 0L
    private var squat = 0L

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, SetwiseDatabase::class.java).build()
        repository = WorkoutRepositoryImpl(db, db.workoutDao(), db.templateDao())
        bench = db.exerciseDao().insert(exercise("Bench Press"))
        squat = db.exerciseDao().insert(exercise("Back Squat"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun previousSetsComeFromTheLastFinishedSessionOnly() = runTest {
        finishedWorkout(startedAt = 1_000, bench to listOf(50.0 to 10))
        finishedWorkout(startedAt = 2_000, bench to listOf(60.0 to 8, 62.5 to 6))
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(3_000))
        repository.addExercises(current, listOf(bench, squat))

        val session = repository.observeSession(current).first()!!

        val (benchRow, squatRow) = session.exercises
        assertEquals(listOf(PreviousSet(60.0, 8), PreviousSet(62.5, 6)), benchRow.previousSets)
        assertTrue("never done before", squatRow.previousSets.isEmpty())
    }

    @Test
    fun addedExercisesGetLastTimesSetCountOrThree() = runTest {
        finishedWorkout(startedAt = 1_000, bench to listOf(60.0 to 8, 60.0 to 8, 60.0 to 8, 60.0 to 8, 60.0 to 8))
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(2_000))

        repository.addExercises(current, listOf(squat, bench))

        val session = repository.observeSession(current).first()!!
        assertEquals(listOf("Back Squat", "Bench Press"), session.exercises.map { it.exercise.name })
        assertEquals(listOf(WorkoutRepository.DEFAULT_SET_COUNT, 5), session.exercises.map { it.sets.size })
    }

    @Test
    fun deletingASetRenumbersTheRest() = runTest {
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(1_000))
        repository.addExercises(current, listOf(squat)) // 3 sets
        val sets = repository.observeSession(current).first()!!.exercises.single().sets

        repository.deleteSet(sets[0].id)

        val after = repository.observeSession(current).first()!!.exercises.single().sets
        assertEquals(listOf(sets[1].id, sets[2].id), after.map { it.id })
        assertEquals(listOf(1, 2), after.map { it.setNumber })
    }

    @Test
    fun finishDropsOpenSetsAndEmptyExercisesAndRenumbers() = runTest {
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(1_000))
        repository.addExercises(current, listOf(bench, squat)) // 3 sets each
        val (benchRow, _) = repository.observeSession(current).first()!!.exercises
        // Bench: sets 1 and 3 done, 2 left open. Squat: nothing done.
        repository.setCompleted(benchRow.sets[0].id, Instant.ofEpochMilli(2_000), 60.0, 8, null)
        repository.setCompleted(benchRow.sets[2].id, Instant.ofEpochMilli(3_000), 60.0, 6, null)

        assertTrue(repository.finishWorkout(current, Instant.ofEpochMilli(4_000)))

        val session = repository.observeSession(current).first()!!
        assertNotNull(session.endedAt)
        val onlyBench = session.exercises.single()
        assertEquals("Bench Press", onlyBench.exercise.name)
        assertEquals(listOf(1, 2), onlyBench.sets.map { it.setNumber })
        assertEquals(listOf(8, 6), onlyBench.sets.map { it.reps })
        assertFalse("already finished", repository.finishWorkout(current, Instant.ofEpochMilli(5_000)))
    }

    @Test
    fun startTimeCanOnlyChangeWhileRunning() = runTest {
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(1_000))
        repository.updateStartTime(current, Instant.ofEpochMilli(500))
        assertEquals(Instant.ofEpochMilli(500), repository.observeSession(current).first()!!.startedAt)
    }

    @Test
    fun discardDeletesEverything() = runTest {
        val current = repository.startWorkout(templateId = null, startedAt = Instant.ofEpochMilli(1_000))
        repository.addExercises(current, listOf(bench))

        repository.discardWorkout(current)

        assertNull(repository.observeSession(current).first())
    }

    private suspend fun finishedWorkout(startedAt: Long, vararg exercises: Pair<Long, List<Pair<Double, Int>>>) {
        val dao = db.workoutDao()
        val workoutId = dao.insertWorkout(WorkoutEntity(name = "W", startedAt = startedAt, endedAt = startedAt + 60_000))
        exercises.forEachIndexed { position, (exerciseId, sets) ->
            val id = dao.insertWorkoutExercise(WorkoutExerciseEntity(workoutId = workoutId, exerciseId = exerciseId, position = position))
            dao.insertSets(sets.mapIndexed { i, (kg, reps) ->
                SetEntity(workoutExerciseId = id, setNumber = i + 1, weightKg = kg, reps = reps, isCompleted = true)
            })
        }
    }

    private fun exercise(name: String) =
        ExerciseEntity(name = name, type = ExerciseType.STRENGTH, muscleGroup = "Chest", equipment = "Barbell", defaultRestSec = 90)
}
