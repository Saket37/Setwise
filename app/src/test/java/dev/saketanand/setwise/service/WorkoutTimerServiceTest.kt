package dev.saketanand.setwise.service

import android.app.Notification
import android.content.Intent
import dev.saketanand.setwise.domain.model.ActiveWorkout
import dev.saketanand.setwise.testing.FakeRestTimer
import dev.saketanand.setwise.testing.FakeUserSettingsRepository
import dev.saketanand.setwise.testing.StubWorkoutRepository
import dev.saketanand.setwise.timer.NextUp
import dev.saketanand.setwise.timer.RestAlert
import dev.saketanand.setwise.timer.RestNotifications
import dev.saketanand.setwise.timer.RestTimer
import dev.saketanand.setwise.timer.RestTimerCoordinator
import dev.saketanand.setwise.timer.RestTimerState
import dev.saketanand.setwise.util.DateProvider
import dev.saketanand.setwise.util.ElapsedClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPowerManager

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WorkoutTimerServiceTest {

    private val context = RuntimeEnvironment.getApplication()
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val restTimer = FakeRestTimer()
    private val active = MutableStateFlow<ActiveWorkout?>(null)

    private val rest = RestTimerState(workoutId = 7, endsAtElapsed = 90_000, totalMillis = 90_000, next = NextUp.Set(3))

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher) // the service's lifecycleScope
        val workouts = object : StubWorkoutRepository() {
            override fun observeActiveWorkout(): Flow<ActiveWorkout?> = active
        }
        val dates = object : DateProvider {
            override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
            override fun now(): Instant = NOW
            override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 3))
        }
        val coordinator = RestTimerCoordinator(context, restTimer, RestAlert(context), workouts, FakeUserSettingsRepository(), dates, scope)
        startKoin {
            modules(
                module {
                    single<RestTimer> { restTimer }
                    single { coordinator }
                    single<ElapsedClock> { ElapsedClock { 30_000 } }
                },
            )
        }
    }

    @After fun tearDown() {
        stopKoin()
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun startService(action: String? = null) =
        Robolectric.buildService(WorkoutTimerService::class.java, Intent(context, WorkoutTimerService::class.java).setAction(action))
            .create()
            .startCommand(0, 1)
            .get()

    private val WorkoutTimerService.notification: Notification get() = shadowOf(this).lastForegroundNotification

    @Test
    fun `during a rest it shows the countdown and keeps the CPU awake`() {
        restTimer.state.value = rest

        val service = startService()

        assertEquals(RestNotifications.CHANNEL_REST, service.notification.channelId)
        assertEquals(RestNotifications.ID_COUNTDOWN, shadowOf(service).lastForegroundNotificationId)
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test
    fun `during a workout it shows the workout, without a wake lock`() {
        active.value = ActiveWorkout(7, "Push Day", NOW.minusSeconds(600), completedSets = 2)

        val service = startService()

        assertEquals(RestNotifications.CHANNEL_WORKOUT, service.notification.channelId)
        assertTrue(ShadowPowerManager.getLatestWakeLock()?.isHeld != true)
    }

    @Test
    fun `the countdown's buttons reach the rest timer`() {
        restTimer.state.value = rest
        startService(WorkoutTimerService.ACTION_ADD_15)
        startService(WorkoutTimerService.ACTION_SKIP)

        assertEquals(listOf(15), restTimer.adjustments)
        assertEquals(1, restTimer.skips)
    }

    @Test
    fun `it stops itself, and lets the CPU sleep, when the rest and workout end`() {
        restTimer.state.value = rest
        val service = startService()

        restTimer.state.value = null

        assertTrue(shadowOf(service).isStoppedBySelf)
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-03T13:00:00Z")
    }
}
