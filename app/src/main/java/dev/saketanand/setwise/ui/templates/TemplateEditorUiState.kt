package dev.saketanand.setwise.ui.templates

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Everything [TemplateEditorScreen] draws. */
@Immutable
data class TemplateEditorUiState(
    val isLoading: Boolean = true,
    /** Creating (vs editing an existing template). */
    val isNew: Boolean = true,
    val draft: TemplateEditorDraft = TemplateEditorDraft(),
    /** Something changed since it was opened (or last saved): leaving asks first. */
    val hasChanges: Boolean = false,
    val isSaving: Boolean = false,
    val dialog: TemplateEditorDialog? = null,
) {
    val canSave: Boolean get() = !isLoading && !isSaving && draft.name.isNotBlank() && draft.exercises.isNotEmpty()

    /** The category chips: the usual ones, plus the template's own if it's something else. */
    val categoryOptions: List<String>
        get() = CATEGORY_OPTIONS + listOfNotNull(draft.category?.takeIf { it !in CATEGORY_OPTIONS })

    companion object {
        val CATEGORY_OPTIONS = listOf("Push", "Pull", "Legs", "Upper", "Lower", "Full body", "Core", "Cardio")
        val SET_RANGE = 1..10
        const val DEFAULT_SETS = 3
    }
}

/**
 * What's being edited. @Serializable: kept in SavedStateHandle, so edits survive the app being
 * killed in the background.
 */
@Immutable
@Serializable
data class TemplateEditorDraft(
    val name: String = "",
    val category: String? = null,
    val exercises: List<TemplateEditorExercise> = emptyList(),
)

/** "Bench Press (Barbell) · Chest   − 3 sets +   ⋮". */
@Immutable
@Serializable
data class TemplateEditorExercise(
    val exerciseId: Long,
    val name: String,
    val muscleGroup: String,
    val targetSets: Int,
    /** Logged as time / distance, not in sets: no set count (always 1 block). */
    val isCardio: Boolean = false,
)

enum class TemplateEditorDialog {
    /** Back with unsaved changes. */
    DiscardChanges,

    /** "Delete template" in the menu. */
    Delete,
}
