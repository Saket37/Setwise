package dev.saketanand.setwise.domain.repository

import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.domain.model.WeeklyRecap
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import dev.saketanand.setwise.domain.model.Sex

/** The user's settings, stored on the phone. */
interface UserSettingsRepository {

    /** Emits again whenever a setting changes. */
    val settings: Flow<UserSettings>

    /** Null clears it. Values outside [UserSettings.BODY_WEIGHT_RANGE_KG] are rejected (false). */
    suspend fun setBodyWeightKg(kg: Double?): Boolean

    /** Blank clears it. */
    suspend fun setName(name: String?)

    /** False (nothing saved) if the age is implausible. */
    suspend fun setAge(age: Int?, today: java.time.LocalDate): Boolean

    suspend fun setSex(sex: Sex?)

    /** Null: each exercise's own rest. */
    suspend fun setRestSecOverride(seconds: Int?)

    suspend fun setRestSound(on: Boolean)

    suspend fun setRestVibrate(on: Boolean)

    suspend fun setWorkoutNotification(on: Boolean)

    /** False (nothing saved) if the height is implausible. */
    suspend fun setHeightCm(cm: Double?): Boolean

    suspend fun setTrainingDays(days: Set<DayOfWeek>)

    suspend fun setAskAboutUnloggedDays(ask: Boolean)

    suspend fun setOnboardingDone()

    suspend fun setCheckInLastAskedOn(day: LocalDate)

    /** The week (Monday) whose summary was closed; null shows it again. */
    suspend fun setWeeklySummaryDismissed(weekStart: LocalDate?)

    suspend fun setWeeklyRecap(recap: WeeklyRecap)
}
