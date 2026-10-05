package dev.saketanand.setwise.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.saketanand.setwise.domain.model.BodyMeasurement
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Runs against a real DataStore file in a temp folder (plain JVM, no Android needed). */
class DataStoreBodyRepositoryTest {

    @get:Rule val folder = TemporaryFolder()

    // One DataStore per file: the body and settings repositories share it, as in the app.
    private val dataStore by lazy { PreferenceDataStoreFactory.create(produceFile = { File(folder.root, "test.preferences_pb") }) }
    private val body by lazy { DataStoreBodyRepository(dataStore) }
    private val settings by lazy { DataStoreUserSettingsRepository(dataStore) }

    private fun on(day: Int) = LocalDate.of(2026, 9, day)

    @Test
    fun `no measurements at first`() = runTest {
        assertTrue(body.observeMeasurements().first().isEmpty())
    }

    @Test
    fun `measurements are kept newest first, and the newest weight is the profile's`() = runTest {
        body.add(BodyMeasurement(1, on(1), weightKg = 80.0))
        body.add(BodyMeasurement(2, on(20), bodyFatPercent = 18.5)) // newer, no weight
        body.add(BodyMeasurement(3, on(10), weightKg = 78.4, bmrKcal = 1_750, source = BodyMeasurement.Source.Report))

        assertEquals(listOf(2L, 3L, 1L), body.observeMeasurements().first().map { it.id })
        assertEquals(BodyMeasurement(3, on(10), weightKg = 78.4, bmrKcal = 1_750, source = BodyMeasurement.Source.Report), body.observeMeasurements().first()[1])
        assertEquals(78.4, settings.settings.first().bodyWeightKg)
    }

    @Test
    fun `adding one with the same id edits it, and delete removes it`() = runTest {
        body.add(BodyMeasurement(1, on(1), weightKg = 80.0))
        body.add(BodyMeasurement(2, on(2), weightKg = 79.0))

        body.add(BodyMeasurement(1, on(1), weightKg = 81.0))
        assertEquals(listOf(79.0, 81.0), body.observeMeasurements().first().map { it.weightKg })

        body.delete(2)
        assertEquals(listOf(1L), body.observeMeasurements().first().map { it.id })
    }

    @Test
    fun `an unreadable value reads as no measurements`() = runTest {
        dataStore.edit { it[MEASUREMENTS] = "{not json" }
        assertTrue(body.observeMeasurements().first().isEmpty())
    }

    @Test
    fun `an older or newer stored shape still reads`() = runTest {
        // An older build's: no source, muscle mass or visceral fat. A newer one's: an unknown
        // field and a source this build doesn't know.
        dataStore.edit {
            it[MEASUREMENTS] = """[{"id": 1, "day": ${on(1).toEpochDay()}, "weightKg": 80.0},""" +
                """{"id": 2, "day": ${on(2).toEpochDay()}, "weightKg": 79.5, "source": "Watch", "hydration": 55}]"""
        }

        assertEquals(
            listOf(BodyMeasurement(2, on(2), weightKg = 79.5), BodyMeasurement(1, on(1), weightKg = 80.0)),
            body.observeMeasurements().first(),
        )
    }

    private companion object {
        val MEASUREMENTS = stringPreferencesKey("body_measurements")
    }
}
