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

/**
 * Foreground service for the rest timer: keeps the countdown notification up and the CPU awake
 * while resting, so the "rest over" alert comes on time with the screen off. Started by
 * RestTimerCoordinator when a rest starts; stops itself when it ends. Also receives the
 * notification's +15s and Skip buttons.
 */
class WorkoutTimerService : LifecycleService() {

    private val restTimer: RestTimer by inject()
    private val clock: ElapsedClock by inject()
    private var wakeLock: PowerManager.WakeLock? = null
    private var isObserving = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_ADD_15 -> restTimer.adjust(15)
            ACTION_SKIP -> restTimer.skip()
        }
        // Android requires startForeground soon after the service is started, even if the rest
        // has already ended in the meantime (it's then stopped right away below).
        showCountdown(restTimer.state.value)
        if (!isObserving) {
            isObserving = true
            lifecycleScope.launch {
                restTimer.state.collect { rest -> if (rest == null) stop() else showCountdown(rest) }
            }
        }
        return START_NOT_STICKY // after the process is killed there's no rest to resume
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun showCountdown(rest: RestTimerState?) {
        val remaining = rest?.remainingMillis(clock.elapsedMillis()) ?: 0
        ServiceCompat.startForeground(
            this,
            RestNotifications.ID_COUNTDOWN,
            RestNotifications.countdown(this, rest, remaining),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (rest != null) holdWakeLock(remaining)
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
        private const val WAKE_LOCK_MARGIN_MS = 5_000L
    }
}
