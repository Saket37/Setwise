package dev.saketanand.setwise.ui.home

import dev.saketanand.setwise.domain.model.DayStatus
import java.time.LocalDate
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

    // "Discard <running workout>?" dialog (starting a workout while one is running)

    /** "Discard and start": delete the running workout and start the requested one. */
    data object OnConfirmDiscardAndStart : HomeAction

    /** "Cancel", back, or tap outside: keep the running workout. */
    data object OnDismissDiscardDialog : HomeAction

    // Weekly summary card (artboard 13)

    /** "See overhead press plan". → nav */
    data class OnPlateauExerciseClick(val exerciseId: Long) : HomeAction

    /** Hide this week's summary card. */
    data object OnWeeklySummaryDismiss : HomeAction

    // "Did you train?" sheet (day check-in)

    /** "Rest" / "Missed" for a day; null clears the answer. */
    data class OnCheckInMark(val date: LocalDate, val status: DayStatus?) : HomeAction

    /** "Mark all as rest": every day not answered yet is a rest day; closes the sheet. */
    data object OnCheckInMarkAllRest : HomeAction

    /** "Log workout" for a day: closes the sheet and starts a workout on that day. */
    data class OnCheckInLogWorkout(val date: LocalDate) : HomeAction

    /** "Not now" / "Done", swipe down or back: closes the sheet until tomorrow. */
    data object OnCheckInDismiss : HomeAction
}
