package dev.saketanand.setwise.domain.model

import java.util.Locale

/**
 * Exercise names as words, for matching what someone types against the library: lower case,
 * no punctuation, gym shorthand spelled out ("db" → dumbbell, "ohp" → overhead press), a few
 * synonyms folded together ("flat" → bench).
 */
object ExerciseNames {

    fun words(name: String): List<String> =
        name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(" ")
            .filter { it.isNotEmpty() }
            .flatMap { word -> SHORTHAND[word]?.split(" ") ?: listOf(word) }
            .map { SYNONYMS[it] ?: it }
            .filter { it !in FILLER }

    /** Same words in any order: "Bench Press (Dumbbell)" and "dumbbell bench press". */
    fun sameName(a: String, b: String): Boolean = words(a).sorted() == words(b).sorted()

    /**
     * Library exercises that look like [typed], best first, with a 0–1 score (shared words over
     * all words). Exercises sharing no word are left out.
     */
    fun candidates(typed: String, library: List<Exercise>, max: Int = 5): List<Pair<Exercise, Double>> {
        val typedWords = words(typed).toSet()
        if (typedWords.isEmpty()) return emptyList()
        return library
            .map { exercise ->
                val words = words(exercise.name).toSet()
                val shared = (typedWords intersect words).size
                exercise to if (shared == 0) 0.0 else shared.toDouble() / (typedWords union words).size
            }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .take(max)
    }

    /**
     * The one exercise whose name is what's typed plus its equipment ("deadlift" → Deadlift
     * (Barbell), not Romanian Deadlift); null if none or several are, or equipment was typed.
     */
    fun onlyOneBesidesEquipment(typed: String, library: List<Exercise>): Exercise? {
        val typedWords = words(typed).sorted()
        if (typedWords.isEmpty() || typedWords.any { it in EQUIPMENT_WORDS }) return null
        return library.filter { exercise -> words(exercise.name).filter { it !in EQUIPMENT_WORDS }.sorted() == typedWords }.singleOrNull()
    }

    /**
     * The one exercise whose name has every typed word ("barbell row" → Bent-over Row
     * (Barbell)); null if none or several do ("bench": both bench presses).
     */
    fun onlyOneCovering(typed: String, library: List<Exercise>): Exercise? {
        val typedWords = words(typed).toSet()
        if (typedWords.isEmpty()) return null
        return library.filter { words(it.name).containsAll(typedWords) }.singleOrNull()
    }

    /** Without the model, a candidate this close is offered as "Already in your library?". */
    const val CLOSE_MATCH = 0.6

    private val SHORTHAND = mapOf(
        "db" to "dumbbell", "dbs" to "dumbbell", "dumbbells" to "dumbbell",
        "bb" to "barbell", "kb" to "kettlebell", "kbs" to "kettlebell",
        "ohp" to "overhead press", "rdl" to "romanian deadlift", "sldl" to "stiff leg deadlift",
        "dl" to "deadlift", "bp" to "bench press", "ez" to "ez bar",
        "pushup" to "push up", "pullup" to "pull up", "chinup" to "chin up", "situp" to "sit up",
        "pushups" to "push up", "pullups" to "pull up", "chinups" to "chin up", "situps" to "sit up",
        "pulldown" to "pull down", "pushdown" to "push down",
    )

    private val SYNONYMS = mapOf(
        "flat" to "bench",
        "military" to "overhead",
        "presses" to "press", "curls" to "curl", "raises" to "raise", "rows" to "row",
        "squats" to "squat", "lunges" to "lunge", "flyes" to "fly", "flys" to "fly",
        "extensions" to "extension", "deadlifts" to "deadlift", "ups" to "up",
    )

    private val FILLER = setOf("the", "a", "with", "on", "and")

    private val EQUIPMENT_WORDS = setOf("barbell", "dumbbell", "machine", "cable", "kettlebell", "smith", "ez", "bar", "bodyweight")
}
