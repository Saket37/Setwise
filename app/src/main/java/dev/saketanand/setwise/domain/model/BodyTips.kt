package dev.saketanand.setwise.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A suggestion a body report gives reason for, worked out in code (#124). */
sealed interface BodyTip {

    /** One side's lean mass is rated Under while the other side's isn't: train the [weaker] side on its own. */
    data class Uneven(val weaker: BodySegment, val stronger: BodySegment) : BodyTip

    /** Changes since the previous report ([since]), in kg; null when either report doesn't say. */
    data class SinceLast(val since: LocalDate, val fatKg: Double?, val muscleKg: Double?) : BodyTip {
        /** Less fat, and muscle kept or gained: what's being done works. */
        val isOnTrack: Boolean get() = (fatKg ?: 0.0) < 0 && (muscleKg ?: 0.0) >= 0
    }

    /** Segments whose fat is rated Over; [trunkPercent] when the trunk is one of them. */
    data class FatOver(val segments: List<BodySegment>, val trunkPercent: Double?) : BodyTip
}

/**
 * The report's suggestions, in code: legs before arms for an uneven side, the change since the
 * last report, then fat rated Over. At most three, most useful first. The screen words them, or
 * the on-device model does from [factLines]. General guidance, not medical advice.
 */
object BodyTips {

    /** @param previous the report before [check], if any. */
    fun of(check: BodyMeasurement, previous: BodyMeasurement?): List<BodyTip> {
        val segments = check.details.segments.associateBy { it.segment }
        val uneven = SIDES.mapNotNull { (right, left) ->
            val r = segments[right]?.leanRating
            val l = segments[left]?.leanRating
            when {
                r == Rating.Under && l != null && l != Rating.Under -> BodyTip.Uneven(right, left)
                l == Rating.Under && r != null && r != Rating.Under -> BodyTip.Uneven(left, right)
                else -> null
            }
        }
        val sinceLast = previous?.let { before ->
            val fat = change(check.fatMassKg(), before.fatMassKg())
            val muscle = change(check.muscleMassKg, before.muscleMassKg)
            if (fat == null && muscle == null) null else BodyTip.SinceLast(before.measuredOn, fat, muscle)
        }
        val over = check.details.segments.filter { it.fatRating == Rating.Over }.map { it.segment }
        val fatOver = over.takeIf { it.isNotEmpty() }?.let { list ->
            BodyTip.FatOver(list, segments[BodySegment.Trunk]?.fatPercent?.takeIf { BodySegment.Trunk in list })
        }
        return (uneven.take(1) + listOfNotNull(sinceLast, fatOver)).take(MAX_TIPS)
    }

    /** One fact per line, numbers written once and exactly: what the model may say. */
    fun factLines(tips: List<BodyTip>): String = tips.joinToString("\n") { tip ->
        when (tip) {
            is BodyTip.Uneven -> "Lean muscle: ${tip.weaker.words()} rated Under, ${tip.stronger.words()} not. " +
                "Idea: ${if (tip.weaker.isLeg) IDEA_LEGS else IDEA_ARMS}"
            is BodyTip.SinceLast -> "Since the last report on ${tip.since.dayMonth()}: " + listOfNotNull(
                tip.fatKg?.let { "fat mass ${it.signedKg()} kg" },
                tip.muscleKg?.let { "muscle ${it.signedKg()} kg" },
            ).joinToString(", ") + if (tip.isOnTrack) ". Idea: keep the current training and eating" else ""
            is BodyTip.FatOver -> "Fat rated Over: " + tip.segments.joinToString(", ") { it.words() } +
                (tip.trunkPercent?.let { " (trunk ${it.kg()}%)" }.orEmpty()) + ". Idea: steady overall fat loss brings it down"
        }
    }

    private fun BodyMeasurement.fatMassKg(): Double? =
        details.fatMassKg ?: weightKg?.let { w -> bodyFatPercent?.let { tenth(w * it / PERCENT) } }

    private fun change(now: Double?, before: Double?): Double? = if (now != null && before != null) tenth(now - before) else null

    private fun Double.signedKg(): String = (if (this > 0) "+" else "") + kg()

    /** "12", "1.5": Latin digits, no trailing ".0". */
    private fun Double.kg(): String = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.ROOT, this)

    private fun tenth(value: Double): Double = kotlin.math.round(value * TENTHS) / TENTHS

    private fun LocalDate.dayMonth(): String = format(DAY_MONTH)

    private val BodySegment.isLeg: Boolean get() = this == BodySegment.RightLeg || this == BodySegment.LeftLeg

    private fun BodySegment.words(): String = when (this) {
        BodySegment.RightArm -> "right arm"
        BodySegment.LeftArm -> "left arm"
        BodySegment.Trunk -> "trunk"
        BodySegment.RightLeg -> "right leg"
        BodySegment.LeftLeg -> "left leg"
    }

    private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private const val MAX_TIPS = 3
    private const val TENTHS = 10.0
    private const val PERCENT = 100
    private const val IDEA_LEGS = "single-leg work like split squats or step-ups"
    private const val IDEA_ARMS = "single-arm work like one-arm dumbbell rows and presses"

    /** Legs first: a weak leg matters more for most lifts. */
    private val SIDES = listOf(BodySegment.RightLeg to BodySegment.LeftLeg, BodySegment.RightArm to BodySegment.LeftArm)
}
