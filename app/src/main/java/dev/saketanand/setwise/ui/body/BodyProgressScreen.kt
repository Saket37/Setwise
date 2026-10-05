package dev.saketanand.setwise.ui.body

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.ProgressMeasure
import dev.saketanand.setwise.domain.model.ProgressRange
import dev.saketanand.setwise.domain.model.SegmentGroup
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.ChartPoint
import dev.saketanand.setwise.ui.designsystem.components.SetwiseLineChart
import dev.saketanand.setwise.ui.designsystem.components.SetwiseSegmentedControl
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTopAppBar
import dev.saketanand.setwise.ui.designsystem.preview.PreviewScreens
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.designsystem.theme.numberMedium
import dev.saketanand.setwise.ui.designsystem.theme.numberSmall
import java.time.LocalDate
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.koin.androidx.compose.koinViewModel

@Composable
fun BodyProgressScreenRoot(onBack: () -> Unit, viewModel: BodyProgressViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    BodyProgressScreen(uiState = uiState, onRangeChange = viewModel::onRangeChange, onBack = onBack)
}

/**
 * Body progress (design 16): a range (1M … All), a line of what changed, then a card per measure
 * ever recorded (latest value, change in range, a line by date) and lean muscle by segment.
 */
@Composable
fun BodyProgressScreen(
    uiState: BodyProgressUiState,
    onRangeChange: (ProgressRange) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SetwiseTopAppBar(title = stringResource(R.string.body_progress), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "range") {
                SetwiseSegmentedControl(
                    options = RANGE_LABELS.map { stringResource(it) }.toImmutableList(),
                    selected = uiState.range.ordinal,
                    onSelect = { onRangeChange(ProgressRange.entries[it]) },
                )
            }
            if (!uiState.isLoading && uiState.measures.isEmpty()) {
                item(key = "empty") { Muted(stringResource(R.string.body_progress_empty)) }
            } else {
                item(key = "summary") { Text(summary(uiState, locale), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }
            }
            items(uiState.measures, key = { it.measure }) { MeasureCard(it, locale) }
            if (uiState.segments.isNotEmpty()) item(key = "segments") { SegmentsCard(uiState, locale) }
            if (uiState.measures.isNotEmpty()) item(key = "note") { Muted(stringResource(R.string.body_progress_note)) }
        }
    }
}

private val RANGE_LABELS = listOf(R.string.range_1m, R.string.range_3m, R.string.range_6m, R.string.range_1y, R.string.range_all)

/** "Since 6 Jul: −2.1 kg, body fat −1.2 pts, muscle +0.5 kg." */
@Composable
private fun summary(uiState: BodyProgressUiState, locale: Locale): String {
    fun of(measure: ProgressMeasure) = uiState.measures.firstOrNull { it.measure == measure }?.takeIf { it.change != null }
    val weight = of(ProgressMeasure.Weight) ?: return stringResource(R.string.body_progress_not_enough)
    val parts = listOfNotNull(
        weight.change?.let { measureChange(ProgressMeasure.Weight, it, locale) },
        of(ProgressMeasure.BodyFat)?.change?.let { stringResource(R.string.body_progress_fat_part, measureChange(ProgressMeasure.BodyFat, it, locale)) },
        of(ProgressMeasure.Muscle)?.change?.let { stringResource(R.string.body_progress_muscle_part, measureChange(ProgressMeasure.Muscle, it, locale)) },
    )
    return stringResource(R.string.body_progress_summary, weight.since?.toDayMonthLabel(locale).orEmpty(), parts.joinToString(", "))
}

@Composable
private fun MeasureCard(ui: MeasureUi, locale: Locale) {
    val name = stringResource(ui.measure.nameRes())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
                Text(
                    text = if (ui.change != null && ui.since != null) {
                        stringResource(R.string.body_progress_since, ui.since.toDayMonthLabel(locale))
                    } else {
                        stringResource(R.string.body_progress_one_check)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(measureValue(ui.measure, ui.latest, locale), style = MaterialTheme.typography.numberMedium, color = MaterialTheme.colorScheme.onSurface)
                ui.change?.let { Text(measureChange(ui.measure, it, locale), style = MaterialTheme.typography.numberSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (ui.points.size >= 2 && ui.since != null && ui.until != null) {
            val first = ui.points.first().y
            val last = ui.points.last().y
            SetwiseLineChart(
                points = ui.points,
                contentDescription = stringResource(
                    R.string.body_progress_chart,
                    name,
                    measureValue(ui.measure, first, locale),
                    ui.since.toDayMonthLabel(locale),
                    measureValue(ui.measure, last, locale),
                    ui.until.toDayMonthLabel(locale),
                ),
            )
            Row {
                Muted(ui.since.toDayMonthLabel(locale), Modifier.weight(1f))
                Muted(pluralStringResource(R.plurals.body_progress_checks, ui.points.size, ui.points.size), Modifier.weight(1f), TextAlign.Center)
                Muted(ui.until.toDayMonthLabel(locale), Modifier.weight(1f), TextAlign.End)
            }
        } else {
            Muted(stringResource(R.string.body_progress_need_more))
        }
    }
}

@Composable
private fun SegmentsCard(uiState: BodyProgressUiState, locale: Locale) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.body_progress_segments), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        if (uiState.segmentsFrom != null && uiState.segmentsTo != null) {
            Muted(stringResource(R.string.body_progress_segments_span, uiState.segmentsFrom.toDayMonthLabel(locale), uiState.segmentsTo.toDayMonthLabel(locale)))
        }
        uiState.segments.forEach { segment ->
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(stringResource(segment.group.nameRes()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Muted(measureValue(ProgressMeasure.Muscle, segment.first, locale), Modifier.weight(1f), TextAlign.End)
                Text(
                    measureValue(ProgressMeasure.Muscle, segment.latest, locale),
                    style = MaterialTheme.typography.numberSmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    measureChange(ProgressMeasure.Muscle, segment.change, locale),
                    style = MaterialTheme.typography.numberSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun SegmentGroup.nameRes(): Int = when (this) {
    SegmentGroup.Arms -> R.string.segment_arms
    SegmentGroup.Trunk -> R.string.segment_trunk
    SegmentGroup.Legs -> R.string.segment_legs
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align, modifier = modifier)
}

/** A quarter of checks: monthly reports with every measure, typed-in weights between. */
internal val SampleBodyProgressState: BodyProgressUiState = run {
    fun day(month: Int, d: Int) = LocalDate.of(2026, month, d)
    fun points(vararg p: Pair<LocalDate, Double>) = p.map { (date, v) -> ChartPoint(date.toEpochDay(), v) }.toImmutableList()
    BodyProgressUiState(
        isLoading = false,
        measures = persistentListOf(
            MeasureUi(ProgressMeasure.Weight, 78.4, -2.1, day(7, 6), day(10, 5), points(day(7, 6) to 80.5, day(7, 27) to 80.0, day(8, 17) to 79.6, day(9, 7) to 79.3, day(9, 28) to 78.8, day(10, 5) to 78.4)),
            MeasureUi(ProgressMeasure.BodyFat, 18.5, -1.2, day(7, 6), day(10, 5), points(day(7, 6) to 19.7, day(8, 17) to 19.4, day(9, 21) to 18.9, day(10, 5) to 18.5)),
            MeasureUi(ProgressMeasure.Muscle, 35.1, 0.5, day(7, 6), day(10, 5), points(day(7, 6) to 34.6, day(8, 17) to 34.7, day(9, 21) to 34.9, day(10, 5) to 35.1)),
            MeasureUi(ProgressMeasure.FitnessScore, 78.0, null, null, null, persistentListOf()),
        ),
        segments = persistentListOf(
            SegmentUi(SegmentGroup.Arms, 7.0, 7.13, 0.13),
            SegmentUi(SegmentGroup.Trunk, 27.6, 28.0, 0.4),
            SegmentUi(SegmentGroup.Legs, 18.3, 18.71, 0.41),
        ),
        segmentsFrom = day(7, 6),
        segmentsTo = day(10, 5),
    )
}

@PreviewScreens
@Composable
private fun BodyProgressScreenPreview() = SetwiseScreenPreview {
    BodyProgressScreen(uiState = SampleBodyProgressState, onRangeChange = {}, onBack = {})
}
