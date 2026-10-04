package dev.saketanand.setwise.domain.repository

import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.domain.model.WeeklyRecap
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The user's settings, stored on the phone. */
interface UserSettingsRepository {

    /** Emits again whenever a setting changes. */
    val settings: Flow<UserSettings>

    /** Null clears it. Values outside [UserSettings.BODY_WEIGHT_RANGE_KG] are rejected (false). */
    suspend fun setBodyWeightKg(kg: Double?): Boolean

    suspend fun setTrainingDays(days: Set<DayOfWeek>)

    suspend fun setAskAboutUnloggedDays(ask: Boolean)

    suspend fun setOnboardingDone()

    suspend fun setCheckInLastAskedOn(day: LocalDate)

    /** The week (Monday) whose summary was closed; null shows it again. */
    suspend fun setWeeklySummaryDismissed(weekStart: LocalDate?)

    suspend fun setWeeklyRecap(recap: WeeklyRecap)
}
