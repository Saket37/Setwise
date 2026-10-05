package dev.saketanand.setwise.domain.model

/** A body check's values beyond the basic five: what a full report (InBody) prints. */
data class ReportDetails(
    val fatMassKg: Double? = null,
    val fatFreeMassKg: Double? = null,
    val bodyWaterL: Double? = null,
    val bmi: Double? = null,
    val waistHipRatio: Double? = null,
    /** InBody's fitness score, out of 100. */
    val fitnessScore: Int? = null,
    /** The report's suggested change to reach its normal ranges ("Muscle-Fat Control"). */
    val muscleControlKg: Double? = null,
    val fatControlKg: Double? = null,
    /** The normal range the report printed beside each value. */
    val ranges: Map<BodyMetric, NormalRange> = emptyMap(),
    /** Arms, trunk and legs, as the report breaks them down. */
    val segments: List<SegmentValues> = emptyList(),
) {
    val isEmpty: Boolean
        get() = listOfNotNull(fatMassKg, fatFreeMassKg, bodyWaterL, bmi, waistHipRatio, fitnessScore, muscleControlKg, fatControlKg).isEmpty() &&
            ranges.isEmpty() && segments.isEmpty()
}

/** The values a report prints a normal range for. */
enum class BodyMetric { Weight, Muscle, FatMass, BodyWater, FatFreeMass, Bmi, BodyFat, WaistHip, Visceral, Bmr }

/** A report's normal range; one end may be open ("below 10"). */
data class NormalRange(val low: Double?, val high: Double?) {
    fun rate(value: Double): Rating = when {
        low != null && value < low -> Rating.Under
        high != null && value > high -> Rating.Over
        else -> Rating.Normal
    }
}

/** How a report rates a value against its normal range. */
enum class Rating { Under, Normal, Over }

enum class BodySegment { RightArm, LeftArm, Trunk, RightLeg, LeftLeg }

/** One body segment's lean and fat, each with the report's rating. */
data class SegmentValues(
    val segment: BodySegment,
    val leanKg: Double? = null,
    val leanRating: Rating? = null,
    val fatPercent: Double? = null,
    val fatKg: Double? = null,
    val fatRating: Rating? = null,
)
