package dev.saketanand.setwise.testing

import dev.saketanand.setwise.timer.NextUp
import dev.saketanand.setwise.timer.RestTimer
import dev.saketanand.setwise.timer.RestTimerState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Records what the screen asks of the rest timer; the state can be set by the test. */
class FakeRestTimer : RestTimer {
    override val state = MutableStateFlow<RestTimerState?>(null)
    override val finished = MutableSharedFlow<RestTimerState>()

    data class Start(val workoutId: Long, val durationSec: Int, val next: NextUp)

    val starts = mutableListOf<Start>()
    val adjustments = mutableListOf<Int>()
    var skips = 0
    val cancels = mutableListOf<Long>()

    override fun start(workoutId: Long, durationSec: Int, next: NextUp) {
        starts += Start(workoutId, durationSec, next)
    }

    override fun adjust(deltaSec: Int) {
        adjustments += deltaSec
    }

    override fun skip() {
        skips++
    }

    override fun cancel(workoutId: Long) {
        cancels += workoutId
    }
}
