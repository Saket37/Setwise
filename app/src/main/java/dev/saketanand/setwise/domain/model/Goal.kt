package dev.saketanand.setwise.domain.model

import java.util.Locale

/** What a training plan is for: decides sets × reps (and, for fat loss, a cardio finisher). */
enum class GoalType { Strength, Muscle, FatLoss, General }

/** Kinds of equipment a plan may use; each library exercise belongs to one ([Gear.of]). */
enum class Gear {
    Barbell,
    Dumbbell,
    Kettlebell,
    Cable,
    Machine,
    Bodyweight,

    /** A pull-up bar, dip bars or rings: the library lists what needs them as bodyweight (#143). */
    Bars,
    ;

    companion object {
        /** What [exercise] needs: its equipment, or [Bars] for a bodyweight move done hanging or on bars. */
        fun of(exercise: Exercise): Gear {
            val name = exercise.name.lowercase(Locale.ROOT)
            val onBars = NEEDS_BARS.any { it in name } && "bench dip" !in name
            return if (onBars && of(exercise.equipment) == Bodyweight) Bars else of(exercise.equipment)
        }

        private val NEEDS_BARS = listOf("pull-up", "chin-up", "hanging", " dip", "inverted row", "muscle-up")

        /** A library exercise's equipment ("EZ Bar", "Smith Machine", "None"…) as a [Gear]. */
        fun of(equipment: String): Gear = when (equipment.lowercase(Locale.ROOT)) {
            "barbell", "ez bar", "trap bar" -> Barbell
            "dumbbell" -> Dumbbell
            "kettlebell" -> Kettlebell
            "cable" -> Cable
            "machine", "smith machine", "plate-loaded", "sled" -> Machine
            else -> Bodyweight // bodyweight, none, ab wheel, jump rope
        }
    }
}

/**
 * A goal as typed ("Get stronger at squat and bench, 45 minutes, I only have dumbbells and a
 * barbell"), read into what a plan needs. [focus] holds lift words the person named ("squat",
 * "bench"), which the plan puts first.
 */
data class Goal(
    val type: GoalType = GoalType.Muscle,
    val daysPerWeek: Int = DEFAULT_DAYS,
    val minutes: Int = DEFAULT_MINUTES,
    val gear: Set<Gear> = Gear.entries.toSet(),
    val focus: List<String> = emptyList(),
) {
    companion object {
        const val DEFAULT_DAYS = 3
        const val DEFAULT_MINUTES = 45
        val DAYS = 1..7
        val MINUTES = 20..120
    }
}

/**
 * Reads a typed goal in code: days a week, minutes, equipment, goal type and named lifts. What
 * it doesn't find keeps a default ([Goal]); [defaultDays] is the profile's training days, when
 * set. Plain functions, unit-tested.
 */
object GoalReader {

    fun read(text: String, defaultDays: Int? = null): Goal {
        val lower = " " + text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9.]+"), " ") + " "
        return Goal(
            type = type(lower),
            daysPerWeek = days(lower) ?: defaultDays?.takeIf { it in Goal.DAYS } ?: Goal.DEFAULT_DAYS,
            minutes = minutes(lower) ?: Goal.DEFAULT_MINUTES,
            gear = gear(lower),
            focus = FOCUS_WORDS.filter { (words, _) -> words.any { " $it " in lower } }.map { it.second },
        )
    }

    /** Fat loss first: "lose 12 kg and get stronger" is mostly about the 12 kg. */
    private fun type(text: String): GoalType = when {
        GoalTargetReader.LOSE.any { " $it" in text } -> GoalType.FatLoss
        STRENGTH.any { " $it" in text } -> GoalType.Strength
        GENERAL.any { " $it" in text } -> GoalType.General
        else -> GoalType.Muscle
    }

    /** "3 days a week", "4x a week", "three times per week", "5 days". */
    private fun days(text: String): Int? {
        val number = "(\\d|${NUMBER_WORDS.keys.joinToString("|")})"
        val match = Regex(" $number (?:days?|x|times?)(?: a| per| each)?(?: week)? ").find(text) ?: return null
        val value = match.groupValues[1].let { NUMBER_WORDS[it] ?: it.toInt() }
        return value.takeIf { it in Goal.DAYS }
    }

    /** "45 minutes", "60 min", "an hour", "1.5 hours", "half an hour". */
    private fun minutes(text: String): Int? {
        Regex(" (\\d{2,3}) ?(?:min|mins|minutes|m) ").find(text)?.let { return it.groupValues[1].toInt().coerceIn(Goal.MINUTES) }
        if (" half an hour " in text) return HALF_HOUR
        Regex(" (\\d(?:\\.\\d)?) ?(?:h|hr|hrs|hour|hours) ").find(text)?.let {
            return (it.groupValues[1].toDouble() * MINUTES_PER_HOUR).toInt().coerceIn(Goal.MINUTES)
        }
        if (" an hour " in text || " one hour " in text) return MINUTES_PER_HOUR
        return null
    }

    /** Named equipment (plus bodyweight); "home" / "no equipment" alone: bodyweight; nothing named: a full gym. */
    private fun gear(text: String): Set<Gear> {
        // "a pull-up bar" is bars, not a barbell: those words are read first, then taken out.
        val bars = BAR_WORDS.any { " $it " in text }
        val rest = BAR_WORDS.fold(text) { t, words -> t.replace(" $words ", " ") }
        val named = GEAR_WORDS.filter { (words, _) -> words.any { " $it " in rest } }.map { it.second }.toSet() +
            listOfNotNull(Gear.Bars.takeIf { bars })
        return when {
            named.isNotEmpty() -> named + Gear.Bodyweight
            BODYWEIGHT_ONLY.any { " $it " in text } -> setOf(Gear.Bodyweight)
            else -> Gear.entries.toSet()
        }
    }

    private const val HALF_HOUR = 30
    private const val MINUTES_PER_HOUR = 60

    private val STRENGTH = listOf("strong", "strength", "powerlift", "heavier", "1rm", "max ")
    private val GENERAL = listOf("fit", "fitness", "lean", "endurance", "conditioning", "health", "cardio")
    private val NUMBER_WORDS = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7)
    private val GEAR_WORDS = listOf(
        listOf("barbell", "barbells", "bar") to Gear.Barbell,
        listOf("dumbbell", "dumbbells", "db", "dbs") to Gear.Dumbbell,
        listOf("kettlebell", "kettlebells", "kb", "kbs") to Gear.Kettlebell,
        listOf("cable", "cables") to Gear.Cable,
        listOf("machine", "machines") to Gear.Machine,
    )

    /** Bars to hang from or dip on ([Gear.Bars]). */
    private val BAR_WORDS = listOf("pull up bar", "pullup bar", "chin up bar", "chinup bar", "dip bars", "dip station", "rings")
    private val BODYWEIGHT_ONLY = listOf("home", "bodyweight", "no equipment", "calisthenics")

    /** Lift words people name, and the word library names use for them. */
    private val FOCUS_WORDS = listOf(
        listOf("squat", "squats") to "squat",
        listOf("bench", "benching") to "bench press",
        listOf("deadlift", "deadlifts") to "deadlift",
        listOf("ohp", "overhead", "military") to "overhead press",
        listOf("pullup", "pullups", "pull ups", "chin ups", "chinup", "chinups") to "pull-up",
        listOf("row", "rows") to "row",
        listOf("hip thrust", "hip thrusts", "glutes") to "hip thrust",
        listOf("arms", "biceps", "curls") to "curl",
    )
}
