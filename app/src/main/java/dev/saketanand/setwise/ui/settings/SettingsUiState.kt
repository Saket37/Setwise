package dev.saketanand.setwise.ui.settings

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.ai.DownloadState
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.model.BmrEstimate
import dev.saketanand.setwise.domain.model.Sex
import java.time.DayOfWeek
import kotlinx.collections.immutable.ImmutableSet

/** Everything [SettingsScreen] draws. */
@Immutable
data class SettingsUiState(
    val isLoading: Boolean = true,
    val name: String? = null,
    val age: Int? = null,
    val sex: Sex? = null,
    val heightCm: Double? = null,
    val bodyWeightKg: Double? = null,
    /** "Body composition" row: BMR (and where it's from), the newest body fat. */
    val bmr: BmrEstimate? = null,
    val bodyFatPercent: Double? = null,
    val trainingDays: Set<DayOfWeek> = emptySet(),
    val noFixedTrainingDays: Boolean = false,
    val askAboutUnloggedDays: Boolean = true,
    /** Rest timer: null = each exercise's own. */
    val restSecOverride: Int? = null,
    val restSound: Boolean = true,
    val restVibrate: Boolean = true,
    val workoutNotification: Boolean = true,
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
    data class TrainingDays(val selected: ImmutableSet<DayOfWeek>, val noFixedDays: Boolean = false) : SettingsEditor

    data object Name : SettingsEditor

    data class Age(val isInvalid: Boolean = false) : SettingsEditor

    data class Height(val isInvalid: Boolean = false) : SettingsEditor

    data object SexChoice : SettingsEditor

    data object DefaultRest : SettingsEditor
}
