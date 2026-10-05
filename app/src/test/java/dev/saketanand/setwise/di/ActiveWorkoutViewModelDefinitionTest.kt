package dev.saketanand.setwise.di

import androidx.lifecycle.SavedStateHandle
import dev.saketanand.setwise.domain.ai.ExerciseAssistant
import dev.saketanand.setwise.domain.ai.QuickLogInterpreter
import dev.saketanand.setwise.domain.ai.SpeechInput
import dev.saketanand.setwise.domain.model.CreateExerciseResult
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.NewExercise
import dev.saketanand.setwise.domain.model.RecentExercise
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.testing.FakeRestTimer
import dev.saketanand.setwise.testing.FakeSpeechInput
import dev.saketanand.setwise.testing.FakeUserSettingsRepository
import dev.saketanand.setwise.testing.StubWorkoutRepository
import dev.saketanand.setwise.timer.RestNotificationRefresher
import dev.saketanand.setwise.timer.RestTimer
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.ui.workout.ActiveWorkoutEvent
import dev.saketanand.setwise.ui.workout.ActiveWorkoutViewModel
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

/** The active workout's Koin definition: it reads the route the screen passes, and survives a missing one. */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModelDefinitionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val observed = mutableListOf<Long>()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        val model = FakeOnDeviceModel()
        startKoin {
            modules(
                viewModelModule,
                module {
                    single<WorkoutRepository> {
                        object : StubWorkoutRepository() {
                            override fun observeSession(workoutId: Long): Flow<WorkoutSession?> {
                                observed += workoutId
                                return flowOf(if (workoutId == FINISHED.id) FINISHED else null)
                            }
                        }
                    }
                    single<DateProvider> { Dates }
                    factory { SavedStateHandle() }
                    single(ApplicationScope) { scope }
                    single<RestTimer> { FakeRestTimer() }
                    single { RestNotificationRefresher {} }
                    single { QuickLogInterpreter(model, ExerciseAssistant(model)) }
                    single<ExerciseRepository> { NoExercises }
                    single<SpeechInput> { FakeSpeechInput() }
                    single<UserSettingsRepository> { FakeUserSettingsRepository() }
                },
            )
        }
    }

    @After fun tearDown() {
        stopKoin()
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun viewModel(vararg params: Any) =
        KoinPlatform.getKoin().get<ActiveWorkoutViewModel> { parametersOf(*params) }

    @Test
    fun `the route's workout is opened, for editing when it says so`() = runTest(dispatcher) {
        val vm = viewModel(Route.ActiveWorkout(workoutId = 7, editingFinished = true))
        backgroundScope.launch { vm.state.collect {} }

        assertEquals(listOf(7L), observed)
        assertTrue(vm.state.value.isEditingFinished)
    }

    @Test
    fun `without a route the screen closes instead of crashing`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }

        assertEquals(listOf(Route.NO_WORKOUT_ID), observed)
        assertEquals(ActiveWorkoutEvent.Closed, vm.events.first())
    }

    private companion object {
        /** A finished workout: kept open only when it's opened for editing. */
        val FINISHED = WorkoutSession(
            id = 7, name = "Push Day", templateId = null,
            startedAt = Instant.parse("2026-10-04T12:30:00Z"), endedAt = Instant.parse("2026-10-04T13:40:00Z"), exercises = emptyList(),
        )
    }

    private object Dates : DateProvider {
        override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        override fun now(): Instant = LocalDate.of(2026, 10, 5).atTime(9, 0).atZone(zone).toInstant()
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 5))
    }

    private object NoExercises : ExerciseRepository {
        override fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>> = flowOf(emptyList())
        override fun observeMuscleGroups(): Flow<List<String>> = flowOf(emptyList())
        override fun observeRecentExercises(limit: Int): Flow<List<RecentExercise>> = flowOf(emptyList())
        override fun observeExerciseCount(): Flow<Int> = flowOf(0)
        override suspend fun createExercise(exercise: NewExercise): CreateExerciseResult = CreateExerciseResult.Created(0)
        override suspend fun getExercises(ids: List<Long>): List<Exercise> = emptyList()
        override fun observeExercise(id: Long): Flow<Exercise?> = flowOf(null)
    }
}
