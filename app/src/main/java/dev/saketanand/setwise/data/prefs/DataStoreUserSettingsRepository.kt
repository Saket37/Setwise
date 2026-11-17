package dev.saketanand.setwise.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.saketanand.setwise.domain.model.BodyRules
import dev.saketanand.setwise.domain.model.Sex
import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.domain.model.WeeklyRecap
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [UserSettingsRepository] in the app's DataStore (the same file as the seed version).
 * Training days are stored as DayOfWeek names ("MONDAY"); unknown names are ignored.
 */
class DataStoreUserSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : UserSettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            name = prefs[NAME],
            birthYear = prefs[BIRTH_YEAR]?.toInt(),
            sex = prefs[SEX]?.let { name -> Sex.entries.firstOrNull { it.name == name } },
            heightCm = prefs[HEIGHT_CM],
            restSecOverride = prefs[REST_SEC]?.toInt(),
            restSound = prefs[REST_SOUND] ?: true,
            restVibrate = prefs[REST_VIBRATE] ?: true,
            workoutNotification = prefs[WORKOUT_NOTIFICATION] ?: true,
            bodyWeightKg = prefs[BODY_WEIGHT_KG],
            trainingDays = prefs[TRAINING_DAYS].orEmpty()
                .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
                .toSet(),
            noFixedTrainingDays = prefs[NO_FIXED_TRAINING_DAYS] ?: false,
            askAboutUnloggedDays = prefs[ASK_ABOUT_UNLOGGED_DAYS] ?: true,
            onboardingDone = prefs[ONBOARDING_DONE] ?: false,
            checkInLastAskedOn = prefs[CHECK_IN_LAST_ASKED_ON]?.let(LocalDate::ofEpochDay),
            weeklySummaryDismissedWeek = prefs[WEEKLY_SUMMARY_DISMISSED]?.let(LocalDate::ofEpochDay),
            weeklyRecap = prefs[WEEKLY_RECAP_WEEK]?.let { week -> prefs[WEEKLY_RECAP_TEXT]?.let { WeeklyRecap(LocalDate.ofEpochDay(week), it) } },
        )
    }

    override suspend fun setBodyWeightKg(kg: Double?): Boolean {
        if (kg != null && kg !in UserSettings.BODY_WEIGHT_RANGE_KG) return false
        dataStore.edit { prefs -> if (kg == null) prefs.remove(BODY_WEIGHT_KG) else prefs[BODY_WEIGHT_KG] = kg }
        return true
    }

    override suspend fun setTrainingDays(days: Set<DayOfWeek>, noFixedDays: Boolean) {
        dataStore.edit {
            it[TRAINING_DAYS] = days.mapTo(HashSet()) { day -> day.name }
            it[NO_FIXED_TRAINING_DAYS] = noFixedDays && days.isEmpty()
        }
    }

    override suspend fun setAskAboutUnloggedDays(ask: Boolean) {
        dataStore.edit { it[ASK_ABOUT_UNLOGGED_DAYS] = ask }
    }

    override suspend fun setOnboardingDone() {
        dataStore.edit { it[ONBOARDING_DONE] = true }
    }

    override suspend fun setCheckInLastAskedOn(day: LocalDate) {
        dataStore.edit { it[CHECK_IN_LAST_ASKED_ON] = day.toEpochDay() }
    }

    override suspend fun setWeeklySummaryDismissed(weekStart: LocalDate?) {
        dataStore.edit { if (weekStart != null) it[WEEKLY_SUMMARY_DISMISSED] = weekStart.toEpochDay() else it.remove(WEEKLY_SUMMARY_DISMISSED) }
    }

    override suspend fun setWeeklyRecap(recap: WeeklyRecap) {
        dataStore.edit {
            it[WEEKLY_RECAP_WEEK] = recap.weekStart.toEpochDay()
            it[WEEKLY_RECAP_TEXT] = recap.text
        }
    }

    override suspend fun setName(name: String?) {
        val trimmed = name?.trim()?.take(MAX_NAME)
        dataStore.edit { if (trimmed.isNullOrEmpty()) it.remove(NAME) else it[NAME] = trimmed }
    }

    override suspend fun setAge(age: Int?, today: LocalDate): Boolean {
        if (age != null && age !in BodyRules.AGE_YEARS) return false
        dataStore.edit { if (age == null) it.remove(BIRTH_YEAR) else it[BIRTH_YEAR] = (today.year - age).toLong() }
        return true
    }

    override suspend fun setSex(sex: Sex?) {
        dataStore.edit { if (sex == null) it.remove(SEX) else it[SEX] = sex.name }
    }

    override suspend fun setHeightCm(cm: Double?): Boolean {
        if (cm != null && cm !in BodyRules.HEIGHT_CM) return false
        dataStore.edit { if (cm == null) it.remove(HEIGHT_CM) else it[HEIGHT_CM] = cm }
        return true
    }

    override suspend fun setRestSecOverride(seconds: Int?) {
        dataStore.edit { if (seconds == null) it.remove(REST_SEC) else it[REST_SEC] = seconds.toLong() }
    }

    override suspend fun setRestSound(on: Boolean) {
        dataStore.edit { it[REST_SOUND] = on }
    }

    override suspend fun setRestVibrate(on: Boolean) {
        dataStore.edit { it[REST_VIBRATE] = on }
    }

    override suspend fun setWorkoutNotification(on: Boolean) {
        dataStore.edit { it[WORKOUT_NOTIFICATION] = on }
    }

    private companion object {
        val REST_SEC = longPreferencesKey("rest_sec_override")
        val REST_SOUND = booleanPreferencesKey("rest_sound")
        val REST_VIBRATE = booleanPreferencesKey("rest_vibrate")
        val WORKOUT_NOTIFICATION = booleanPreferencesKey("workout_notification")
        const val MAX_NAME = 40
        val NAME = stringPreferencesKey("name")
        val BIRTH_YEAR = longPreferencesKey("birth_year")
        val SEX = stringPreferencesKey("sex")
        val HEIGHT_CM = doublePreferencesKey("height_cm")
        val BODY_WEIGHT_KG = doublePreferencesKey("body_weight_kg")
        val TRAINING_DAYS = stringSetPreferencesKey("training_days")
        val NO_FIXED_TRAINING_DAYS = booleanPreferencesKey("no_fixed_training_days")
        val ASK_ABOUT_UNLOGGED_DAYS = booleanPreferencesKey("ask_about_unlogged_days")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val CHECK_IN_LAST_ASKED_ON = longPreferencesKey("check_in_last_asked_on")
        val WEEKLY_SUMMARY_DISMISSED = longPreferencesKey("weekly_summary_dismissed_week")
        val WEEKLY_RECAP_WEEK = longPreferencesKey("weekly_recap_week")
        val WEEKLY_RECAP_TEXT = stringPreferencesKey("weekly_recap_text")
    }
}
