package dev.saketanand.setwise.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/** How a day looks in History's strip. */
enum class DayState {
    /** A finished workout that day. */
    Trained,

    /** The user said rest, or it's not one of their training days. */
    Rest,

    /** The user said missed. */
    Missed,

    /** A past day nobody has said anything about yet. */
    Unanswered,

    /** Today, a future day, or before the user started (nothing to show). */
    None,
}

/**
 * The day check-in rules: which past days without a workout to ask about, and how each day is
 * shown. Built not to nag: only the last [LOOKBACK_DAYS] days, never before the first workout,
 * only planned days when training days are set, at most once a day, never during a workout.
 */
object DayCheckIn {

    const val LOOKBACK_DAYS = 7

    /** Days to ask about, newest first. */
    fun daysToAsk(
        today: LocalDate,
        trainedDays: Set<LocalDate>,
        marks: Map<LocalDate, DayStatus>,
        trainingDays: Set<DayOfWeek>,
        firstWorkoutDate: LocalDate?,
    ): List<LocalDate> {
        firstWorkoutDate ?: return emptyList() // nothing to compare with before the first workout
        return (1L..LOOKBACK_DAYS).map { today.minusDays(it) }.filter { day ->
            !day.isBefore(firstWorkoutDate) &&
                day !in trainedDays &&
                day !in marks &&
                (trainingDays.isEmpty() || day.dayOfWeek in trainingDays)
        }
    }

    /** Whether to show the check-in now. [lastAskedOn]: the day it was last shown. */
    fun shouldAsk(
        settings: UserSettings,
        today: LocalDate,
        lastAskedOn: LocalDate?,
        hasActiveWorkout: Boolean,
        daysToAsk: List<LocalDate>,
    ): Boolean = settings.askAboutUnloggedDays && !hasActiveWorkout && lastAskedOn != today && daysToAsk.isNotEmpty()

    fun stateOf(
        day: LocalDate,
        today: LocalDate,
        trainedDays: Set<LocalDate>,
        marks: Map<LocalDate, DayStatus>,
        trainingDays: Set<DayOfWeek>,
        firstWorkoutDate: LocalDate?,
    ): DayState = when {
        day in trainedDays -> DayState.Trained
        marks[day] == DayStatus.Rest -> DayState.Rest
        marks[day] == DayStatus.Missed -> DayState.Missed
        !day.isBefore(today) -> DayState.None
        firstWorkoutDate == null || day.isBefore(firstWorkoutDate) -> DayState.None
        trainingDays.isNotEmpty() && day.dayOfWeek !in trainingDays -> DayState.Rest
        else -> DayState.Unanswered
    }
}
