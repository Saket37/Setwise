package dev.saketanand.setwise.domain.model

import kotlin.math.abs

// Helpers for reading a report's text as the phone's text recognition gives it: lines with
// positions, letters for digits ("1llevel"), lost decimal points ("812" kg), ranges in brackets.

/** "l" and "I" next to a digit are a 1 ("1llevel" → "11level", "l6" → "16"). */
internal fun cleanOcr(text: String): String =
    text.replace(Regex("(?<=\\d)[lI]|(?<![A-Za-z])[lI](?=\\d)"), "1")

/**
 * [value], or it with a decimal point the text recognition lost ("812" kg → 81.2), whichever is
 * believable. Only numbers from [repairFrom] up are taken to have lost one: a "92" % body fat is
 * left out, not read as 9.2 (a table that always prints decimals can pass 10).
 */
internal fun fit(value: Double, believable: ClosedFloatingPointRange<Double>, repairFrom: Double = REPAIR_FROM): Double? =
    if (value in believable) value else listOf(value / TENTHS, value / HUNDREDTHS).firstOrNull { value >= repairFrom && it in believable }

/** The numbers in [text] ("12,7" is 12.7), in order. */
internal fun numbersIn(text: String): List<Double> =
    Regex("\\d+(?:[.,]\\d+)?").findAll(text).mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }.toList()

/** [line]'s row from [line] rightwards: its text after [from], then the lines beside it, left to right. */
internal fun rowText(lines: List<OcrLine>, line: OcrLine, from: Int = 0): String {
    val beside = lines.filter { other ->
        other !== line && other.left >= line.left - EDGE &&
            abs(other.centerY - line.centerY) <= maxOf(line.height, other.height) * ROW_TOLERANCE
    }.sortedBy { it.left }
    return (listOf(line.text.substring(from)) + beside.map { it.text }).joinToString(" ")
}

/** The first number of [text] that's outside brackets (not part of a range). */
internal fun valueIn(text: String): Double? = numbersIn(text.substringBefore('('))
    .firstOrNull()

/**
 * The normal range in brackets in [text] ("(56.0~75.8)", "(28.9-35.3", "(560-75.8)" with a lost
 * decimal point), both ends believable and low < high; [oneBound]: a single upper bound
 * ("below 10", whatever its words read as).
 */
internal fun rangeIn(text: String, believable: ClosedFloatingPointRange<Double>, oneBound: Boolean = false): NormalRange? {
    val inside = text.substringAfter('(', "").substringBefore(')').takeIf { it.isNotBlank() } ?: return null
    val numbers = numbersIn(inside)
    if (oneBound) return numbers.lastOrNull()?.let { fit(it, believable) }?.let { NormalRange(null, it) }
    if (numbers.size != 2 || !Regex("\\d\\s*[-~–]\\s*\\d").containsMatchIn(inside)) return null
    val lows = listOf(numbers[0], numbers[0] / TENTHS, numbers[0] / HUNDREDTHS).filter { it in believable }
    val highs = listOf(numbers[1], numbers[1] / TENTHS, numbers[1] / HUNDREDTHS).filter { it in believable }
    return lows.firstNotNullOfOrNull { low -> highs.firstOrNull { it > low }?.let { NormalRange(low, it) } }
}

/** A rating word, however it reads ("Noumal", "Nomal" → Normal). */
internal fun ratingIn(text: String): Rating? = Regex("[A-Za-z]{3,}").findAll(text).firstNotNullOfOrNull { word ->
    val w = word.value.lowercase()
    when {
        w.startsWith("no") && w != "none" -> Rating.Normal
        w.startsWith("un") -> Rating.Under
        w.startsWith("ov") -> Rating.Over
        else -> null
    }
}

/** A lost decimal point: one or two places from the end. */
private const val TENTHS = 10
private const val HUNDREDTHS = 100

/** Three digits or more: a summary value's lost decimal point ("812", "(560-75.8)"). */
private const val REPAIR_FROM = 100.0

/** How far from a line's middle another line can be and still be on its row, in line heights. */
private const val ROW_TOLERANCE = 0.6

/** Pixels a line beside can start left of the label (text recognition boxes aren't exact). */
private const val EDGE = 4
