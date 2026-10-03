package dev.saketanand.setwise.ui.home

/**
 * One-off things the ViewModel tells the screen to do, after some work finishes.
 * Unlike state, each event is handled once (e.g. navigate), so they're sent through a Channel.
 */
sealed interface HomeEvent {

    /** The workout row was created; open the active workout screen. */
    data class WorkoutStarted(val workoutId: Long) : HomeEvent

    /** Creating the workout failed; show a snackbar. */
    data object StartWorkoutFailed : HomeEvent

    /** A template was saved from the last workout; open it in the editor to review and rename. */
    data class TemplateCreated(val templateId: Long) : HomeEvent

    /** Saving the last workout as a template failed; show a snackbar. */
    data object SaveTemplateFailed : HomeEvent
}
