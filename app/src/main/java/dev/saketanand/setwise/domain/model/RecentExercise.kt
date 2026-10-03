package dev.saketanand.setwise.domain.model

/** An exercise the user did recently, and what they lifted the last time ("last 60 kg × 8"). */
data class RecentExercise(
    val exercise: Exercise,
    /** Top set of the last session; null if no set was completed. */
    val lastSet: PreviousSet?,
)

/**
 * A set from an earlier workout, shown for reference ("last 60 kg × 8").
 * Bodyweight sets have no [weightKg]; timed and cardio sets have neither value.
 */
data class PreviousSet(
    val weightKg: Double?,
    val reps: Int?,
)
