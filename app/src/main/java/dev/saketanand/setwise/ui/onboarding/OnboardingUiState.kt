package dev.saketanand.setwise.ui.onboarding

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek

/** The onboarding steps, in order. */
enum class OnboardingStep { Welcome, TrainingDays, BodyWeight, CheckIns }

/** Everything [OnboardingScreen] draws. */
@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val trainingDays: Set<DayOfWeek> = emptySet(),
    val askAboutUnloggedDays: Boolean = true,
    /** The weight typed isn't a plausible body weight. */
    val isBodyWeightInvalid: Boolean = false,
    /** Saving the last answers; ignore further taps. */
    val isFinishing: Boolean = false,
) {
    val stepIndex: Int get() = step.ordinal
    val stepCount: Int get() = OnboardingStep.entries.size
    val isLastStep: Boolean get() = step == OnboardingStep.entries.last()
}
