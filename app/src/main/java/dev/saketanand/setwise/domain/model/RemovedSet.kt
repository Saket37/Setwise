package dev.saketanand.setwise.domain.model

/** A set as it was just before it was deleted: enough to put it back (Undo). */
data class RemovedSet(
    val workoutExerciseId: Long,
    /** 1-based position it had. */
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val inclinePct: Double?,
    val speedMinKmh: Double?,
    val speedMaxKmh: Double?,
    val distanceKm: Double?,
    val level: Int?,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val isPr: Boolean,
)
