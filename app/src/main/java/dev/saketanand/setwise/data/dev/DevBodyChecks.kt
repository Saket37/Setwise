package dev.saketanand.setwise.data.dev

import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.BodyMetric
import dev.saketanand.setwise.domain.model.BodySegment
import dev.saketanand.setwise.domain.model.NormalRange
import dev.saketanand.setwise.domain.model.Rating
import dev.saketanand.setwise.domain.model.ReportDetails
import dev.saketanand.setwise.domain.model.SegmentValues
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * DEBUG ONLY: made-up body checks for [DevDataSeeder], so the body screens (latest check, progress,
 * report details) can be checked with data: reports every few weeks over a year, with weights
 * typed in between, slowly leaner. Not anyone's real values.
 */
internal object DevBodyChecks {

    /** Newest last; [today] dates them. */
    fun all(today: LocalDate): List<BodyMeasurement> = CHECKS.mapIndexed { i, check ->
        val day = today.minusDays(check.daysAgo.toLong())
        val id = day.toEpochDay() * ID_PER_DAY + i
        check.report?.let { report(id, day, check.weight, it) } ?: BodyMeasurement(id, day, weightKg = check.weight)
    }

    private class Check(val daysAgo: Int, val weight: Double, val report: Report? = null)

    /** What a report adds: fat %, muscle, visceral, BMR, score, lean kg (arms, trunk, legs, both sides). Waist–hip follows the fat. */
    private class Report(
        val fat: Double,
        val muscle: Double,
        val visceral: Double,
        val bmr: Int,
        val score: Int,
        val lean: Triple<Double, Double, Double>,
    )

    private fun report(id: Long, day: LocalDate, weight: Double, r: Report): BodyMeasurement {
        val fatKg = tenth(weight * r.fat / 100)
        val fatFree = tenth(weight - fatKg)
        val (arms, trunk, legs) = r.lean
        return BodyMeasurement(
            id, day, weightKg = weight, bodyFatPercent = r.fat, muscleMassKg = r.muscle, bmrKcal = r.bmr, visceralFat = r.visceral,
            source = BodyMeasurement.Source.Report,
            details = ReportDetails(
                fatMassKg = fatKg,
                fatFreeMassKg = fatFree,
                bodyWaterL = tenth(fatFree * WATER_SHARE),
                bmi = tenth(weight / (HEIGHT_M * HEIGHT_M)),
                waistHipRatio = hundredth(WAIST_HIP_BASE + r.fat * WAIST_HIP_PER_FAT),
                fitnessScore = r.score,
                muscleControlKg = 0.0,
                fatControlKg = -tenth((fatKg - TARGET_FAT_KG).coerceAtLeast(0.0)),
                ranges = RANGES,
                segments = SEGMENTS.map { s ->
                    val lean = when (s.segment) {
                        BodySegment.RightArm, BodySegment.LeftArm -> arms / 2
                        BodySegment.Trunk -> trunk
                        BodySegment.RightLeg, BodySegment.LeftLeg -> legs / 2
                    }
                    SegmentValues(s.segment, hundredth(lean + s.leanGap), s.leanRating, tenth(r.fat + s.fatOffset), tenth(fatKg * s.fatShare), s.fatRating)
                },
            ),
        )
    }

    private fun tenth(value: Double) = (value * TENTHS).roundToInt() / TENTHS

    private fun hundredth(value: Double) = (value * HUNDREDTHS).roundToInt() / HUNDREDTHS

    /** How each segment differs from the whole: lean kg either side, fat % and share of fat kg, ratings. */
    private class SegmentShape(
        val segment: BodySegment,
        val leanGap: Double,
        val leanRating: Rating,
        val fatOffset: Double,
        val fatShare: Double,
        val fatRating: Rating,
    )

    private val SEGMENTS = listOf(
        SegmentShape(BodySegment.RightArm, 0.05, Rating.Normal, -0.6, 0.055, Rating.Normal),
        SegmentShape(BodySegment.LeftArm, -0.05, Rating.Normal, 0.1, 0.055, Rating.Normal),
        SegmentShape(BodySegment.Trunk, 0.0, Rating.Normal, 1.8, 0.5, Rating.Over),
        SegmentShape(BodySegment.RightLeg, 0.05, Rating.Normal, -2.4, 0.14, Rating.Normal),
        SegmentShape(BodySegment.LeftLeg, -0.05, Rating.Under, -2.1, 0.14, Rating.Normal),
    )

    private const val ID_PER_DAY = 100L
    private const val HEIGHT_M = 1.75
    private const val WATER_SHARE = 0.73
    private const val TARGET_FAT_KG = 12.0
    private const val TENTHS = 10.0
    private const val HUNDREDTHS = 100.0
    private const val WAIST_HIP_BASE = 0.8
    private const val WAIST_HIP_PER_FAT = 0.004

    private val RANGES = mapOf(
        BodyMetric.Weight to NormalRange(56.7, 76.7), BodyMetric.Muscle to NormalRange(29.5, 36.1),
        BodyMetric.FatMass to NormalRange(8.0, 16.0), BodyMetric.BodyWater to NormalRange(40.1, 49.0),
        BodyMetric.FatFreeMass to NormalRange(54.6, 66.7), BodyMetric.Bmi to NormalRange(18.5, 25.0),
        BodyMetric.BodyFat to NormalRange(10.0, 20.0), BodyMetric.WaistHip to NormalRange(0.8, 0.9),
        BodyMetric.Visceral to NormalRange(null, 10.0), BodyMetric.Bmr to NormalRange(1600.0, 1900.0),
    )

    private val CHECKS = listOf(
        Check(358, 83.0, Report(22.4, 33.9, 9.0, 1690, 70, Triple(6.7, 26.9, 17.9))),
        Check(319, 82.1, Report(21.8, 34.0, 8.0, 1695, 71, Triple(6.7, 27.0, 18.0))),
        Check(270, 81.6),
        Check(233, 81.0, Report(20.9, 34.2, 8.0, 1702, 73, Triple(6.8, 27.2, 18.1))),
        Check(198, 80.4),
        Check(163, 80.6, Report(20.1, 34.4, 7.0, 1710, 74, Triple(6.9, 27.4, 18.2))),
        Check(128, 79.9),
        Check(91, 80.5, Report(19.7, 34.6, 7.0, 1718, 75, Triple(7.0, 27.6, 18.3))),
        Check(70, 80.0),
        Check(49, 79.6, Report(19.4, 34.7, 7.0, 1724, 76, Triple(7.0, 27.7, 18.5))),
        Check(28, 79.3),
        Check(14, 79.0, Report(18.9, 34.9, 6.0, 1738, 77, Triple(7.1, 27.9, 18.6))),
        Check(7, 78.8),
        Check(0, 78.4, Report(18.5, 35.1, 6.0, 1750, 78, Triple(7.13, 28.0, 18.71))),
    )
}
