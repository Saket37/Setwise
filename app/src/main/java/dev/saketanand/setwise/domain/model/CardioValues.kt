package dev.saketanand.setwise.domain.model

/**
 * One cardio entry: what the cardio screen logs. Which fields an exercise uses comes from its
 * [Exercise.metrics]; the rest stay null.
 */
data class CardioValues(
    val durationSec: Int?,
    val inclinePct: Double? = null,
    val speedMinKmh: Double? = null,
    val speedMaxKmh: Double? = null,
    val distanceKm: Double? = null,
    /** Machine resistance level (bike, elliptical, rower, stairs). */
    val level: Int? = null,
) {
    val isEmpty: Boolean
        get() = durationSec == null && inclinePct == null && speedMinKmh == null && speedMaxKmh == null &&
            distanceKm == null && level == null
}

/** What the cardio screen needs: the exercise, this workout's entry and last session's. */
data class CardioEntry(
    val workoutId: Long,
    val exercise: Exercise,
    /** Logged in this workout; null until then. */
    val logged: CardioValues?,
    /** The last finished session's entry; null if never done. */
    val lastTime: CardioValues?,
)
