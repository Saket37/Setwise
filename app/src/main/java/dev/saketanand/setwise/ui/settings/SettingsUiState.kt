package dev.saketanand.setwise.ui.settings

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.ai.DownloadState

/** Everything [SettingsScreen] draws. */
@Immutable
data class SettingsUiState(
    val isLoading: Boolean = true,
    val bodyWeightKg: Double? = null,
    val trainingDays: Set<DayOfWeek> = emptySet(),
    val askAboutUnloggedDays: Boolean = true,
    /** Last week's summary was closed on Home: it can be shown again. */
    val isWeeklySummaryClosed: Boolean = false,
    /** The editor dialog that's open, if any. */
    val editor: SettingsEditor? = null,
    val ai: AiStatusUi = AiStatusUi(),
)

/** The "On-device AI" row: Gemini Nano's state, and the download (followed app-wide). */
@Immutable
data class AiStatusUi(
    /** Null until checked. */
    val availability: ModelAvailability? = null,
    val download: DownloadState = DownloadState.Idle,
) {
    val canDownload: Boolean
        get() = availability != ModelAvailability.Ready &&
            (availability == ModelAvailability.Downloadable || download is DownloadState.Failed)
}

/** Settings edited in a dialog. */
@Immutable
sealed interface SettingsEditor {
    /** [isInvalid]: the last Save had an implausible weight. */
    data class BodyWeight(val isInvalid: Boolean = false) : SettingsEditor

    /** Days picked so far (saved on Save). */
    data class TrainingDays(val selected: Set<DayOfWeek>) : SettingsEditor
}
