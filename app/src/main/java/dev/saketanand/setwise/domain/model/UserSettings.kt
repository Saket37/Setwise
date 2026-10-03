package dev.saketanand.setwise.domain.model

import java.time.DayOfWeek

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
) {
    companion object {
        /** A plausible body weight; anything outside is a typo. */
        val BODY_WEIGHT_RANGE_KG = 20.0..400.0
    }
}
