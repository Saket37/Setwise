package dev.saketanand.setwise.domain.model

import java.time.Instant

/** One completed set of a finished workout, with its workout and exercise (history questions). */
data class LoggedSetRecord(
    val workoutId: Long,
    val workoutName: String,
    val startedAt: Instant,
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String,
    /** 1, 2, 3… within the exercise. */
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val distanceKm: Double?,
    val isPr: Boolean,
)
