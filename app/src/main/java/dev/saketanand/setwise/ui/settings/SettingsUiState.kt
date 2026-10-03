package dev.saketanand.setwise.ui.settings

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek

/** Everything [SettingsScreen] draws. */
@Immutable
data class SettingsUiState(
    val isLoading: Boolean = true,
    val bodyWeightKg: Double? = null,
    val trainingDays: Set<DayOfWeek> = emptySet(),
    val askAboutUnloggedDays: Boolean = true,
    /** The editor dialog that's open, if any. */
    val editor: SettingsEditor? = null,
)

/** Settings edited in a dialog. */
@Immutable
sealed interface SettingsEditor {
    /** [isInvalid]: the last Save had an implausible weight. */
    data class BodyWeight(val isInvalid: Boolean = false) : SettingsEditor

    /** Days picked so far (saved on Save). */
    data class TrainingDays(val selected: Set<DayOfWeek>) : SettingsEditor
}
