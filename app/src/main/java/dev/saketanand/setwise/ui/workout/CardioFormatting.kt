package dev.saketanand.setwise.ui.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.CardioValues
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.util.toClockLabel
import java.text.NumberFormat
import kotlin.time.Duration.Companion.seconds

/** "30:00 · 5% · 5.5–7.5 km/h · 3.9 km · Level 8": only what was logged. */
@Composable
fun CardioValues.summary(): String {
    val number = NumberFormat.getNumberInstance(currentLocale()).apply { maximumFractionDigits = 2 }
    val speed = listOfNotNull(speedMinKmh, speedMaxKmh).distinct().joinToString("–") { number.format(it) }
    return listOfNotNull(
        durationSec?.seconds?.toClockLabel(),
        inclinePct?.let { stringResource(R.string.cardio_incline_value, number.format(it)) },
        speed.takeIf { it.isNotEmpty() }?.let { "$it ${stringResource(R.string.unit_kmh)}" },
        distanceKm?.let { "${number.format(it)} ${stringResource(R.string.unit_km)}" },
        level?.let { stringResource(R.string.cardio_level_value, it) },
    ).joinToString(" · ")
}
