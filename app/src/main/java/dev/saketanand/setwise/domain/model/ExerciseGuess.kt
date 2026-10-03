package dev.saketanand.setwise.domain.model

/**
 * Suggested details for a new exercise, from its name: each null when there's no good guess.
 * [isTimed] goes with [type] (a timed hold is BODYWEIGHT + timed).
 */
data class ExerciseGuess(
    val muscleGroup: String? = null,
    val type: ExerciseType? = null,
    val isTimed: Boolean = false,
    val equipment: String? = null,
) {
    val isEmpty: Boolean get() = muscleGroup == null && type == null && equipment == null
}

/** The fixed choices a new exercise's details come from (the library's own). */
object ExerciseCatalog {
    val MUSCLE_GROUPS = listOf(
        "Chest", "Back", "Shoulders", "Biceps", "Triceps", "Forearms", "Core",
        "Quads", "Hamstrings", "Glutes", "Calves", "Full Body", "Cardio",
    )
    val EQUIPMENT = listOf("Barbell", "Dumbbell", "Machine", "Cable", "Kettlebell", "Smith Machine", "EZ Bar", "Bodyweight", "None")
}

/**
 * The fallback guess when the on-device model isn't there: keywords in the name. Rough on
 * purpose; it fills only what it recognises, and the user can change anything.
 */
object ExerciseKeywords {

    fun guess(name: String): ExerciseGuess {
        val words = ExerciseNames.words(name)
        val text = " ${words.joinToString(" ")} "
        fun has(vararg keys: String) = keys.any { " $it " in text }

        val cardio = has(
            "run", "running", "jog", "walk", "walking", "treadmill", "bike", "cycle", "cycling", "spin",
            "rower", "rowing machine", "elliptical", "stair", "stairs", "jump rope", "skipping", "swim", "swimming", "ski erg", "sprint",
        )
        val timed = !cardio && has("plank", "hold", "wall sit", "hang", "l sit", "hollow")
        val bodyweightMove = has(
            "push up", "pull up", "chin up", "dip", "dips", "burpee", "sit up", "crunch", "pistol", "muscle up",
            "leg raise", "mountain climber", "inverted row", "bodyweight",
        )
        val equipment = when {
            has("smith") -> "Smith Machine"
            has("dumbbell") -> "Dumbbell"
            has("ez bar") -> "EZ Bar"
            has("barbell") -> "Barbell"
            has("kettlebell") -> "Kettlebell"
            has("cable", "rope") -> "Cable"
            has("machine", "leg press", "hack", "pec deck", "lat pull down", "leg extension") -> "Machine"
            timed || bodyweightMove -> "Bodyweight"
            else -> null
        }
        val muscle = when {
            cardio -> "Cardio"
            has("calf", "calves") -> "Calves"
            has("leg curl", "romanian deadlift", "stiff leg deadlift", "good morning", "nordic", "hamstring") -> "Hamstrings"
            has("hip thrust", "glute", "bridge", "kickback", "abduction") -> "Glutes"
            has("squat", "leg press", "lunge", "leg extension", "step up", "wall sit", "pistol", "hack") -> "Quads"
            has("plank", "crunch", "sit up", "ab", "abs", "leg raise", "russian twist", "hollow", "core", "mountain climber") -> "Core"
            has("wrist", "farmer", "forearm", "grip") -> "Forearms"
            has("triceps", "push down", "skull", "dip", "dips", "close grip") -> "Triceps"
            has("curl") -> "Biceps"
            has("lateral raise", "overhead", "shoulder", "face pull", "rear delt", "arnold", "upright row") -> "Shoulders"
            has("bench", "chest", "fly", "pec", "push up") -> "Chest"
            has("row", "pull down", "pull up", "chin up", "deadlift", "lat", "back", "hang") -> "Back"
            has("clean", "snatch", "thruster", "burpee", "carry") -> "Full Body"
            else -> null
        }
        val type = when {
            cardio -> ExerciseType.CARDIO
            timed || bodyweightMove -> ExerciseType.BODYWEIGHT
            equipment != null -> ExerciseType.STRENGTH
            else -> null
        }
        return ExerciseGuess(muscleGroup = muscle, type = type, isTimed = timed, equipment = equipment)
    }
}
