package dev.saketanand.setwise.ui.onboarding

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.model.UserSettings
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.timer.NotificationPermission
import dev.saketanand.setwise.ui.toggled
import dev.saketanand.setwise.util.parseWeight
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen: [OnboardingScreenRoot]. Each answer is saved when its step is continued (Skip saves
 * nothing for that step), so leaving halfway keeps what was answered. The current step survives
 * the app being killed in the background.
 */
class OnboardingViewModel(
    private val userSettings: UserSettingsRepository,
    notificationPermission: NotificationPermission,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val steps = OnboardingStep.entries.filter { step ->
        step != OnboardingStep.Notifications || notificationPermission.needsAsking()
    }

    private val _state = MutableStateFlow(
        OnboardingUiState(
            // Saved by name: the steps shown can differ after the app is killed (permission granted meanwhile).
            step = savedStateHandle.get<String>(KEY_STEP)
                ?.let { name -> steps.firstOrNull { it.name == name } }
                ?: steps.first(),
            steps = steps,
        )
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** Onboarding is over (finished or skipped): leave it. */
    private val finished = Channel<Unit>(Channel.BUFFERED)
    val onFinished: Flow<Unit> = finished.receiveAsFlow()

    fun onAction(action: OnboardingAction) {
        when (action) {
            is OnboardingAction.OnContinue -> continueFrom(_state.value.step, action.bodyWeightText)
            OnboardingAction.OnSkipStep -> next()
            OnboardingAction.OnSkipAll -> finish()
            OnboardingAction.OnBack -> goTo(_state.value.stepIndex - 1)
            is OnboardingAction.OnDayToggle -> _state.update {
                it.copy(trainingDays = it.trainingDays.toggled(action.day), noFixedDays = false)
            }
            OnboardingAction.OnNoFixedDaysToggle -> _state.update {
                it.copy(noFixedDays = !it.noFixedDays, trainingDays = persistentSetOf())
            }
            is OnboardingAction.OnAskToggle -> _state.update { it.copy(askAboutUnloggedDays = action.ask) }
            OnboardingAction.OnBodyWeightEdited -> _state.update { it.copy(isBodyWeightInvalid = false) }
            OnboardingAction.OnNotificationsAnswered -> next()
        }
    }

    private fun continueFrom(step: OnboardingStep, bodyWeightText: String) {
        val state = _state.value
        when (step) {
            OnboardingStep.Welcome -> next()
            OnboardingStep.TrainingDays -> {
                if (state.trainingDays.isNotEmpty() || state.noFixedDays) {
                    save { userSettings.setTrainingDays(state.trainingDays, noFixedDays = state.noFixedDays) }
                }
                next()
            }
            OnboardingStep.BodyWeight -> {
                val kg = parseWeight(bodyWeightText)
                if (bodyWeightText.isBlank()) return next() // nothing typed = skip
                if (kg == null || kg !in UserSettings.BODY_WEIGHT_RANGE_KG) {
                    _state.update { it.copy(isBodyWeightInvalid = true) }
                    return
                }
                save { userSettings.setBodyWeightKg(kg) }
                next()
            }
            OnboardingStep.CheckIns -> {
                save { userSettings.setAskAboutUnloggedDays(state.askAboutUnloggedDays) }
                next()
            }
            // OnboardingScreenRoot shows the system prompt instead; its answer → OnNotificationsAnswered.
            OnboardingStep.Notifications -> next()
        }
    }

    private fun next() {
        if (_state.value.isLastStep) finish() else goTo(_state.value.stepIndex + 1)
    }

    private fun goTo(index: Int) {
        val step = steps.getOrNull(index) ?: return
        savedStateHandle[KEY_STEP] = step.name
        _state.update { it.copy(step = step, isBodyWeightInvalid = false) }
    }

    private fun finish() {
        if (_state.value.isFinishing) return
        _state.update { it.copy(isFinishing = true) }
        viewModelScope.launch {
            runCatching { userSettings.setOnboardingDone() }
                .onFailure { e -> Log.e(TAG, "Saving onboarding done failed", e) }
            // Leave even if saving failed: onboarding just shows again next launch.
            finished.send(Unit)
        }
    }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { e -> Log.e(TAG, "Saving an onboarding answer failed", e) }
        }
    }

    private companion object {
        const val TAG = "OnboardingViewModel"
        const val KEY_STEP = "step"
    }
}
