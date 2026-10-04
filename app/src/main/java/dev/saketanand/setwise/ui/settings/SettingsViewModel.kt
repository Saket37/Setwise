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
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.ai.OnDeviceModel
import dev.saketanand.setwise.domain.ai.DownloadState
import dev.saketanand.setwise.domain.ai.ModelDownloader
import dev.saketanand.setwise.domain.model.WeeklySummaryRules
import dev.saketanand.setwise.util.DateProvider
import dev.saketanand.setwise.domain.model.BodyRules
import dev.saketanand.setwise.domain.repository.BodyRepository

/** Screen: [SettingsScreenRoot]. Shows and edits what onboarding asked (all optional). */
class SettingsViewModel(
    private val userSettings: UserSettingsRepository,
    private val model: OnDeviceModel,
    private val downloader: ModelDownloader,
    private val dateProvider: DateProvider,
    bodyRepository: BodyRepository,
) : ViewModel() {

    private val editor = MutableStateFlow<SettingsEditor?>(null)
    private val availability = MutableStateFlow<ModelAvailability?>(null)
    private val ai = combine(availability, downloader.state) { availability, download -> AiStatusUi(availability, download) }

    val state: StateFlow<SettingsUiState> = combine(userSettings.settings, editor, ai, bodyRepository.observeMeasurements()) { settings, editor, ai, body ->
        val today = dateProvider.now().atZone(dateProvider.zone).toLocalDate()
        SettingsUiState(
            isLoading = false,
            name = settings.name,
            age = settings.ageOn(today),
            sex = settings.sex,
            heightCm = settings.heightCm,
            bmr = BodyRules.bmr(body, settings, today),
            bodyFatPercent = body.firstOrNull { it.bodyFatPercent != null }?.bodyFatPercent,
            bodyWeightKg = settings.bodyWeightKg,
            trainingDays = settings.trainingDays,
            askAboutUnloggedDays = settings.askAboutUnloggedDays,
            isWeeklySummaryClosed = settings.weeklySummaryDismissedWeek != null &&
                settings.weeklySummaryDismissedWeek == WeeklySummaryRules.lastWeekStart(dateProvider.now().atZone(dateProvider.zone).toLocalDate()),
            editor = editor,
            ai = ai,
        )
    }
        .catch { e ->
            Log.e(TAG, "Loading settings failed", e)
            emit(SettingsUiState(isLoading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.OnNameClick -> editor.value = SettingsEditor.Name
            is SettingsAction.OnSaveName -> {
                editor.value = null
                save { userSettings.setName(action.text) }
            }
            SettingsAction.OnAgeClick -> editor.value = SettingsEditor.Age()
            is SettingsAction.OnSaveAge -> saveChecked(SettingsEditor.Age(isInvalid = true)) {
                action.text.trim().toIntOrNull()?.let { userSettings.setAge(it, today()) } ?: false
            }
            SettingsAction.OnSexClick -> editor.value = SettingsEditor.SexChoice
            is SettingsAction.OnSaveSex -> {
                editor.value = null
                save { userSettings.setSex(action.sex) }
            }
            SettingsAction.OnHeightClick -> editor.value = SettingsEditor.Height()
            is SettingsAction.OnSaveHeight -> saveChecked(SettingsEditor.Height(isInvalid = true)) {
                parseWeight(action.text)?.let { userSettings.setHeightCm(it) } ?: false
            }
            is SettingsAction.OnRemoveProfileValue -> {
                editor.value = null
                save {
                    when (action.editor) {
                        SettingsEditor.Name -> userSettings.setName(null)
                        is SettingsEditor.Age -> userSettings.setAge(null, today())
                        is SettingsEditor.Height -> userSettings.setHeightCm(null)
                        SettingsEditor.SexChoice -> userSettings.setSex(null)
                        else -> Unit
                    }
                }
            }
            SettingsAction.OnBodyCompositionClick -> Unit
            SettingsAction.OnShowWeeklySummary -> viewModelScope.launch { userSettings.setWeeklySummaryDismissed(null) }
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

            SettingsAction.OnDownloadModelClick -> if (state.value.ai.canDownload) downloader.start()
            SettingsAction.OnScreenResumed -> refreshModel()

            SettingsAction.OnDismissEditor -> editor.value = null
        }
    }

    init {
        refreshModel()
        // When the download ends, AICore's answer changes (Ready, or still Downloadable).
        viewModelScope.launch {
            downloader.state.collect { if (it is DownloadState.Done || it is DownloadState.Failed) refreshModel() }
        }
    }

    private fun refreshModel() {
        viewModelScope.launch {
            availability.value = runCatching { model.availability() }.getOrDefault(ModelAvailability.Unavailable)
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

    private fun today() = dateProvider.now().atZone(dateProvider.zone).toLocalDate()

    /** Saves with [block] (false: implausible); closes the dialog, or keeps it open as [invalid]. */
    private fun saveChecked(invalid: SettingsEditor, block: suspend () -> Boolean) {
        viewModelScope.launch {
            val saved = runCatching { block() }.getOrDefault(false)
            editor.value = if (saved) null else invalid
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
