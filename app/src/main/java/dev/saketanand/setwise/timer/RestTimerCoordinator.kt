package dev.saketanand.setwise.timer

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dev.saketanand.setwise.domain.model.ActiveWorkout
import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.service.WorkoutTimerService
import dev.saketanand.setwise.ui.workout.isLoggedAfterwards
import dev.saketanand.setwise.util.DateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the ongoing notification shows: a rest's countdown, else the live workout. */
sealed interface Ongoing {
    data class Rest(val rest: RestTimerState) : Ongoing

    data class Workout(val workout: ActiveWorkout) : Ongoing
}

/**
 * Connects the [RestTimer] and the running workout to the system, for the whole life of the app
 * (started in SetwiseApp):
 * - while resting, or while a workout runs (if Settings' workout notification is on, and it
 *   isn't one logged afterwards) → [WorkoutTimerService] shows [ongoing] (the countdown, or the
 *   workout's time and sets) and stops itself when there's nothing to show;
 * - a rest runs out → alert as Settings say (sound, vibration): in-app while the app is on
 *   screen, otherwise a "Rest over" notification (or in-app if notifications aren't allowed).
 *
 * @param scope main-thread scope that lives as long as the app.
 */
class RestTimerCoordinator(
    private val context: Context,
    private val restTimer: RestTimer,
    private val restAlert: RestAlert,
    workoutRepository: WorkoutRepository,
    userSettings: UserSettingsRepository,
    private val dateProvider: DateProvider,
    private val scope: CoroutineScope,
) : RestNotificationRefresher {

    private val settings: StateFlow<UserSettings> = userSettings.settings
        .catch { e -> Log.e(TAG, "Reading settings failed", e) }
        .stateIn(scope, SharingStarted.Eagerly, UserSettings())

    private val activeWorkout = workoutRepository.observeActiveWorkout()
        .catch { e -> Log.e(TAG, "Reading the running workout failed", e); emit(null) }

    /** What the ongoing notification shows now; null: nothing (the service stops). */
    val ongoing: StateFlow<Ongoing?> = combine(restTimer.state, activeWorkout, settings) { rest, workout, settings ->
        when {
            rest != null -> Ongoing.Rest(rest)
            workout != null && settings.workoutNotification && !isLoggedAfterwards(workout.startedAt, dateProvider.now()) -> Ongoing.Workout(workout)
            else -> null
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun start() {
        scope.launch {
            restTimer.state.map { it != null }.distinctUntilChanged().collect { resting ->
                // A new rest: the last "Rest over" is old news.
                if (resting) NotificationManagerCompat.from(context).cancel(RestNotifications.ID_REST_OVER)
            }
        }
        scope.launch {
            ongoing.map { it != null }.distinctUntilChanged().collect { show -> if (show) startService() }
        }
        scope.launch {
            restTimer.finished.collect { rest -> alert(rest) }
        }
    }

    override fun refresh() {
        // The service posts its notification on every start command; it's already running, so
        // this just shows it again (now that it's allowed).
        if (ongoing.value != null) startService(WorkoutTimerService.ACTION_REFRESH)
    }

    private fun startService(action: String? = null) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, WorkoutTimerService::class.java).setAction(action))
        } catch (e: IllegalStateException) {
            // Android 12+ refuses to start one from the background (e.g. the app opened by a
            // notification's action). The timer still works in the app, just without it.
            Log.w(TAG, "Couldn't start the workout service", e)
        }
    }

    private fun alert(rest: RestTimerState) {
        val settings = settings.value
        if (!settings.restSound && !settings.restVibrate) return
        val onScreen = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (!onScreen && canNotify()) {
            try {
                NotificationManagerCompat.from(context).notify(
                    RestNotifications.ID_REST_OVER,
                    RestNotifications.restOver(context, rest, sound = settings.restSound, vibrate = settings.restVibrate),
                )
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification permission was revoked", e)
            }
        }
        restAlert.play(sound = settings.restSound, vibrate = settings.restVibrate)
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
