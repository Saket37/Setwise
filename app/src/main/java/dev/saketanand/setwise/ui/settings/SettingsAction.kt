package dev.saketanand.setwise.ui.settings

import dev.saketanand.setwise.domain.model.Sex
import java.time.DayOfWeek

/** What the user can do on [SettingsScreen]. */
sealed interface SettingsAction {
    data object OnBodyWeightClick : SettingsAction
    data class OnSaveBodyWeight(val text: String) : SettingsAction
    data object OnRemoveBodyWeight : SettingsAction

    data object OnTrainingDaysClick : SettingsAction
    data class OnDayToggle(val day: DayOfWeek) : SettingsAction
    data object OnNoFixedDaysToggle : SettingsAction
    data object OnSaveTrainingDays : SettingsAction

    data class OnAskAboutUnloggedDaysChange(val ask: Boolean) : SettingsAction

    /** The "On-device AI" row when the model can be downloaded (or the last try failed). */
    data object OnDownloadModelClick : SettingsAction

    /** Back on screen: the model may have finished downloading meanwhile. */
    data object OnScreenResumed : SettingsAction

    /** Cancel, back or outside the dialog. */
    data object OnDismissEditor : SettingsAction

    /** "Show again": last week's summary back on Home. */
    data object OnShowWeeklySummary : SettingsAction

    data object OnNameClick : SettingsAction
    data class OnSaveName(val text: String) : SettingsAction
    data object OnAgeClick : SettingsAction
    data class OnSaveAge(val text: String) : SettingsAction
    data object OnSexClick : SettingsAction
    data class OnSaveSex(val sex: Sex?) : SettingsAction
    data object OnHeightClick : SettingsAction
    data class OnSaveHeight(val text: String) : SettingsAction

    /** A profile value's Remove: cleared. */
    data class OnRemoveProfileValue(val editor: SettingsEditor) : SettingsAction

    /** Opens Body composition (handled in SettingsScreenRoot). */
    data object OnBodyCompositionClick : SettingsAction

    data object OnDefaultRestClick : SettingsAction

    /** Null: each exercise's own rest. */
    data class OnSaveDefaultRest(val seconds: Int?) : SettingsAction
    data class OnRestSoundChange(val on: Boolean) : SettingsAction
    data class OnRestVibrateChange(val on: Boolean) : SettingsAction
    data class OnWorkoutNotificationChange(val on: Boolean) : SettingsAction

    /** Opens "Import from Strong" (handled in SettingsScreenRoot). */
    data object OnImportClick : SettingsAction
}
