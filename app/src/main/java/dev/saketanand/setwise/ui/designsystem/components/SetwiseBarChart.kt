package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Equal-width bars, oldest left. A null value is an empty slot (a short stub keeps the rhythm).
 * Heights are scaled by [barFractions] so small changes still show. Read out as
 * [contentDescription] only: the bars themselves aren't focusable.
 */
@Composable
fun SetwiseBarChart(
    values: ImmutableList<Double?>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 110.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
    emptyColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val fractions = barFractions(values)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        fractions.forEach { fraction ->
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction ?: EMPTY_FRACTION)
                        .background(if (fraction == null) emptyColor else barColor, BarShape),
                )
            }
        }
    }
}

private val BarShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
private const val EMPTY_FRACTION = 0.04f
private const val MIN_FRACTION = 0.08f

/**
 * Each value's bar height (0–1). The axis doesn't start at zero: it starts below the lowest value
 * by the spread of the values, so 44 → 48 kg reads as a climb rather than four equal bars. But
 * never above [MAX_FLOOR] of the highest, so 48 → 48.2 kg (a plateau) stays level instead of
 * doubling.
 */

/** The axis starts at most this far up the highest value: smaller differences don't look big. */
private const val MAX_FLOOR = 0.8

fun barFractions(values: List<Double?>): List<Float?> {
    val present = values.filterNotNull()
    if (present.isEmpty()) return values.map { null }
    val max = present.max()
    val min = present.min()
    val floor = if (max == min) max / 2 else (min - (max - min)).coerceIn(0.0, max * MAX_FLOOR)
    return values.map { value ->
        value?.let { (((it - floor) / (max - floor)).toFloat()).coerceIn(MIN_FRACTION, 1f) }
    }
}

@PreviewComponents
@Composable
private fun SetwiseBarChartPreview() = SetwisePreview {
    SetwiseBarChart(values = persistentListOf(40.0, 42.0, null, 47.5, 48.0, 48.0, 47.5, 48.0), contentDescription = "")
}
