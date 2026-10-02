package dev.saketanand.setwise.domain.model

import java.time.Instant
import kotlin.time.Duration

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

/** Totals over a time window, e.g. this week. */
data class WorkoutStats(
    val workouts: Int,
    val timeTrained: Duration,
    val prs: Int,
)
