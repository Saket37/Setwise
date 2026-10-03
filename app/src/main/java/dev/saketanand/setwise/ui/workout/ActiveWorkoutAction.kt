package dev.saketanand.setwise.ui.workout

import java.time.LocalTime

/** What the user can do on [ActiveWorkoutScreen]. */
sealed interface ActiveWorkoutAction {

    // Header

    data object OnFinishClick : ActiveWorkoutAction
    data object OnStartTimeClick : ActiveWorkoutAction
    data class OnStartTimeChange(val time: LocalTime) : ActiveWorkoutAction
    data object OnStartTimePickerDismiss : ActiveWorkoutAction

    // Dialogs

    data object OnConfirmFinish : ActiveWorkoutAction
    data object OnDiscardWorkoutClick : ActiveWorkoutAction
    data object OnConfirmDiscard : ActiveWorkoutAction
    data object OnConfirmRemoveExercise : ActiveWorkoutAction
    data object OnDismissDialog : ActiveWorkoutAction

    /** Tap on the workout's name: the rename dialog. */
    data object OnRenameClick : ActiveWorkoutAction
    data class OnRenameConfirm(val name: String) : ActiveWorkoutAction

    // Exercises

    /** Tap on a card's header: expand it, or collapse it if it's the open one. */
    data class OnExerciseHeaderClick(val workoutExerciseId: Long) : ActiveWorkoutAction

    /** Result from the exercise picker. */
    data class OnExercisesPicked(val exerciseIds: List<Long>) : ActiveWorkoutAction

    data class OnRemoveExerciseClick(val workoutExerciseId: Long) : ActiveWorkoutAction

    /** "History & progress" in an exercise's ⋮ menu: Exercise detail. → nav */
    data class OnExerciseHistoryClick(val exerciseId: Long) : ActiveWorkoutAction

    // Sets

    data class OnAddSetClick(val workoutExerciseId: Long) : ActiveWorkoutAction

    /** Typing in a set row; both fields' text, saved as typed. */
    data class OnSetValuesChange(val setId: Long, val weight: String, val reps: String) : ActiveWorkoutAction

    /**
     * ✓ tapped. Carries what's in the fields right now (the last keystroke may not be saved yet);
     * empty fields fall back to the hints.
     */
    data class OnSetDoneToggle(val setId: Long, val weight: String, val reps: String) : ActiveWorkoutAction

    data class OnDeleteSet(val setId: Long) : ActiveWorkoutAction

    // Rest timer

    /** −15 / +15 seconds. */
    data class OnRestAdjust(val deltaSec: Int) : ActiveWorkoutAction
    data object OnRestSkip : ActiveWorkoutAction

    /** The user just allowed notifications (Android 13+ prompt). */
    data object OnNotificationsAllowed : ActiveWorkoutAction

    // Navigation (handled in ActiveWorkoutScreenRoot)

    data object OnMinimizeClick : ActiveWorkoutAction
    data object OnAddExerciseClick : ActiveWorkoutAction
    data class OnLogCardioClick(val workoutExerciseId: Long) : ActiveWorkoutAction

    // Quick log

    /** Send (or the keyboard's Done) on the quick-log bar. */
    data class OnQuickLogSubmit(val text: String) : ActiveWorkoutAction

    /** "Add 3 sets" / "Log cardio" on the "Understood as" card. */
    data object OnQuickLogConfirm : ActiveWorkoutAction

    /** "Edit" on the card: back to the line to change it. */
    data object OnQuickLogEdit : ActiveWorkoutAction

    /** The line changed: a card or "couldn't understand" about another line goes away. */
    data class OnQuickLogEdited(val text: String) : ActiveWorkoutAction
}
