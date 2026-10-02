package dev.saketanand.setwise.data.seed

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first

/** Remembers which version of exercises.json has already been loaded into the database. */
class SeedPreferences(private val dataStore: DataStore<Preferences>) {

    /** 0 if the library has never been seeded. */
    suspend fun exerciseSeedVersion(): Int = dataStore.data.first()[EXERCISE_SEED_VERSION] ?: 0

    suspend fun setExerciseSeedVersion(version: Int) {
        dataStore.edit { it[EXERCISE_SEED_VERSION] = version }
    }

    private companion object {
        val EXERCISE_SEED_VERSION = intPreferencesKey("exercise_seed_version")
    }
}
