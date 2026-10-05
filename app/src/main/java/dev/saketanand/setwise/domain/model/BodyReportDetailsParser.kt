package dev.saketanand.setwise.domain.model

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * What a full report (InBody) prints beyond the basic five, read in code from its text: fat
 * mass, body water, fat-free mass, BMI, waist–hip ratio, each value's normal range, the arms /
 * trunk / legs tables, the suggested muscle and fat change and the fitness score. Each value
 * must be believable (a lost decimal point is put back when that makes it so). Plain functions,
 * unit-tested.
 */
internal object BodyReportDetailsParser {

    /** @param lines cleaned ([cleanOcr]), top to bottom. */
    fun parse(lines: List<OcrLine>, rangeLabels: Map<BodyMetric, ReportLabel>): ReportDetails = ReportDetails(
        fatMassKg = value(lines, FAT_MASS, BELIEVABLE.getValue(BodyMetric.FatMass)),
        fatFreeMassKg = value(lines, FAT_FREE_MASS, BELIEVABLE.getValue(BodyMetric.FatFreeMass)),
        bodyWaterL = value(lines, BODY_WATER, BELIEVABLE.getValue(BodyMetric.BodyWater)),
        bmi = value(lines, BMI, BELIEVABLE.getValue(BodyMetric.Bmi)),
        waistHipRatio = value(lines, WAIST_HIP, BELIEVABLE.getValue(BodyMetric.WaistHip), places = 2),
        fitnessScore = value(lines, FITNESS_SCORE, SCORE)?.toInt(),
        muscleControlKg = control(lines, "muscle"),
        fatControlKg = control(lines, "fat"),
        ranges = (rangeLabels + LABELS).mapNotNull { (metric, label) -> range(lines, label, metric)?.let { metric to it } }.toMap(),
        segments = segments(lines),
    )

    private fun value(lines: List<OcrLine>, label: ReportLabel, believable: ClosedFloatingPointRange<Double>, places: Int = DECIMALS): Double? =
        lines.firstNotNullOfOrNull { line ->
            val match = label.find(line.text) ?: return@firstNotNullOfOrNull null
            valueIn(rowText(lines, line, match.range.last + 1))?.let { fit(it, believable) }?.round(places)
        }

    /** The first row with [label] that has a range in brackets (the header's "Weight: 81.2" has none). */
    private fun range(lines: List<OcrLine>, label: ReportLabel, metric: BodyMetric): NormalRange? =
        lines.firstNotNullOfOrNull { line ->
            val match = label.find(line.text) ?: return@firstNotNullOfOrNull null
            rangeIn(rowText(lines, line, match.range.last + 1), BELIEVABLE.getValue(metric), oneBound = metric == BodyMetric.Visceral)
        }

    /** "Muscle - Fat Control": the row below it, "Muscle 0.0 kg Fat -9.6 kg". */
    private fun control(lines: List<OcrLine>, what: String): Double? {
        val header = lines.firstOrNull { CONTROL.find(it.text) != null } ?: return null
        val row = lines.filter { it.top >= header.bottom - EDGE && it.top - header.bottom <= header.height * 2 }
            .minByOrNull { it.top }
            ?.let { rowTextFromLeft(lines, it) } ?: return null
        val value = Regex("$what\\D*?([-−+]?\\d+(?:[.,]\\d+)?)", RegexOption.IGNORE_CASE).find(row)?.groupValues?.get(1)
            ?.replace('−', '-')?.replace(',', '.')?.toDoubleOrNull() ?: return null
        return value.takeIf { it in CONTROL_KG }
    }

    /** The whole row a line is on, left to right. */
    private fun rowTextFromLeft(lines: List<OcrLine>, line: OcrLine): String {
        val first = lines.filter { abs(it.centerY - line.centerY) <= maxOf(it.height, line.height) * HALF }.minBy { it.left }
        return rowText(lines, first)
    }

    /** "Segmental Lean" (lean kg, rating) and "Segmental Fat" (fat %, fat kg, rating), by segment. */
    private fun segments(lines: List<OcrLine>): List<SegmentValues> {
        val leanTop = lines.firstOrNull { SEGMENTAL_LEAN.find(it.text) != null }?.top
        val fatTop = lines.firstOrNull { SEGMENTAL_FAT.find(it.text) != null }?.top
        val end = lines.firstOrNull { line -> fatTop != null && line.top > fatTop && SECTION_AFTER.find(line.text) != null }?.top ?: Int.MAX_VALUE
        val lean = leanTop?.let { section(lines, it, fatTop ?: end) }.orEmpty()
        val fat = fatTop?.let { section(lines, it, end) }.orEmpty()
        return BodySegment.entries.mapNotNull { segment ->
            val leanRow = lean[segment]
            val fatRow = fat[segment]
            val leanNumbers = leanRow?.let { numbersIn(it) }.orEmpty()
            val fatNumbers = fatRow?.let { numbersIn(it) }.orEmpty()
            SegmentValues(
                segment = segment,
                leanKg = leanNumbers.firstOrNull()?.let { fit(it, LEAN_KG.getValue(segment), TABLE_REPAIR_FROM) }?.round(2),
                leanRating = leanRow?.let(::ratingIn),
                fatPercent = fatNumbers.getOrNull(0)?.let { fit(it, BELIEVABLE.getValue(BodyMetric.BodyFat)) }?.round(DECIMALS),
                fatKg = fatNumbers.getOrNull(1)?.let { fit(it, FAT_KG.getValue(segment), TABLE_REPAIR_FROM) }?.round(DECIMALS),
                fatRating = fatRow?.let(::ratingIn),
            ).takeIf { it.leanKg != null || it.fatPercent != null || it.fatKg != null }
        }
    }

    /** Each segment's row text (after its name) between [top] and [bottom]. */
    private fun section(lines: List<OcrLine>, top: Int, bottom: Int): Map<BodySegment, String> {
        val inside = lines.filter { it.top > top && it.top < bottom }
        return SEGMENT_LABELS.mapNotNull { (segment, label) ->
            inside.firstNotNullOfOrNull { line ->
                label.find(line.text)?.let { match -> segment to rowText(inside, line, match.range.last + 1) }
            }
        }.toMap()
    }

    private fun Double.round(places: Int): Double {
        val factor = 10.0.pow(places)
        return (this * factor).roundToLong() / factor
    }

    private val FAT_MASS = ReportLabel("(body fat mass|\\bfat mass\\b|^\\s*fat\\b)", unless = "visceral|segmental|control|pbf|evaluat|%|free|-\\s*fat")
    private val FAT_FREE_MASS = ReportLabel("(\\bffm\\b|fat.?free mass)")
    private val BODY_WATER = ReportLabel("(\\btbw\\b|total body water|body water)")
    private val BMI = ReportLabel("(\\bbmi\\b|body mass index)")
    private val WAIST_HIP = ReportLabel("(\\bwhr\\b|waist.?hip)")
    private val FITNESS_SCORE = ReportLabel("(fitness score|inbody score)")
    private val CONTROL = ReportLabel("control")
    private val SEGMENTAL_LEAN = ReportLabel("segmental lean")
    private val SEGMENTAL_FAT = ReportLabel("segmental fat")
    private val SECTION_AFTER = ReportLabel("(control|fitness|impedance|score)")
    private val LABELS = mapOf(
        BodyMetric.FatMass to FAT_MASS,
        BodyMetric.FatFreeMass to FAT_FREE_MASS,
        BodyMetric.BodyWater to BODY_WATER,
        BodyMetric.Bmi to BMI,
        BodyMetric.WaistHip to WAIST_HIP,
    )
    private val SEGMENT_LABELS = listOf(
        BodySegment.RightArm to ReportLabel("right\\s*arm"),
        BodySegment.LeftArm to ReportLabel("left\\s*arm"),
        BodySegment.Trunk to ReportLabel("trunk"),
        BodySegment.RightLeg to ReportLabel("right\\s*leg"),
        BodySegment.LeftLeg to ReportLabel("left\\s*leg"),
    )

    /** A believable value of each, for its normal range too. */
    val BELIEVABLE: Map<BodyMetric, ClosedFloatingPointRange<Double>> = mapOf(
        BodyMetric.Weight to BodyRules.WEIGHT_KG,
        BodyMetric.Muscle to BodyRules.MUSCLE_KG,
        BodyMetric.FatMass to 0.5..250.0,
        BodyMetric.BodyWater to 5.0..150.0,
        BodyMetric.FatFreeMass to 10.0..250.0,
        BodyMetric.Bmi to 10.0..80.0,
        BodyMetric.BodyFat to BodyRules.BODY_FAT_PERCENT,
        BodyMetric.WaistHip to 0.5..1.5,
        BodyMetric.Visceral to BodyRules.VISCERAL,
        BodyMetric.Bmr to BodyRules.BMR_KCAL.first.toDouble()..BodyRules.BMR_KCAL.last.toDouble(),
    )
    private val LEAN_KG = mapOf(
        BodySegment.RightArm to 0.2..15.0, BodySegment.LeftArm to 0.2..15.0, BodySegment.Trunk to 5.0..80.0,
        BodySegment.RightLeg to 1.0..40.0, BodySegment.LeftLeg to 1.0..40.0,
    )
    private val FAT_KG = mapOf(
        BodySegment.RightArm to 0.05..10.0, BodySegment.LeftArm to 0.05..10.0, BodySegment.Trunk to 0.5..80.0,
        BodySegment.RightLeg to 0.1..20.0, BodySegment.LeftLeg to 0.1..20.0,
    )
    private val SCORE = 1.0..100.0
    private val CONTROL_KG = -60.0..60.0
    private const val DECIMALS = 1

    /** The segment tables always print decimals: a two-digit "16" kg arm fat lost its point. */
    private const val TABLE_REPAIR_FROM = 10.0
    private const val EDGE = 4
    private const val HALF = 0.6
}
