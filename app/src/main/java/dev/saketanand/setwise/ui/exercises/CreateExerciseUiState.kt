package dev.saketanand.setwise.ui.exercises

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType

/** Everything [CreateExerciseScreen] draws. */
@Immutable
data class CreateExerciseUiState(
    val name: String = "",
    val kind: ExerciseKindOption = ExerciseKindOption.WeightReps,
    /** Required (except cardio); null until picked. */
    val muscleGroup: String? = null,
    val equipment: String = ExerciseKindOption.WeightReps.defaultEquipment,
    val restSec: Int = ExerciseKindOption.WeightReps.defaultRestSec,
    /** Library muscle groups for the chips (cardio has its own type). */
    val muscleGroups: List<String> = emptyList(),
    /** A library exercise that looks like what's being typed: "Already in your library?". */
    val match: Exercise? = null,
    /** [match] has exactly this name: creating it again isn't allowed. */
    val isNameTaken: Boolean = false,
    val isSaving: Boolean = false,
) {
    val canCreate: Boolean
        get() = name.isNotBlank() && !isNameTaken && !isSaving &&
            (kind == ExerciseKindOption.Cardio || muscleGroup != null)

    companion object {
        val EQUIPMENT_OPTIONS = listOf("Barbell", "Dumbbell", "Machine", "Cable", "Kettlebell", "Smith Machine", "EZ Bar", "Bodyweight", "None")
        const val REST_STEP_SEC = 15
        val REST_RANGE_SEC = 15..300
    }
}

/** How the exercise is logged; picks the type, and sensible equipment and rest. */
enum class ExerciseKindOption(
    val type: ExerciseType,
    val isTimed: Boolean,
    val defaultEquipment: String,
    val defaultRestSec: Int,
) {
    /** kg × reps. */
    WeightReps(ExerciseType.STRENGTH, false, "Barbell", 120),

    /** Reps (optional added weight). */
    Bodyweight(ExerciseType.BODYWEIGHT, false, "Bodyweight", 90),

    /** Seconds (plank). */
    Timed(ExerciseType.BODYWEIGHT, true, "Bodyweight", 60),

    /** Time and distance. */
    Cardio(ExerciseType.CARDIO, false, "Machine", 0),
}
