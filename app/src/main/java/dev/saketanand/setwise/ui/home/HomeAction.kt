package dev.saketanand.setwise.ui.home

import java.time.LocalTime

/**
 * Everything the user can do on the Workout tab. HomeScreen calls `onAction(...)`.
 *
 * Two kinds:
 * - Pure navigation (marked "→ nav"): HomeScreenRoot handles these by calling its navigation
 *   callback directly; the ViewModel can ignore them.
 * - Everything else goes to HomeViewModel.onAction().
 */
sealed interface HomeAction {

    // Start workout sheet (artboard 2)

    /** "Start workout" button: opens the sheet. */
    data object OnStartWorkoutClick : HomeAction

    /** Sheet dismissed (swipe down, tap outside, back). */
    data object OnStartSheetDismiss : HomeAction

    /** "Empty workout" in the sheet, or "Start an empty workout" on the first-run screen. */
    data object OnStartEmptyWorkout : HomeAction

    /** A template row in the sheet, or the ▶ button on a template card. */
    data class OnStartFromTemplate(val templateId: Long) : HomeAction

    /** User picked a different start time ("Change"). Null resets to "now". */
    data class OnStartTimeChange(val startTime: LocalTime?) : HomeAction

    // Resume

    /** "Resume workout" card. → nav (to the active workout) */
    data class OnResumeWorkout(val workoutId: Long) : HomeAction

    // Templates

    /** Tap on a template card (not its ▶ button): edit it. → nav */
    data class OnTemplateClick(val templateId: Long) : HomeAction

    /** "New" next to Templates, or "Create a template" on the first-run screen. → nav */
    data object OnCreateTemplateClick : HomeAction

    /** "Build from a goal" on the first-run screen. → nav */
    data object OnCreateTemplateFromGoalClick : HomeAction

    /**
     * "Save Pull Day as a template" in the Plan your routine section (trained, no templates yet).
     * The ViewModel creates the template, then sends [HomeEvent.TemplateCreated].
     */
    data class OnSaveLastWorkoutAsTemplate(val workoutId: Long) : HomeAction

    // Weekly summary card (artboard 13)

    /** "See overhead press plan". → nav */
    data class OnPlateauExerciseClick(val exerciseId: Long) : HomeAction

    /** Hide this week's summary card. */
    data object OnWeeklySummaryDismiss : HomeAction
}
