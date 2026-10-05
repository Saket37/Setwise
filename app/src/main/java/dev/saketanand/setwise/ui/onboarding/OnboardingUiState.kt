package dev.saketanand.setwise.ui.onboarding

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/** The onboarding steps, in order. Notifications only when the permission still has to be asked. */
enum class OnboardingStep { Welcome, TrainingDays, BodyWeight, CheckIns, Notifications }

/** Everything [OnboardingScreen] draws. */
@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    /** The steps shown, in order (Notifications left out when there's nothing to ask). */
    val steps: List<OnboardingStep> = OnboardingStep.entries,
    val trainingDays: ImmutableSet<DayOfWeek> = persistentSetOf(),
    val askAboutUnloggedDays: Boolean = true,
    /** The weight typed isn't a plausible body weight. */
    val isBodyWeightInvalid: Boolean = false,
    /** Saving the last answers; ignore further taps. */
    val isFinishing: Boolean = false,
) {
    val stepIndex: Int get() = steps.indexOf(step)
    val stepCount: Int get() = steps.size
    val isLastStep: Boolean get() = step == steps.last()
}
