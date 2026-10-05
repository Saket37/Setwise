package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.BodyProgress
import dev.saketanand.setwise.domain.model.BodySegment
import dev.saketanand.setwise.domain.model.ProgressMeasure
import dev.saketanand.setwise.domain.model.ProgressRange
import dev.saketanand.setwise.domain.model.ReportDetails
import dev.saketanand.setwise.domain.model.SegmentGroup
import dev.saketanand.setwise.domain.model.SegmentProgress
import dev.saketanand.setwise.domain.model.SegmentValues
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyProgressTest {

    private val today = LocalDate.of(2026, 10, 5)
    private fun on(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private fun report(id: Long, day: LocalDate, weight: Double, fat: Double, arms: Double, legs: Double, score: Int) = BodyMeasurement(
        id, day, weightKg = weight, bodyFatPercent = fat, source = BodyMeasurement.Source.Report,
        details = ReportDetails(
            fitnessScore = score,
            segments = listOf(
                SegmentValues(BodySegment.RightArm, leanKg = arms / 2), SegmentValues(BodySegment.LeftArm, leanKg = arms / 2),
                SegmentValues(BodySegment.Trunk, leanKg = 28.0),
                SegmentValues(BodySegment.RightLeg, leanKg = legs / 2), SegmentValues(BodySegment.LeftLeg, leanKg = legs / 2),
            ),
        ),
    )

    private val checks = listOf(
        report(1, on(4, 25), 80.6, 20.1, 6.9, 18.2, 74),
        BodyMeasurement(2, on(5, 30), weightKg = 79.9), // typed in: weight only
        report(3, on(7, 6), 80.5, 19.7, 7.0, 18.3, 75),
        BodyMeasurement(4, on(9, 28), weightKg = 78.8),
        report(5, on(10, 5), 78.4, 18.5, 7.2, 18.7, 78),
    ).shuffled(kotlin.random.Random(3))

    private fun progress(range: ProgressRange, measure: ProgressMeasure) =
        BodyProgress.measures(checks, range, today).single { it.measure == measure }

    @Test
    fun `a measure's points in range, oldest first, and its change since the range began`() {
        val weight = progress(ProgressRange.ThreeMonths, ProgressMeasure.Weight)

        assertEquals(listOf(on(7, 6) to 80.5, on(9, 28) to 78.8, on(10, 5) to 78.4), weight.points)
        assertEquals(on(7, 6), weight.since)
        assertEquals(-2.1, weight.change!!, 0.001)
        assertEquals(78.4, weight.latest)
    }

    @Test
    fun `report-only measures skip typed-in checks, and All takes every check`() {
        val score = progress(ProgressRange.All, ProgressMeasure.FitnessScore)
        assertEquals(listOf(74.0, 75.0, 78.0), score.points.map { it.second })
        assertEquals(4.0, score.change!!, 0.001)
    }

    @Test
    fun `fewer than two points in range give no change, but the latest value is still known`() {
        val fat = progress(ProgressRange.Month, ProgressMeasure.BodyFat)
        assertEquals(1, fat.points.size)
        assertNull(fat.change)
        assertEquals(18.5, fat.latest)

        val water = progress(ProgressRange.All, ProgressMeasure.BodyWater) // never measured
        assertTrue(water.points.isEmpty())
        assertNull(water.latest)
    }

    @Test
    fun `lean muscle by arms, trunk and legs, both sides added`() {
        assertEquals(
            listOf(
                SegmentProgress(SegmentGroup.Arms, 6.9, 7.2),
                SegmentProgress(SegmentGroup.Trunk, 28.0, 28.0),
                SegmentProgress(SegmentGroup.Legs, 18.2, 18.7),
            ),
            BodyProgress.segments(checks, ProgressRange.SixMonths, today),
        )
        assertTrue(BodyProgress.segments(checks, ProgressRange.Month, today).isEmpty()) // one report in range
    }

    @Test
    fun `each check's weight change from the one before it`() {
        val changes = BodyProgress.weightChanges(checks)
        assertEquals(setOf(2L, 3L, 4L, 5L), changes.keys) // the first has nothing before it
        assertEquals(-0.7, changes.getValue(2), 0.001)
        assertEquals(-0.4, changes.getValue(5), 0.001)
    }
}
