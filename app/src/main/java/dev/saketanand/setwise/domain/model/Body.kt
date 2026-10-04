package dev.saketanand.setwise.domain.model

import java.time.LocalDate
import kotlin.math.roundToInt

enum class Sex { Male, Female }

/** One body check: typed in, or read from a body composition report (InBody, smart scale…). */
data class BodyMeasurement(
    /** Unique; when it was saved (epoch ms). */
    val id: Long,
    val measuredOn: LocalDate,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val muscleMassKg: Double? = null,
    /** As the report states it. */
    val bmrKcal: Int? = null,
    val visceralFat: Double? = null,
    val source: Source = Source.Manual,
) {
    enum class Source { Manual, Report }

    val isEmpty: Boolean get() = listOfNotNull(weightKg, bodyFatPercent, muscleMassKg, bmrKcal, visceralFat).isEmpty()
}

/** Basal metabolic rate and where it came from. */
data class BmrEstimate(val kcal: Int, val source: Source) {
    enum class Source {
        /** A report's measured value. */
        Report,

        /** Katch-McArdle, from weight and body fat. */
        BodyFat,

        /** Mifflin-St Jeor, from weight, height, age and sex. */
        Profile,
    }
}

/** Plain functions, unit-tested. */
object BodyRules {

    /** A plausible value of each; anything outside is a misread or a typo. */
    val WEIGHT_KG = 20.0..400.0
    val BODY_FAT_PERCENT = 2.0..70.0
    val MUSCLE_KG = 5.0..150.0
    val BMR_KCAL = 600..5_000
    val VISCERAL = 1.0..60.0
    val HEIGHT_CM = 90.0..250.0
    val AGE_YEARS = 10..100

    /**
     * The best BMR there is: a report's (if it's the newest body check with one, within
     * [REPORT_VALID_DAYS]), else from the newest body fat and weight, else from the profile.
     * @param measurements newest first.
     */
    fun bmr(measurements: List<BodyMeasurement>, profile: UserSettings, today: LocalDate): BmrEstimate? {
        measurements.firstOrNull { it.bmrKcal != null }
            ?.takeIf { !it.measuredOn.isBefore(today.minusDays(REPORT_VALID_DAYS)) }
            ?.let { return BmrEstimate(it.bmrKcal!!, BmrEstimate.Source.Report) }
        val weight = latestWeight(measurements, profile)
        val fat = measurements.firstOrNull { it.bodyFatPercent != null }?.bodyFatPercent
        if (weight != null && fat != null) return BmrEstimate(katchMcArdle(weight, fat), BmrEstimate.Source.BodyFat)
        val age = profile.ageOn(today)
        if (weight != null && profile.heightCm != null && age != null && profile.sex != null) {
            return BmrEstimate(mifflinStJeor(weight, profile.heightCm, age, profile.sex), BmrEstimate.Source.Profile)
        }
        return null
    }

    fun latestWeight(measurements: List<BodyMeasurement>, profile: UserSettings): Double? =
        measurements.firstOrNull { it.weightKg != null }?.weightKg ?: profile.bodyWeightKg

    fun mifflinStJeor(weightKg: Double, heightCm: Double, age: Int, sex: Sex): Int =
        (10 * weightKg + 6.25 * heightCm - 5 * age + if (sex == Sex.Male) 5 else -161).roundToInt()

    fun katchMcArdle(weightKg: Double, bodyFatPercent: Double): Int =
        (370 + 21.6 * weightKg * (1 - bodyFatPercent / 100)).roundToInt()

    private const val REPORT_VALID_DAYS = 90L
}
