package dev.saketanand.setwise.ui.exercises

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Everything [ExercisePickerScreen] draws. */
@Immutable
data class ExercisePickerUiState(
    /** True until the exercise list has loaded once (nothing is drawn, so no empty state flashes). */
    val isLoading: Boolean = true,
    /** Search text as typed (the field itself is a TextFieldState in the screen). */
    val query: String = "",
    /** Filter chips after "All", biggest group first. */
    val muscleGroups: ImmutableList<String> = persistentListOf(),
    /** Selected chip; null = "All". */
    val selectedMuscleGroup: String? = null,
    /** Recently done exercises, with last session's top set. */
    val recent: List<ExerciseRowUi> = emptyList(),
    /** The library, filtered by [listFilter]. */
    val exercises: List<ExerciseRowUi> = emptyList(),
    /**
     * The search + chip that [exercises] was loaded for. The search is debounced, so this can lag
     * [query] by a moment; the screen scrolls to the top when it changes.
     */
    val listFilter: String = "",
    /** Picked exercises in the order they were tapped (that's the order they're added in). */
    val selectedIds: List<Long> = emptyList(),
) {
    val selectedCount: Int get() = selectedIds.size

    /** "Recent" only on the unfiltered list; while searching or filtering it would repeat results. */
    val showRecent: Boolean get() = recent.isNotEmpty() && query.isBlank() && selectedMuscleGroup == null

    val showNoResults: Boolean get() = !isLoading && exercises.isEmpty()
}

/** One exercise row: "BP · Bench Press (Barbell) · Chest · last 60 kg × 8 · ✓". */
@Immutable
data class ExerciseRowUi(
    val id: Long,
    val name: String,
    /** Shown in the tile, e.g. "BP". */
    val initials: String,
    val muscleGroup: String,
    /** Null when the exercise needs none. */
    val equipment: String?,
    /** Last session's top set; only on recent exercises. */
    val lastSet: LastSetUi?,
    val isSelected: Boolean,
)

/** "last 62.5 kg × 8"; [weight] is already formatted. */
@Immutable
data class LastSetUi(
    val weight: String?,
    val reps: Int?,
)
