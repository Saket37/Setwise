package dev.saketanand.setwise.ui.body

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.BodySegment
import dev.saketanand.setwise.domain.model.BodyTip
import dev.saketanand.setwise.domain.model.ProgressMeasure
import java.util.Locale

/** "✦ Suggestions" (design 17): what this report and the last one give reason to do. */
@Composable
internal fun SuggestionsCard(tips: List<BodyTip>, modelTips: List<String>?, locale: Locale) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_ai_sparkle), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.body_tips_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
        }
        (modelTips ?: tips.map { tipText(it, locale) }).forEach { tip ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
                Text(tip, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(
            text = stringResource(if (modelTips != null) R.string.body_tips_footer_by_model else R.string.body_tips_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "down 0.4 kg", "up 0.2 kg", "unchanged". */
@Composable
@ReadOnlyComposable
private fun upOrDown(measure: ProgressMeasure, change: Double, locale: Locale): String = when {
    change < 0 -> stringResource(R.string.body_tip_down, measureValue(measure, -change, locale))
    change > 0 -> stringResource(R.string.body_tip_up, measureValue(measure, change, locale))
    else -> stringResource(R.string.body_tip_unchanged)
}

/** A tip in the screen's own words, when the model didn't word it. */
@Composable
private fun tipText(tip: BodyTip, locale: Locale): String = when (tip) {
    is BodyTip.Uneven -> stringResource(
        if (tip.weaker == BodySegment.RightLeg || tip.weaker == BodySegment.LeftLeg) R.string.body_tip_uneven_legs else R.string.body_tip_uneven_arms,
        stringResource(tip.weaker.labelRes()).lowercase(locale),
        stringResource(tip.stronger.labelRes()).lowercase(locale),
    )
    is BodyTip.SinceLast -> sinceLastText(tip, locale)
    is BodyTip.FatOver -> tip.trunkPercent?.takeIf { tip.segments.size == 1 }
        ?.let { stringResource(R.string.body_tip_fat_over_trunk, measureValue(ProgressMeasure.BodyFat, it, locale)) }
        ?: stringResource(R.string.body_tip_fat_over, tip.segments.map { stringResource(it.labelRes()).lowercase(locale) }.joinToString(", "))
}

/** "Since 22 Sep, fat mass is down 0.4 kg and muscle up 0.2 kg. Keep the current training and eating." */
@Composable
private fun sinceLastText(tip: BodyTip.SinceLast, locale: Locale): String {
    val day = tip.since.toDayMonthLabel(locale)
    val fat = tip.fatKg?.let { upOrDown(ProgressMeasure.FatMass, it, locale) }
    val muscle = tip.muscleKg?.let { upOrDown(ProgressMeasure.Muscle, it, locale) }
    val change = when {
        fat != null && muscle != null -> stringResource(R.string.body_tip_since_fat_muscle, day, fat, muscle)
        fat != null -> stringResource(R.string.body_tip_since_fat, day, fat)
        else -> stringResource(R.string.body_tip_since_muscle, day, muscle.orEmpty())
    }
    return if (tip.isOnTrack) change + " " + stringResource(R.string.body_tip_keep_going) else change
}
