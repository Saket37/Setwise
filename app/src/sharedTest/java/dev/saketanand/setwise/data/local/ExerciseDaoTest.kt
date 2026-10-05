package dev.saketanand.setwise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.saketanand.setwise.data.local.dao.ExerciseDao
import dev.saketanand.setwise.data.local.dao.WorkoutDao
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.data.local.entity.SetEntity
import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.local.entity.WorkoutExerciseEntity
import dev.saketanand.setwise.domain.model.ExerciseType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real SQL against an in-memory database: on a device/emulator, and on Robolectric. */
@RunWith(AndroidJUnit4::class)
class ExerciseDaoTest {

    private lateinit var db: SetwiseDatabase
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var workoutDao: WorkoutDao

    private var bench = 0L
    private var pullUp = 0L
    private var squat = 0L

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, SetwiseDatabase::class.java).build()
        exerciseDao = db.exerciseDao()
        workoutDao = db.workoutDao()

        bench = exerciseDao.insert(exercise("Bench Press", "Chest"))
        pullUp = exerciseDao.insert(exercise("Pull-up", "Back", ExerciseType.BODYWEIGHT))
        squat = exerciseDao.insert(exercise("Back Squat", "Quads"))
        exerciseDao.insert(exercise("Barbell Row", "Back"))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun recentExercisesAreNewestFirstWithTheLatestSessionsTopSet() = runTest {
        // Older session: heavier bench than the latest one; must NOT be shown.
        workout(startedAt = 1_000, bench to listOf(set(80.0, 5)))
        // Latest session: top set = heaviest completed (70 × 6); the uncompleted 90 is ignored.
        workout(
            startedAt = 2_000,
            bench to listOf(set(60.0, 10), set(70.0, 6), set(70.0, 4), set(90.0, 1, completed = false)),
            pullUp to listOf(set(null, 8), set(null, 12)),
        )

        val recent = exerciseDao.observeRecentExercises(limit = 10).first()

        // Both were done in the same (latest) workout, so their relative order isn't defined.
        assertEquals(setOf("Bench Press", "Pull-up"), recent.map { it.exercise.name }.toSet())
        val benchRow = recent.first { it.exercise.id == bench }
        assertEquals(70.0, benchRow.lastWeightKg)
        assertEquals(6, benchRow.lastReps)
        val pullUpRow = recent.first { it.exercise.id == pullUp }
        assertNull(pullUpRow.lastWeightKg)
        assertEquals("bodyweight: best by reps", 12, pullUpRow.lastReps)
    }

    @Test
    fun recentExcludesRunningWorkoutsAndRespectsTheLimit() = runTest {
        workout(startedAt = 1_000, bench to listOf(set(60.0, 8)))
        workout(startedAt = 2_000, squat to listOf(set(100.0, 5)))
        workout(startedAt = 3_000, pullUp to listOf(set(null, 10)), finished = false)

        val recent = exerciseDao.observeRecentExercises(limit = 1).first()

        assertEquals(listOf(squat), recent.map { it.exercise.id })
    }

    @Test
    fun exerciseWithNoCompletedSetHasNoLastSet() = runTest {
        workout(startedAt = 1_000, bench to listOf(set(60.0, 8, completed = false)))

        val row = exerciseDao.observeRecentExercises(limit = 10).first().single()

        assertNull(row.lastWeightKg)
        assertNull(row.lastReps)
    }

    @Test
    fun muscleGroupsAreBiggestFirstThenByName() = runTest {
        assertEquals(listOf("Back", "Chest", "Quads"), exerciseDao.observeMuscleGroups().first())
    }

    private suspend fun workout(
        startedAt: Long,
        vararg exercises: Pair<Long, List<SetEntity>>,
        finished: Boolean = true,
    ) {
        val workoutId = workoutDao.insertWorkout(
            WorkoutEntity(name = "W$startedAt", startedAt = startedAt, endedAt = if (finished) startedAt + 3_600_000 else null)
        )
        exercises.forEachIndexed { position, (exerciseId, sets) ->
            val workoutExerciseId = workoutDao.insertWorkoutExercise(
                WorkoutExerciseEntity(workoutId = workoutId, exerciseId = exerciseId, position = position)
            )
            workoutDao.insertSets(sets.mapIndexed { i, s -> s.copy(workoutExerciseId = workoutExerciseId, setNumber = i + 1) })
        }
    }

    @Test
    fun aBuiltInExerciseIsRenamedInPlaceButNotACustomOneOrOverAnotherName() = runTest {
        val (preacher, mine, curl, taken) = exerciseDao.insertAll(
            listOf(
                exercise("Preacher Curl", "Biceps"),
                exercise("Hammer Curl", "Biceps").copy(isCustom = true),
                exercise("Barbell Curl", "Biceps"),
                exercise("Bicep Curl (Barbell)", "Biceps"),
            ),
        )

        assertEquals(1, exerciseDao.renameBuiltIn("Preacher Curl", "Preacher Curl (Barbell)"))
        assertEquals(preacher, exerciseDao.getByName("Preacher Curl (Barbell)")?.id) // same id: its history stays
        assertEquals(0, exerciseDao.renameBuiltIn("Hammer Curl", "Hammer Curl (Dumbbell)")) // theirs
        assertEquals(mine, exerciseDao.getByName("Hammer Curl")?.id)
        assertEquals(0, exerciseDao.renameBuiltIn("Barbell Curl", "Bicep Curl (Barbell)")) // name taken
        assertEquals(curl, exerciseDao.getByName("Barbell Curl")?.id)
        assertEquals(taken, exerciseDao.getByName("Bicep Curl (Barbell)")?.id)
    }

    private fun set(weightKg: Double?, reps: Int, completed: Boolean = true) =
        SetEntity(workoutExerciseId = 0, setNumber = 0, weightKg = weightKg, reps = reps, isCompleted = completed)

    private fun exercise(name: String, muscleGroup: String, type: ExerciseType = ExerciseType.STRENGTH) =
        ExerciseEntity(name = name, type = type, muscleGroup = muscleGroup, equipment = "Barbell", defaultRestSec = 90)
}
