package dev.saketanand.setwise.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/** What the user told the app about themselves (onboarding, editable in Settings). All optional. */
data class UserSettings(
    /** For calorie estimates; null until the user enters it. */
    val bodyWeightKg: Double? = null,
    /** Days they usually train; empty = not set (then every unlogged day may be asked about). */
    val trainingDays: Set<DayOfWeek> = emptySet(),
    /** Whether to ask about past days without a workout (day check-in). On by default. */
    val askAboutUnloggedDays: Boolean = true,
    /** Onboarding was finished or skipped: it isn't shown again. */
    val onboardingDone: Boolean = false,
    /** The day the check-in was last shown (it's shown at most once a day). */
    val checkInLastAskedOn: LocalDate? = null,
    /** Monday of the week whose summary card was closed: it stays closed. */
    val weeklySummaryDismissedWeek: LocalDate? = null,
    /** The on-device recap of a week (Monday), written once and kept. */
    val weeklyRecap: WeeklyRecap? = null,
    /** What to call them; null = not given. */
    val name: String? = null,
    /** Stored as a birth year, so the age stays right; null = not given. */
    val birthYear: Int? = null,
    /** For the BMR formula only; null = not given. */
    val sex: Sex? = null,
    val heightCm: Double? = null,
    /** Rest after every set; null = each exercise's own (its library default). */
    val restSecOverride: Int? = null,
    /** "Rest over": sound and vibration (each can be off). */
    val restSound: Boolean = true,
    val restVibrate: Boolean = true,
    /** An ongoing notification while a workout runs (time, sets done; the rest countdown). */
    val workoutNotification: Boolean = true,
) {
    /** Age this year (from the birth year). */
    fun ageOn(today: LocalDate): Int? = birthYear?.let { today.year - it }

    companion object {
        /** A plausible body weight; anything outside is a typo. */
        val BODY_WEIGHT_RANGE_KG = 20.0..400.0

        /** The default rests offered (seconds). */
        val REST_CHOICES = listOf(30, 45, 60, 90, 120, 150, 180, 240, 300)
    }
}

/** The weekly summary's recap in words, for the week starting [weekStart]. */
data class WeeklyRecap(val weekStart: LocalDate, val text: String)
