package dev.saketanand.setwise.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.util.parseWeight
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Screen: [SettingsScreenRoot]. Shows and edits what onboarding asked (all optional). */
class SettingsViewModel(
    private val userSettings: UserSettingsRepository,
) : ViewModel() {

    private val editor = MutableStateFlow<SettingsEditor?>(null)

    val state: StateFlow<SettingsUiState> = combine(userSettings.settings, editor) { settings, editor ->
        SettingsUiState(
            isLoading = false,
            bodyWeightKg = settings.bodyWeightKg,
            trainingDays = settings.trainingDays,
            askAboutUnloggedDays = settings.askAboutUnloggedDays,
            editor = editor,
        )
    }
        .catch { e ->
            Log.e(TAG, "Loading settings failed", e)
            emit(SettingsUiState(isLoading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.OnBodyWeightClick -> editor.value = SettingsEditor.BodyWeight()
            is SettingsAction.OnSaveBodyWeight -> saveBodyWeight(action.text)
            SettingsAction.OnRemoveBodyWeight -> {
                editor.value = null
                save { userSettings.setBodyWeightKg(null) }
            }

            SettingsAction.OnTrainingDaysClick -> editor.value = SettingsEditor.TrainingDays(state.value.trainingDays)
            is SettingsAction.OnDayToggle -> editor.update { current ->
                (current as? SettingsEditor.TrainingDays)?.let {
                    it.copy(selected = if (action.day in it.selected) it.selected - action.day else it.selected + action.day)
                } ?: current
            }
            SettingsAction.OnSaveTrainingDays -> {
                val days = (editor.value as? SettingsEditor.TrainingDays)?.selected ?: return
                editor.value = null
                save { userSettings.setTrainingDays(days) }
            }

            is SettingsAction.OnAskAboutUnloggedDaysChange -> save { userSettings.setAskAboutUnloggedDays(action.ask) }

            SettingsAction.OnDismissEditor -> editor.value = null
        }
    }

    private fun saveBodyWeight(text: String) {
        val kg = parseWeight(text)
        if (kg == null) {
            editor.value = SettingsEditor.BodyWeight(isInvalid = true)
            return
        }
        viewModelScope.launch {
            val saved = runCatching { userSettings.setBodyWeightKg(kg) }.getOrDefault(false)
            editor.value = if (saved) null else SettingsEditor.BodyWeight(isInvalid = true)
        }
    }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { e -> Log.e(TAG, "Saving a setting failed", e) }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
