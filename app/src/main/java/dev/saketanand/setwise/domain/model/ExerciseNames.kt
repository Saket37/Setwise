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
     * The one exercise with the same name apart from equipment, whose equipment (if any) fits
     * what's typed: "deadlift" → Deadlift (Barbell), "Preacher Curl (Barbell)" → Preacher Curl,
     * "Bicep Curl (Barbell)" → Barbell Curl (not Cable Curl). Null if none, or several and none
     * with exactly the typed equipment ("bench press": barbell or dumbbell?).
     */
    fun onlyOneBesidesEquipment(typed: String, library: List<Exercise>): Exercise? {
        val typedWords = words(typed)
        val core = typedWords.filter { it !in EQUIPMENT_WORDS }.sorted()
        val gear = typedWords.filter { it in EQUIPMENT_WORDS }.toSet()
        if (core.isEmpty()) return null
        val same = library.filter { exercise ->
            val words = words(exercise.name)
            val nameGear = words.filter { it in EQUIPMENT_WORDS }.toSet()
            // Its equipment is (part of) what's typed: "(Bar)" for "Cable - Straight Bar".
            words.filter { it !in EQUIPMENT_WORDS }.sorted() == core && (gear.isEmpty() || gear.containsAll(nameGear))
        }
        if (same.size <= 1) return same.singleOrNull()
        return same.filter { exercise -> words(exercise.name).filter { it in EQUIPMENT_WORDS }.toSet() == gear }.singleOrNull()
    }

    /**
     * A name that doesn't say its variant means the usual one: "Lat Pulldown (Cable)" → Lat
     * Pulldown (Wide Grip), "seated row" → Seated Cable Row (V-Bar). Unless the typed equipment
     * rules it out ("Preacher Curl (Dumbbell)" isn't the barbell one).
     */
    fun usualVariant(typed: String, library: List<Exercise>): Exercise? {
        val typedWords = words(typed)
        val core = typedWords.filter { it !in EQUIPMENT_WORDS }.sorted()
        val gear = typedWords.filter { it in EQUIPMENT_WORDS }.toSet()
        // All the words first ("cable curl"), then without equipment ("Lat Pulldown (Cable)").
        val usual = USUAL_VARIANTS[typedWords.sorted()] ?: USUAL_VARIANTS[core] ?: return null
        val exercise = library.firstOrNull { it.name == usual } ?: return null
        val usualGear = words(exercise.name).filter { it in EQUIPMENT_WORDS }.toSet()
        return exercise.takeIf { gear.isEmpty() || usualGear.isEmpty() || usualGear.any { it in gear } }
    }

    /** Every typed word that isn't equipment is in [name]: nothing that tells them apart is dropped ("preacher"). */
    fun coversCore(typed: String, name: String): Boolean {
        val nameWords = words(name).toSet()
        return words(typed).filter { it !in EQUIPMENT_WORDS }.all { it in nameWords }
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
        "skullcrusher" to "skull crusher", "skullcrushers" to "skull crusher",
        "pulldown" to "pull down", "pushdown" to "push down",
    )

    private val SYNONYMS = mapOf(
        "flat" to "bench",
        "military" to "overhead",
        "presses" to "press", "curls" to "curl", "raises" to "raise", "rows" to "row",
        "squats" to "squat", "lunges" to "lunge", "flyes" to "fly", "flys" to "fly",
        "extensions" to "extension", "deadlifts" to "deadlift", "ups" to "up",
    )

    /** The library's usual variant of a name said without one (the names it had before it named variants). */
    private val USUAL_VARIANTS: Map<List<String>, String> by lazy { usualVariants() }

    private fun usualVariants(): Map<List<String>, String> = listOf(
        "Lat Pulldown" to "Lat Pulldown (Wide Grip)",
        "Seated Row" to "Seated Cable Row (V-Bar)",
        "Seated Cable Row" to "Seated Cable Row (V-Bar)",
        "Cable Row" to "Seated Cable Row (V-Bar)",
        "Straight-arm Pulldown" to "Straight-arm Pulldown (Bar)",
        "Triceps Pushdown" to "Triceps Pushdown (Cable - Straight Bar)",
        "Overhead Triceps Extension" to "Overhead Triceps Extension (Dumbbell)",
        "Triceps Kickback" to "Triceps Kickback (Dumbbell)",
        "Skull Crusher" to "Skull Crusher (EZ Bar)",
        "Bicep Curl" to "Bicep Curl (Barbell)",
        "Preacher Curl" to "Preacher Curl (Barbell)",
        "Hammer Curl" to "Hammer Curl (Dumbbell)",
        "Concentration Curl" to "Concentration Curl (Dumbbell)",
        "Incline Curl" to "Incline Curl (Dumbbell)",
        "Face Pull" to "Face Pull (Rope)",
        "Upright Row" to "Upright Row (Barbell)",
        "Arnold Press" to "Arnold Press (Dumbbell)",
        "Lateral Raise" to "Lateral Raise (Dumbbell)",
        "Front Raise" to "Front Raise (Dumbbell)",
        "Rear Delt Fly" to "Rear Delt Fly (Dumbbell)",
        "Chest Fly" to "Chest Fly (Dumbbell)",
        "Incline Chest Fly" to "Incline Chest Fly (Dumbbell)",
        "Single-arm Row" to "Single-arm Row (Dumbbell)",
        "Wrist Curl" to "Wrist Curl (Dumbbell)",
        "Goblet Squat" to "Goblet Squat (Dumbbell)",
        "Bulgarian Split Squat" to "Bulgarian Split Squat (Dumbbell)",
        "Reverse Lunge" to "Reverse Lunge (Dumbbell)",
        "Walking Lunge" to "Walking Lunge (Dumbbell)",
        "Step-up" to "Step-up (Dumbbell)",
        "Stiff-leg Deadlift" to "Stiff-leg Deadlift (Barbell)",
        "Good Morning" to "Good Morning (Barbell)",
        "Sumo Deadlift" to "Sumo Deadlift (Barbell)",
        "Seated Calf Raise" to "Seated Calf Raise (Machine)",
        "Glute Kickback" to "Glute Kickback (Cable)",
        // Names the library had before, other words for the same exercise.
        "Cable Curl" to "Bicep Curl (Cable - Straight Bar)",
        "Cable Crossover" to "Chest Fly (Cable)",
        "Pec Deck" to "Chest Fly (Machine)",
        "Reverse Pec Deck" to "Rear Delt Fly (Machine)",
        "One-arm Dumbbell Row" to "Single-arm Row (Dumbbell)",
        "Seated Dumbbell Shoulder Press" to "Overhead Press (Dumbbell)",
        "Dumbbell Shoulder Press" to "Overhead Press (Dumbbell)",
    ).associate { (said, usual) -> words(said).sorted() to usual }

    /** Words that don't tell exercises apart ("Bicep Curl" is a curl). */
    private val FILLER = setOf("the", "a", "with", "on", "and", "bicep", "biceps")

    private val EQUIPMENT_WORDS = setOf(
        "barbell", "dumbbell", "machine", "cable", "kettlebell", "smith", "ez", "bar", "bodyweight",
        // Attachments and the like (Strong: "Triceps Pushdown (Cable - Straight Bar)").
        "straight", "rope", "handle", "attachment", "plate", "loaded",
    )
}
