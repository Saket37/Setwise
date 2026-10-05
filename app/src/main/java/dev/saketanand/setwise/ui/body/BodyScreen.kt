package dev.saketanand.setwise.ui.body

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.BmrEstimate
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.Sex
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.NumberKind
import dev.saketanand.setwise.ui.designsystem.components.SetwiseBarChart
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseIconButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseIconButtonDefaults
import dev.saketanand.setwise.ui.designsystem.components.SetwiseNumberField
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTopAppBar
import dev.saketanand.setwise.ui.designsystem.theme.numberLarge
import dev.saketanand.setwise.util.toShortDayLabel
import dev.saketanand.setwise.util.toWeightInput
import dev.saketanand.setwise.util.toWeightLabel
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import org.koin.androidx.compose.koinViewModel

@Composable
fun BodyScreenRoot(onBack: () -> Unit, viewModel: BodyViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // A report photo: taken with the camera (into the app's cache), or chosen. Neither is kept.
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = cameraUri
        if (taken && uri != null) viewModel.onAction(BodyAction.OnReportPhoto(uri))
    }
    val choosePhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onAction(BodyAction.OnReportPhoto(uri.toString()))
    }
    BodyScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onBack = dropUnlessResumed(block = onBack),
        onTakePhoto = {
            val file = File(context.cacheDir, "reports").apply { mkdirs() }.resolve("report.jpg")
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            cameraUri = uri.toString()
            takePhoto.launch(uri)
        },
        onChoosePhoto = { choosePhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}

/**
 * Body composition: the newest values and BMR (and where it's from), weight and body fat over
 * the last checks, "Scan a report" (camera or a photo) and "Add manually", then every check.
 */
@Composable
fun BodyScreen(
    uiState: BodyUiState,
    onAction: (BodyAction) -> Unit,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SetwiseTopAppBar(title = stringResource(R.string.body_composition), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "latest") { LatestCard(uiState, locale) }
            item(key = "scan") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.body_scan_detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SetwiseButton(
                            text = stringResource(R.string.body_take_photo),
                            onClick = onTakePhoto,
                            size = SetwiseButtonSize.Medium,
                            enabled = !uiState.isReading,
                            modifier = Modifier.weight(1f),
                        )
                        SetwiseButton(
                            text = stringResource(R.string.body_choose_photo),
                            onClick = onChoosePhoto,
                            style = SetwiseButtonStyle.Tonal,
                            size = SetwiseButtonSize.Medium,
                            enabled = !uiState.isReading,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SetwiseButton(
                        text = stringResource(R.string.body_add_manually),
                        onClick = { onAction(BodyAction.OnAddManually) },
                        style = SetwiseButtonStyle.Outlined,
                        size = SetwiseButtonSize.Medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    when {
                        uiState.isReading -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.body_reading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        uiState.readFailed -> Text(
                            stringResource(R.string.body_read_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
            if (uiState.weightTrend.size >= 2) item(key = "weight") { TrendCard(stringResource(R.string.body_weight_trend), uiState.weightTrend, "kg", locale) }
            if (uiState.bodyFatTrend.size >= 2) item(key = "fat") { TrendCard(stringResource(R.string.body_fat_trend), uiState.bodyFatTrend, "%", locale) }
            if (uiState.history.isNotEmpty()) {
                item(key = "history-title") {
                    Text(stringResource(R.string.body_history), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                }
                items(uiState.history, key = { it.id }) { check -> CheckRow(check, locale, onDelete = { onAction(BodyAction.OnDelete(check.id)) }) }
            }
        }
    }
    uiState.editor?.let { editor -> key(editor.key) { CheckSheet(editor, onAction) } }
}

@Composable
private fun LatestCard(uiState: BodyUiState, locale: Locale) {
    val latest = uiState.latest
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Value(latest.weightKg?.toWeightLabel(locale), stringResource(R.string.body_weight_kg_label), Modifier.weight(1f))
            Value(latest.bodyFatPercent?.toWeightLabel(locale), stringResource(R.string.body_fat_label), Modifier.weight(1f))
            Value(latest.muscleMassKg?.toWeightLabel(locale), stringResource(R.string.body_muscle_label), Modifier.weight(1f))
            Value(latest.visceralFat?.toWeightLabel(locale), stringResource(R.string.body_visceral_label), Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
        val bmr = uiState.bmr
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = bmr?.let { stringResource(R.string.bmr_kcal, NumberFormat.getIntegerInstance(locale).format(it.kcal)) } ?: stringResource(R.string.body_bmr_unknown),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    when (bmr?.source) {
                        BmrEstimate.Source.Report -> R.string.body_bmr_from_report
                        BmrEstimate.Source.BodyFat -> R.string.body_bmr_from_fat
                        BmrEstimate.Source.Profile -> R.string.body_bmr_from_profile
                        null -> R.string.body_bmr_how
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Value(value: String?, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value ?: "–", style = MaterialTheme.typography.numberLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun TrendCard(title: String, values: ImmutableList<Double?>, unit: String, locale: Locale) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${values.last()?.toWeightLabel(locale)} $unit", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        SetwiseBarChart(values = values, contentDescription = "$title: " + values.joinToString { "${it?.toWeightLabel(locale)} $unit" })
    }
}

@Composable
private fun CheckRow(check: BodyMeasurement, locale: Locale, onDelete: () -> Unit) {
    val parts = listOfNotNull(
        check.weightKg?.let { "${it.toWeightLabel(locale)} kg" },
        check.bodyFatPercent?.let { "${it.toWeightLabel(locale)}% fat" },
        check.muscleMassKg?.let { "${it.toWeightLabel(locale)} kg muscle" },
        check.bmrKcal?.let { "BMR $it" },
    )
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = check.measuredOn.toShortDayLabel(locale) + if (check.source == BodyMeasurement.Source.Report) " · " + stringResource(R.string.body_source_report) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            SetwiseIconButton(
                icon = R.drawable.ic_delete,
                contentDescription = stringResource(R.string.body_delete_check),
                onClick = onDelete,
                size = 40.dp,
                iconSize = 18.dp,
                colors = SetwiseIconButtonDefaults.plainColors(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
}

/** Check and save: the values (read from a report, or blank), the day, what fills the profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckSheet(editor: BodyEditor, onAction: (BodyAction) -> Unit) {
    val locale = currentLocale()
    val weight = rememberTextFieldState(editor.weightKg?.toWeightInput().orEmpty())
    val fat = rememberTextFieldState(editor.bodyFatPercent?.toWeightInput().orEmpty())
    val muscle = rememberTextFieldState(editor.muscleMassKg?.toWeightInput().orEmpty())
    val bmr = rememberTextFieldState(editor.bmrKcal?.toString().orEmpty())
    val visceral = rememberTextFieldState(editor.visceralFat?.toWeightInput().orEmpty())
    var pickingDay by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { onAction(BodyAction.OnDismissEditor) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        // Scrolls: with the keyboard open, the sheet only has the space above it. A plain Column
        // squeezed its last child, Save, to a sliver (#123).
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (editor.fromReport) R.string.body_read_title else R.string.body_add_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (editor.fromReport) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_ai_sparkle), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(if (editor.byModel) R.string.body_read_by_model else R.string.body_read_on_device),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(editor.day.toShortDayLabel(locale), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                SetwiseButton(text = stringResource(R.string.change), onClick = { pickingDay = true }, style = SetwiseButtonStyle.Text, size = SetwiseButtonSize.Medium)
            }
            Field(stringResource(R.string.body_weight_field), weight, "kg", NumberKind.Decimal)
            Field(stringResource(R.string.body_fat_field), fat, "%", NumberKind.Decimal)
            Field(stringResource(R.string.body_muscle_field), muscle, "kg", NumberKind.Decimal)
            Field(stringResource(R.string.body_bmr_field), bmr, "kcal", NumberKind.Integer)
            Field(stringResource(R.string.body_visceral_field), visceral, null, NumberKind.Decimal)
            val profileParts = listOfNotNull(
                editor.profileHeightCm?.let { stringResource(R.string.height_cm, it.toWeightLabel(locale)) },
                editor.profileAge?.let { stringResource(R.string.body_profile_age, it) },
                editor.profileSex?.let { stringResource(if (it == Sex.Male) R.string.sex_male else R.string.sex_female).lowercase(locale) },
            )
            if (profileParts.isNotEmpty()) {
                Text(stringResource(R.string.body_fills_profile, profileParts.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editor.isInvalid) {
                Text(stringResource(R.string.body_invalid), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            SetwiseButton(
                text = stringResource(R.string.save),
                onClick = {
                    onAction(BodyAction.OnSave(weight.text.toString(), fat.text.toString(), muscle.text.toString(), bmr.text.toString(), visceral.text.toString()))
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (pickingDay) {
        val latestMillis = editor.latestDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = editor.day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = DatePickerDefaults.YearRange.first..editor.latestDay.year,
            selectableDates = remember(latestMillis) {
                // Up to today: a reading can't be for a day that hasn't happened (#128).
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= latestMillis
                    override fun isSelectableYear(year: Int) = year <= editor.latestDay.year
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDay = false },
            confirmButton = {
                SetwiseButton(text = stringResource(R.string.ok), onClick = {
                    picker.selectedDateMillis?.let { onAction(BodyAction.OnDayChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())) }
                    pickingDay = false
                }, style = SetwiseButtonStyle.Text, size = SetwiseButtonSize.Medium)
            },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun Field(label: String, state: androidx.compose.foundation.text.input.TextFieldState, unit: String?, kind: NumberKind) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        SetwiseNumberField(
            state = state,
            contentDescription = label,
            kind = kind,
            imeAction = ImeAction.Next,
            maxLength = 6,
            minHeight = 44.dp,
            modifier = Modifier.width(96.dp),
        )
        Text(unit.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
    }
}
