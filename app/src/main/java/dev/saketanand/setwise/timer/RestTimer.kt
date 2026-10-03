package dev.saketanand.setwise.timer

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The rest countdown between sets. One for the whole app: it keeps running when the active
 * workout screen is left, and the foreground service and the screen both show the same one.
 */
interface RestTimer {

    /** The rest in progress, or null. */
    val state: StateFlow<RestTimerState?>

    /** A rest that ran out (not one that was skipped or cancelled): time to alert the user. */
    val finished: SharedFlow<RestTimerState>

    /** Starts a rest, replacing any running one. */
    fun start(workoutId: Long, durationSec: Int, next: NextUp)

    /** ±15s. Taking it to zero or below ends the rest without an alert, like Skip. */
    fun adjust(deltaSec: Int)

    fun skip()

    /** Stops the rest if it belongs to [workoutId] (the workout was finished or discarded). */
    fun cancel(workoutId: Long)
}

/**
 * @param endsAtElapsed when it runs out, on the [dev.saketanand.setwise.util.ElapsedClock].
 * @param totalMillis the full length (changes with ±15s), for the progress bar.
 */
data class RestTimerState(
    val workoutId: Long,
    val endsAtElapsed: Long,
    val totalMillis: Long,
    val next: NextUp,
) {
    fun remainingMillis(nowElapsed: Long): Long = (endsAtElapsed - nowElapsed).coerceAtLeast(0)
}

/** What comes after the rest: "next set 3", or the next exercise. */
sealed interface NextUp {
    data class Set(val number: Int) : NextUp
    data class Exercise(val name: String) : NextUp
    /** The last set of the workout was just done. */
    data object Nothing : NextUp
}
