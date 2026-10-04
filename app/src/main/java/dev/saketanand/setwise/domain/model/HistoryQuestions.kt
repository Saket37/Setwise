package dev.saketanand.setwise.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** A span of days a question is about; [start] null = from the first workout, [end] exclusive. */
data class Period(val start: LocalDate?, val end: LocalDate?, val name: PeriodName) {
    fun contains(day: LocalDate) = (start == null || !day.isBefore(start)) && (end == null || day.isBefore(end))

    companion object {
        val AllTime = Period(null, null, PeriodName.AllTime)

        fun of(name: PeriodName, today: LocalDate): Period {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val month = today.withDayOfMonth(1)
            return when (name) {
                PeriodName.AllTime -> AllTime
                PeriodName.ThisWeek -> Period(monday, monday.plusWeeks(1), name)
                PeriodName.LastWeek -> Period(monday.minusWeeks(1), monday, name)
                PeriodName.ThisMonth -> Period(month, month.plusMonths(1), name)
                PeriodName.LastMonth -> Period(month.minusMonths(1), month, name)
                PeriodName.ThisYear -> Period(today.withDayOfYear(1), today.withDayOfYear(1).plusYears(1), name)
                is PeriodName.InMonth -> Period(name.month.atDay(1), name.month.plusMonths(1).atDay(1), name)
            }
        }
    }
}

sealed interface PeriodName {
    data object AllTime : PeriodName
    data object ThisWeek : PeriodName
    data object LastWeek : PeriodName
    data object ThisMonth : PeriodName
    data object LastMonth : PeriodName
    data object ThisYear : PeriodName
    data class InMonth(val month: YearMonth) : PeriodName
}

/** The fixed lookups a history question can be: code runs them, nothing is made up. */
sealed interface HistoryQuestion {
    /** "What's my best bench press?": the best set (estimated 1RM, most reps, longest hold). */
    data class BestSet(val exercise: Exercise, val period: Period) : HistoryQuestion

    /** "When did I last squat 100 kg?": the newest set, at least [atLeastKg] if given. */
    data class LastLifted(val exercise: Exercise, val atLeastKg: Double?) : HistoryQuestion

    /** "How much volume did I do in September?": weight × reps, in [muscles] if given ("leg volume"). */
    data class Volume(val period: Period, val muscles: MuscleWords?) : HistoryQuestion

    /** "How many times did I train legs this month?": workouts with the exercise or muscles (or any). */
    data class Sessions(val period: Period, val exercise: Exercise?, val muscles: MuscleWords?) : HistoryQuestion

    /** "Show workouts where I hit a PR". */
    data class PrWorkouts(val period: Period) : HistoryQuestion
}

/** A body part as asked ("legs") and the muscle groups it means. */
data class MuscleWords(val asked: String, val groups: Set<String>)

/** A set as an answer shows it: "100 kg for 5 reps on your second set". */
data class AnsweredSet(val exerciseName: String, val setNumber: Int, val weightKg: Double?, val reps: Int?, val seconds: Int?, val isPr: Boolean)

/** A workout an answer points to: "MON 28 · Leg Day · Back Squat 100 × 5 · PR". */
data class AnsweredWorkout(val id: Long, val name: String, val date: LocalDate, val highlight: AnsweredSet?)

sealed interface HistoryAnswer {
    val question: HistoryQuestion

    /** A set found: its workout, and whether it's also the best set ever. */
    data class Lifted(override val question: HistoryQuestion, val workout: AnsweredWorkout, val set: AnsweredSet, val isBest: Boolean) : HistoryAnswer

    data class Total(override val question: HistoryQuestion, val volumeKg: Double, val workouts: Int) : HistoryAnswer

    /** [workouts] newest first (a few, for the list). */
    data class Count(override val question: HistoryQuestion, val count: Int, val workouts: List<AnsweredWorkout>) : HistoryAnswer

    /** Nothing in the log matches. */
    data class NoneFound(override val question: HistoryQuestion) : HistoryAnswer
}

/**
 * Reads a history question in code: which lookup, which exercise or body part, which period.
 * Null when it can't tell which lookup (the on-device model may then pick one). Plain functions.
 */
object HistoryQuestionReader {

    /** What's left once the lookup, period and numbers are read: the exercise words, if any. */
    data class Read(val kind: Kind?, val period: PeriodName?, val kg: Double?, val muscles: MuscleWords?, val exerciseWords: String)

    enum class Kind { BestSet, LastLifted, Volume, Sessions, PrWorkouts }

    fun read(question: String, today: LocalDate): Read {
        var text = " " + QuickLogParser.withDigits(question.replace('’', '\'').replace(Regex("'(ve|m|s|d|ll|re)\\b"), ""))
            .replace(Regex("[?!.,']"), " ").replace(Regex("\\s+"), " ").trim() + " "
        fun has(vararg words: String) = words.any { text.contains(" $it ") }

        val period = periodIn(text, today)
        val kg = Regex(" (\\d+(?:\\.\\d+)?) ?(?:kg|kgs|kilos?|kilograms?)? ").find(text)?.groupValues?.get(1)?.toDouble()
        val muscles = MUSCLES.entries.firstOrNull { (word, _) -> has(word) }?.let { (word, groups) -> MuscleWords(word, groups) }
        val kind = when {
            has("volume", "tonnage") || text.contains(" how much weight ") || text.contains(" total weight ") -> Kind.Volume
            text.contains(" how many ") || text.contains(" how often ") -> Kind.Sessions
            (has("pr", "prs", "record", "records") && !has("best", "heaviest", "max", "top", "strongest")) &&
                (has("workouts", "workout", "sessions", "where", "which", "show") || text.contains(" hit a ")) -> Kind.PrWorkouts
            has("best", "heaviest", "max", "top", "strongest", "pr", "record") || text.contains(" how strong ") -> Kind.BestSet
            has("last", "when", "latest", "recently") -> Kind.LastLifted
            else -> null
        }
        // The exercise: what's left after the question's own words.
        text = text.replace(Regex(" (\\d+(?:\\.\\d+)?) ?(?:kg|kgs|kilos?|kilograms?)? "), " ")
        PERIOD_PHRASES.forEach { text = text.replace(" $it ", " ") }
        val words = text.trim().split(" ").filter { it.isNotEmpty() && it !in QUESTION_WORDS && it !in MONTHS && (muscles == null || it != muscles.asked) }
        return Read(kind, period, kg, muscles, words.joinToString(" ") { it.stemmed() })
    }

    private fun periodIn(text: String, today: LocalDate): PeriodName? {
        if (text.contains(" this week ")) return PeriodName.ThisWeek
        if (text.contains(" last week ")) return PeriodName.LastWeek
        if (text.contains(" this month ")) return PeriodName.ThisMonth
        if (text.contains(" last month ")) return PeriodName.LastMonth
        if (text.contains(" this year ")) return PeriodName.ThisYear
        if (text.contains(" ever ") || text.contains(" all time ")) return PeriodName.AllTime
        val month = MONTHS.entries.firstOrNull { (word, _) -> text.contains(" $word ") }?.value ?: return null
        // The latest such month up to now: "September" in October 2026 is September 2026.
        val thisYear = YearMonth.of(today.year, month)
        return PeriodName.InMonth(if (thisYear.isAfter(YearMonth.from(today))) thisYear.minusYears(1) else thisYear)
    }

    /** "squatted" → squat, "benched" → bench, "pressing" → press: for matching exercise names. */
    private fun String.stemmed(): String {
        val stems = listOf("ted" to "t", "ed" to "", "ing" to "", "es" to "", "s" to "")
        if (length < 5 || this in KEEP_AS_IS) return this
        stems.forEach { (ending, replacement) -> if (endsWith(ending)) return dropLast(ending.length).let { if (ending == "ted" && it.endsWith("t")) it else it + replacement } }
        return this
    }

    private val KEEP_AS_IS = setOf("press", "dips", "abs", "rows", "pullups", "pushups", "lunges", "curls", "raises", "squats", "deadlifts", "presses")

    val MUSCLES: Map<String, Set<String>> = linkedMapOf(
        "legs" to setOf("Quads", "Hamstrings", "Glutes", "Calves"),
        "leg" to setOf("Quads", "Hamstrings", "Glutes", "Calves"),
        "chest" to setOf("Chest"),
        "back" to setOf("Back"),
        "shoulders" to setOf("Shoulders"),
        "arms" to setOf("Biceps", "Triceps", "Forearms"),
        "biceps" to setOf("Biceps"),
        "triceps" to setOf("Triceps"),
        "core" to setOf("Core"),
        "abs" to setOf("Core"),
        "glutes" to setOf("Glutes"),
        "quads" to setOf("Quads"),
        "hamstrings" to setOf("Hamstrings"),
        "calves" to setOf("Calves"),
        "cardio" to setOf("Cardio"),
    )

    private val MONTHS: Map<String, Month> = Month.entries.flatMap { month ->
        val name = month.name.lowercase(Locale.ROOT)
        listOf(name to month, name.take(3) to month) + if (month == Month.SEPTEMBER) listOf("sept" to month) else emptyList()
    }.toMap()

    private val PERIOD_PHRASES = listOf("this week", "last week", "this month", "last month", "this year", "all time")

    private val QUESTION_WORDS = setOf(
        "what", "whats", "what's", "s", "is", "was", "were", "are", "my", "me", "i", "the", "a", "an", "of", "on", "in", "at", "for", "with",
        "did", "do", "does", "have", "has", "had", "how", "many", "much", "often", "times", "time", "when", "last", "latest", "recently",
        "best", "heaviest", "max", "top", "strongest", "pr", "prs", "record", "records", "ever", "show", "workouts", "workout", "sessions",
        "session", "where", "which", "hit", "train", "trained", "training", "volume", "tonnage", "total", "weight", "lift", "lifted",
        "set", "sets", "rep", "reps", "kg", "kgs", "kilo", "kilos", "go", "went", "and", "or", "to", "it", "this", "that", "week",
        "month", "year", "all", "tell", "about", "can", "you", "please", "most", "far", "so",
        "strong", "these", "days", "now", "currently", "nowadays", "lately", "been", "doing", "going", "ve", "any", "work",
    )
}

/** Runs a [HistoryQuestion] over the training log (newest workout first). Plain functions. */
object HistoryAnswerer {

    fun answer(question: HistoryQuestion, log: List<LoggedSetRecord>, zone: ZoneId): HistoryAnswer {
        fun day(record: LoggedSetRecord) = record.startedAt.atZone(zone).toLocalDate()
        return when (question) {
            is HistoryQuestion.BestSet -> {
                val sets = log.filter { it.exerciseId == question.exercise.id && question.period.contains(day(it)) }
                val best = sets.maxWithOrNull(compareBy({ score(it) }, { -it.startedAt.toEpochMilli() })) ?: return HistoryAnswer.NoneFound(question)
                HistoryAnswer.Lifted(question, best.workout(day(best)), best.answered(), isBest = true)
            }
            is HistoryQuestion.LastLifted -> {
                val sets = log.filter { it.exerciseId == question.exercise.id }
                val newest = sets.firstOrNull { set -> question.atLeastKg == null || (set.weightKg ?: 0.0) >= question.atLeastKg - 1e-6 }
                    ?: return HistoryAnswer.NoneFound(question)
                // The first such set of that workout (log is newest workout first, sets in order).
                val first = sets.first { it.workoutId == newest.workoutId && (question.atLeastKg == null || (it.weightKg ?: 0.0) >= question.atLeastKg - 1e-6) }
                val best = sets.maxOfOrNull { score(it) }
                HistoryAnswer.Lifted(question, first.workout(day(first)), first.answered(), isBest = best != null && score(first) >= best)
            }
            is HistoryQuestion.Volume -> {
                val sets = log.filter { question.period.contains(day(it)) && (question.muscles == null || it.muscleGroup in question.muscles.groups) }
                if (sets.isEmpty()) return HistoryAnswer.NoneFound(question)
                HistoryAnswer.Total(question, sets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }, sets.map { it.workoutId }.distinct().size)
            }
            is HistoryQuestion.Sessions -> {
                val sets = log.filter { set ->
                    question.period.contains(day(set)) &&
                        (question.exercise == null || set.exerciseId == question.exercise.id) &&
                        (question.muscles == null || set.muscleGroup in question.muscles.groups)
                }
                val workouts = sets.groupBy { it.workoutId }.values.map { workoutSets ->
                    val top = workoutSets.maxWith(compareBy { score(it) })
                    top.workout(day(top))
                }
                if (workouts.isEmpty()) HistoryAnswer.NoneFound(question) else HistoryAnswer.Count(question, workouts.size, workouts.take(MAX_LISTED))
            }
            is HistoryQuestion.PrWorkouts -> {
                val prs = log.filter { it.isPr && question.period.contains(day(it)) }
                val workouts = prs.groupBy { it.workoutId }.values.map { workoutPrs ->
                    val top = workoutPrs.maxWith(compareBy { score(it) })
                    top.workout(day(top))
                }
                if (workouts.isEmpty()) HistoryAnswer.NoneFound(question) else HistoryAnswer.Count(question, workouts.size, workouts.take(MAX_LISTED))
            }
        }
    }

    /** How good a set is: estimated 1RM, else reps, else seconds. */
    private fun score(set: LoggedSetRecord): Double =
        PersonalRecords.estimatedOneRepMax(set.weightKg, set.reps) ?: set.reps?.toDouble() ?: set.durationSec?.toDouble() ?: 0.0

    private fun LoggedSetRecord.answered() = AnsweredSet(exerciseName, setNumber, weightKg?.takeIf { it > 0 }, reps, durationSec, isPr)

    private fun LoggedSetRecord.workout(date: LocalDate) = AnsweredWorkout(workoutId, workoutName, date, answered())

    private const val MAX_LISTED = 5
}
