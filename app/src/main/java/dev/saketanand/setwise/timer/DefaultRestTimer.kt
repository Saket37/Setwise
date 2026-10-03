package dev.saketanand.setwise.timer

import dev.saketanand.setwise.util.ElapsedClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [RestTimer] in memory. A coroutine waits until the end and then reports it; the wait is
 * restarted whenever the end moves (±15s). Keeping the CPU awake meanwhile is the foreground
 * service's job (wake lock), so the delay isn't stretched by the phone sleeping.
 *
 * @param scope a main-thread scope that lives as long as the app (all calls come from the main
 *   thread: the screen and the notification's buttons).
 */
class DefaultRestTimer(
    private val scope: CoroutineScope,
    private val clock: ElapsedClock,
) : RestTimer {

    private val _state = MutableStateFlow<RestTimerState?>(null)
    override val state: StateFlow<RestTimerState?> = _state.asStateFlow()

    // Buffer of 1: the alert isn't lost if the collector is busy for a moment.
    private val _finished = MutableSharedFlow<RestTimerState>(extraBufferCapacity = 1)
    override val finished: SharedFlow<RestTimerState> = _finished.asSharedFlow()

    private var countdown: Job? = null

    override fun start(workoutId: Long, durationSec: Int, next: NextUp) {
        if (durationSec <= 0) return
        val total = durationSec * 1_000L
        set(RestTimerState(workoutId, clock.elapsedMillis() + total, total, next))
    }

    override fun adjust(deltaSec: Int) {
        val current = _state.value ?: return
        val delta = deltaSec * 1_000L
        val updated = current.copy(endsAtElapsed = current.endsAtElapsed + delta, totalMillis = current.totalMillis + delta)
        if (updated.remainingMillis(clock.elapsedMillis()) <= 0) skip() else set(updated)
    }

    override fun skip() = set(null)

    override fun cancel(workoutId: Long) {
        if (_state.value?.workoutId == workoutId) skip()
    }

    private fun set(newState: RestTimerState?) {
        countdown?.cancel()
        _state.value = newState
        if (newState == null) return
        countdown = scope.launch {
            delay(newState.remainingMillis(clock.elapsedMillis()))
            // Alert first, then clear: whoever shows the alert still sees which rest it was.
            _finished.tryEmit(newState)
            _state.value = null
        }
    }
}
