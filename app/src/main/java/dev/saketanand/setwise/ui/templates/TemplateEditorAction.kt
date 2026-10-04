package dev.saketanand.setwise.ui.templates

/** What the user can do on [TemplateEditorScreen]. */
sealed interface TemplateEditorAction {
    data class OnNameChange(val name: String) : TemplateEditorAction

    /** A category chip: select it, or clear it if it's selected. */
    data class OnCategoryClick(val category: String) : TemplateEditorAction

    /** − / + on an exercise's set count. */
    data class OnSetsChange(val exerciseId: Long, val delta: Int) : TemplateEditorAction
    data class OnRepsChange(val exerciseId: Long, val delta: Int) : TemplateEditorAction

    data class OnMoveUp(val exerciseId: Long) : TemplateEditorAction
    data class OnMoveDown(val exerciseId: Long) : TemplateEditorAction
    data class OnRemoveExercise(val exerciseId: Long) : TemplateEditorAction

    /** "Add exercises". → nav (exercise picker) */
    data object OnAddExercisesClick : TemplateEditorAction

    /** The picker's result: appended, skipping ones already in the template. */
    data class OnExercisesPicked(val exerciseIds: List<Long>) : TemplateEditorAction

    data object OnSaveClick : TemplateEditorAction

    /** ← or system back: closes, or asks first when there are unsaved changes. */
    data object OnBackClick : TemplateEditorAction
    data object OnConfirmDiscard : TemplateEditorAction

    data object OnDeleteClick : TemplateEditorAction
    data object OnConfirmDelete : TemplateEditorAction

    data object OnDialogDismiss : TemplateEditorAction
}
