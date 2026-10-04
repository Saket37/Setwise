package dev.saketanand.setwise.domain.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Reads workouts from a CSV export (one row per set) by its column headers, so it works for
 * Strong ("Date, Workout Name, Duration, Exercise Name, Set Order, Weight, Reps, Distance,
 * Seconds"), Hevy ("title, start_time, end_time, exercise_title, set_index, weight_kg, reps,
 * distance_km, duration_seconds"), FitNotes ("Date, Exercise, Weight (kg), Reps, Distance,
 * Time") and others with similar headers. Comma or semicolon separated, quoted values, kg or lb
 * (from the header or a unit column). Rows of one start time and name make a workout. Plain
 * functions, unit-tested.
 */
object CsvWorkoutParser {

    /** Null when it isn't a workout CSV (no date, exercise or set columns). */
    fun parse(text: String): List<SharedWorkout>? {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return null
        val separator = if (lines[0].count { it == ';' } > lines[0].count { it == ',' }) ';' else ','
        val header = split(lines[0], separator).map { normalize(it) }
        val columns = Columns.of(header) ?: return null
        val poundsByHeader = header.getOrNull(columns.weight ?: -1)?.let { "lb" in it } == true

        data class Row(val start: LocalDateTime, val name: String, val duration: Duration?, val exercise: String, val set: SharedSet)
        val rows = lines.drop(1).mapNotNull { line ->
            val cells = split(line, separator)
            fun cell(index: Int?) = index?.let { cells.getOrNull(it)?.trim() }?.takeIf { it.isNotEmpty() }
            val start = cell(columns.date)?.let(::dateTime) ?: return@mapNotNull null
            val exercise = cell(columns.exercise) ?: return@mapNotNull null
            val pounds = poundsByHeader || cell(columns.weightUnit)?.lowercase(Locale.ROOT)?.startsWith("lb") == true
            val miles = cell(columns.distanceUnit)?.lowercase(Locale.ROOT)?.startsWith("mi") == true ||
                header.getOrNull(columns.distance ?: -1)?.contains("mi") == true
            val weight = cell(columns.weight)?.let(::number)?.let { if (pounds) it * LB_TO_KG else it }
            val set = SharedSet(
                weightKg = weight?.takeIf { it > 0 }?.let { (it * 10).roundToInt() / 10.0 },
                reps = cell(columns.reps)?.let(::number)?.toInt()?.takeIf { it > 0 },
                seconds = cell(columns.seconds)?.let(::seconds)?.takeIf { it > 0 },
                distanceKm = cell(columns.distance)?.let(::number)?.let { if (miles) it * MI_TO_KM else it }?.takeIf { it > 0 }
                    ?.let { (it * 100).roundToInt() / 100.0 },
            )
            if (set.reps == null && set.seconds == null && set.distanceKm == null) return@mapNotNull null
            val end = cell(columns.end)?.let(::dateTime)
            val duration = end?.let { Duration.between(start, it).takeIf { d -> !d.isNegative } } ?: cell(columns.duration)?.let(::duration)
            Row(start, cell(columns.name) ?: DEFAULT_NAME, duration, exercise, set)
        }
        if (rows.isEmpty()) return null
        // A workout: its start and name, in the order they come; its exercises in first-seen order.
        return rows.groupBy { it.start to it.name }.map { (key, workoutRows) ->
            SharedWorkout(
                name = key.second,
                startedAt = key.first,
                exercises = workoutRows.groupBy { it.exercise }.map { (exercise, sets) -> SharedExercise(exercise, sets.map { it.set }) },
                duration = workoutRows.firstNotNullOfOrNull { it.duration },
            )
        }
    }

    private data class Columns(
        val date: Int,
        val end: Int?,
        val name: Int?,
        val duration: Int?,
        val exercise: Int,
        val weight: Int?,
        val weightUnit: Int?,
        val reps: Int?,
        val distance: Int?,
        val distanceUnit: Int?,
        val seconds: Int?,
    ) {
        companion object {
            fun of(header: List<String>): Columns? {
                fun find(vararg names: String) = names.firstNotNullOfOrNull { name -> header.indexOf(name).takeIf { it >= 0 } }
                fun findStart(vararg prefixes: String) = header.indexOfFirst { h -> prefixes.any { h.startsWith(it) } }.takeIf { it >= 0 }
                val date = find("date", "starttime", "start", "workoutdate", "datetime", "time") ?: return null
                val exercise = find("exercisename", "exercisetitle", "exercise", "name") ?: return null
                val reps = find("reps", "repetitions")
                val seconds = find("seconds", "durationseconds", "time", "durationsec").takeIf { it != date }
                val distance = findStart("distance").takeIf { header.getOrNull(it ?: -1)?.contains("unit") != true }
                if (reps == null && seconds == null && distance == null) return null
                return Columns(
                    date = date,
                    end = find("endtime", "end"),
                    name = find("workoutname", "title", "workout", "routine", "routinename").takeIf { it != exercise },
                    duration = find("duration", "workoutduration"),
                    exercise = exercise,
                    weight = findStart("weight").takeIf { header.getOrNull(it ?: -1)?.contains("unit") != true },
                    weightUnit = find("weightunit", "unit"),
                    reps = reps,
                    distance = distance,
                    distanceUnit = find("distanceunit"),
                    seconds = seconds,
                )
            }
        }
    }

    /** One line's cells: separated by [separator], with "quoted, values" and "" for a quote. */
    private fun split(line: String, separator: Char): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && line.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == separator && !quoted -> { cells += cell.toString(); cell.clear() }
                else -> cell.append(c)
            }
            i++
        }
        cells += cell.toString()
        return cells
    }

    /** "Weight (kg)" → "weightkg", "start_time" → "starttime". */
    private fun normalize(header: String) = header.lowercase(Locale.ROOT).replace(Regex("[^a-z]"), "")

    private fun number(text: String): Double? = text.replace(',', '.').replace(Regex("[^0-9.\\-]"), "").toDoubleOrNull()

    /** "90", "1:30", "00:01:30". */
    private fun seconds(text: String): Int? {
        val parts = text.split(':').map { it.trim().toIntOrNull() ?: return number(text)?.toInt() }
        return parts.fold(0) { total, part -> total * 60 + part }
    }

    /** Strong: "1h 5m", "48m", "1h"; or seconds; or "1:05:00". */
    private fun duration(text: String): Duration? {
        Regex("(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m(?:in)?)?").matchEntire(text.trim())?.let { m ->
            val hours = m.groupValues[1].toLongOrNull() ?: 0
            val minutes = m.groupValues[2].toLongOrNull() ?: 0
            if (hours + minutes > 0) return Duration.ofMinutes(hours * 60 + minutes)
        }
        return seconds(text)?.takeIf { it > 0 }?.let { Duration.ofSeconds(it.toLong()) }
    }

    fun dateTime(text: String): LocalDateTime? {
        val value = text.trim().replace(Regex("\\s+"), " ")
        DATE_TIMES.forEach { format -> runCatching { return LocalDateTime.parse(value, format) } }
        DATES.forEach { format -> runCatching { return LocalDate.parse(value, format).atTime(DEFAULT_TIME) } }
        return null
    }

    private fun lenient(pattern: String): DateTimeFormatter =
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH)

    private val DATE_TIMES = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm",
        "d MMM yyyy, HH:mm", "d MMM yyyy HH:mm", "d MMMM yyyy, HH:mm", "MMM d, yyyy, h:mm a", "MMM d, yyyy h:mm a",
        "d/M/yyyy HH:mm", "M/d/yyyy h:mm a", "dd.MM.yyyy HH:mm",
    ).map(::lenient)

    private val DATES = listOf("yyyy-MM-dd", "d MMM yyyy", "d/M/yyyy", "dd.MM.yyyy").map(::lenient)

    /** A file with dates only (FitNotes): noon, so each day's workout is one. */
    private val DEFAULT_TIME: LocalTime = LocalTime.NOON
    private const val DEFAULT_NAME = "Workout"
    private const val LB_TO_KG = 0.45359237
    private const val MI_TO_KM = 1.609344
}
