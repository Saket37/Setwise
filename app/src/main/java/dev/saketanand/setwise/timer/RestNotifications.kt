package dev.saketanand.setwise.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.saketanand.setwise.MainActivity
import dev.saketanand.setwise.R
import dev.saketanand.setwise.service.WorkoutTimerService

/**
 * The rest timer's notification channels and notifications, in one place.
 *
 * - [CHANNEL_REST]: the ongoing countdown. Default importance so it sits with the regular
 *   notifications (Low would file it under "Silent", collapsed, hiding +15s / Skip), but with
 *   no sound or vibration of its own.
 * - [CHANNEL_REST_OVER]: "Rest over". High importance: sound + vibration + pop-up, as the user
 *   set the channel up (and respecting Do Not Disturb).
 */
object RestNotifications {

    const val CHANNEL_REST = "rest_countdown"
    const val CHANNEL_REST_OVER = "rest_over"
    const val ID_COUNTDOWN = 1
    const val ID_REST_OVER = 2

    /** Safe to call on every app start: creating an existing channel changes nothing. */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REST, context.getString(R.string.channel_rest_timer), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_rest_timer_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REST_OVER, context.getString(R.string.channel_rest_over), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_rest_over_description)
                enableVibration(true)
            }
        )
    }

    /** Ongoing countdown: "Rest · next set 3", ticking down by itself, with +15s and Skip. */
    fun countdown(context: Context, rest: RestTimerState?, remainingMillis: Long): Notification =
        NotificationCompat.Builder(context, CHANNEL_REST)
            .setSmallIcon(R.drawable.ic_stat_setwise)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(rest?.let { label(context, it.next) } ?: context.getString(R.string.rest))
            .setContentText(context.getString(R.string.rest_notification_text))
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // The system counts down to "when" itself, so the notification needn't be updated every second.
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(System.currentTimeMillis() + remainingMillis)
            .addAction(0, context.getString(R.string.rest_notification_add), serviceAction(context, WorkoutTimerService.ACTION_ADD_15))
            .addAction(0, context.getString(R.string.skip), serviceAction(context, WorkoutTimerService.ACTION_SKIP))
            .build()

    /** "Rest over · Time for set 3". Disappears by itself after a minute. */
    fun restOver(context: Context, rest: RestTimerState): Notification =
        NotificationCompat.Builder(context, CHANNEL_REST_OVER)
            .setSmallIcon(R.drawable.ic_stat_setwise)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(context.getString(R.string.rest_over))
            .setContentText(
                when (val next = rest.next) {
                    is NextUp.Set -> context.getString(R.string.rest_over_next_set, next.number)
                    is NextUp.Exercise -> context.getString(R.string.rest_over_next_exercise, next.name)
                    NextUp.Nothing -> context.getString(R.string.rest_over_next_nothing)
                }
            )
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun label(context: Context, next: NextUp): String = when (next) {
        is NextUp.Set -> context.getString(R.string.rest_next_set, next.number)
        is NextUp.Exercise -> context.getString(R.string.rest_next_exercise, next.name)
        NextUp.Nothing -> context.getString(R.string.rest)
    }

    // TODO: open the active workout directly (deep link) instead of the app's start screen.
    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceAction(context: Context, action: String): PendingIntent = PendingIntent.getService(
        context,
        action.hashCode(),
        Intent(context, WorkoutTimerService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
