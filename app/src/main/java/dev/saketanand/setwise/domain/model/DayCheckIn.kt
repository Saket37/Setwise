package dev.saketanand.setwise.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * When the user trains: [days], or none set. [noFixedDays]: they picked "None", their days change
 * week to week (#156), which the check-in treats differently from never answering (#158).
 */
data class TrainingPlan(val days: Set<DayOfWeek> = emptySet(), val noFixedDays: Boolean = false)

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
 * With no fixed days ("None"), only after [NO_FIXED_DAYS_GAP] quiet days in a row that nobody
 * has answered: an answer resets it, so a long break isn't asked about every day (#158).
 */
object DayCheckIn {

    const val LOOKBACK_DAYS = 7

    /** With no fixed days: unanswered days in a row without a workout before they're asked about. */
    const val NO_FIXED_DAYS_GAP = 4

    /** When a workout logged for a past day starts; the user can change it on the workout. */
    val BACKFILL_START_TIME: LocalTime = LocalTime.of(18, 0)

    fun backfillStartedAt(day: LocalDate, zone: ZoneId): Instant = day.atTime(BACKFILL_START_TIME).atZone(zone).toInstant()

    /** Days to ask about, newest first. */
    fun daysToAsk(
        today: LocalDate,
        trainedDays: Set<LocalDate>,
        marks: Map<LocalDate, DayStatus>,
        plan: TrainingPlan,
        firstWorkoutDate: LocalDate?,
    ): List<LocalDate> {
        firstWorkoutDate ?: return emptyList() // nothing to compare with before the first workout
        fun quiet(day: LocalDate) = !day.isBefore(firstWorkoutDate) && day !in trainedDays && day !in marks
        if (plan.noFixedDays) {
            // The unanswered quiet days up to yesterday, if there are enough in a row.
            val run = (1L..LOOKBACK_DAYS).map { today.minusDays(it) }.takeWhile(::quiet)
            return if (run.size >= NO_FIXED_DAYS_GAP) run else emptyList()
        }
        return (1L..LOOKBACK_DAYS).map { today.minusDays(it) }.filter { day ->
            quiet(day) && (plan.days.isEmpty() || day.dayOfWeek in plan.days)
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
        plan: TrainingPlan,
        firstWorkoutDate: LocalDate?,
    ): DayState = when {
        day in trainedDays -> DayState.Trained
        marks[day] == DayStatus.Rest -> DayState.Rest
        marks[day] == DayStatus.Missed -> DayState.Missed
        !day.isBefore(today) -> DayState.None
        firstWorkoutDate == null || day.isBefore(firstWorkoutDate) -> DayState.None
        plan.days.isNotEmpty() && day.dayOfWeek !in plan.days -> DayState.Rest
        // No fixed days: a quiet day is only a question as part of a long enough quiet stretch.
        plan.noFixedDays && quietRunAround(day, today, trainedDays, marks, firstWorkoutDate) < NO_FIXED_DAYS_GAP -> DayState.None
        else -> DayState.Unanswered
    }

    /** How many unanswered days without a workout in a row (before today) [day] is part of. */
    private fun quietRunAround(
        day: LocalDate,
        today: LocalDate,
        trainedDays: Set<LocalDate>,
        marks: Map<LocalDate, DayStatus>,
        firstWorkoutDate: LocalDate,
    ): Int {
        fun quiet(d: LocalDate) = d.isBefore(today) && !d.isBefore(firstWorkoutDate) && d !in trainedDays && d !in marks
        val before = generateSequence(day.minusDays(1)) { it.minusDays(1) }.takeWhile(::quiet).count()
        val after = generateSequence(day.plusDays(1)) { it.plusDays(1) }.takeWhile(::quiet).count()
        return before + 1 + after
    }
}
