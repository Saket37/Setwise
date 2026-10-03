package dev.saketanand.setwise.domain.model

import java.time.Instant

/** A workout with everything logged in it (the Active workout screen; later Summary). */
data class WorkoutSession(
    val id: Long,
    val name: String,
    /** Template it was started from; null for an empty workout. */
    val templateId: Long?,
    val startedAt: Instant,
    /** Null while the workout is running. */
    val endedAt: Instant?,
    /** In display order. */
    val exercises: List<SessionExercise>,
    /** Estimated once finished (and a body weight is known); null until then. */
    val calories: Int? = null,
    val intensity: Intensity? = null,
    /** The on-device model's insight (summary screen); null until written. */
    val insight: String? = null,
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
    /** Best sets of this exercise in workouts before this one: what records are measured against. */
    val bestsBefore: PersonalBests = PersonalBests.None,
) {
    /** This workout's record in this exercise, if any. */
    val personalRecord: PersonalRecord? get() = PersonalRecords.find(exercise, sets, bestsBefore)
}

data class WorkoutSet(
    val id: Long,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val isCompleted: Boolean,
    val isPr: Boolean,
    /** Cardio exercises only: the logged time, distance, etc. Null for other exercises. */
    val cardio: CardioValues? = null,
    /** When it was ticked off (rest times are the gaps between these). */
    val completedAt: Instant? = null,
)
