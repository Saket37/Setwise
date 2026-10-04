package dev.saketanand.setwise.ui.templates

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.model.Gear
import dev.saketanand.setwise.domain.model.GoalType

/** Everything "Build from a goal" draws. */
@Immutable
data class TemplateFromGoalUiState(
    /** What was read from the goal: days, minutes, equipment, goal type. Null before anything is typed. */
    val understood: GoalChipsUi? = null,
    val templates: List<PlannedTemplateUi> = emptyList(),
    /** The on-device model is choosing exercises (code's draft is already shown). */
    val isChoosing: Boolean = false,
    /** The shown draft has the model's choices. */
    val byModel: Boolean = false,
    val isSaving: Boolean = false,
) {
    val canSave: Boolean get() = templates.isNotEmpty() && !isSaving
}

@Immutable
data class GoalChipsUi(val type: GoalType, val daysPerWeek: Int, val minutes: Int, val gear: Set<Gear>)

/** "Strength A · 4 exercises · ~45 min", then its exercises. */
@Immutable
data class PlannedTemplateUi(val name: String, val estimatedMinutes: Int, val exercises: List<PlannedExerciseUi>)

/** "Back Squat (Barbell)   4 × 5" (timed: "3 × 45 s"). */
@Immutable
data class PlannedExerciseUi(val name: String, val sets: Int, val reps: Int, val isTimed: Boolean)

sealed interface TemplateFromGoalAction {
    data class OnGoalChange(val text: String) : TemplateFromGoalAction
    data object OnRegenerateClick : TemplateFromGoalAction
    data object OnSaveClick : TemplateFromGoalAction
}

sealed interface TemplateFromGoalEvent {
    /** Saved: open the first template in the editor. */
    data class Saved(val firstTemplateId: Long) : TemplateFromGoalEvent
    data object SaveFailed : TemplateFromGoalEvent
}
