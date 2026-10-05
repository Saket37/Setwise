package dev.saketanand.setwise.data.prefs

import android.database.sqlite.SQLiteFullException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import dev.saketanand.setwise.data.local.SetwiseDatabase
import dev.saketanand.setwise.data.local.dao.BodyMeasurementDao
import dev.saketanand.setwise.data.local.entity.BodyMeasurementEntity
import dev.saketanand.setwise.data.mapper.toDomain
import dev.saketanand.setwise.domain.model.BodyMeasurement
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Older builds' DataStore measurements, copied into the database once. */
@RunWith(RobolectricTestRunner::class)
class LegacyBodyMeasurementsTest {

    @get:Rule val folder = TemporaryFolder()

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SetwiseDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dataStore by lazy { PreferenceDataStoreFactory.create(produceFile = { File(folder.root, "test.preferences_pb") }) }
    private val legacy by lazy { LegacyBodyMeasurements(dataStore, db.bodyMeasurementDao()) }

    @After fun tearDown() {
        if (db.isOpen) db.close()
    }

    private fun on(day: Int) = LocalDate.of(2026, 9, day)
    private suspend fun stored() = dataStore.data.first()[MEASUREMENTS]
    private suspend fun inDatabase() = db.bodyMeasurementDao().observeAll().first().map { it.toDomain() }

    @Test
    fun `stored measurements move into the database, then are cleared`() = runTest {
        // An older build's shape (no source, muscle or visceral) and a newer one's (an unknown
        // field, a source this build doesn't know).
        dataStore.edit {
            it[MEASUREMENTS] = """[{"id": 1, "day": ${on(1).toEpochDay()}, "weightKg": 80.0},""" +
                """{"id": 2, "day": ${on(2).toEpochDay()}, "weightKg": 79.5, "bmrKcal": 1700, "source": "Watch", "hydration": 55},""" +
                """{"id": 3, "day": ${on(3).toEpochDay()}, "bodyFatPercent": 18.0, "source": "Report"}]"""
        }

        legacy.moveToDatabase()

        assertEquals(
            listOf(
                BodyMeasurement(3, on(3), bodyFatPercent = 18.0, source = BodyMeasurement.Source.Report),
                BodyMeasurement(2, on(2), weightKg = 79.5, bmrKcal = 1_700),
                BodyMeasurement(1, on(1), weightKg = 80.0),
            ),
            inDatabase(),
        )
        assertNull(stored())
    }

    @Test
    fun `nothing stored, nothing to do`() = runTest {
        legacy.moveToDatabase()
        assertTrue(inDatabase().isEmpty())
    }

    @Test
    fun `an unreadable value is cleared, adding nothing`() = runTest {
        dataStore.edit { it[MEASUREMENTS] = "{not json" }

        legacy.moveToDatabase()

        assertTrue(inDatabase().isEmpty())
        assertNull(stored())
    }

    @Test
    fun `measurements that fail to move are kept for the next start`() = runTest {
        val json = """[{"id": 1, "day": ${on(1).toEpochDay()}, "weightKg": 80.0}]"""
        dataStore.edit { it[MEASUREMENTS] = json }
        val diskFull = object : BodyMeasurementDao by db.bodyMeasurementDao() {
            override suspend fun insertAll(measurements: List<BodyMeasurementEntity>) = throw SQLiteFullException("disk full")
        }

        LegacyBodyMeasurements(dataStore, diskFull).moveToDatabase() // logged, not thrown

        assertEquals(json, stored())
    }

    private companion object {
        val MEASUREMENTS = stringPreferencesKey("body_measurements")
    }
}
