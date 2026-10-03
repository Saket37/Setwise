package dev.saketanand.setwise.timer

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dev.saketanand.setwise.service.WorkoutTimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Connects the [RestTimer] to the system, for the whole life of the app (started in SetwiseApp):
 * - a rest starts → start [WorkoutTimerService] (countdown notification, keeps counting in the
 *   background; it stops itself when the rest ends);
 * - a rest runs out → alert: in-app buzz + sound while the app is on screen, otherwise a
 *   "Rest over" notification (or the buzz + sound if notifications aren't allowed).
 *
 * @param scope main-thread scope that lives as long as the app.
 */
class RestTimerCoordinator(
    private val context: Context,
    private val restTimer: RestTimer,
    private val restAlert: RestAlert,
    private val scope: CoroutineScope,
) : RestNotificationRefresher {

    fun start() {
        scope.launch {
            restTimer.state.map { it != null }.distinctUntilChanged().collect { resting ->
                if (resting) {
                    // A new rest: the last "Rest over" is old news.
                    NotificationManagerCompat.from(context).cancel(RestNotifications.ID_REST_OVER)
                    startService()
                }
            }
        }
        scope.launch {
            restTimer.finished.collect { rest -> alert(rest) }
        }
    }

    override fun refresh() {
        // The service posts the countdown on every start command; it's already running, so this
        // just shows the notification again (now that it's allowed).
        if (restTimer.state.value != null) startService(WorkoutTimerService.ACTION_REFRESH)
    }

    private fun startService(action: String? = null) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, WorkoutTimerService::class.java).setAction(action))
        } catch (e: IllegalStateException) {
            // Android 12+ refuses to start one from the background (shouldn't happen: rests start
            // from a tap). The timer still works in the app, just without the notification.
            Log.w(TAG, "Couldn't start the rest timer service", e)
        }
    }

    private fun alert(rest: RestTimerState) {
        val onScreen = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (!onScreen && canNotify()) {
            try {
                NotificationManagerCompat.from(context).notify(RestNotifications.ID_REST_OVER, RestNotifications.restOver(context, rest))
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification permission was revoked", e)
            }
        }
        restAlert.play()
    }

    /**
     * On every Android version: notifications aren't blocked for the app (on 13+ this is also
     * false until POST_NOTIFICATIONS is granted; on older versions that permission doesn't exist,
     * so checking it directly would always say "denied").
     */
    private fun canNotify(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private companion object {
        const val TAG = "RestTimerCoordinator"
    }
}
