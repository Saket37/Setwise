package dev.saketanand.setwise.ui.body

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.model.ProgressMeasure
import dev.saketanand.setwise.domain.model.ProgressRange
import dev.saketanand.setwise.domain.model.SegmentGroup
import dev.saketanand.setwise.ui.designsystem.components.ChartPoint
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class BodyProgressUiState(
    val isLoading: Boolean = true,
    val range: ProgressRange = ProgressRange.ThreeMonths,
    /** Each measure ever recorded, in [ProgressMeasure] order. */
    val measures: ImmutableList<MeasureUi> = persistentListOf(),
    /** Lean muscle by arms, trunk and legs; empty without two reports in range. */
    val segments: ImmutableList<SegmentUi> = persistentListOf(),
    val segmentsFrom: LocalDate? = null,
    val segmentsTo: LocalDate? = null,
)

/** One measure's card: [points] in range (a chart from two), its latest value, the change since [since]. */
@Immutable
data class MeasureUi(
    val measure: ProgressMeasure,
    val latest: Double,
    val change: Double?,
    val since: LocalDate?,
    val until: LocalDate?,
    /** x: epoch days. */
    val points: ImmutableList<ChartPoint>,
)

@Immutable
data class SegmentUi(val group: SegmentGroup, val first: Double, val latest: Double, val change: Double)
