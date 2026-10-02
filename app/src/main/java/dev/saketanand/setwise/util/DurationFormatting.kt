package dev.saketanand.setwise.util

import kotlin.time.Duration

/** Compact total time for stat tiles: "45m", "3h 24m", "2h", "0m". */
fun Duration.toShortDurationLabel(): String {
    val totalMinutes = inWholeMinutes.coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}

/** Running clock: "4:05", "12:34", "1:02:03". */
fun Duration.toClockLabel(): String {
    val totalSeconds = inWholeSeconds.coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
