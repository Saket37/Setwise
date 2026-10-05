package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A line through [points] (oldest left), spaced by time, with a dot on the latest and a
 * baseline. The y axis fits the points with some room above and below. Read out as
 * [contentDescription] only.
 */
@Composable
fun SetwiseLineChart(
    points: ImmutableList<ChartPoint>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 84.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    baselineColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val fractions = remember(points) { lineFractions(points) }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        val inset = DOT_RADIUS.toPx()
        val w = size.width - 2 * inset
        val h = size.height - 2 * inset
        fun at(f: Pair<Float, Float>) = Offset(inset + f.first * w, inset + (1 - f.second) * h)
        drawLine(baselineColor, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 1.dp.toPx())
        if (fractions.isEmpty()) return@Canvas
        val path = Path().apply {
            fractions.forEachIndexed { i, f -> at(f).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }
        drawPath(path, lineColor, style = Stroke(width = LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(lineColor, radius = DOT_RADIUS.toPx(), center = at(fractions.last()))
    }
}

private val LINE_WIDTH = 2.5.dp
private val DOT_RADIUS = 4.5.dp

/** Room above and below the points, as a share of their spread. */
private const val PADDING = 0.15

/**
 * Each point as (x, y) fractions, 0–1: x by time between the first and last point (one point:
 * the middle), y between the lowest and highest with [PADDING] around (all equal: the middle).
 */
fun lineFractions(points: List<ChartPoint>): List<Pair<Float, Float>> {
    if (points.isEmpty()) return emptyList()
    val x0 = points.minOf { it.x }
    val x1 = points.maxOf { it.x }
    val lo = points.minOf { it.y }
    val hi = points.maxOf { it.y }
    val pad = (hi - lo) * PADDING
    return points.map { p ->
        val x = if (x1 == x0) 0.5f else ((p.x - x0).toDouble() / (x1 - x0)).toFloat()
        val y = if (hi == lo) 0.5f else ((p.y - (lo - pad)) / (hi - lo + 2 * pad)).toFloat()
        x to y
    }
}

@PreviewComponents
@Composable
private fun SetwiseLineChartPreview() = SetwisePreview {
    SetwiseLineChart(
        points = persistentListOf(ChartPoint(0, 80.5), ChartPoint(21, 80.0), ChartPoint(42, 79.6), ChartPoint(84, 78.8), ChartPoint(91, 78.4)),
        contentDescription = "",
    )
}
