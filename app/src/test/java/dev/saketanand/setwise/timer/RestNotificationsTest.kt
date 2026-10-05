package dev.saketanand.setwise.timer

import android.app.Notification
import android.app.NotificationManager
import dev.saketanand.setwise.domain.model.ActiveWorkout
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RestNotificationsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun rest(next: NextUp) = RestTimerState(workoutId = 7, endsAtElapsed = 90_000, totalMillis = 90_000, next = next)

    private val Notification.title get() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    private val Notification.text get() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    @Test
    fun `a silent countdown channel, three Rest over ones for the settings, a quiet workout one`() {
        RestNotifications.createChannels(context)
        RestNotifications.createChannels(context) // every app start: changes nothing

        val countdown = manager.getNotificationChannel(RestNotifications.CHANNEL_REST)
        assertNull(countdown.sound)
        assertFalse(countdown.shouldVibrate())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, countdown.importance)

        val withSound = manager.getNotificationChannel(RestNotifications.CHANNEL_REST_OVER)
        assertTrue(withSound.shouldVibrate())
        assertEquals(NotificationManager.IMPORTANCE_HIGH, withSound.importance)
        val vibrateOnly = manager.getNotificationChannel(RestNotifications.CHANNEL_REST_OVER_VIBRATE)
        assertNull(vibrateOnly.sound)
        assertTrue(vibrateOnly.shouldVibrate())
        val quiet = manager.getNotificationChannel(RestNotifications.CHANNEL_REST_OVER_QUIET)
        assertNull(quiet.sound)
        assertFalse(quiet.shouldVibrate())

        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(RestNotifications.CHANNEL_WORKOUT).importance)
        assertEquals(5, manager.notificationChannels.size)
    }

    @Test
    fun `Rest over goes to the channel Settings' sound and vibration choose`() {
        assertEquals(RestNotifications.CHANNEL_REST_OVER, RestNotifications.restOver(context, rest(NextUp.Nothing)).channelId)
        assertEquals(RestNotifications.CHANNEL_REST_OVER_VIBRATE, RestNotifications.restOver(context, rest(NextUp.Nothing), sound = false).channelId)
        assertEquals(
            RestNotifications.CHANNEL_REST_OVER_QUIET,
            RestNotifications.restOver(context, rest(NextUp.Nothing), sound = false, vibrate = false).channelId,
        )
    }

    @Test
    fun `Rest over says what's next`() {
        assertEquals("Rest over", RestNotifications.restOver(context, rest(NextUp.Set(3))).title)
        assertEquals("Time for set 3", RestNotifications.restOver(context, rest(NextUp.Set(3))).text)
        assertEquals("Time for Pull-up", RestNotifications.restOver(context, rest(NextUp.Exercise("Pull-up"))).text)
        assertEquals("Time to finish your workout", RestNotifications.restOver(context, rest(NextUp.Nothing)).text)
        assertTrue(RestNotifications.restOver(context, rest(NextUp.Nothing)).flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun `the countdown names the next set, counts down by itself and has +15s and Skip`() {
        val countdown = RestNotifications.countdown(context, rest(NextUp.Set(3)), remainingMillis = 56_000)

        assertEquals(RestNotifications.CHANNEL_REST, countdown.channelId)
        assertEquals("Rest · next set 3", countdown.title)
        assertTrue(countdown.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(countdown.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(2, countdown.actions.size)
        assertEquals("Rest", RestNotifications.countdown(context, null, 0).title) // the rest just ended
    }

    @Test
    fun `the workout notification shows its name and sets done`() {
        val started = Instant.parse("2026-10-03T12:30:00Z")
        val notification = RestNotifications.workout(context, ActiveWorkout(7, "Push Day", started, completedSets = 8))

        assertEquals(RestNotifications.CHANNEL_WORKOUT, notification.channelId)
        assertEquals("Push Day", notification.title)
        assertTrue(notification.text!!.startsWith("8 "))
        assertEquals(started.toEpochMilli(), notification.`when`)
    }
}
