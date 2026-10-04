package dev.saketanand.setwise.testing

import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.domain.model.WeeklyRecap
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import dev.saketanand.setwise.domain.model.BodyRules
import dev.saketanand.setwise.domain.model.Sex

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
    override suspend fun setName(name: String?) = settings.update { it.copy(name = name?.trim()?.takeIf { n -> n.isNotEmpty() }) }
    override suspend fun setAge(age: Int?, today: LocalDate): Boolean {
        if (age != null && age !in BodyRules.AGE_YEARS) return false
        settings.update { it.copy(birthYear = age?.let { a -> today.year - a }) }
        return true
    }
    override suspend fun setSex(sex: Sex?) = settings.update { it.copy(sex = sex) }
    override suspend fun setHeightCm(cm: Double?): Boolean {
        if (cm != null && cm !in BodyRules.HEIGHT_CM) return false
        settings.update { it.copy(heightCm = cm) }
        return true
    }
    override suspend fun setRestSecOverride(seconds: Int?) = settings.update { it.copy(restSecOverride = seconds) }
    override suspend fun setRestSound(on: Boolean) = settings.update { it.copy(restSound = on) }
    override suspend fun setRestVibrate(on: Boolean) = settings.update { it.copy(restVibrate = on) }
    override suspend fun setWorkoutNotification(on: Boolean) = settings.update { it.copy(workoutNotification = on) }
}
