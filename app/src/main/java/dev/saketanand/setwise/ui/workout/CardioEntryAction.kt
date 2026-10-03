package dev.saketanand.setwise.ui.workout

/** What the user can do on [CardioEntryScreen]. */
sealed interface CardioEntryAction {
    data class OnInclineChange(val steps: Int) : CardioEntryAction
    data class OnLevelChange(val delta: Int) : CardioEntryAction

    /** A field was edited: clears the error. */
    data object OnInputEdited : CardioEntryAction

    /** "Log cardio" / "Save": with the text fields as typed. */
    data class OnLogClick(val inputs: CardioInputs) : CardioEntryAction

    /** ← or system back. → nav */
    data object OnBackClick : CardioEntryAction
}
