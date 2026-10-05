package dev.saketanand.setwise.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay
import java.util.Locale

/**
 * Reads a workout log written by hand (a notes app, a chat, another tracker's text): a line with
 * a date starts a workout ("Leg day - Tue 29/09/2026 18:30", "2 Oct 2026 push"), and every
 * other line is an exercise read by [QuickLogParser] ("Squat: 100kg 5,5,5", "bench press 60x8
 * 62.5x6", "Pullups 10 8 6", "Plank 60s, 45s"). Lines it can't read are counted, not guessed.
 * Plain functions, unit-tested.
 */
object LogTextParser {

    data class Result(val workouts: List<SharedWorkout>, val unreadLines: Int)

    /**
     * @param today when given, a date without a year ("30 Sep") is read as its last time on or
     *   before today; without it, such lines aren't dates.
     */
    fun parse(text: String, today: LocalDate? = null): Result {
        val workouts = mutableListOf<SharedWorkout>()
        var unread = 0
        var current: SharedWorkout? = null
        fun close() {
            current?.takeIf { it.exercises.isNotEmpty() }?.let { workouts += it }
            current = null
        }
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val start = dateTime(line, today)
            if (start != null) {
                close()
                current = SharedWorkout(name(line), start, emptyList())
                return@forEach
            }
            val workout = current ?: return@forEach
            val exercise = exercise(line)
            if (exercise == null) {
                unread++
                return@forEach
            }
            // The same exercise again in one workout: more sets of it.
            val existing = workout.exercises.indexOfFirst { it.name.equals(exercise.name, ignoreCase = true) }
            current = workout.copy(
                exercises = if (existing < 0) {
                    workout.exercises + exercise
                } else {
                    workout.exercises.toMutableList().also { it[existing] = it[existing].copy(sets = it[existing].sets + exercise.sets) }
                },
            )
        }
        close()
        return Result(workouts, unread)
    }

    /** "Squat: 100kg 5,5,5" → Squat, 3 × 100 kg × 5; "Pullups 10 8 6" → reps alone; null if it can't read it. */
    fun exercise(line: String): SharedExercise? {
        val parse = QuickLogParser.parse(line)
        val phrase = parse.exercisePhrase ?: return null
        val sets = parse.sets.ifEmpty {
            parse.bare.takeIf { bare -> bare.isNotEmpty() && bare.all { it in 1..MAX_BARE_REPS } && parse.leftover.all { it.toIntOrNull() != null } }
                ?.map { SetFact(null, it, null) }
                .orEmpty()
        }
        if (sets.isEmpty()) return null
        // The name as written (its case), up to the first number.
        val written = line.takeWhile { !it.isDigit() }.trim().trimEnd(':', '-', '–', ',', '@').trim()
        val name = written.takeIf { it.lowercase(Locale.ROOT).contains(phrase.substringBefore(' ')) } ?: phrase
        return SharedExercise(name, sets.map { SharedSet(it.weightKg, it.reps, it.seconds) })
    }

    /**
     * A date in the line ("29/09/2026", "2026-09-29", "2 Oct 2026", "3rd October 2026", "Oct 2, 2026"), with its time if any.
     * With [today], also a named month without a year ("30 Sep", "Sep 30"), on a line that isn't an exercise.
     */
    fun dateTime(line: String, today: LocalDate? = null): LocalDateTime? {
        val lower = line.lowercase(Locale.ROOT)
        val day = date(lower) ?: today?.let { yearless(lower, line, it) } ?: return null
        return LocalDateTime.of(day, time(lower))
    }

    /**
     * "30 Sep", "Sep 30", "3rd October": the last such day on or before [today]. Only a whole
     * month name or its abbreviation counts ("3 decline" isn't 3 December), and only if the rest
     * of the line isn't an exercise ("bench 3x8 at 60, sep 30" isn't a workout's date).
     */
    private fun yearless(lower: String, line: String, today: LocalDate): LocalDate? {
        val monthDay = YEARLESS_FORMS.asSequence().flatMap { it.findAll(lower) }.firstNotNullOfOrNull { m ->
            val month = MONTH_WORDS[m.groups["month"]?.value] ?: return@firstNotNullOfOrNull null
            val day = m.groups["day"]?.value?.toIntOrNull() ?: return@firstNotNullOfOrNull null
            // The rest of the line mustn't be an exercise ("bench 3x8 at 60, sep 30").
            if (exercise(line.removeRange(m.range)) != null) return@firstNotNullOfOrNull null
            runCatching { MonthDay.of(month, day) }.getOrNull()
        } ?: return null
        return monthDay.atYear(today.year).let { if (it.isAfter(today)) monthDay.atYear(today.year - 1) else it }
    }

    /** The first date written in one of [DATE_FORMS]. */
    private fun date(text: String): LocalDate? = DATE_FORMS.firstNotNullOfOrNull { (form, namedMonth) ->
        val m = form.find(text) ?: return@firstNotNullOfOrNull null
        fun group(name: String) = m.groups[name]?.value
        val month = group("month")?.let { if (namedMonth) month(it) else it.toIntOrNull() } ?: return@firstNotNullOfOrNull null
        val year = group("year")?.toIntOrNull() ?: return@firstNotNullOfOrNull null
        val day = group("day")?.toIntOrNull() ?: return@firstNotNullOfOrNull null
        runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    /** Each with year, month and day groups; true where the month is a name ("Oct", "October"). */
    private val DATE_FORMS = listOf(
        Regex("\\b(?<year>20\\d{2})-(?<month>\\d{1,2})-(?<day>\\d{1,2})\\b") to false,
        Regex("\\b(?<day>\\d{1,2})[/.](?<month>\\d{1,2})[/.](?<year>20\\d{2})\\b") to false,
        Regex("\\b(?<day>\\d{1,2})(?:st|nd|rd|th)?\\s+(?<month>[a-z]{3,9})\\.?,?\\s+(?<year>20\\d{2})\\b") to true,
        Regex("\\b(?<month>[a-z]{3,9})\\.?\\s+(?<day>\\d{1,2})(?:st|nd|rd|th)?,?\\s+(?<year>20\\d{2})\\b") to true,
    )

    /** A day and a month name, no year. */
    private val YEARLESS_FORMS = listOf(
        Regex("\\b(?<day>\\d{1,2})(?:st|nd|rd|th)?\\s+(?<month>[a-z]{3,9})\\b\\.?"),
        Regex("\\b(?<month>[a-z]{3,9})\\.?\\s+(?<day>\\d{1,2})(?:st|nd|rd|th)?\\b"),
    )

    /** Month names and their usual abbreviations ("sep", "sept", "september"), nothing looser. */
    private val MONTH_WORDS: Map<String, Int> = Month.entries.flatMap { month ->
        val name = month.name.lowercase(Locale.ROOT)
        listOf(name to month.value, name.take(MONTH_ABBREVIATION) to month.value)
    }.toMap() + ("sept" to Month.SEPTEMBER.value)

    private fun month(word: String): Int? =
        Month.entries.firstOrNull { it.name.lowercase(Locale.ROOT).startsWith(word.take(MONTH_ABBREVIATION)) }?.value

    /** "18:30", "6pm", "6:30 pm", "morning", "evening"; else noon. */
    private fun time(text: String): LocalTime {
        TWELVE_HOUR.find(text)?.let { m ->
            val hour = m.groups["hour"]?.value?.toIntOrNull() ?: return@let
            val time = LocalTime.of(hour % HALF_DAY, m.groups["minute"]?.value?.toIntOrNull() ?: 0)
            return if (m.groups["pm"] != null) time.plusHours(HALF_DAY.toLong()) else time
        }
        TWENTY_FOUR_HOUR.find(text)?.let { m -> return LocalTime.parse(m.value.padStart(TIME_LENGTH, '0')) }
        return when {
            "morning" in text -> MORNING
            "evening" in text || "night" in text -> EVENING
            "afternoon" in text -> AFTERNOON
            else -> LocalTime.NOON
        }
    }

    /** The line without its date, time and weekday: "Leg day - Tue 29/09/2026 18:30" → "Leg day". */
    private fun name(line: String): String {
        val cleaned = line
            .replace(Regex("\\b20\\d{2}-\\d{1,2}-\\d{1,2}\\b"), " ")
            .replace(Regex("\\b\\d{1,2}[/.]\\d{1,2}[/.]20\\d{2}\\b"), " ")
            .replace(Regex("\\b\\d{1,2}(?:st|nd|rd|th)?\\s+[A-Za-z]{3,9}\\.?,?\\s+20\\d{2}\\b"), " ")
            .replace(Regex("\\b[A-Za-z]{3,9}\\.?\\s+\\d{1,2}(?:st|nd|rd|th)?,?\\s+20\\d{2}\\b"), " ")
            // No year ("30 Sep"): only with a real month name, so other words stay.
            .replace(Regex("\\b\\d{1,2}(?:st|nd|rd|th)?\\s+(?:${MONTH_PATTERN})\\b\\.?", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\b(?:${MONTH_PATTERN})\\.?\\s+\\d{1,2}(?:st|nd|rd|th)?\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\b\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\b\\d{1,2}:\\d{2}\\b"), " ")
            .replace(Regex("\\b(mon|tue|wed|thu|fri|sat|sun)[a-z]*\\b|\\b(at|on|morning|evening|afternoon|night)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("[-–,:|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.replaceFirstChar { it.titlecase(Locale.ROOT) }.ifEmpty { "Workout" }
    }

    private val MONTH_PATTERN = MONTH_WORDS.keys.sortedByDescending { it.length }.joinToString("|")
    private const val MAX_BARE_REPS = 100
    private const val HALF_DAY = 12
    private const val MONTH_ABBREVIATION = 3 // "oct"
    private const val TIME_LENGTH = 5 // "08:30"
    private val TWELVE_HOUR = Regex("\\b(?<hour>\\d{1,2})(?::(?<minute>\\d{2}))?\\s*(?:am|(?<pm>pm))\\b")
    private val TWENTY_FOUR_HOUR = Regex("\\b([01]?\\d|2[0-3]):[0-5]\\d\\b")

    /** Times for a log that only says when in the day. */
    private val MORNING = LocalTime.of(8, 0)
    private val AFTERNOON = LocalTime.of(15, 0)
    private val EVENING = LocalTime.of(19, 0)
}
