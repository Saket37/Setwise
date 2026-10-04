package dev.saketanand.setwise.ui.settings

import java.time.DayOfWeek

/** What the user can do on [SettingsScreen]. */
sealed interface SettingsAction {
    data object OnBodyWeightClick : SettingsAction
    data class OnSaveBodyWeight(val text: String) : SettingsAction
    data object OnRemoveBodyWeight : SettingsAction

    data object OnTrainingDaysClick : SettingsAction
    data class OnDayToggle(val day: DayOfWeek) : SettingsAction
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
}
