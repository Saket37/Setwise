package dev.saketanand.setwise.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.saketanand.setwise.data.repository.ExerciseRepositoryImpl
import dev.saketanand.setwise.domain.model.CardioMetric
import dev.saketanand.setwise.domain.model.CreateExerciseResult
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.NewExercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The real SQL on an in-memory database: on a device/emulator, and on Robolectric. */
@RunWith(AndroidJUnit4::class)
class ExerciseRepositoryTest {

    private lateinit var db: SetwiseDatabase
    private lateinit var repository: ExerciseRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, SetwiseDatabase::class.java).build()
        repository = ExerciseRepositoryImpl(db.exerciseDao())
    }

    @After
    fun tearDown() = db.close()

    private fun new(name: String, type: ExerciseType = ExerciseType.STRENGTH, muscle: String = "Chest", timed: Boolean = false) =
        NewExercise(name, type, isTimed = timed, muscleGroup = muscle, equipment = "Dumbbell", restSec = 90)

    @Test
    fun newExerciseIsSavedAsCustomWithItsNameTidied() = runTest {
        val created = repository.createExercise(new("  Spoto   press ")) as CreateExerciseResult.Created

        val saved = repository.observeExercise(created.exerciseId).first()!!
        assertEquals("Spoto press", saved.name)
        assertTrue(saved.isCustom)
        assertEquals(90, saved.defaultRestSec)
    }

    @Test
    fun takenNameInAnyCaseIsNotAddedTwice() = runTest {
        val first = repository.createExercise(new("Spoto Press")) as CreateExerciseResult.Created

        val again = repository.createExercise(new("spoto  PRESS"))

        assertEquals(first.exerciseId, (again as CreateExerciseResult.NameTaken).existing.id)
        assertEquals(1, repository.observeExerciseCount().first())
    }

    @Test
    fun cardioExerciseLogsTimeAndDistanceWithNoRestOrHold() = runTest {
        val id = (repository.createExercise(new("Rowing machine", ExerciseType.CARDIO, muscle = "Back", timed = true)) as CreateExerciseResult.Created).exerciseId

        val rowing = repository.observeExercise(id).first()!!
        assertEquals("Cardio", rowing.muscleGroup)
        assertEquals(0, rowing.defaultRestSec)
        assertEquals(false, rowing.isTimed)
        assertEquals(listOf(CardioMetric.DURATION, CardioMetric.DISTANCE), rowing.metrics)
    }

    @Test
    fun exercisesAreFoundByIdInOrderAndBySearchAndMuscle() = runTest {
        fun id(result: CreateExerciseResult) = (result as CreateExerciseResult.Created).exerciseId
        val press = id(repository.createExercise(new("Spoto Press")))
        val row = id(repository.createExercise(new("Seal Row", muscle = "Back")))
        val curl = id(repository.createExercise(new("Spider Curl", muscle = "Biceps")))

        assertEquals(listOf(curl, press), repository.getExercises(listOf(curl, 999, press)).map { it.id })
        assertEquals(listOf("Spider Curl", "Spoto Press"), repository.observeExercises(" sp ", null).first().map { it.name }.sorted())
        assertEquals(listOf(row), repository.observeExercises("", "Back").first().map { it.id })
        assertEquals(listOf("Back", "Biceps", "Chest"), repository.observeMuscleGroups().first().sorted())
        assertTrue(repository.observeRecentExercises(5).first().isEmpty()) // none done yet
        assertNull(repository.observeExercise(999).first())
    }
}
