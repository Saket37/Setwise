package dev.saketanand.setwise.ui.body

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.BodyMetric
import dev.saketanand.setwise.domain.model.BodySegment
import dev.saketanand.setwise.domain.model.NormalRange
import dev.saketanand.setwise.domain.model.ProgressMeasure
import dev.saketanand.setwise.domain.model.Rating
import dev.saketanand.setwise.domain.model.ReportDetails
import dev.saketanand.setwise.domain.model.SegmentValues
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.SectionLabel
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseConfirmDialog
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTopAppBar
import dev.saketanand.setwise.ui.designsystem.preview.PreviewScreens
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.designsystem.theme.numberMedium
import dev.saketanand.setwise.util.toShortDayLabel
import java.time.LocalDate
import java.util.Locale

@Composable
fun BodyReportScreenRoot(onBack: () -> Unit, viewModel: BodyReportViewModel) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    // Deleted, or never there: back to the list. The latest callback, not the first one.
    val back by rememberUpdatedState(onBack)
    LaunchedEffect(uiState.isMissing) { if (uiState.isMissing) back() }
    BodyReportScreen(
        uiState = uiState,
        onBack = onBack,
        onDeleteClick = viewModel::onDeleteClick,
        onDeleteDismiss = viewModel::onDeleteDismiss,
        onConfirmDelete = viewModel::onConfirmDelete,
    )
}

/**
 * Everything one body check's report said (design 17): fitness score, body composition and
 * health markers each against its normal range, the arms / trunk / legs, and the report's
 * suggested change. A typed-in check shows just its values.
 */
@Composable
fun BodyReportScreen(
    uiState: BodyReportUiState,
    onBack: () -> Unit,
    onDeleteClick: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val check = uiState.check
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SetwiseTopAppBar(
            title = check?.let { stringResource(R.string.body_report_title, it.measuredOn.toShortDayLabel(locale)) }.orEmpty(),
            onBack = onBack,
        )
        if (check == null) return@Column
        val details = check.details
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            details.fitnessScore?.let { score -> item(key = "score") { ScoreCard(score) } }
            val composition = compositionRows(check)
            if (composition.isNotEmpty()) item(key = "composition") { Group(stringResource(R.string.body_report_composition), composition, details.ranges, locale) }
            val markers = markerRows(check)
            if (markers.isNotEmpty()) item(key = "markers") { Group(stringResource(R.string.body_report_markers), markers, details.ranges, locale) }
            if (details.segments.isNotEmpty()) item(key = "segments") { Segments(details.segments, locale) }
            if (details.muscleControlKg != null || details.fatControlKg != null) item(key = "control") { ControlCard(details, locale) }
            item(key = "delete") {
                SetwiseButton(
                    text = stringResource(R.string.body_delete_check),
                    onClick = onDeleteClick,
                    style = SetwiseButtonStyle.Outlined,
                    size = SetwiseButtonSize.Medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (uiState.isConfirmingDelete) {
        SetwiseConfirmDialog(
            title = stringResource(R.string.body_delete_check_title),
            message = stringResource(R.string.body_delete_check_message),
            confirmText = stringResource(R.string.delete),
            onConfirm = onConfirmDelete,
            onDismiss = onDeleteDismiss,
            isDestructive = true,
        )
    }
}

/** A measure, its value and the metric its report's normal range is under. */
private data class Row(val measure: ProgressMeasure, val metric: BodyMetric, val value: Double)

private fun compositionRows(check: BodyMeasurement) = listOfNotNull(
    check.weightKg?.let { Row(ProgressMeasure.Weight, BodyMetric.Weight, it) },
    check.muscleMassKg?.let { Row(ProgressMeasure.Muscle, BodyMetric.Muscle, it) },
    check.details.fatMassKg?.let { Row(ProgressMeasure.FatMass, BodyMetric.FatMass, it) },
    check.details.bodyWaterL?.let { Row(ProgressMeasure.BodyWater, BodyMetric.BodyWater, it) },
    check.details.fatFreeMassKg?.let { Row(ProgressMeasure.FatFreeMass, BodyMetric.FatFreeMass, it) },
)

private fun markerRows(check: BodyMeasurement) = listOfNotNull(
    check.details.bmi?.let { Row(ProgressMeasure.Bmi, BodyMetric.Bmi, it) },
    check.bodyFatPercent?.let { Row(ProgressMeasure.BodyFat, BodyMetric.BodyFat, it) },
    check.details.waistHipRatio?.let { Row(ProgressMeasure.WaistHip, BodyMetric.WaistHip, it) },
    check.visceralFat?.let { Row(ProgressMeasure.Visceral, BodyMetric.Visceral, it) },
    check.bmrKcal?.let { Row(ProgressMeasure.Bmr, BodyMetric.Bmr, it.toDouble()) },
)

@Composable
private fun ScoreCard(score: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.measure_fitness_score), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.body_report_score_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(score.toString(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Group(title: String, rows: List<Row>, ranges: Map<BodyMetric, NormalRange>, locale: Locale) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(title, modifier = Modifier.semantics { heading() })
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            rows.forEachIndexed { i, row ->
                ValueRow(row, ranges[row.metric], locale)
                if (i < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            }
        }
    }
}

/** "Body fat  18.5%  Normal", a bar of under | normal | over with a marker, "Normal 10–20%". */
@Composable
private fun ValueRow(row: Row, range: NormalRange?, locale: Locale) {
    val rating = range?.rate(row.value)
    Column(
        modifier = Modifier.padding(vertical = 10.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(row.measure.nameRes()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(measureValue(row.measure, row.value, locale), style = MaterialTheme.typography.numberMedium)
            rating?.let { RatingLabel(it, Modifier.padding(start = 8.dp).width(56.dp)) }
        }
        if (range != null && rating != null) {
            RangeBar(row.value, range, rating)
            Text(
                stringResource(R.string.body_report_normal, rangeLabel(row.measure, range, locale)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A full report (made-up values). */
internal val SampleBodyReportState = BodyReportUiState(
    isLoading = false,
    check = BodyMeasurement(
        id = 1,
        measuredOn = LocalDate.of(2026, 10, 5),
        weightKg = 78.4, bodyFatPercent = 18.5, muscleMassKg = 35.1, bmrKcal = 1750, visceralFat = 6.0,
        source = BodyMeasurement.Source.Report,
        details = ReportDetails(
            fatMassKg = 14.5, fatFreeMassKg = 63.9, bodyWaterL = 46.6, bmi = 25.6, waistHipRatio = 0.88, fitnessScore = 78,
            muscleControlKg = 0.0, fatControlKg = -2.6,
            ranges = mapOf(
                BodyMetric.Weight to NormalRange(56.7, 76.7), BodyMetric.Muscle to NormalRange(29.5, 36.1),
                BodyMetric.FatMass to NormalRange(8.0, 16.0), BodyMetric.BodyWater to NormalRange(40.1, 49.0),
                BodyMetric.FatFreeMass to NormalRange(54.6, 66.7), BodyMetric.Bmi to NormalRange(18.5, 25.0),
                BodyMetric.BodyFat to NormalRange(10.0, 20.0), BodyMetric.WaistHip to NormalRange(0.8, 0.9),
                BodyMetric.Visceral to NormalRange(null, 10.0), BodyMetric.Bmr to NormalRange(1600.0, 1900.0),
            ),
            segments = listOf(
                SegmentValues(BodySegment.RightArm, 3.62, Rating.Normal, 17.9, 0.8, Rating.Normal),
                SegmentValues(BodySegment.LeftArm, 3.51, Rating.Normal, 18.6, 0.8, Rating.Normal),
                SegmentValues(BodySegment.Trunk, 28.0, Rating.Normal, 20.3, 7.4, Rating.Over),
                SegmentValues(BodySegment.RightLeg, 9.40, Rating.Normal, 16.1, 2.0, Rating.Normal),
                SegmentValues(BodySegment.LeftLeg, 9.31, Rating.Under, 16.4, 2.0, Rating.Normal),
            ),
        ),
    ),
)

@PreviewScreens
@Composable
private fun BodyReportScreenPreview() = SetwiseScreenPreview {
    BodyReportScreen(uiState = SampleBodyReportState, onBack = {}, onDeleteClick = {}, onDeleteDismiss = {}, onConfirmDelete = {})
}
