package dev.saketanand.setwise.ui.importing

import dev.saketanand.setwise.domain.ai.ExerciseAssistant
import dev.saketanand.setwise.domain.ai.FileTextReader
import dev.saketanand.setwise.domain.ai.ImportReader
import dev.saketanand.setwise.domain.ai.TextReader
import dev.saketanand.setwise.domain.ai.WorkoutImporter
import dev.saketanand.setwise.domain.model.CreateExerciseResult
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.NewExercise
import dev.saketanand.setwise.domain.model.OcrLine
import dev.saketanand.setwise.domain.model.RecentExercise
import dev.saketanand.setwise.domain.model.SharedSet
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.testing.StubWorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImportViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    /** Two workouts in Strong's shared text: the 30th's is already in Setwise. */
    private val strongText = """
        Evening Workout
        Wednesday, 30 September 2026 at 8:01 pm

        Barbell Curl
        Set 1: 10 kg × 15 reps

        Morning Workout
        Tuesday, 29 September 2026 at 7:00 am

        Barbell Curl
        Set 1: 12.5 kg × 10 reps
    """.trimIndent()

    private val curl = Library.curl

    private var files: Map<String, suspend () -> String> = emptyMap()
    private var screenshots: Map<String, List<OcrLine>> = emptyMap()
    private val workouts = Workouts()
    private val model = FakeOnDeviceModel()

    private fun viewModel(shared: SharedImport = SharedImport()) = ImportViewModel(
        shared = shared,
        reader = ImportReader(
            textReader = object : TextReader { override suspend fun read(uri: String) = screenshots.getValue(uri) },
            fileReader = object : FileTextReader { override suspend fun read(uri: String, maxChars: Int) = files.getValue(uri)() },
            model = model,
            dateProvider = Dates,
        ),
        importer = WorkoutImporter(ExerciseAssistant(model), Library, workouts),
        exerciseRepository = Library,
        dateProvider = Dates,
    )

    @Test
    fun `shared text is read on open, and an already imported workout is marked`() = runTest(dispatcher) {
        workouts.existingStart = LocalDate.of(2026, 9, 30).atTime(20, 1).atZone(Dates.zone).toInstant()

        val state = viewModel(SharedImport(text = strongText)).state.value

        val plan = checkNotNull(state.plan)
        assertEquals(ImportReader.Source.StrongShare, state.source)
        assertEquals(listOf(true, false), plan.workouts.map { it.alreadyImported })
        assertEquals(curl, plan.workouts[1].exercises.single().match)
        assertEquals(1, plan.toImport.size)
    }

    @Test
    fun `importing saves only the new workouts`() = runTest(dispatcher) {
        workouts.existingStart = LocalDate.of(2026, 9, 30).atTime(20, 1).atZone(Dates.zone).toInstant()
        val vm = viewModel(SharedImport(text = strongText))

        vm.import()

        val result = checkNotNull(vm.state.value.result)
        assertEquals(1, result.workouts)
        assertEquals(listOf("Morning Workout"), workouts.imported.map { it.first })
        assertEquals(listOf(1L to listOf(SharedSet(12.5, 10))), workouts.imported.single().second)
    }

    @Test
    fun `a failed import keeps the preview and says so`() = runTest(dispatcher) {
        workouts.failImport = true
        val vm = viewModel(SharedImport(text = strongText))

        vm.import()

        val state = vm.state.value
        assertTrue(state.failed)
        assertFalse(state.isImporting)
        assertEquals(2, state.plan?.workouts?.size)
        assertNull(state.result)
    }

    @Test
    fun `nothing to import does nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.import() // no plan yet
        assertEquals(ImportUiState(), vm.state.value)

        workouts.existingStart = LocalDate.of(2026, 9, 30).atTime(20, 1).atZone(Dates.zone).toInstant()
        vm.read(strongText.substringBefore("\n\nMorning Workout")) // only the imported one
        vm.import()
        assertNull(vm.state.value.result)
        assertTrue(workouts.imported.isEmpty())
    }

    @Test
    fun `a shared CSV file is read on open`() = runTest(dispatcher) {
        files = mapOf("export.csv" to { "Date,Exercise,Weight (kg),Reps\n2026-09-29,Barbell Curl,20,8" })

        val state = viewModel(SharedImport(file = "export.csv")).state.value

        assertEquals(ImportReader.Source.Csv, state.source)
        assertEquals(SharedSet(20.0, 8), state.plan?.workouts?.single()?.shared?.exercises?.single()?.sets?.single())
    }

    @Test
    fun `shared screenshots are read on open`() = runTest(dispatcher) {
        screenshots = mapOf(
            "shot" to strongText.substringBefore("\n\nMorning Workout").lines().filter { it.isNotBlank() }
                .mapIndexed { i, line -> OcrLine(line, 0, i * 50, 500, i * 50 + 40) },
        )

        val state = viewModel(SharedImport(images = listOf("shot"))).state.value

        assertEquals("Evening Workout", state.plan?.workouts?.single()?.shared?.name)
    }

    @Test
    fun `a hand-written log's unread lines are counted`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.read("Arms 2 Oct 2026\nBarbell curl 20kg 8, 8\nfelt great")

        assertEquals(ImportReader.Source.Log, vm.state.value.source)
        assertEquals(1, vm.state.value.unreadLines)
    }

    @Test
    fun `nothing found, and a read that fails`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.read("just some notes, no workouts")
        assertTrue(vm.state.value.nothingFound)

        files = mapOf("broken.csv" to { error("Couldn't open it") })
        vm.readFile("broken.csv")
        assertTrue(vm.state.value.failed)
        assertNull(vm.state.value.plan)
    }

    @Test
    fun `blank text and no screenshots are ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.read("   ")
        vm.readImages(emptyList())
        assertEquals(ImportUiState(), vm.state.value)
    }

    @Test
    fun `a second read is ignored while one is running`() = runTest(dispatcher) {
        val slow = CompletableDeferred<String>()
        files = mapOf("slow.csv" to { slow.await() })
        val vm = viewModel(SharedImport(file = "slow.csv"))
        assertTrue(vm.state.value.isReading)

        vm.read(strongText)
        assertTrue(vm.state.value.isReading)

        slow.complete("Date,Exercise,Weight (kg),Reps\n2026-09-29,Barbell Curl,20,8")
        assertEquals(ImportReader.Source.Csv, vm.state.value.source) // the first read, not the text
    }

    private class Workouts : StubWorkoutRepository() {
        val imported = mutableListOf<Pair<String, List<Pair<Long, List<SharedSet>>>>>()
        var existingStart: Instant? = null
        var failImport = false
        override suspend fun importWorkout(name: String, startedAt: Instant, endedAt: Instant, exercises: List<Pair<Long, List<SharedSet>>>): Long {
            if (failImport) error("Disk full")
            imported += name to exercises
            return imported.size.toLong()
        }
        override suspend fun hasWorkoutStartedAt(startedAt: Instant) = startedAt == existingStart
    }

    private object Library : ExerciseRepository {
        val curl = Exercise(1, "Barbell Curl", ExerciseType.STRENGTH, "Biceps", "Barbell", 90, false, false, null, null, null)
        override fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>> = flowOf(listOf(curl))
        override fun observeMuscleGroups(): Flow<List<String>> = flowOf(emptyList())
        override fun observeRecentExercises(limit: Int): Flow<List<RecentExercise>> = flowOf(emptyList())
        override fun observeExerciseCount(): Flow<Int> = flowOf(1)
        override suspend fun createExercise(exercise: NewExercise): CreateExerciseResult = CreateExerciseResult.Created(50)
        override suspend fun getExercises(ids: List<Long>): List<Exercise> = emptyList()
        override fun observeExercise(id: Long): Flow<Exercise?> = flowOf(null)
    }

    private object Dates : DateProvider {
        override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        override fun now(): Instant = LocalDate.of(2026, 10, 5).atTime(9, 0).atZone(zone).toInstant()
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 5))
    }
}
