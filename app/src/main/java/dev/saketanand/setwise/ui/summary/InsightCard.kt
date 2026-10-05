package dev.saketanand.setwise.ui.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.ExerciseChange
import dev.saketanand.setwise.domain.model.Intensity
import dev.saketanand.setwise.domain.model.Measure
import dev.saketanand.setwise.domain.model.WorkoutFacts
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.util.toClockLabel
import dev.saketanand.setwise.util.toWeightLabel
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

/**
 * Design "On-device insight · Gemini Nano": the model's few sentences; until it has written
 * them (or without the model), "Highlights" from the same facts.
 */
@Composable
fun InsightCard(insight: InsightUi, modifier: Modifier = Modifier) {
    val text = insight.modelText ?: templateInsight(insight.facts)
    if (text.isBlank()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_ai_sparkle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(if (insight.modelText != null) R.string.insight_by_model else R.string.insight_highlights),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Up to three sentences from the facts: the best improvement on last time (or a lift matched),
 * the volume against the last workout of the same name, and the intensity with the usual rest.
 */
@Composable
private fun templateInsight(facts: WorkoutFacts): String {
    val sentences = mutableListOf<String>()
    val best = facts.changes.firstOrNull()
    if (best != null) {
        when {
            best.isImprovement -> sentences += improvement(best)
            best.isSame -> sentences += stringResource(R.string.insight_matched, best.exercise)
        }
    }
    val previous = facts.previous
    val percent = facts.volumeChangePercent
    if (previous != null && percent != null) {
        sentences += when {
            percent > 0 -> stringResource(R.string.insight_volume_up, percent, previous.name)
            percent < 0 -> stringResource(R.string.insight_volume_down, abs(percent), previous.name)
            else -> stringResource(R.string.insight_volume_same, previous.name)
        }
    }
    facts.intensity?.let { intensity ->
        val word = stringResource(intensity.labelRes())
        sentences += facts.medianRestSec?.let { rest ->
            stringResource(R.string.insight_intensity_rest, word, rest.seconds.toClockLabel())
        } ?: stringResource(R.string.insight_intensity, word)
    }
    return sentences.joinToString(" ")
}

@Composable
private fun improvement(change: ExerciseChange): String {
    val locale = currentLocale()
    val now = change.now
    val kg = stringResource(R.string.unit_kg)
    // "120 kg × 8" with non-breaking spaces, so it doesn't wrap mid-set.
    return when (change.measure) {
        Measure.Weight -> if (change.weightDeltaKg > 0) {
            stringResource(
                R.string.insight_weight_up,
                change.exercise,
                change.weightDeltaKg.toWeightLabel(locale),
                "${(now.weightKg ?: 0.0).toWeightLabel(locale)}\u00A0$kg\u00A0×\u00A0${now.reps ?: 0}",
            )
        } else {
            stringResource(R.string.insight_reps_up, change.exercise, change.repsDelta, "${(now.weightKg ?: 0.0).toWeightLabel(locale)}\u00A0$kg\u00A0×\u00A0${now.reps ?: 0}")
        }
        Measure.Reps -> stringResource(R.string.insight_reps_up, change.exercise, change.repsDelta, "${now.reps ?: 0}")
        Measure.Seconds -> stringResource(R.string.insight_seconds_up, change.exercise, change.secondsDelta)
    }
}

private fun Intensity.labelRes() = when (this) {
    Intensity.Light -> R.string.intensity_light
    Intensity.Moderate -> R.string.intensity_moderate
    Intensity.Vigorous -> R.string.intensity_vigorous
}
