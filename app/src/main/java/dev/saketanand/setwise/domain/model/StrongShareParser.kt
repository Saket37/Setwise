package dev.saketanand.setwise.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.util.Locale
import kotlin.math.roundToInt

/** A workout as another app shared it: what to import. */
data class SharedWorkout(
    val name: String,
    val startedAt: LocalDateTime,
    val exercises: List<SharedExercise>,
    /** How long it took, when the source says (CSV exports do; Strong's share text doesn't). */
    val duration: java.time.Duration? = null,
) {
    val setCount: Int get() = exercises.sumOf { it.sets.size }
}

/** [readByModel]: its sets came from the on-device model, not from Setwise's own readers: worth checking. */
data class SharedExercise(val name: String, val sets: List<SharedSet>, val readByModel: Boolean = false)

/** One set: weight × reps, reps, a hold, or cardio (distance and/or time). */
data class SharedSet(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val seconds: Int? = null,
    val distanceKm: Double? = null,
)

/**
 * Reads workouts shared from Strong ("Share workout" text), one or several pasted together:
 *
 *     Evening Workout
 *     Wednesday, 30 September 2026 at 8:01 pm
 *
 *     Bicep Curl (Barbell)
 *     Set 1: 10 kg × 15 reps
 *     …
 *     https://link.strong.app/…
 *
 * A workout starts at a line followed by a date line; an exercise is a line followed by "Set"
 * lines. Sets: "10 kg × 15 reps", "15 reps", "+10 kg × 8 reps" (weighted), "-20 kg × 8 reps"
 * (assisted: no weight), "1:30" / "45 s" (holds), "2.5 km", "5 km · 25:00"; lb become kg.
 * Lines it doesn't know are skipped. Plain functions, unit-tested.
 */
object StrongShareParser {

    fun parse(text: String): List<SharedWorkout> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("http") }
        val workouts = mutableListOf<SharedWorkout>()
        var i = 0
        while (i < lines.size) {
            val start = lines.getOrNull(i + 1)?.let(::dateTime)
            if (start == null) {
                i++
                continue
            }
            val name = lines[i]
            i += 2
            val exercises = mutableListOf<SharedExercise>()
            while (i < lines.size && lines.getOrNull(i + 1)?.let(::dateTime) == null) {
                val exercise = lines[i]
                val sets = mutableListOf<SharedSet>()
                i++
                while (i < lines.size) {
                    val match = SET_LINE.matchEntire(lines[i]) ?: break
                    set(match.groupValues[1])?.let { sets += it }
                    i++
                }
                if (sets.isNotEmpty() && !SET_LINE.matches(exercise)) exercises += SharedExercise(exercise, sets)
            }
            if (exercises.isNotEmpty()) workouts += SharedWorkout(name, start, exercises)
        }
        return workouts
    }

    /** "Wednesday, 30 September 2026 at 8:01 pm" (also without the weekday, or 24-hour "20:01"). */
    fun dateTime(line: String): LocalDateTime? {
        val m = DATE.find(line) ?: return null
        val month = Month.entries.firstOrNull { it.name.startsWith(m.groupValues[2].uppercase(Locale.ROOT).take(3)) } ?: return null
        val day = runCatching { LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt()) }.getOrNull() ?: return null
        var hour = m.groupValues[4].toInt()
        val minute = m.groupValues[5].toInt()
        when (m.groupValues[6].lowercase(Locale.ROOT)) {
            "pm" -> if (hour < 12) hour += 12
            "am" -> if (hour == 12) hour = 0
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalDateTime.of(day, LocalTime.of(hour, minute))
    }

    /** One set's value: "10 kg × 15 reps", "15 reps", "1:30", "2.5 km"… (also [StrongScreenParser]'s rows). */
    internal fun set(value: String): SharedSet? {
        val text = value.replace('×', 'x').replace(',', '.').lowercase(Locale.ROOT)
        val pounds = Regex("\\blbs?\\b").containsMatchIn(text)
        fun kg(v: Double) = if (pounds) (v * LB_TO_KG * 10).roundToInt() / 10.0 else v
        // "10 kg x 15 reps", "+10 kg x 8 reps" (weighted), "-20 kg x 8 reps" (assisted).
        Regex("([+-]?\\d+(?:\\.\\d+)?)\\s*(?:kg|lbs?)\\s*x\\s*(\\d+)").find(text)?.let { m ->
            val weight = m.groupValues[1].toDouble()
            return SharedSet(weightKg = kg(weight).takeIf { it > 0 }, reps = m.groupValues[2].toInt())
        }
        Regex("^(\\d+)\\s*reps?\\b").find(text)?.let { return SharedSet(reps = it.groupValues[1].toInt()) }
        val distance = Regex("(\\d+(?:\\.\\d+)?)\\s*(km|mi)\\b").find(text)?.let { m ->
            m.groupValues[1].toDouble().let { if (m.groupValues[2] == "mi") (it * MI_TO_KM * 100).roundToInt() / 100.0 else it }
        }
        val seconds = Regex("(?:(\\d+):)?(\\d{1,2}):(\\d{2})").find(text)?.let { m ->
            (m.groupValues[1].toIntOrNull() ?: 0) * 3600 + m.groupValues[2].toInt() * 60 + m.groupValues[3].toInt()
        } ?: Regex("(\\d+)\\s*(?:s|sec|secs|seconds)\\b").find(text)?.groupValues?.get(1)?.toInt()
        if (distance == null && seconds == null) return null
        return SharedSet(seconds = seconds, distanceKm = distance)
    }

    private val SET_LINE = Regex("^(?:set\\s*\\w+|warm-?up(?: set)?|drop(?: set)?|failure(?: set)?)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
    private val DATE = Regex(
        "(\\d{1,2})\\s+([A-Za-z]{3,9})\\.?\\s+(\\d{4})(?:,)?\\s*(?:at\\s+)?(\\d{1,2})[:.](\\d{2})\\s*(am|pm)?",
        RegexOption.IGNORE_CASE,
    )
    private const val LB_TO_KG = 0.45359237
    private const val MI_TO_KM = 1.609344
}
