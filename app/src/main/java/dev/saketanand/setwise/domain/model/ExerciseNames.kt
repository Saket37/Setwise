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

    /**
     * [typed] with each word that's in no exercise name swapped for the one word that is and is a
     * letter away (two for words over 5 letters): what speech-to-text writes for gym words
     * ("squad" → squat, "flank" → plank, "full ops" → pull ups). Words with no single close
     * match stay as they are.
     */
    fun soundAlikeFixed(typed: String, library: List<Exercise>): String {
        val vocabulary = buildSet {
            library.forEach { addAll(rawWords(it.name)) }
            addAll(SHORTHAND.keys)
            SHORTHAND.values.forEach { addAll(it.split(" ")) }
            addAll(SYNONYMS.keys)
            addAll(SYNONYMS.values)
        }
        return rawWords(typed).joinToString(" ") { word ->
            if (word in vocabulary || word.length < 3 || word.any { it.isDigit() }) return@joinToString word
            val allowed = if (word.length > 5) 2 else 1
            val closest = vocabulary.map { it to editDistance(word, it) }.filter { (_, d) -> d <= allowed }
            val best = closest.minOfOrNull { it.second } ?: return@joinToString word
            val tied = closest.filter { it.second == best }.map { it.first }
            // A tie goes to the word it starts with: the same word with an ending ("pulled" → pull).
            tied.singleOrNull() ?: tied.filter { word.startsWith(it) }.singleOrNull() ?: word
        }
    }

    private fun rawWords(text: String) =
        text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim().split(" ").filter { it.isNotEmpty() }

    /** Edits (insert, delete, change, swap two neighbours) from [a] to [b]. */
    private fun editDistance(a: String, b: String): Int {
        val d = Array(a.length + 1) { i -> IntArray(b.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val change = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + change)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
        return d[a.length][b.length]
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
