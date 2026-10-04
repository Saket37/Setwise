package dev.saketanand.setwise.ui.body

import java.time.LocalDate

/** What the user can do on [BodyScreen]. */
sealed interface BodyAction {
    data object OnBack : BodyAction
    data object OnAddManually : BodyAction

    /** A report photo, taken or chosen: read it. */
    data class OnReportPhoto(val uri: String) : BodyAction

    data class OnDayChange(val day: LocalDate) : BodyAction

    /** Save on the sheet, with the fields as typed. */
    data class OnSave(
        val weight: String,
        val bodyFat: String,
        val muscle: String,
        val bmr: String,
        val visceral: String,
    ) : BodyAction

    data object OnDismissEditor : BodyAction
    data class OnDelete(val id: Long) : BodyAction
}
