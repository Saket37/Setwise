package dev.saketanand.setwise.data.prefs

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.saketanand.setwise.data.local.dao.BodyMeasurementDao
import dev.saketanand.setwise.data.mapper.toEntity
import dev.saketanand.setwise.domain.model.BodyMeasurement
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Body measurements as older builds kept them: a JSON list in the app's DataStore. On startup
 * they're copied into the body_measurements table once, then cleared ([moveToDatabase]).
 */
class LegacyBodyMeasurements(
    private val dataStore: DataStore<Preferences>,
    private val bodyMeasurementDao: BodyMeasurementDao,
) {

    /** Copies any stored measurements into the database; clears them only once they're there. */
    suspend fun moveToDatabase() {
        // Any failure keeps them stored, for the next start; DataStore and Room don't narrow what they throw.
        @Suppress("TooGenericExceptionCaught")
        try {
            val json = dataStore.data.first()[MEASUREMENTS] ?: return
            val measurements = decode(json)
            if (measurements.isNotEmpty()) bodyMeasurementDao.insertAll(measurements.map { it.toEntity() })
            dataStore.edit { it.remove(MEASUREMENTS) }
            Log.i(TAG, "Moved ${measurements.size} body measurements into the database")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Moving body measurements failed; kept for the next start", e)
        }
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

    /** Unreadable: nothing (it can't be read later either). */
    private fun decode(json: String): List<BodyMeasurement> =
        runCatching { JSON.decodeFromString<List<Stored>>(json) }
            .onFailure { Log.e(TAG, "Unreadable body measurements", it) }
            .getOrDefault(emptyList())
            .map {
                BodyMeasurement(
                    it.id, LocalDate.ofEpochDay(it.day), it.weightKg, it.bodyFatPercent, it.muscleMassKg, it.bmrKcal, it.visceralFat,
                    BodyMeasurement.Source.entries.firstOrNull { source -> source.name == it.source } ?: BodyMeasurement.Source.Manual,
                )
            }

    private companion object {
        const val TAG = "LegacyBodyMeasurements"
        val JSON = Json { ignoreUnknownKeys = true }
        val MEASUREMENTS = stringPreferencesKey("body_measurements")
    }
}
