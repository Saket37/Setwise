package dev.saketanand.setwise.ui.settings

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import dev.saketanand.setwise.domain.ai.ModelAvailability

/** Everything [SettingsScreen] draws. */
@Immutable
data class SettingsUiState(
    val isLoading: Boolean = true,
    val bodyWeightKg: Double? = null,
    val trainingDays: Set<DayOfWeek> = emptySet(),
    val askAboutUnloggedDays: Boolean = true,
    /** The editor dialog that's open, if any. */
    val editor: SettingsEditor? = null,
    val ai: AiStatusUi = AiStatusUi(),
)

/** The "On-device AI" row: Gemini Nano's state, and the download while it runs. */
@Immutable
data class AiStatusUi(
    /** Null until checked. */
    val availability: ModelAvailability? = null,
    /** 0–100 while downloading from here; null otherwise (or when the size is unknown). */
    val downloadPercent: Int? = null,
    /** The last download from here didn't finish. */
    val downloadFailed: Boolean = false,
) {
    val canDownload: Boolean get() = availability == ModelAvailability.Downloadable || downloadFailed
}

/** Settings edited in a dialog. */
@Immutable
sealed interface SettingsEditor {
    /** [isInvalid]: the last Save had an implausible weight. */
    data class BodyWeight(val isInvalid: Boolean = false) : SettingsEditor

    /** Days picked so far (saved on Save). */
    data class TrainingDays(val selected: Set<DayOfWeek>) : SettingsEditor
}
