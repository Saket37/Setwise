package dev.saketanand.setwise.testing

import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.domain.model.WeeklyRecap
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory settings, with the same validation as the real one. */
class FakeUserSettingsRepository(initial: UserSettings = UserSettings()) : UserSettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun setBodyWeightKg(kg: Double?): Boolean {
        if (kg != null && kg !in UserSettings.BODY_WEIGHT_RANGE_KG) return false
        settings.update { it.copy(bodyWeightKg = kg) }
        return true
    }

    override suspend fun setTrainingDays(days: Set<DayOfWeek>) = settings.update { it.copy(trainingDays = days) }
    override suspend fun setAskAboutUnloggedDays(ask: Boolean) = settings.update { it.copy(askAboutUnloggedDays = ask) }
    override suspend fun setOnboardingDone() = settings.update { it.copy(onboardingDone = true) }
    override suspend fun setCheckInLastAskedOn(day: LocalDate) = settings.update { it.copy(checkInLastAskedOn = day) }
    override suspend fun setWeeklySummaryDismissed(weekStart: LocalDate?) = settings.update { it.copy(weeklySummaryDismissedWeek = weekStart) }
    override suspend fun setWeeklyRecap(recap: WeeklyRecap) = settings.update { it.copy(weeklyRecap = recap) }
}
