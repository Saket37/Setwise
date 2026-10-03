package dev.saketanand.setwise.ui.workout

import dev.saketanand.setwise.domain.model.CardioMetric
import dev.saketanand.setwise.domain.model.CardioValues
import dev.saketanand.setwise.util.parseWeight

/** Cardio screen input rules. Plain functions, unit-tested without Android. */

/** What's logged, or why it can't be: empty fields fall back to [lastTime]. */
sealed interface CardioParseResult {
    data class Valid(val values: CardioValues) : CardioParseResult
    data class Invalid(val error: CardioInputError) : CardioParseResult
}

fun parseCardio(
    inputs: CardioInputs,
    metrics: Set<CardioMetric>,
    inclinePct: Double,
    level: Int,
    lastTime: CardioValues?,
): CardioParseResult {
    val minutes = inputs.minutes.trim().toIntOrNull()
    val seconds = inputs.seconds.trim().toIntOrNull()
    val durationSec = if (minutes == null && seconds == null) {
        lastTime?.durationSec
    } else {
        (minutes ?: 0) * 60 + (seconds ?: 0)
    }
    if (durationSec == null || durationSec <= 0) return CardioParseResult.Invalid(CardioInputError.MissingDuration)

    fun decimal(text: String, fallback: Double?) = if (text.isBlank()) fallback else parseWeight(text)?.takeIf { it > 0 }

    val speedFrom = if (CardioMetric.SPEED in metrics) decimal(inputs.speedFrom, lastTime?.speedMinKmh) else null
    val speedTo = if (CardioMetric.SPEED in metrics) decimal(inputs.speedTo, lastTime?.speedMaxKmh) else null
    if (speedFrom != null && speedTo != null && speedFrom > speedTo) return CardioParseResult.Invalid(CardioInputError.SpeedOrder)

    return CardioParseResult.Valid(
        CardioValues(
            durationSec = durationSec,
            inclinePct = inclinePct.takeIf { CardioMetric.INCLINE in metrics },
            speedMinKmh = speedFrom,
            speedMaxKmh = speedTo,
            distanceKm = if (CardioMetric.DISTANCE in metrics) decimal(inputs.distance, lastTime?.distanceKm) else null,
            level = level.takeIf { CardioMetric.LEVEL in metrics },
        )
    )
}
