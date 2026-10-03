package dev.saketanand.setwise.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.saketanand.setwise.domain.model.UserSettings
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
            bodyWeightKg = prefs[BODY_WEIGHT_KG],
            trainingDays = prefs[TRAINING_DAYS].orEmpty()
                .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
                .toSet(),
            askAboutUnloggedDays = prefs[ASK_ABOUT_UNLOGGED_DAYS] ?: true,
            onboardingDone = prefs[ONBOARDING_DONE] ?: false,
            checkInLastAskedOn = prefs[CHECK_IN_LAST_ASKED_ON]?.let(LocalDate::ofEpochDay),
        )
    }

    override suspend fun setBodyWeightKg(kg: Double?): Boolean {
        if (kg != null && kg !in UserSettings.BODY_WEIGHT_RANGE_KG) return false
        dataStore.edit { prefs -> if (kg == null) prefs.remove(BODY_WEIGHT_KG) else prefs[BODY_WEIGHT_KG] = kg }
        return true
    }

    override suspend fun setTrainingDays(days: Set<DayOfWeek>) {
        dataStore.edit { it[TRAINING_DAYS] = days.mapTo(HashSet()) { day -> day.name } }
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

    private companion object {
        val BODY_WEIGHT_KG = doublePreferencesKey("body_weight_kg")
        val TRAINING_DAYS = stringSetPreferencesKey("training_days")
        val ASK_ABOUT_UNLOGGED_DAYS = booleanPreferencesKey("ask_about_unlogged_days")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val CHECK_IN_LAST_ASKED_ON = longPreferencesKey("check_in_last_asked_on")
    }
}
