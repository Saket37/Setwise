package dev.saketanand.setwise.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dev.saketanand.setwise.timer.RestNotifications
import dev.saketanand.setwise.timer.RestTimer
import dev.saketanand.setwise.timer.RestTimerState
import dev.saketanand.setwise.util.ElapsedClock
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import dev.saketanand.setwise.timer.Ongoing
import dev.saketanand.setwise.timer.RestTimerCoordinator

/**
 * Foreground service while there's something ongoing ([RestTimerCoordinator.ongoing]): the
 * workout in progress (its time and sets), or a rest's countdown, which keeps the CPU awake so
 * "Rest over" comes on time with the screen off. Started by RestTimerCoordinator; stops itself
 * when there's nothing to show. Also receives the countdown's +15s and Skip buttons.
 */
class WorkoutTimerService : LifecycleService() {

    private val restTimer: RestTimer by inject()
    private val coordinator: RestTimerCoordinator by inject()
    private val clock: ElapsedClock by inject()
    private var wakeLock: PowerManager.WakeLock? = null
    private var isObserving = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_ADD_15 -> restTimer.adjust(15)
            ACTION_SKIP -> restTimer.skip()
            ACTION_REFRESH -> Unit // just post the notification again, below
        }
        // Android requires startForeground soon after the service is started, even if there's
        // nothing to show anymore (it's then stopped right away below).
        show(coordinator.ongoing.value)
        if (!isObserving) {
            isObserving = true
            lifecycleScope.launch {
                coordinator.ongoing.collect { ongoing -> if (ongoing == null) stop() else show(ongoing) }
            }
        }
        return START_NOT_STICKY // after the process is killed, the app restarts it when it's opened
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun show(ongoing: Ongoing?) {
        val rest = (ongoing as? Ongoing.Rest)?.rest
        val remaining = rest?.remainingMillis(clock.elapsedMillis()) ?: 0
        val notification = when (ongoing) {
            is Ongoing.Workout -> RestNotifications.workout(this, ongoing.workout)
            else -> RestNotifications.countdown(this, rest, remaining)
        }
        ServiceCompat.startForeground(
            this,
            RestNotifications.ID_COUNTDOWN,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        // Awake only while resting (the alert must come on time); not for the whole workout.
        if (rest != null) holdWakeLock(remaining) else releaseWakeLock()
    }

    /** Keeps the CPU on until just after the rest ends (the timeout is a safety net). */
    private fun holdWakeLock(remainingMillis: Long) {
        releaseWakeLock()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Setwise:RestTimer")
            .apply {
                setReferenceCounted(false)
                acquire(remainingMillis + WAKE_LOCK_MARGIN_MS)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun stop() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_ADD_15 = "dev.saketanand.setwise.action.REST_ADD_15"
        const val ACTION_SKIP = "dev.saketanand.setwise.action.REST_SKIP"
        const val ACTION_REFRESH = "dev.saketanand.setwise.action.REST_REFRESH"
        private const val WAKE_LOCK_MARGIN_MS = 5_000L
    }
}
