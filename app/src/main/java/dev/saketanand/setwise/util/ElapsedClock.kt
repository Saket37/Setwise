package dev.saketanand.setwise.util

import android.os.SystemClock

/**
 * Milliseconds since boot, for countdowns: unlike the wall clock it never jumps when the user
 * (or the network) changes the time, and it keeps counting while the phone sleeps.
 */
fun interface ElapsedClock {
    fun elapsedMillis(): Long
}

object SystemElapsedClock : ElapsedClock {
    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()
}
