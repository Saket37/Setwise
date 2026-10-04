package dev.saketanand.setwise.ui.body

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.ai.BodyReportReader
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.BodyRules
import dev.saketanand.setwise.domain.repository.BodyRepository
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.util.DateProvider
import dev.saketanand.setwise.util.parseWeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen: [BodyScreenRoot]. Body checks over time: typed in, or read from a report photo
 * ([BodyReportReader]) and checked on the sheet before saving. Nothing is saved unchecked.
 */
class BodyViewModel(
    private val bodyRepository: BodyRepository,
    private val userSettings: UserSettingsRepository,
    private val reportReader: BodyReportReader,
    private val dateProvider: DateProvider,
) : ViewModel() {

    private data class Screen(val isReading: Boolean = false, val readFailed: Boolean = false, val editor: BodyEditor? = null)

    private val screen = MutableStateFlow(Screen())

    val state: StateFlow<BodyUiState> = combine(bodyRepository.observeMeasurements(), userSettings.settings, screen) { history, settings, screen ->
        fun <T> newest(value: (BodyMeasurement) -> T?) = history.firstNotNullOfOrNull(value)
        BodyUiState(
            isLoading = false,
            history = history,
            bmr = BodyRules.bmr(history, settings, today()),
            latest = LatestBody(newest { it.weightKg } ?: settings.bodyWeightKg, newest { it.bodyFatPercent }, newest { it.muscleMassKg }, newest { it.visceralFat }),
            weightTrend = history.mapNotNull { it.weightKg }.take(CHART_POINTS).reversed(),
            bodyFatTrend = history.mapNotNull { it.bodyFatPercent }.take(CHART_POINTS).reversed(),
            isReading = screen.isReading,
            readFailed = screen.readFailed,
            editor = screen.editor,
        )
    }
        .catch { e ->
            Log.e(TAG, "Loading body checks failed", e)
            emit(BodyUiState(isLoading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyUiState())

    fun onAction(action: BodyAction) {
        when (action) {
            BodyAction.OnBack -> Unit
            BodyAction.OnAddManually -> screen.update { it.copy(readFailed = false, editor = BodyEditor(key = System.nanoTime(), day = today())) }
            is BodyAction.OnReportPhoto -> read(action.uri)
            is BodyAction.OnDayChange -> screen.update { it.copy(editor = it.editor?.copy(day = action.day)) }
            is BodyAction.OnSave -> save(action)
            BodyAction.OnDismissEditor -> screen.update { it.copy(editor = null) }
            is BodyAction.OnDelete -> viewModelScope.launch { bodyRepository.delete(action.id) }
        }
    }

    private fun read(uri: String) {
        screen.update { it.copy(isReading = true, readFailed = false) }
        viewModelScope.launch {
            val profile = userSettings.settings.first()
            val result = try {
                reportReader.read(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Reading the report failed", e)
                BodyReportReader.Result.NothingFound
            }
            screen.update {
                when (result) {
                    is BodyReportReader.Result.Read -> {
                        val values = result.values
                        it.copy(
                            isReading = false,
                            editor = BodyEditor(
                                key = System.nanoTime(),
                                day = values.measuredOn?.takeIf { day -> !day.isAfter(today()) } ?: today(),
                                weightKg = values.weightKg,
                                bodyFatPercent = values.bodyFatPercent,
                                muscleMassKg = values.muscleMassKg,
                                bmrKcal = values.bmrKcal,
                                visceralFat = values.visceralFat,
                                fromReport = true,
                                byModel = result.byModel,
                                // Only what the profile doesn't have yet.
                                profileHeightCm = values.heightCm?.takeIf { profile.heightCm == null },
                                profileAge = values.age?.takeIf { profile.birthYear == null },
                                profileSex = values.sex?.takeIf { profile.sex == null },
                            ),
                        )
                    }
                    BodyReportReader.Result.NothingFound -> it.copy(isReading = false, readFailed = true)
                }
            }
        }
    }

    private fun save(action: BodyAction.OnSave) {
        val editor = screen.value.editor ?: return
        // Blank: not given. Typed but not a believable number: not saved.
        fun decimal(text: String, range: ClosedFloatingPointRange<Double>): Result<Double?> =
            if (text.isBlank()) Result.success(null) else parseWeight(text)?.takeIf { it in range }?.let { Result.success(it) } ?: Result.failure(IllegalArgumentException())
        val weight = decimal(action.weight, BodyRules.WEIGHT_KG)
        val fat = decimal(action.bodyFat, BodyRules.BODY_FAT_PERCENT)
        val muscle = decimal(action.muscle, BodyRules.MUSCLE_KG)
        val visceral = decimal(action.visceral, BodyRules.VISCERAL)
        val bmr = if (action.bmr.isBlank()) Result.success(null) else action.bmr.trim().toIntOrNull()?.takeIf { it in BodyRules.BMR_KCAL }
            ?.let { Result.success(it) } ?: Result.failure(IllegalArgumentException())
        val measurement = if (listOf(weight, fat, muscle, visceral, bmr).any { it.isFailure }) {
            null
        } else {
            BodyMeasurement(
                id = System.currentTimeMillis(),
                measuredOn = editor.day,
                weightKg = weight.getOrNull(),
                bodyFatPercent = fat.getOrNull(),
                muscleMassKg = muscle.getOrNull(),
                bmrKcal = bmr.getOrNull(),
                visceralFat = visceral.getOrNull(),
                source = if (editor.fromReport) BodyMeasurement.Source.Report else BodyMeasurement.Source.Manual,
            ).takeUnless { it.isEmpty }
        }
        if (measurement == null) {
            screen.update { it.copy(editor = editor.copy(isInvalid = true)) }
            return
        }
        screen.update { it.copy(editor = null) }
        viewModelScope.launch {
            bodyRepository.add(measurement)
            editor.profileHeightCm?.let { userSettings.setHeightCm(it) }
            editor.profileAge?.let { userSettings.setAge(it, editor.day) }
            editor.profileSex?.let { userSettings.setSex(it) }
        }
    }

    private fun today() = dateProvider.now().atZone(dateProvider.zone).toLocalDate()

    private companion object {
        const val TAG = "BodyViewModel"
    }
}
