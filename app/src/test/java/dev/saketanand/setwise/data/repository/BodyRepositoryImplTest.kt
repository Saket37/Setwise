package dev.saketanand.setwise.data.repository

import androidx.room.Room
import dev.saketanand.setwise.data.local.SetwiseDatabase
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.BodyMetric
import dev.saketanand.setwise.domain.model.BodySegment
import dev.saketanand.setwise.domain.model.NormalRange
import dev.saketanand.setwise.domain.model.Rating
import dev.saketanand.setwise.domain.model.ReportDetails
import dev.saketanand.setwise.domain.model.SegmentValues
import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.testing.FakeUserSettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Body checks in the body_measurements table (an in-memory database on Robolectric). */
@RunWith(RobolectricTestRunner::class)
class BodyRepositoryImplTest {

    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SetwiseDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val settings = FakeUserSettingsRepository(UserSettings(bodyWeightKg = 82.0))
    private val body = BodyRepositoryImpl(db.bodyMeasurementDao(), settings)

    @After fun tearDown() = db.close()

    private fun on(day: Int) = LocalDate.of(2026, 9, day)

    @Test
    fun `no measurements at first`() = runTest {
        assertTrue(body.observeMeasurements().first().isEmpty())
    }

    @Test
    fun `measurements are kept newest first, and the newest weight is the profile's`() = runTest {
        body.add(BodyMeasurement(1, on(1), weightKg = 80.0))
        body.add(BodyMeasurement(2, on(20), bodyFatPercent = 18.5)) // newer, no weight
        val report = BodyMeasurement(3, on(10), weightKg = 78.4, muscleMassKg = 35.1, bmrKcal = 1_750, visceralFat = 6.0, source = BodyMeasurement.Source.Report)
        body.add(report)

        assertEquals(listOf(2L, 3L, 1L), body.observeMeasurements().first().map { it.id })
        assertEquals(report, body.observeMeasurements().first()[1]) // every field round-trips
        assertEquals(78.4, settings.settings.value.bodyWeightKg)
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
    fun `a check without a weight leaves the profile's weight as it is`() = runTest {
        body.add(BodyMeasurement(1, on(1), bodyFatPercent = 18.0))
        assertEquals(82.0, settings.settings.value.bodyWeightKg)
    }

    @Test
    fun `a report's details and segments are kept, replaced on edit, and go with it on delete`() = runTest {
        val details = ReportDetails(
            fatMassKg = 19.7, fatFreeMassKg = 61.5, bodyWaterL = 44.6, bmi = 26.8, waistHipRatio = 0.92, fitnessScore = 71,
            muscleControlKg = 0.0, fatControlKg = -9.6,
            ranges = mapOf(BodyMetric.Weight to NormalRange(56.0, 75.8), BodyMetric.Visceral to NormalRange(null, 10.0)),
            segments = listOf(
                SegmentValues(BodySegment.RightArm, 3.72, Rating.Normal, 24.1, 1.4, Rating.Over),
                SegmentValues(BodySegment.LeftLeg, 9.05, Rating.Under, 21.9, 2.6, Rating.Normal),
            ),
        )
        val report = BodyMeasurement(1, on(5), weightKg = 81.2, source = BodyMeasurement.Source.Report, details = details)

        body.add(report)
        assertEquals(report, body.observeMeasurements().first().single())

        val edited = report.copy(details = details.copy(segments = details.segments.take(1)))
        body.add(edited)
        assertEquals(edited, body.observeMeasurements().first().single())

        body.delete(1)
        assertTrue(body.observeMeasurements().first().isEmpty())
        assertEquals(0, db.query("SELECT COUNT(*) FROM body_segments", null).use { it.moveToFirst(); it.getInt(0) })
    }
}
