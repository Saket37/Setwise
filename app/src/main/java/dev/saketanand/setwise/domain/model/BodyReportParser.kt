package dev.saketanand.setwise.domain.model

import java.time.LocalDate
import java.time.Month
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** A line of text read from a photo, with where it is on the page (pixels). */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

/** What a body composition report says; each null when it isn't there (or isn't believable). */
data class ReportValues(
    val measuredOn: LocalDate? = null,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val muscleMassKg: Double? = null,
    val bmrKcal: Int? = null,
    val visceralFat: Double? = null,
    /** From the report's header (InBody prints them): fill the profile where it's empty. */
    val heightCm: Double? = null,
    val age: Int? = null,
    val sex: Sex? = null,
) {
    val found: Int get() = listOfNotNull(weightKg, bodyFatPercent, muscleMassKg, bmrKcal, visceralFat).size

    /** These values, with the gaps filled from [other]. */
    fun orElse(other: ReportValues) = ReportValues(
        measuredOn ?: other.measuredOn,
        weightKg ?: other.weightKg,
        bodyFatPercent ?: other.bodyFatPercent,
        muscleMassKg ?: other.muscleMassKg,
        bmrKcal ?: other.bmrKcal,
        visceralFat ?: other.visceralFat,
        heightCm ?: other.heightCm,
        age ?: other.age,
        sex ?: other.sex,
    )
}

/**
 * Reads a body composition report (InBody, Tanita, smart-scale apps…) from its text in code:
 * each known label, and the number beside it on the same row, or else just below it. Values
 * outside believable ranges are left out ([BodyRules]). Plain functions, unit-tested.
 */
object BodyReportParser {

    fun parse(unordered: List<OcrLine>): ReportValues {
        // Top to bottom, then left to right: the first of a label is the summary's (InBody's
        // "PBF 31.6 %" before the Segmental Fat table's "PBF(%)" column).
        val lines = unordered.sortedWith(compareBy({ it.top }, { it.left }))
        val pounds = lines.any { Regex("\\blbs?\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.text) } &&
            lines.none { Regex("\\bkg\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
        fun kg(value: Double?) = value?.let { if (pounds) it * LB_TO_KG else it }
        return ReportValues(
            measuredOn = lines.firstNotNullOfOrNull { dateIn(it.text) },
            weightKg = kg(valueFor(lines, WEIGHT))?.takeIf { it in BodyRules.WEIGHT_KG }?.round1(),
            bodyFatPercent = valueFor(lines, BODY_FAT)?.takeIf { it in BodyRules.BODY_FAT_PERCENT }?.round1(),
            muscleMassKg = kg(valueFor(lines, MUSCLE))?.takeIf { it in BodyRules.MUSCLE_KG }?.round1(),
            bmrKcal = valueFor(lines, BMR)?.roundToInt()?.takeIf { it in BodyRules.BMR_KCAL },
            visceralFat = valueFor(lines, VISCERAL)?.takeIf { it in BodyRules.VISCERAL }?.round1(),
            heightCm = valueFor(lines, HEIGHT)?.takeIf { it in BodyRules.HEIGHT_CM }?.round1(),
            age = valueFor(lines, AGE)?.toInt()?.takeIf { it in BodyRules.AGE_YEARS },
            sex = lines.firstNotNullOfOrNull { line ->
                when {
                    Regex("\\b(gender|sex)\\b.*\\bfemale\\b|\\bfemale\\b", RegexOption.IGNORE_CASE).containsMatchIn(line.text) -> Sex.Female
                    Regex("\\b(gender|sex)\\b.*\\bmale\\b", RegexOption.IGNORE_CASE).containsMatchIn(line.text) -> Sex.Male
                    else -> null
                }
            },
        )
    }

    /** The first number after a label: in its own line, else the nearest to its right on the same row, else just below. */
    private fun valueFor(lines: List<OcrLine>, label: Label): Double? {
        for (line in lines) {
            val match = label.find(line.text) ?: continue
            numberIn(line.text.substring(match.range.last + 1))?.let { return it }
            val sameRow = lines.filter { it !== line && abs(it.centerY - line.centerY) <= line.height.coerceAtLeast(1) * 0.6 && it.left >= line.right - 4 }
                .sortedBy { it.left }
            sameRow.firstNotNullOfOrNull { other -> numberIn(other.text).takeIf { label.find(other.text) == null && ANY_LABEL.none { l -> l.find(other.text) != null } } }
                ?.let { return it }
            val below = lines.filter { it.top >= line.bottom - 2 && it.top - line.bottom <= line.height * 2 && overlaps(it, line) }
                .minByOrNull { it.top }
            below?.let { other -> numberIn(other.text)?.let { return it } }
        }
        return null
    }

    private fun overlaps(a: OcrLine, b: OcrLine) = a.left < b.right && b.left < a.right

    /** The first number, skipping a reference range's ("55.8~75.5", "18.0-28.0"). */
    private fun numberIn(text: String): Double? =
        Regex("(?<![\\d.~\\-])(\\d{1,4}(?:[.,]\\d{1,2})?)(?![\\d~]|[.,]\\d)")
            .find(text.replace(Regex("(\\d),(\\d{3})(?!\\d)"), "$1$2")) // "1,645" kcal
            ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()

    private fun dateIn(text: String): LocalDate? {
        Regex("\\b(20\\d{2})[-./](\\d{1,2})[-./](\\d{1,2})\\b").find(text)?.let { m ->
            return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        }
        Regex("\\b(\\d{1,2})[-./](\\d{1,2})[-./](20\\d{2})\\b").find(text)?.let { m ->
            // Day first (the usual outside the US); a first part over 12 can only be a day anyway.
            return runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
        // "24/07/26" (InBody): day, month, two-digit year.
        Regex("\\b(\\d{1,2})[-./](\\d{1,2})[-./](\\d{2})\\b").find(text)?.let { m ->
            return runCatching { LocalDate.of(2000 + m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
        Regex("\\b(\\d{1,2})\\s+([A-Za-z]{3,9})\\.?\\s+(20\\d{2})\\b").find(text)?.let { m ->
            val month = Month.entries.firstOrNull { it.name.startsWith(m.groupValues[2].uppercase(Locale.ROOT).take(3)) } ?: return null
            return runCatching { LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt()) }.getOrNull()
        }
        return null
    }

    private fun Double.round1() = (this * 10).roundToInt() / 10.0

    /** A label: matches [pattern] and none of [unless] ("Body Fat Mass" isn't body fat %). */
    private class Label(pattern: String, unless: String? = null) {
        private val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        private val unlessRegex = unless?.let { Regex(it, RegexOption.IGNORE_CASE) }
        fun find(text: String): MatchResult? = if (unlessRegex?.containsMatchIn(text) == true) null else regex.find(text)
    }

    private val WEIGHT = Label("\\b(body ?weight|weight)\\b", unless = "ideal|target|control|fat.?free|lean|standard|muscle")
    // "PBF" is often read as "PBE" or "P8F".
    private val BODY_FAT = Label("(percent body fat|body fat percentage|body fat ?%|\\bp[b8][fe]\\b|fat ?%|\\bbf ?%)", unless = "mass|visceral")
    private val MUSCLE = Label("(skeletal muscle mass|\\bsmm\\b|muscle mass|^\\s*muscle\\b)", unless = "fat|control|lean")
    private val BMR = Label("(basal metabolic rate|\\bbmr\\b)")
    private val VISCERAL = Label("visceral fat( level| rating| area)?")
    private val HEIGHT = Label("\\bheight\\b")
    private val AGE = Label("\\bage\\b")
    private val ANY_LABEL = listOf(WEIGHT, BODY_FAT, MUSCLE, BMR, VISCERAL, HEIGHT, AGE)

    private const val LB_TO_KG = 0.45359237
}
