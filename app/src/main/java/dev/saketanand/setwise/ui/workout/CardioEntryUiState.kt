package dev.saketanand.setwise.ui.workout

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.model.CalorieMethod
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
    /** What "About 310 kcal · ACSM treadmill formula at 78 kg" is worked out from; null without a body weight. */
    val calories: CardioCalorieBasis? = null,
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

/** The exercise's calorie formula and the person's burn per MET-hour (from their BMR, else their weight). */
@Immutable
data class CardioCalorieBasis(
    val method: CalorieMethod?,
    val met: Double?,
    val kcalPerMetHour: Double,
    val weightKg: Double,
    /** [kcalPerMetHour] came from the BMR rather than the weight. */
    val fromBmr: Boolean,
)
