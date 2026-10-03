package dev.saketanand.setwise.ui.workout

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.model.CardioMetric
import dev.saketanand.setwise.domain.model.CardioValues

/** Everything [CardioEntryScreen] draws. Text fields live in the screen; steppers here. */
@Immutable
data class CardioEntryUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    /** Which inputs to show; duration always. */
    val metrics: Set<CardioMetric> = setOf(CardioMetric.DURATION),
    /** Already logged in this workout: the fields start with it and the button says Save. */
    val logged: CardioValues? = null,
    /** Last session: shown on top, and empty fields fall back to it (like set hints). */
    val lastTime: CardioValues? = null,
    /** Percent grade, in steps of [INCLINE_STEP]. */
    val inclinePct: Double = 0.0,
    val level: Int = 1,
    val error: CardioInputError? = null,
    val isSaving: Boolean = false,
) {
    val isEditing: Boolean get() = logged != null

    companion object {
        const val INCLINE_STEP = 0.5
        const val MAX_INCLINE = 15.0
        val LEVEL_RANGE = 1..30
    }
}

enum class CardioInputError {
    /** No duration typed and none last time to fall back to. */
    MissingDuration,

    /** Speed "from" is above "to". */
    SpeedOrder,
}

/** The text fields as typed ("" = empty: use last time's value). */
data class CardioInputs(
    val minutes: String = "",
    val seconds: String = "",
    val speedFrom: String = "",
    val speedTo: String = "",
    val distance: String = "",
)
