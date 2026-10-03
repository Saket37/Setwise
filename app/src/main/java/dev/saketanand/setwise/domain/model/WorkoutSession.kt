package dev.saketanand.setwise.domain.model

import java.time.Instant

/** A workout with everything logged in it (the Active workout screen; later Summary). */
data class WorkoutSession(
    val id: Long,
    val name: String,
    val startedAt: Instant,
    /** Null while the workout is running. */
    val endedAt: Instant?,
    /** In display order. */
    val exercises: List<SessionExercise>,
)

/** One exercise in a workout, with its sets and what was done last time. */
data class SessionExercise(
    /** The workout_exercises row id (an exercise can appear twice in one workout). */
    val id: Long,
    val exercise: Exercise,
    /** In set order. */
    val sets: List<WorkoutSet>,
    /** Completed sets of the last finished session of this exercise, in order; empty if none. */
    val previousSets: List<PreviousSet>,
)

data class WorkoutSet(
    val id: Long,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val isCompleted: Boolean,
    val isPr: Boolean,
)
