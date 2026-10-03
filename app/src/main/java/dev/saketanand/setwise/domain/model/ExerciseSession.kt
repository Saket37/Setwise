package dev.saketanand.setwise.domain.model

import java.time.Instant

/** One finished workout's sets of a single exercise (Exercise detail). */
data class ExerciseSession(
    val workoutId: Long,
    val startedAt: Instant,
    /** Completed sets, in the order done. */
    val sets: List<LoggedSet>,
)

/** A completed set: weight × reps, bodyweight reps, a timed hold, or cardio. */
data class LoggedSet(
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val distanceKm: Double?,
)
