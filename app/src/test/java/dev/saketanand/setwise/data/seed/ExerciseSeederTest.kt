package dev.saketanand.setwise.data.seed

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import dev.saketanand.setwise.data.local.SetwiseDatabase
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.domain.model.ExerciseType
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** The real exercises.json (version 2: 204 exercises, 42 renames) into an in-memory database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExerciseSeederTest {

    @get:Rule val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(context, SetwiseDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.exerciseDao()
    private val preferences by lazy {
        SeedPreferences(PreferenceDataStoreFactory.create(produceFile = { File(folder.root, "seed.preferences_pb") }))
    }

    @After fun tearDown() = db.close()

    private fun TestScope.seeder() =
        ExerciseSeeder(context, dao, preferences, Json { ignoreUnknownKeys = true }, UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `a first launch seeds the whole library and remembers its version`() = runTest {
        seeder().seedIfNeeded()

        assertEquals(204, dao.count())
        assertEquals(2, preferences.exerciseSeedVersion())
    }

    @Test
    fun `the same version isn't seeded again`() = runTest {
        preferences.setExerciseSeedVersion(2)

        seeder().seedIfNeeded()

        assertEquals(0, dao.count())
    }

    @Test
    fun `an older library's exercises are renamed in place, keeping their history`() = runTest {
        preferences.setExerciseSeedVersion(1)
        val dumbbellPress = dao.insert(builtIn("Incline Dumbbell Press"))
        val mine = dao.insert(builtIn("Dumbbell Fly").copy(isCustom = true)) // someone's own: left alone

        seeder().seedIfNeeded()

        assertEquals(dumbbellPress, dao.findByNameIgnoringCase("Incline Bench Press (Dumbbell)")?.id) // same id: same history
        assertNull(dao.findByNameIgnoringCase("Incline Dumbbell Press"))
        assertEquals(mine, dao.findByNameIgnoringCase("Dumbbell Fly")?.id)
        assertEquals(205, dao.count()) // the library, plus their own
        assertEquals(2, preferences.exerciseSeedVersion())
    }

    @Test
    fun `a seed that fails is tried again next launch`() = runTest {
        db.close() // the insert fails

        seeder().seedIfNeeded() // logged, not thrown

        assertEquals(0, preferences.exerciseSeedVersion())
    }

    private fun builtIn(name: String) =
        ExerciseEntity(name = name, type = ExerciseType.STRENGTH, muscleGroup = "Chest", equipment = "Dumbbell", defaultRestSec = 90)
}
