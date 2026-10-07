package dev.saketanand.setwise.domain.model

import java.time.Duration
import kotlin.math.roundToInt

/** How hard a workout was; stored with its calories ("light", "moderate", "vigorous"). */
enum class Intensity(val storedName: String) {
    Light("light"),
    Moderate("moderate"),
    Vigorous("vigorous"),
    ;

    companion object {
        fun fromStored(name: String?): Intensity? = entries.firstOrNull { it.storedName == name }
    }
}

data class CalorieEstimate(val kcal: Int, val intensity: Intensity)

/**
 * Calories by formula: the fallback when the on-device model isn't there, and its sanity check.
 * Gross energy (resting included), as fitness apps usually show.
 *
 * - Strength: the workout's time not spent on cardio × a MET picked by how dense the session
 *   was (completed sets per hour): light 3.5, moderate 5.0, vigorous 6.0 (Compendium of
 *   Physical Activities, resistance training).
 * - Cardio, per logged entry, by the exercise's [CalorieMethod]: its MET × time; the ACSM
 *   treadmill equation from speed and incline; or the ACSM running / walking equation from
 *   pace (distance ÷ time). Without what a method needs, a typical MET for it.
 * - A MET-hour is 1 kcal per kg of body weight, or, with a known BMR (a body report, body fat
 *   or the profile), the person's own resting burn: BMR ÷ 24 ("corrected MET").
 */
object CalorieFormula {

    const val SOURCE = "formula"

    private const val HOURS_PER_DAY = 24.0

    /**
     * Null without a body weight, or for a workout with no time.
     * @param bmrKcal resting burn per day, when known: personalises every MET-hour.
     */
    fun estimate(session: WorkoutSession, bodyWeightKg: Double?, bmrKcal: Int? = null): CalorieEstimate? {
        val weight = bodyWeightKg?.takeIf { it > 0 } ?: return null
        val kcalPerMetHour = kcalPerMetHour(weight, bmrKcal)
        val end = session.endedAt ?: return null
        val totalHours = Duration.between(session.startedAt, end).seconds / 3600.0
        if (totalHours <= 0) return null

        val cardio = session.exercises.filter { it.exercise.type == ExerciseType.CARDIO }
        val cardioEntries = cardio.flatMap { exercise ->
            exercise.sets.filter { it.isCompleted }.mapNotNull { set -> set.cardio?.let { exercise.exercise to it } }
        }
        val cardioHours = cardioEntries.sumOf { (_, values) -> (values.durationSec ?: 0) / 3600.0 }
        val cardioKcal = cardioEntries.sumOf { (exercise, values) -> cardioKcal(exercise.calorieMethod, exercise.met, values, kcalPerMetHour) }

        val strengthSets = session.exercises
            .filter { it.exercise.type != ExerciseType.CARDIO }
            .sumOf { exercise -> exercise.sets.count { it.isCompleted } }
        val strengthHours = (totalHours - cardioHours).coerceAtLeast(0.0)
        val strengthIntensity = strengthIntensity(strengthSets, strengthHours)
        val strengthKcal = if (strengthSets > 0) strengthIntensity.met() * kcalPerMetHour * strengthHours else 0.0

        val kcal = (strengthKcal + cardioKcal).roundToInt()
        if (kcal <= 0) return null
        val intensity = when {
            strengthSets > 0 -> strengthIntensity
            // Cardio only: from its average MET.
            cardioHours > 0 -> metIntensity(cardioKcal / (kcalPerMetHour * cardioHours))
            else -> Intensity.Moderate
        }
        return CalorieEstimate(kcal, intensity)
    }

    /** Completed sets per hour of lifting: fewer than 12 is light, more than 24 vigorous. */
    fun strengthIntensity(sets: Int, hours: Double): Intensity {
        if (hours <= 0) return Intensity.Moderate
        val perHour = sets / hours
        return when {
            perHour < 12 -> Intensity.Light
            perHour > 24 -> Intensity.Vigorous
            else -> Intensity.Moderate
        }
    }

    /** One MET for an hour: 1 kcal per kg, or the person's measured or estimated resting burn. */
    fun kcalPerMetHour(weightKg: Double, bmrKcal: Int?): Double =
        bmrKcal?.takeIf { it in BodyRules.BMR_KCAL }?.let { it / HOURS_PER_DAY } ?: weightKg

    /**
     * One cardio entry, by its exercise's [method] and [met] (also the cardio screen's live line).
     * @param kcalPerMetHour see [kcalPerMetHour].
     */
    fun cardioKcal(method: CalorieMethod?, met: Double?, values: CardioValues, kcalPerMetHour: Double): Double {
        val seconds = values.durationSec ?: return 0.0
        val minutes = seconds / 60.0
        val speedKmh = values.averageSpeedKmh() ?: values.distanceKm?.let { it / (minutes / 60.0) }
        val grade = (values.inclinePct ?: 0.0) / 100
        val vo2 = when (method) {
            // ml O2 / kg / min. Above ~8 km/h the treadmill is run, below it walked.
            CalorieMethod.ACSM_TREADMILL -> speedKmh?.let { if (it >= RUN_FROM_KMH) runningVo2(it, grade) else walkingVo2(it, grade) }
            CalorieMethod.ACSM_RUN_FROM_PACE -> speedKmh?.let { runningVo2(it, 0.0) }
            CalorieMethod.ACSM_WALK_FROM_PACE -> speedKmh?.let { walkingVo2(it, 0.0) }
            CalorieMethod.MET, null -> null
        }
        // 1 MET = 3.5 ml/kg/min; kcal = MET × (kcal per MET-hour) × hours.
        val mets = vo2?.let { it / 3.5 } ?: met ?: typicalMet(method)
        return mets * kcalPerMetHour * (minutes / 60.0)
    }

    private fun CardioValues.averageSpeedKmh(): Double? = when {
        speedMinKmh != null && speedMaxKmh != null -> (speedMinKmh + speedMaxKmh) / 2
        else -> speedMinKmh ?: speedMaxKmh
    }

    private fun metersPerMinute(kmh: Double) = kmh * 1000 / 60

    private fun walkingVo2(kmh: Double, grade: Double) = 0.1 * metersPerMinute(kmh) + 1.8 * metersPerMinute(kmh) * grade + 3.5

    private fun runningVo2(kmh: Double, grade: Double) = 0.2 * metersPerMinute(kmh) + 0.9 * metersPerMinute(kmh) * grade + 3.5

    private fun typicalMet(method: CalorieMethod?): Double = when (method) {
        CalorieMethod.ACSM_RUN_FROM_PACE -> 9.8
        CalorieMethod.ACSM_WALK_FROM_PACE -> 3.5
        CalorieMethod.ACSM_TREADMILL -> 6.0
        CalorieMethod.MET, null -> 6.0
    }

    private fun Intensity.met() = when (this) {
        Intensity.Light -> 3.5
        Intensity.Moderate -> 5.0
        Intensity.Vigorous -> 6.0
    }

    private fun metIntensity(met: Double) = when {
        met < 4 -> Intensity.Light
        met < 7 -> Intensity.Moderate
        else -> Intensity.Vigorous
    }

    private const val RUN_FROM_KMH = 8.0
}
