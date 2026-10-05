package dev.saketanand.setwise.domain.model

import java.time.LocalDate

/** A time range for body progress: the last month… or all checks. */
enum class ProgressRange(val days: Long?) {
    Month(30), ThreeMonths(92), SixMonths(183), Year(365), All(null);

    /** The first day in range; null: no limit. */
    fun start(today: LocalDate): LocalDate? = days?.let { today.minusDays(it) }
}

/** The measures body progress follows, and how each is read from a check. */
enum class ProgressMeasure(val value: (BodyMeasurement) -> Double?) {
    Weight({ it.weightKg }),
    BodyFat({ it.bodyFatPercent }),
    Muscle({ it.muscleMassKg }),
    Visceral({ it.visceralFat }),
    Bmr({ it.bmrKcal?.toDouble() }),
    FatMass({ it.details.fatMassKg }),
    FatFreeMass({ it.details.fatFreeMassKg }),
    BodyWater({ it.details.bodyWaterL }),
    WaistHip({ it.details.waistHipRatio }),
    FitnessScore({ it.details.fitnessScore?.toDouble() }),
    Bmi({ it.details.bmi }),
}

/** One measure in a range: its points (oldest first), its latest value ever, and its change in range. */
data class MeasureProgress(
    val measure: ProgressMeasure,
    val points: List<Pair<LocalDate, Double>>,
    /** The newest value of all checks, in range or not. */
    val latest: Double?,
) {
    /** Last minus first in range; null with fewer than two points. */
    val change: Double? get() = if (points.size < 2) null else points.last().second - points.first().second
    val since: LocalDate? get() = points.firstOrNull()?.first
}

/** Lean muscle of a group of segments (arms, trunk, legs): first and latest report in range. */
data class SegmentProgress(val group: SegmentGroup, val first: Double, val latest: Double) {
    val change: Double get() = latest - first
}

/** Arms and legs add both sides. */
enum class SegmentGroup(val segments: Set<BodySegment>) {
    Arms(setOf(BodySegment.RightArm, BodySegment.LeftArm)),
    Trunk(setOf(BodySegment.Trunk)),
    Legs(setOf(BodySegment.RightLeg, BodySegment.LeftLeg)),
}

/** Plain functions, unit-tested. */
object BodyProgress {

    /** @param checks any order. */
    fun measures(checks: List<BodyMeasurement>, range: ProgressRange, today: LocalDate): List<MeasureProgress> {
        val oldestFirst = checks.sortedWith(compareBy({ it.measuredOn }, { it.id }))
        val start = range.start(today)
        val inRange = oldestFirst.filter { start == null || !it.measuredOn.isBefore(start) }
        return ProgressMeasure.entries.map { measure ->
            MeasureProgress(
                measure = measure,
                points = inRange.mapNotNull { check -> measure.value(check)?.let { check.measuredOn to it } },
                latest = oldestFirst.lastOrNull { measure.value(it) != null }?.let(measure.value),
            )
        }
    }

    /**
     * Lean muscle by arms, trunk and legs: the first and latest report in range that has all of
     * a group's segments. Empty with fewer than two such reports.
     */
    fun segments(checks: List<BodyMeasurement>, range: ProgressRange, today: LocalDate): List<SegmentProgress> {
        val start = range.start(today)
        val reports = checks.sortedWith(compareBy({ it.measuredOn }, { it.id }))
            .filter { (start == null || !it.measuredOn.isBefore(start)) && it.details.segments.isNotEmpty() }
        return SegmentGroup.entries.mapNotNull { group ->
            val totals = reports.mapNotNull { leanOf(it, group) }
            if (totals.size < 2) null else SegmentProgress(group, totals.first(), totals.last())
        }
    }

    /** A group's lean kg in [check]; null unless every segment of it is there. */
    private fun leanOf(check: BodyMeasurement, group: SegmentGroup): Double? {
        val lean = check.details.segments.filter { it.segment in group.segments }.mapNotNull { it.leanKg }
        return if (lean.size == group.segments.size) lean.sum() else null
    }

    /** Each check's weight change from the check with a weight before it (by date); null for the first or one without. */
    fun weightChanges(checks: List<BodyMeasurement>): Map<Long, Double> {
        val weights = checks.sortedWith(compareBy({ it.measuredOn }, { it.id }))
            .mapNotNull { check -> check.weightKg?.let { check.id to it } }
        return weights.zipWithNext { (_, before), (id, after) -> id to after - before }.toMap()
    }
}
