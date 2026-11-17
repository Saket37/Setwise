package dev.saketanand.setwise.ui.onboarding

import java.time.DayOfWeek

/** What the user can do on [OnboardingScreen]. */
sealed interface OnboardingAction {
    /** "Get started" / "Continue" / "Finish". On the body-weight step it carries the text typed. */
    data class OnContinue(val bodyWeightText: String = "") : OnboardingAction

    /** "Skip": this step only, nothing saved for it. */
    data object OnSkipStep : OnboardingAction

    /** "Skip setup": straight to the app; onboarding won't come back (Settings has it all). */
    data object OnSkipAll : OnboardingAction

    /** System back: the previous step. */
    data object OnBack : OnboardingAction

    data class OnDayToggle(val day: DayOfWeek) : OnboardingAction

    /** The "None" chip: no fixed days (clears the days). */
    data object OnNoFixedDaysToggle : OnboardingAction
    data class OnAskToggle(val ask: Boolean) : OnboardingAction
    data object OnBodyWeightEdited : OnboardingAction

    /**
     * The system's notification prompt was answered (allowed or not), after "Allow notifications"
     * on the last step. Nothing to save: the system keeps the answer.
     */
    data object OnNotificationsAnswered : OnboardingAction
}
