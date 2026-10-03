package dev.saketanand.setwise.domain.model

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

/** A finished workout, as shown in overviews ("Last session: Pull Day"). */
data class FinishedWorkout(
    val id: Long,
    val name: String,
    val startedAt: Instant,
    val endedAt: Instant,
)

/** A workout that was started and not finished yet. */
data class ActiveWorkout(
    val id: Long,
    val name: String,
    val startedAt: Instant,
    val completedSets: Int,
)

/** A finished workout in the History list, with its totals (ticked-off sets only). */
data class WorkoutHistoryItem(
    val id: Long,
    val name: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val completedSets: Int,
    val volumeKg: Double,
    val distanceKm: Double,
    val personalRecords: Int,
    /** Null until calories are estimated (AI milestone). */
    val calories: Int?,
) {
    val duration: Duration get() = java.time.Duration.between(startedAt, endedAt).toKotlinDuration()
}

/** Totals over a time window, e.g. this week. */
data class WorkoutStats(
    val workouts: Int,
    val timeTrained: Duration,
    val prs: Int,
)
