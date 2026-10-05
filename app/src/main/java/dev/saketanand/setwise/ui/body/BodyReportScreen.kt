package dev.saketanand.setwise.ui.body

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import dev.saketanand.setwise.ui.designsystem.components.SetwiseSegmentedControl
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTopAppBar
import dev.saketanand.setwise.ui.designsystem.preview.PreviewScreens
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.designsystem.theme.numberLarge
import dev.saketanand.setwise.ui.designsystem.theme.numberMedium
import dev.saketanand.setwise.util.toShortDayLabel
import java.time.LocalDate
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf

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

/** "10.0–20.0%", "56.7–76.7 kg", "below 10" (the unit once, after the high end; none for a level). */
@Composable
private fun rangeLabel(measure: ProgressMeasure, range: NormalRange, locale: Locale): String {
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
private fun RatingLabel(rating: Rating, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(rating.labelRes()),
        style = MaterialTheme.typography.labelMedium,
        color = if (rating == Rating.Normal) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
        modifier = modifier,
    )
}

private fun Rating.labelRes() = when (this) {
    Rating.Under -> R.string.rating_under
    Rating.Normal -> R.string.rating_normal
    Rating.Over -> R.string.rating_over
}

/** Thirds (under | normal | over) and a marker where the value sits; a one-sided range starts at 0. */
@Composable
private fun RangeBar(value: Double, range: NormalRange, rating: Rating) {
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
private fun Segments(segments: List<SegmentValues>, locale: Locale) {
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
private fun SegmentCell(segment: BodySegment, values: SegmentValues, fat: Boolean, locale: Locale, modifier: Modifier = Modifier) {
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
        rating?.let { Text(stringResource(it.labelRes()), style = MaterialTheme.typography.labelMedium, color = if (it == Rating.Normal) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary) }
    }
}

private fun BodySegment.labelRes() = when (this) {
    BodySegment.RightArm -> R.string.segment_right_arm
    BodySegment.LeftArm -> R.string.segment_left_arm
    BodySegment.Trunk -> R.string.segment_trunk
    BodySegment.RightLeg -> R.string.segment_right_leg
    BodySegment.LeftLeg -> R.string.segment_left_leg
}

/** "InBody's suggested change": as printed, to reach its normal ranges. */
@Composable
private fun ControlCard(details: ReportDetails, locale: Locale) {
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
            details.muscleControlKg?.let { ControlValue(measureChange(ProgressMeasure.Muscle, it, locale), stringResource(R.string.body_report_control_muscle), Modifier.weight(1f)) }
            details.fatControlKg?.let { ControlValue(measureChange(ProgressMeasure.FatMass, it, locale), stringResource(R.string.body_report_control_fat), Modifier.weight(1f)) }
        }
        Text(stringResource(R.string.body_report_control_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ControlValue(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.numberLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                BodyMetric.Visceral to NormalRange(null, 10.0), BodyMetric.Bmr to NormalRange(1669.0, 1957.0),
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
