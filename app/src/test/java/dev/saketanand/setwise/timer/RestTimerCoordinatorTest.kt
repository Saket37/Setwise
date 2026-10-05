package dev.saketanand.setwise.timer

import android.app.NotificationManager
import android.os.VibratorManager
import dev.saketanand.setwise.domain.model.ActiveWorkout
import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.service.WorkoutTimerService
import dev.saketanand.setwise.testing.FakeRestTimer
import dev.saketanand.setwise.testing.FakeUserSettingsRepository
import dev.saketanand.setwise.testing.StubWorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RestTimerCoordinatorTest {

    private val context = RuntimeEnvironment.getApplication()
    private val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val vibrator = shadowOf(context.getSystemService(VibratorManager::class.java).defaultVibrator)
    private val restTimer = FakeRestTimer()
    private val workouts = Workouts()
    private val settings = FakeUserSettingsRepository()

    private val rest = RestTimerState(workoutId = 7, endsAtElapsed = 90_000, totalMillis = 90_000, next = NextUp.Set(3))
    private val running = ActiveWorkout(7, "Push Day", NOW.minusSeconds(1_800), completedSets = 4)

    private fun TestScope.coordinator() =
        RestTimerCoordinator(context, restTimer, RestAlert(context), workouts, settings, Dates, backgroundScope).also { it.start() }

    private fun nextService() = shadowOf(context).nextStartedService

    @Test
    fun `nothing shows with no rest and no workout`() = runTest(UnconfinedTestDispatcher()) {
        assertNull(coordinator().ongoing.value)
        assertNull(nextService())
    }

    @Test
    fun `a running workout shows, and starts the service`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinator()
        workouts.active.value = running

        assertEquals(Ongoing.Workout(running), coordinator.ongoing.value)
        assertEquals(WorkoutTimerService::class.java.name, nextService()?.component?.className)
    }

    @Test
    fun `a rest shows over the workout`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinator()
        workouts.active.value = running
        restTimer.state.value = rest

        assertEquals(Ongoing.Rest(rest), coordinator.ongoing.value)
    }

    @Test
    fun `no workout notification when Settings turn it off, or for one logged afterwards`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinator()
        workouts.active.value = running.copy(startedAt = NOW.minusSeconds(7 * 3_600)) // started 7 hours ago
        assertNull(coordinator.ongoing.value)

        workouts.active.value = running
        settings.settings.value = UserSettings(workoutNotification = false)
        assertNull(coordinator.ongoing.value)
    }

    @Test
    fun `refresh posts the notification again only while something shows`() = runTest(UnconfinedTestDispatcher()) {
        val coordinator = coordinator()
        coordinator.refresh()
        assertNull(nextService())

        restTimer.state.value = rest
        nextService() // the start for the rest
        coordinator.refresh()
        assertEquals(WorkoutTimerService.ACTION_REFRESH, nextService()?.action)
    }

    @Test
    fun `a rest that runs out in the background posts Rest over on the settings' channel`() = runTest(UnconfinedTestDispatcher()) {
        settings.settings.value = UserSettings(restSound = false)
        coordinator()

        restTimer.finished.emit(rest)

        val restOver = notifications.getNotification(RestNotifications.ID_REST_OVER)
        assertEquals(RestNotifications.CHANNEL_REST_OVER_VIBRATE, restOver.channelId)
        assertFalse(vibrator.isVibrating) // the notification buzzes, not the app
    }

    @Test
    fun `a new rest clears the last Rest over`() = runTest(UnconfinedTestDispatcher()) {
        coordinator()
        restTimer.finished.emit(rest)
        assertTrue(notifications.getNotification(RestNotifications.ID_REST_OVER) != null)

        restTimer.state.value = rest.copy(next = NextUp.Set(4))

        assertNull(notifications.getNotification(RestNotifications.ID_REST_OVER))
    }

    @Test
    fun `with notifications blocked, the app itself buzzes`() = runTest(UnconfinedTestDispatcher()) {
        notifications.setNotificationsEnabled(false)
        coordinator()

        restTimer.finished.emit(rest)

        assertNull(notifications.getNotification(RestNotifications.ID_REST_OVER))
        assertTrue(vibrator.isVibrating)
    }

    @Test
    fun `with sound and vibration both off, a rest ends silently`() = runTest(UnconfinedTestDispatcher()) {
        settings.settings.value = UserSettings(restSound = false, restVibrate = false)
        coordinator()

        restTimer.finished.emit(rest)

        assertNull(notifications.getNotification(RestNotifications.ID_REST_OVER))
        assertFalse(vibrator.isVibrating)
    }

    private class Workouts : StubWorkoutRepository() {
        val active = MutableStateFlow<ActiveWorkout?>(null)
        override fun observeActiveWorkout(): Flow<ActiveWorkout?> = active
    }

    private object Dates : DateProvider {
        override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        override fun now(): Instant = NOW
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 3))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-03T13:00:00Z")
    }
}
