package dev.saketanand.setwise.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
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

    fun parse(text: String): Result {
        val workouts = mutableListOf<SharedWorkout>()
        var unread = 0
        var current: SharedWorkout? = null
        fun close() {
            current?.takeIf { it.exercises.isNotEmpty() }?.let { workouts += it }
            current = null
        }
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val start = dateTime(line)
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

    /** "Squat: 100kg 5,5,5" → Squat, 3 × 100 kg × 5; "Pullups 10 8 6" → reps alone. */
    private fun exercise(line: String): SharedExercise? {
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

    /** A date in the line ("29/09/2026", "2026-09-29", "2 Oct 2026", "3rd October 2026", "Oct 2, 2026"), with its time if any. */
    fun dateTime(line: String): LocalDateTime? {
        val lower = line.lowercase(Locale.ROOT)
        val day = date(lower) ?: return null
        return LocalDateTime.of(day, time(lower))
    }

    private fun date(text: String): LocalDate? {
        Regex("\\b(20\\d{2})-(\\d{1,2})-(\\d{1,2})\\b").find(text)?.let { m ->
            return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        }
        Regex("\\b(\\d{1,2})[/.](\\d{1,2})[/.](20\\d{2})\\b").find(text)?.let { m ->
            return runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
        Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+([a-z]{3,9})\\.?,?\\s+(20\\d{2})\\b").find(text)?.let { m ->
            val month = month(m.groupValues[2]) ?: return@let
            return runCatching { LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt()) }.getOrNull()
        }
        Regex("\\b([a-z]{3,9})\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(20\\d{2})\\b").find(text)?.let { m ->
            val month = month(m.groupValues[1]) ?: return@let
            return runCatching { LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[2].toInt()) }.getOrNull()
        }
        return null
    }

    private fun month(word: String): Month? =
        Month.entries.firstOrNull { it.name.lowercase(Locale.ROOT).startsWith(word.take(3)) && word.length >= 3 }

    /** "18:30", "6pm", "6:30 pm", "morning", "evening"; else noon. */
    private fun time(text: String): LocalTime {
        Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b").find(text)?.let { m ->
            var hour = m.groupValues[1].toInt() % 12
            if (m.groupValues[3] == "pm") hour += 12
            return LocalTime.of(hour, m.groupValues[2].toIntOrNull() ?: 0)
        }
        Regex("\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b").find(text)?.let { m -> return LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        return when {
            "morning" in text -> LocalTime.of(8, 0)
            "evening" in text || "night" in text -> LocalTime.of(19, 0)
            "afternoon" in text -> LocalTime.of(15, 0)
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
            .replace(Regex("\\b\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\b\\d{1,2}:\\d{2}\\b"), " ")
            .replace(Regex("\\b(mon|tue|wed|thu|fri|sat|sun)[a-z]*\\b|\\b(at|on|morning|evening|afternoon|night)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("[-–,:|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.replaceFirstChar { it.titlecase(Locale.ROOT) }.ifEmpty { "Workout" }
    }

    private const val MAX_BARE_REPS = 100
}
