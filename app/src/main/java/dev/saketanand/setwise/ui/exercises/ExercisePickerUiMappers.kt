package dev.saketanand.setwise.ui.exercises

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.util.toWeightLabel

/** Domain → UI for the picker rows. Plain functions, so they're unit-tested without Android. */

fun Exercise.toRowUi(isSelected: Boolean, lastSet: PreviousSet? = null): ExerciseRowUi = ExerciseRowUi(
    id = id,
    name = name,
    initials = exerciseInitials(name),
    muscleGroup = muscleGroup,
    // The seed uses "None" for e.g. Plank; there's nothing useful to show then.
    equipment = equipment.takeUnless { it.isBlank() || it.equals("None", ignoreCase = true) },
    lastSet = lastSet?.toUi(),
    isSelected = isSelected,
)

fun PreviousSet.toUi(): LastSetUi? =
    if (weightKg == null && reps == null) null else LastSetUi(weight = weightKg?.toWeightLabel(), reps = reps)

/**
 * Two letters for the row tile: first letters of the first two words ("Bench Press (Barbell)"
 * → "BP"), or the first two letters of a one-word name ("Plank" → "PL"). Text in brackets
 * (the variant) is ignored.
 */
fun exerciseInitials(name: String): String {
    val words = name.substringBefore('(')
        .split(' ', '-', '/')
        .map { word -> word.filter { it.isLetterOrDigit() } }
        .filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> ""
    }.uppercase()
}
