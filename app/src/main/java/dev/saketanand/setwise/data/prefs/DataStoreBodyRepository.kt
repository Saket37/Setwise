package dev.saketanand.setwise.data.prefs

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.repository.BodyRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [BodyRepository] as JSON in the app's DataStore: a few checks a month, so no database table
 * (the schema stays as it is until it's frozen for release).
 */
class DataStoreBodyRepository(private val dataStore: DataStore<Preferences>) : BodyRepository {

    override fun observeMeasurements(): Flow<List<BodyMeasurement>> =
        dataStore.data.map { prefs -> decode(prefs[MEASUREMENTS]).sortedWith(NEWEST_FIRST) }

    override suspend fun add(measurement: BodyMeasurement) {
        dataStore.edit { prefs ->
            val all = (decode(prefs[MEASUREMENTS]).filter { it.id != measurement.id } + measurement).sortedWith(NEWEST_FIRST)
            prefs[MEASUREMENTS] = encode(all)
            // The newest weight is the profile's (calorie estimates use it).
            all.firstOrNull { it.weightKg != null }?.weightKg?.let { prefs[BODY_WEIGHT_KG] = it }
        }
    }

    override suspend fun delete(id: Long) {
        dataStore.edit { prefs -> prefs[MEASUREMENTS] = encode(decode(prefs[MEASUREMENTS]).filter { it.id != id }) }
    }

    @Serializable
    private data class Stored(
        val id: Long,
        val day: Long,
        val weightKg: Double? = null,
        val bodyFatPercent: Double? = null,
        val muscleMassKg: Double? = null,
        val bmrKcal: Int? = null,
        val visceralFat: Double? = null,
        val source: String = "Manual",
    )

    private fun decode(json: String?): List<BodyMeasurement> = if (json.isNullOrEmpty()) {
        emptyList()
    } else {
        runCatching { JSON.decodeFromString<List<Stored>>(json) }
            .onFailure { Log.e(TAG, "Unreadable body measurements", it) }
            .getOrDefault(emptyList())
            .map {
                BodyMeasurement(
                    it.id, LocalDate.ofEpochDay(it.day), it.weightKg, it.bodyFatPercent, it.muscleMassKg, it.bmrKcal, it.visceralFat,
                    BodyMeasurement.Source.entries.firstOrNull { source -> source.name == it.source } ?: BodyMeasurement.Source.Manual,
                )
            }
    }

    private fun encode(all: List<BodyMeasurement>): String = JSON.encodeToString(
        all.map { Stored(it.id, it.measuredOn.toEpochDay(), it.weightKg, it.bodyFatPercent, it.muscleMassKg, it.bmrKcal, it.visceralFat, it.source.name) },
    )

    private companion object {
        const val TAG = "DataStoreBodyRepository"
        val JSON = Json { ignoreUnknownKeys = true }
        val MEASUREMENTS = stringPreferencesKey("body_measurements")

        /** Same key as the profile's body weight (DataStoreUserSettingsRepository). */
        val BODY_WEIGHT_KG = doublePreferencesKey("body_weight_kg")
        val NEWEST_FIRST = compareByDescending<BodyMeasurement> { it.measuredOn }.thenByDescending { it.id }
    }
}
