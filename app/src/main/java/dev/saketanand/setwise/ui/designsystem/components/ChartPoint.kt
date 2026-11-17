package dev.saketanand.setwise.ui.designsystem.components

import androidx.compose.runtime.Immutable

/** A point on a [SetwiseLineChart]: [x] in any unit of time (e.g. epoch days), so uneven gaps show. */
@Immutable
data class ChartPoint(val x: Long, val y: Double)
