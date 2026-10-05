package dev.saketanand.setwise.ui.body

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.BodySegment
import dev.saketanand.setwise.domain.model.NormalRange
import dev.saketanand.setwise.domain.model.ProgressMeasure
import dev.saketanand.setwise.domain.model.Rating
import dev.saketanand.setwise.domain.model.ReportDetails
import dev.saketanand.setwise.domain.model.SegmentValues
import dev.saketanand.setwise.ui.designsystem.components.SectionLabel
import dev.saketanand.setwise.ui.designsystem.components.SetwiseSegmentedControl
import dev.saketanand.setwise.ui.designsystem.theme.numberLarge
import dev.saketanand.setwise.ui.designsystem.theme.numberMedium
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf

/** "10.0–20.0%", "56.7–76.7 kg", "below 10" (the unit once, after the high end; none for a level). */
@Composable
internal fun rangeLabel(measure: ProgressMeasure, range: NormalRange, locale: Locale): String {
    val low = range.low
    val high = range.high
    val level = measure == ProgressMeasure.Visceral
    val lowText = low?.let { if (level) measureNumber(measure, it, locale) else measureValue(measure, it, locale) }
    val highText = high?.let { if (level) measureNumber(measure, it, locale) else measureValue(measure, it, locale) }
    return when {
        low != null && highText != null -> stringResource(R.string.range_between, measureNumber(measure, low, locale), highText)
        highText != null -> stringResource(R.string.range_below, highText)
        lowText != null -> stringResource(R.string.range_above, lowText)
        else -> ""
    }
}

@Composable
internal fun RatingLabel(rating: Rating, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(rating.labelRes()),
        style = MaterialTheme.typography.labelMedium,
        color = if (rating == Rating.Normal) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
        modifier = modifier,
    )
}

internal fun Rating.labelRes() = when (this) {
    Rating.Under -> R.string.rating_under
    Rating.Normal -> R.string.rating_normal
    Rating.Over -> R.string.rating_over
}

/** Thirds (under | normal | over) and a marker where the value sits; a one-sided range starts at 0. */
@Composable
internal fun RangeBar(value: Double, range: NormalRange, rating: Rating) {
    val markerColor = if (rating == Rating.Normal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val fraction = rangeFraction(value, range)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(12.dp).clearAndSetSemantics {}) {
        Row(modifier = Modifier.fillMaxWidth().height(6.dp).align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.surfaceContainerHighest)
                .forEach { Box(Modifier.weight(1f).height(6.dp).background(it, RoundedCornerShape(3.dp))) }
        }
        Box(
            Modifier
                .offset(x = maxWidth * fraction - 2.dp)
                .width(4.dp)
                .height(12.dp)
                .background(markerColor, RoundedCornerShape(2.dp)),
        )
    }
}

/** Where [value] sits on a bar of thirds: 0–⅓ under, ⅓–⅔ in range, ⅔–1 over (proportionally, capped). */
internal fun rangeFraction(value: Double, range: NormalRange): Float {
    val low = range.low ?: 0.0
    val high = range.high ?: (low * 2).coerceAtLeast(1.0)
    val span = (high - low).takeIf { it > 0 } ?: 1.0
    val third = 1f / 3
    val f = when {
        value < low -> third * (1 - ((low - value) / span).coerceAtMost(1.0)).toFloat()
        value > high -> 2 * third + third * ((value - high) / span).coerceAtMost(1.0).toFloat()
        else -> third + third * ((value - low) / span).toFloat()
    }
    return f.coerceIn(MARKER_EDGE, 1 - MARKER_EDGE)
}

private const val MARKER_EDGE = 0.01f

/** Arms, trunk and legs laid out like a body; Lean muscle or Fat. */
@Composable
internal fun Segments(segments: List<SegmentValues>, locale: Locale) {
    var showFat by rememberSaveable { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(stringResource(R.string.body_report_segments), modifier = Modifier.semantics { heading() })
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SetwiseSegmentedControl(
                options = persistentListOf(stringResource(R.string.body_report_lean), stringResource(R.string.body_report_fat)),
                selected = showFat,
                onSelect = { showFat = it },
            )
            val bySegment = segments.associateBy { it.segment }
            LAYOUT.forEach { rowSegments ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowSegments.forEach { segment ->
                        val values = segment?.let { bySegment[it] }
                        if (segment == null || values == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            SegmentCell(segment, values, fat = showFat == 1, locale = locale, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Text(
                stringResource(if (showFat == 1) R.string.body_report_fat_note else R.string.body_report_lean_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Right arm · trunk · left arm, then the legs (as you face the report). */
private val LAYOUT = listOf(
    listOf(BodySegment.RightArm, BodySegment.Trunk, BodySegment.LeftArm),
    listOf(BodySegment.RightLeg, null, BodySegment.LeftLeg),
)

@Composable
internal fun SegmentCell(segment: BodySegment, values: SegmentValues, fat: Boolean, locale: Locale, modifier: Modifier = Modifier) {
    val value = if (fat) values.fatPercent?.let { measureValue(ProgressMeasure.BodyFat, it, locale) } else values.leanKg?.let { measureValue(ProgressMeasure.Muscle, it, locale) }
    val rating = if (fat) values.fatRating else values.leanRating
    Column(
        modifier = modifier
            .heightIn(min = 84.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(10.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(segment.labelRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "–", style = MaterialTheme.typography.numberMedium)
        rating?.let {
            Text(
                text = stringResource(it.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                color = if (it == Rating.Normal) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

internal fun BodySegment.labelRes() = when (this) {
    BodySegment.RightArm -> R.string.segment_right_arm
    BodySegment.LeftArm -> R.string.segment_left_arm
    BodySegment.Trunk -> R.string.segment_trunk
    BodySegment.RightLeg -> R.string.segment_right_leg
    BodySegment.LeftLeg -> R.string.segment_left_leg
}

/** "InBody's suggested change": as printed, to reach its normal ranges. */
@Composable
internal fun ControlCard(details: ReportDetails, locale: Locale) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.large)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SectionLabel(stringResource(R.string.body_report_control))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            details.muscleControlKg?.let {
                ControlValue(measureChange(ProgressMeasure.Muscle, it, locale), stringResource(R.string.body_report_control_muscle), Modifier.weight(1f))
            }
            details.fatControlKg?.let { ControlValue(measureChange(ProgressMeasure.FatMass, it, locale), stringResource(R.string.body_report_control_fat), Modifier.weight(1f)) }
        }
        Text(stringResource(R.string.body_report_control_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ControlValue(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.numberLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
