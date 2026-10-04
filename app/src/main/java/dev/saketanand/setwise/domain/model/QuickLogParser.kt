package dev.saketanand.setwise.domain.model

import java.util.Locale

/**
 * What [QuickLogParser] read from a quick-log line: the words naming the exercise, the sets (or
 * a cardio entry, or "same as last time"), and the words it couldn't place. [isComplete]: every
 * word is accounted for, so the result can be trusted without asking the on-device model.
 */
data class QuickLogParse(
    val exercisePhrase: String?,
    val sets: List<SetFact> = emptyList(),
    val cardio: CardioValues? = null,
    val sameAsLastTime: Boolean = false,
    val leftover: List<String> = emptyList(),
    /** Whole numbers on their own, read as nothing ("pull ups 10, 8, 6"). */
    val bare: List<Int> = emptyList(),
) {
    val hasSomething: Boolean get() = sets.isNotEmpty() || cardio != null || sameAsLastTime
    val isComplete: Boolean get() = hasSomething && leftover.isEmpty()
}

/**
 * The quick-log bar's own reader for the usual ways of writing sets: "3x8 at 60", "60x8x3",
 * "3 sets of 6 at 40", "100 for 5, 105 for 3", "30 for 10 twice", "8 reps at 20", "x12 x12 x10",
 * "3x45s", "3x12 at 50, last one 45 for 8", "same as last time", and cardio ("30 min 6% incline", "5 km", "level 8", "7 km/h"), also as
 * speech-to-text writes them ("three sets of eight at sixty", "3 by 8", "37 and a half"). Anything
 * else is left over, for the on-device model. Plain functions, unit-tested.
 */
object QuickLogParser {

    fun parse(text: String): QuickLogParse {
        var rest = " " + normalize(text) + " "
        val found = mutableListOf<Pair<Int, List<SetFact>>>() // position → sets, to keep their order
        var cardio = CardioValues(durationSec = null)
        var same = false
        var lastSetAt: Int? = null // "last one": what follows changes the final set

        fun take(regex: Regex, handle: (MatchResult) -> Boolean) {
            regex.findAll(rest).toList().forEach { match ->
                if (handle(match)) rest = rest.replaceRange(match.range, " ".repeat(match.value.length))
            }
        }
        fun sets(match: MatchResult, count: Int, weight: Double?, reps: Int?, seconds: Int?): Boolean {
            if (count !in 1..MAX_SETS) return false
            if (weight != null && weight !in 0.0..MAX_KG) return false
            if (reps != null && reps !in 1..MAX_REPS) return false
            if (seconds != null && seconds !in 1..MAX_SECONDS) return false
            found += match.range.first to List(count) { SetFact(weight, reps, seconds) }
            return true
        }
        fun num(match: MatchResult, group: Int) = match.groups[group]?.value?.toDouble()
        fun int(match: MatchResult, group: Int) = match.groups[group]?.value?.toIntOrNull()

        // "3 sets of bench at 15 reps": the count comes before the exercise, the set after it.
        var leadingCount: Int? = null
        take(LEADING_COUNT) { m -> m.range.first <= 1 && (int(m, 1) ?: 0) in 1..MAX_SETS && run { leadingCount = int(m, 1); true } }
        take(SAME) { same = true; true }
        take(LAST_SET) { lastSetAt = lastSetAt ?: it.range.first; true }
        // Cardio first: "km/h" before "km", "min" before plain numbers.
        take(SPEED) { cardio = cardio.copy(speedMinKmh = num(it, 1), speedMaxKmh = num(it, 1)); true }
        take(DISTANCE) { cardio = cardio.copy(distanceKm = num(it, 1)); true }
        take(MINUTES) { cardio = cardio.copy(durationSec = ((num(it, 1) ?: 0.0) * 60).toInt()); true }
        take(INCLINE) { cardio = cardio.copy(inclinePct = num(it, 1)); true }
        take(LEVEL) { cardio = cardio.copy(level = int(it, 1)); true }
        // Sets: the forms with the most numbers first.
        take(SETS_OF) { m ->
            val reps = int(m, 2)
            val seconds = if (m.groups[3] != null) reps else null
            sets(m, int(m, 1) ?: 0, num(m, 4), if (seconds != null) null else reps, seconds)
        }
        // "40 for 6 x 3" before the plain "a x b" forms read "6 x 3" as sets × reps.
        take(WEIGHT_FOR_REPS) { m ->
            val count = int(m, 3) ?: int(m, 4) ?: int(m, 5) ?: if (m.groups[6] != null) 2 else 1
            sets(m, count, num(m, 1), int(m, 2), null)
        }
        take(WEIGHT_X_REPS_X_SETS) { sets(it, int(it, 3) ?: 0, num(it, 1), int(it, 2), null) }
        take(SETS_X_REPS_AT_WEIGHT) { sets(it, int(it, 1) ?: 0, num(it, 3), int(it, 2), null) }
        take(SETS_X_SECONDS) { sets(it, int(it, 1) ?: 0, null, null, int(it, 2)) }
        take(WEIGHT_X_REPS) { m ->
            val first = num(m, 1) ?: 0.0
            // "60x8" is weight × reps; "3x10" (small, whole, no kg) is sets × reps.
            if (first > MAX_SETS || first % 1.0 != 0.0 || m.groups[2] != null) sets(m, 1, first, int(m, 3), null)
            else sets(m, first.toInt(), null, int(m, 3), null)
        }
        take(REPS_AT_WEIGHT) { sets(it, 1, num(it, 2), int(it, 1), null) }
        take(WEIGHT_KG_REPS) { sets(it, 1, num(it, 1), int(it, 2), null) }
        take(X_REPS) { sets(it, 1, null, int(it, 1), null) }
        // "45 seconds, three times", "20 reps twice".
        take(SECONDS) { sets(it, int(it, 2) ?: if (it.groups[3] != null) 2 else 1, null, null, int(it, 1)) }
        take(REPS_KG) { sets(it, 1, num(it, 2), int(it, 1), null) }
        take(REPS) { sets(it, int(it, 2) ?: if (it.groups[3] != null) 2 else 1, null, int(it, 1), null) }
        // "last one at 37.5": a weight alone only changes the final set.
        take(WEIGHT_ONLY) { m -> lastSetAt?.let { m.range.first > it } == true && sets(m, 1, num(m, 1), null, null) }

        val words = rest.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it !in FILLER }
        // The exercise: the words before the first thing read (or all words if nothing was).
        val firstRead = (found.map { it.first } + listOfNotNull(firstIndexOf(text, cardio, same))).minOrNull()
        val leading = if (firstRead == null) words else rest.substring(0, firstRead).trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it !in FILLER }
        val phrase = leading.filter { word -> word.any { it.isLetter() } }.joinToString(" ").ifEmpty { null }
        val leftover = words.drop(leading.size).filter { it !in FILLER }.toMutableList()
        val ordered = found.sortedBy { it.first }
        val changed = lastSetAt?.let { at ->
            changeLastSet(ordered.filter { it.first < at }.flatMap { it.second }, ordered.filter { it.first > at }.flatMap { it.second })
        }
        val read = changed ?: ordered.flatMap { it.second }.filter { it.reps != null || it.seconds != null }
        val sets = leadingCount?.let { count -> read.singleOrNull()?.let { set -> List(count) { set } } } ?: read
        // A "last one" that couldn't be applied stays for the model.
        if (lastSetAt != null && changed == null) leftover += "last"
        return QuickLogParse(
            exercisePhrase = phrase,
            bare = words.mapNotNull { word -> word.takeIf { w -> w.all { it.isDigit() } }?.toIntOrNull() },
            sets = sets,
            cardio = cardio.takeUnless { it.isEmpty },
            sameAsLastTime = same,
            leftover = leftover,
        )
    }

    /**
     * [before] with its final set changed by [change] ("3x12 at 50, last one only 9" → the third
     * is 50 × 9: what the change doesn't say stays). Null unless there are sets to change and
     * exactly one change.
     */
    private fun changeLastSet(before: List<SetFact>, change: List<SetFact>): List<SetFact>? {
        if (before.size < 2 || change.size != 1) return null
        val last = before.last()
        val new = change.single()
        return before.dropLast(1) + SetFact(new.weightKg ?: last.weightKg, new.reps ?: last.reps, new.seconds ?: last.seconds)
    }

    /** Where cardio or "same" was read, for finding the exercise words before it. */
    private fun firstIndexOf(text: String, cardio: CardioValues, same: Boolean): Int? {
        val normalized = " " + normalize(text) + " "
        val regexes = buildList {
            if (same) add(SAME)
            if (!cardio.isEmpty) addAll(listOf(SPEED, DISTANCE, MINUTES, INCLINE, LEVEL))
        }
        return regexes.mapNotNull { it.find(normalized)?.range?.first }.minOrNull()
    }

    /**
     * [text] in lower case with spoken numbers as digits: "Thirty seven and a half" → "37.5",
     * "three sets of eight" → "3 sets of 8". Only number words next to a number or a word about
     * sets become digits: "one arm row" and "last one" keep theirs.
     */
    fun withDigits(text: String): String {
        val words = text.lowercase(Locale.ROOT)
            .replace(Regex("\\b(${TENS.keys.joinToString("|")})-"), "$1 ") // sixty-five
            .trim()
            .split(Regex("\\s+"))
        val out = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            val number = numberAt(words, i)
            if (number != null) {
                val (value, end) = number
                val neighbours = listOfNotNull(out.lastOrNull(), words.getOrNull(end))
                    .map { it.trimEnd(',', ';', '.') }
                val nearNumbers = neighbours.any { it in NUMBER_CONTEXT || it.firstOrNull()?.isDigit() == true }
                if (nearNumbers && out.lastOrNull() !in NOT_A_COUNT) {
                    out += value.toString() + words[end - 1].takeLastWhile { it in ",;." }
                    i = end
                    continue
                }
            }
            out += words[i]
            i++
        }
        return out.joinToString(" ")
            .replace(Regex("(\\d+) point (\\d+)"), "$1.$2")
            .replace(Regex("(\\d+) and a half\\b"), "$1.5")
    }

    /** The number spelled out from words[start] ("sixty five", "a hundred and ten") and where it ends. */
    private fun numberAt(words: List<String>, start: Int): Pair<Int, Int>? {
        var i = start
        fun word() = words.getOrNull(i)?.trimEnd(',', ';', '.')
        fun belowHundred(): Int? {
            val tens = TENS[word()]
            if (tens != null) {
                i++
                if (!words[i - 1].last().isLetter()) return tens // "sixty, five"
                val unit = UNITS[word()]?.takeIf { it in 1..9 }?.also { i++ }
                return tens + (unit ?: 0)
            }
            return UNITS[word()]?.also { i++ }
        }
        var value = belowHundred()
        if (word() == "hundred" && (value == null || value in 1..9)) {
            i++
            value = (value ?: 1) * 100
            val afterHundred = i
            if (word() == "and") i++
            val rest = belowHundred()
            if (rest != null) value += rest else i = afterHundred
        }
        return value?.let { it to i }
    }

    private fun normalize(text: String): String {
        var t = withDigits(text)
            .replace('×', 'x')
            .replace("@", " at ")
            .replace(Regex("(\\d),(\\d)"), "$1.$2") // 37,5 → 37.5
            .replace(Regex("[,;]|\\.(?!\\d)"), " ")
            .replace(Regex("(\\d) (?:by|times) (?=\\d)"), "$1 x ") // "3 by 8", spoken
            // A lone "x12": 12 reps, not part of "a x b". Either the x is attached to its number
            // but not to the one before ("x12 x12 x10"), or it starts the reps with nothing
            // numeric before it ("dips x 12"). "60 x 8" and "3x8" are left as they are.
            .replace(Regex("(?<![a-z0-9.])x(?=\\d)"), "xr ")
            .replace(Regex("(?<![a-z0-9.])(?<![0-9.] )x (?=\\d)"), "xr ")
        // "3x8", "140x5x3" → "3 x 8", "140 x 5 x 3": again until nothing changes (a digit can end
        // one "x" and start the next, which one pass skips). Only an x written between digits:
        // in "x12 x12" each x belongs to the number after it.
        val digitXDigit = Regex("(\\d)x(?=\\d)")
        do {
            val before = t
            t = t.replace(digitXDigit, "$1 x ")
        } while (t != before)
        return t
            .replace(Regex("(\\d)([a-z%/]+)"), "$1 $2") // "60kg", "45sec", "6%", "7km/h"
            .replace(Regex("\\b(kgs|kilos?|kilograms?)\\b"), "kg")
            .replace(Regex("\\b(kms|kilometers?|kilometres?)\\b"), "km")
            .replace(Regex("\\bpercent\\b"), "%")
            .replace(Regex("\\b(seconds?|secs?)\\b"), "s")
            .replace(Regex("\\b(minutes?|mins?)\\b"), "min")
            .replace(Regex("\\breps?\\b"), "rep")
            .replace(Regex("\\b(?:last|final) (?:one|set)\\b"), "lastset")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** A pattern between spaces, without taking them: patterns can follow each other directly. */
    private fun r(pattern: String) = Regex("(?<= )$pattern(?= )")

    private const val N = "(\\d+(?:\\.\\d+)?)"
    private const val I = "(\\d+)"
    private val SAME = r("(?:same as last(?: time)?|repeat last(?: time)?|same again)")
    private val LAST_SET = r("lastset")
    private val SPEED = r("$N ?(?:km/h|kmh|kph)")
    private val DISTANCE = r("$N ?km")
    private val MINUTES = r("$N ?min")
    private val INCLINE = r("$N ?%(?: incline)?")
    private val LEVEL = r("(?:level|lvl) $I")
    private val SETS_OF = r("$I (?:more )?sets? of $I( s)?(?: rep)?(?: (?:at|with) $N(?: kg)?)?")
    private val WEIGHT_X_REPS_X_SETS = r("$N(?: kg)? x $I x $I")
    private val SETS_X_REPS_AT_WEIGHT = r("$I x $I(?: rep)? (?:at|with) $N(?: kg)?")
    private val SETS_X_SECONDS = r("$I x $I s")
    private val WEIGHT_X_REPS = r("$N( kg)? x $I(?: rep)?")
    private val WEIGHT_FOR_REPS = r("$N(?: kg)? for $I(?: rep)?(?: (?:x $I|for $I sets?|$I times|(twice)))?")
    private val REPS_AT_WEIGHT = r("$I rep (?:at|with) $N(?: kg)?")
    private val REPS_KG = r("$I rep $N kg")
    private val LEADING_COUNT = r("$I sets?(?: of)?(?= [a-z])")
    private val WEIGHT_KG_REPS = r("$N kg $I rep")
    private val X_REPS = r("xr $I")
    private val SECONDS = r("$I s(?: (?:$I times|(twice)))?")
    private val REPS = r("$I rep(?: (?:$I times|(twice)))?")
    private val WEIGHT_ONLY = r("(?:at|with) $N(?: kg)?")

    private val FILLER = setOf(
        "and", "then", "set", "sets", "rep", "kg", "x", "at", "for", "of", "with", "the", "a", "in", "did", "i", "my",
        "but", "only", "just", "was", "to",
    )

    private val UNITS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
        "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    ).withIndex().associate { (value, word) -> word to value }
    private val TENS = listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
        .withIndex().associate { (index, word) -> word to (index + 2) * 10 }

    /** Words around a number in a log line: a number word next to one is a number. */
    private val NUMBER_CONTEXT = setOf(
        "set", "sets", "of", "rep", "reps", "at", "for", "with", "x", "by", "times", "and", "point", "more",
        "kg", "kgs", "kilo", "kilos", "kilograms", "s", "sec", "secs", "second", "seconds", "min", "mins", "minute", "minutes",
        "km", "kms", "kilometers", "kilometres", "level", "percent", "%", "incline",
    )

    /** "last one", "the one": a word, not a count. */
    private val NOT_A_COUNT = setOf("last", "the", "that", "this", "each", "every", "which")

    private const val MAX_SETS = 20
    private const val MAX_REPS = 100
    private const val MAX_SECONDS = 3_600
    private const val MAX_KG = 500.0
}
