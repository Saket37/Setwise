package dev.saketanand.setwise.domain.model

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A weight the goal names: "lose 12 kg in 2 months", "gain 5 kg". [weeks] and [timeLabel] ("2
 * months", as typed) are null when it gives no time.
 */
data class GoalTarget(val kg: Double, val losing: Boolean, val weeks: Double? = null, val timeLabel: String? = null)

/**
 * What a goal's target means, worked out in code (#125): the pace a week it asks for, the usual
 * steady pace (losing: 0.5 to 1% of body weight a week, or 0.5 to 1 kg without a weight; gaining
 * muscle: 0.25 to 0.5 kg a week), and how many weeks a steady pace takes. The screen words it,
 * or the on-device model does from [factLines]. General guidance, not medical advice.
 */
data class GoalAdvice(
    val target: GoalTarget,
    /** kg a week the goal asks for (to 0.1); null without a time. */
    val kgPerWeek: Double?,
    val steadyLowKg: Double,
    val steadyHighKg: Double,
    /** Body weight the steady pace is from, if known. */
    val bodyWeightKg: Double?,
    /** Weeks at a steady pace: at the high end, and at the low end. */
    val steadyWeeksMin: Int,
    val steadyWeeksMax: Int,
) {
    /** Faster than a steady pace. */
    val isFast: Boolean get() = kgPerWeek != null && kgPerWeek > steadyHighKg + EPSILON

    /** One fact per line, numbers written once and exactly: what the model may say. */
    fun factLines(): String = listOfNotNull(
        "Goal: ${if (target.losing) "lose" else "gain"} ${target.kg.kg()} kg" + target.timeLabel?.let { " in $it" }.orEmpty(),
        kgPerWeek?.let { "Pace it asks for: about ${it.kg()} kg a week" + if (isFast) ", faster than steady" else ", a steady pace" },
        "Steady pace that usually lasts: ${steadyLowKg.kg()} to ${steadyHighKg.kg()} kg a week" +
            bodyWeightKg?.let { " (from ${it.kg()} kg body weight)" }.orEmpty(),
        "At a steady pace it takes: about $steadyWeeksMin to $steadyWeeksMax weeks",
        if (target.losing) {
            "These workouts: keep muscle while losing fat; most of the loss comes from eating a little less"
        } else {
            "These workouts: give muscle a reason to grow; enough food and sleep do the rest"
        },
    ).joinToString("\n")

    companion object {
        private const val EPSILON = 1e-9
        private const val LOSE_LOW_SHARE = 0.005
        private const val LOSE_HIGH_SHARE = 0.01
        private const val LOSE_LOW_KG = 0.5
        private const val LOSE_HIGH_KG = 1.0
        private const val GAIN_LOW_KG = 0.25
        private const val GAIN_HIGH_KG = 0.5

        /** The advice for a goal's text, or null if it names no weight to lose or gain. */
        fun of(text: String, bodyWeightKg: Double?): GoalAdvice? {
            val target = GoalTargetReader.read(text) ?: return null
            val (low, high) = when {
                !target.losing -> GAIN_LOW_KG to GAIN_HIGH_KG
                bodyWeightKg != null -> tenth(bodyWeightKg * LOSE_LOW_SHARE) to tenth(bodyWeightKg * LOSE_HIGH_SHARE)
                else -> LOSE_LOW_KG to LOSE_HIGH_KG
            }
            return GoalAdvice(
                target = target,
                kgPerWeek = target.weeks?.let { tenth(target.kg / it) },
                steadyLowKg = low,
                steadyHighKg = high,
                bodyWeightKg = bodyWeightKg?.takeIf { target.losing },
                steadyWeeksMin = ceil(target.kg / high - EPSILON).toInt(),
                steadyWeeksMax = ceil(target.kg / low - EPSILON).toInt(),
            )
        }
    }
}

/** To one decimal: 1.38 → 1.4. */
internal fun tenth(value: Double): Double = (value * TENTHS).roundToInt() / TENTHS.toDouble()

private const val TENTHS = 10

/** "12", "1.5": a kg number as the app writes it (Latin digits). */
fun Double.kg(): String = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.ROOT, this)

/** Reads [GoalTarget] from a goal's text, in code. Plain functions, unit-tested. */
object GoalTargetReader {

    fun read(text: String): GoalTarget? {
        val lower = " " + text.lowercase(Locale.ROOT).replace(',', '.').replace(Regex("[^a-z0-9.]+"), " ") + " "
        val amount = AMOUNT.find(lower) ?: return null
        val number = amount.groupValues[1].toDoubleOrNull() ?: return null
        val kg = if (amount.groupValues[2].startsWith("l") || amount.groupValues[2].startsWith("p")) number * LB_TO_KG else number
        if (kg !in BELIEVABLE_KG) return null
        val losing = LOSE.any { " $it" in lower }
        val gaining = GAIN.any { " $it" in lower }
        if (losing == gaining) return null // neither, or both: not clear which way
        val (weeks, label) = time(lower) ?: (null to null)
        return GoalTarget(tenth(kg), losing, weeks, label)
    }

    /** "in 2 months": about 8.7 weeks, and "2 months" to show; null without a believable time. */
    private fun time(text: String): Pair<Double, String>? {
        val match = TIME.find(text) ?: return null
        val count = match.groupValues[1].let { NUMBER_WORDS[it] ?: it.toIntOrNull() } ?: return null
        val months = match.groupValues[2].startsWith("m")
        val weeks = (if (months) count * WEEKS_PER_MONTH else count.toDouble()).takeIf { it in BELIEVABLE_WEEKS } ?: return null
        return weeks to "$count ${if (months) "month" else "week"}${if (count == 1) "" else "s"}"
    }

    private const val LB_TO_KG = 0.45359237
    private const val WEEKS_PER_MONTH = 4.35
    private val BELIEVABLE_KG = 0.5..80.0
    private val BELIEVABLE_WEEKS = 1.0..104.0
    private val AMOUNT = Regex(" (\\d{1,3}(?:\\.\\d)?) ?(kg|kgs|kilo|kilos|kilograms?|lb|lbs|pounds?) ")
    private val TIME = Regex(" (\\d{1,2}|a|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve) ?(weeks?|wks?|months?) ")
    private val NUMBER_WORDS = mapOf(
        "a" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
    )

    /** Words for losing weight; "loose" is how it's often typed (#125). */
    val LOSE = listOf("lose", "loose", "losing", "weight loss", "fat", "cut ", "cutting", "shred", "slim", "burn", "drop ")
    private val GAIN = listOf("gain", "bulk", "put on", "build", "add ")
}
