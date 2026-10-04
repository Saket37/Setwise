package dev.saketanand.setwise.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.NumberKind
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonDefaults
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseDayPicker
import dev.saketanand.setwise.ui.designsystem.components.SetwiseNumberField
import dev.saketanand.setwise.ui.designsystem.components.SetwiseSettingsGroup
import dev.saketanand.setwise.ui.designsystem.components.SetwiseSettingsRow
import dev.saketanand.setwise.ui.designsystem.components.SetwiseSwitchRow
import dev.saketanand.setwise.ui.designsystem.preview.ScreenPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.toWeightInput
import dev.saketanand.setwise.util.toWeightLabel
import java.time.DayOfWeek
import java.time.format.TextStyle
import org.koin.androidx.compose.koinViewModel
import dev.saketanand.setwise.ui.designsystem.components.SetwiseBodyWeightDialog
import dev.saketanand.setwise.domain.ai.ModelAvailability
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.saketanand.setwise.domain.ai.DownloadFailure
import dev.saketanand.setwise.domain.ai.DownloadState
import dev.saketanand.setwise.domain.model.Sex
import dev.saketanand.setwise.ui.designsystem.components.SetwiseChoiceDialog
import dev.saketanand.setwise.ui.designsystem.components.SetwiseValueDialog
import dev.saketanand.setwise.ui.designsystem.components.ValueKind
import java.text.NumberFormat
import androidx.lifecycle.compose.dropUnlessResumed
import dev.saketanand.setwise.domain.model.UserSettings

/** Destination: [Route.Settings]. */
@Composable
fun SettingsScreenRoot(
    onOpenBody: () -> Unit,
    onOpenImport: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    // Back from elsewhere (or the app): the model's state may have changed.
    LifecycleResumeEffect(Unit) {
        viewModel.onAction(SettingsAction.OnScreenResumed)
        onPauseOrDispose { }
    }
    val openBody = dropUnlessResumed(block = onOpenBody)
    val openImport = dropUnlessResumed(block = onOpenImport)
    SettingsScreen(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                SettingsAction.OnBodyCompositionClick -> openBody()
                SettingsAction.OnImportClick -> openImport()
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * Design "Settings": what onboarding asked, editable. (Rest timer, on-device AI, connected apps
 * and data sections come with their features.)
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        if (uiState.isLoading) return@Column

        SetwiseSettingsGroup(title = stringResource(R.string.settings_profile)) {
            val notSet = stringResource(R.string.not_set)
            SetwiseSettingsRow(
                label = stringResource(R.string.profile_name),
                value = uiState.name ?: notSet,
                onClick = { onAction(SettingsAction.OnNameClick) },
            )
            SetwiseSettingsRow(
                label = stringResource(R.string.profile_age),
                value = uiState.age?.toString() ?: notSet,
                onClick = { onAction(SettingsAction.OnAgeClick) },
            )
            SetwiseSettingsRow(
                label = stringResource(R.string.profile_sex),
                value = uiState.sex?.let { stringResource(it.labelRes()) } ?: notSet,
                onClick = { onAction(SettingsAction.OnSexClick) },
            )
            SetwiseSettingsRow(
                label = stringResource(R.string.profile_height),
                value = uiState.heightCm?.let { stringResource(R.string.height_cm, it.toWeightLabel(currentLocale())) } ?: notSet,
                onClick = { onAction(SettingsAction.OnHeightClick) },
            )
            SetwiseSettingsRow(
                label = stringResource(R.string.body_weight),
                value = uiState.bodyWeightKg?.let { stringResource(R.string.weight_kg, it.toWeightLabel(currentLocale())) }
                    ?: notSet,
                onClick = { onAction(SettingsAction.OnBodyWeightClick) },
            )
            SetwiseSettingsRow(
                label = stringResource(R.string.body_composition),
                value = uiState.bmr?.let { stringResource(R.string.bmr_kcal, NumberFormat.getIntegerInstance(currentLocale()).format(it.kcal)) }
                    ?: stringResource(R.string.body_composition_add),
                supporting = stringResource(R.string.body_composition_detail),
                onClick = { onAction(SettingsAction.OnBodyCompositionClick) },
                showDivider = false,
            )
        }
        SetwiseSettingsGroup(title = stringResource(R.string.settings_training)) {
            SetwiseSettingsRow(
                label = stringResource(R.string.training_days),
                value = trainingDaysLabel(uiState.trainingDays),
                onClick = { onAction(SettingsAction.OnTrainingDaysClick) },
            )
            SetwiseSwitchRow(
                label = stringResource(R.string.ask_about_unlogged_days),
                supporting = stringResource(R.string.ask_about_unlogged_days_detail),
                checked = uiState.askAboutUnloggedDays,
                onCheckedChange = { onAction(SettingsAction.OnAskAboutUnloggedDaysChange(it)) },
            )
            SetwiseSettingsRow(
                label = stringResource(R.string.settings_weekly_summary),
                value = stringResource(if (uiState.isWeeklySummaryClosed) R.string.settings_weekly_summary_show else R.string.settings_weekly_summary_on_home),
                supporting = stringResource(R.string.settings_weekly_summary_detail),
                onClick = { if (uiState.isWeeklySummaryClosed) onAction(SettingsAction.OnShowWeeklySummary) },
                showDivider = false,
            )
        }
        SetwiseSettingsGroup(title = stringResource(R.string.settings_rest_timer)) {
            SetwiseSettingsRow(
                label = stringResource(R.string.default_rest),
                value = uiState.restSecOverride?.let { restLabel(it) } ?: stringResource(R.string.rest_per_exercise),
                onClick = { onAction(SettingsAction.OnDefaultRestClick) },
            )
            SetwiseSwitchRow(
                label = stringResource(R.string.rest_sound),
                checked = uiState.restSound,
                onCheckedChange = { onAction(SettingsAction.OnRestSoundChange(it)) },
            )
            SetwiseSwitchRow(
                label = stringResource(R.string.rest_vibrate),
                checked = uiState.restVibrate,
                onCheckedChange = { onAction(SettingsAction.OnRestVibrateChange(it)) },
            )
            SetwiseSwitchRow(
                label = stringResource(R.string.workout_notification),
                supporting = stringResource(R.string.workout_notification_detail),
                checked = uiState.workoutNotification,
                onCheckedChange = { onAction(SettingsAction.OnWorkoutNotificationChange(it)) },
                showDivider = false,
            )
        }
        SetwiseSettingsGroup(title = stringResource(R.string.settings_data)) {
            SetwiseSettingsRow(
                label = stringResource(R.string.import_title),
                value = "",
                supporting = stringResource(R.string.import_settings_detail),
                onClick = { onAction(SettingsAction.OnImportClick) },
                showDivider = false,
            )
        }
        SetwiseSettingsGroup(title = stringResource(R.string.settings_ai)) {
            AiRow(ai = uiState.ai, onDownload = { onAction(SettingsAction.OnDownloadModelClick) })
        }
    }

    when (val editor = uiState.editor) {
        is SettingsEditor.BodyWeight -> SetwiseBodyWeightDialog(
            current = uiState.bodyWeightKg,
            isInvalid = editor.isInvalid,
            onSave = { onAction(SettingsAction.OnSaveBodyWeight(it)) },
            onDismiss = { onAction(SettingsAction.OnDismissEditor) },
            onRemove = if (uiState.bodyWeightKg != null) {
                { onAction(SettingsAction.OnRemoveBodyWeight) }
            } else {
                null
            },
        )
        is SettingsEditor.TrainingDays -> TrainingDaysDialog(selected = editor.selected, onAction = onAction)
        SettingsEditor.Name -> SetwiseValueDialog(
            title = stringResource(R.string.profile_name),
            current = uiState.name.orEmpty(),
            kind = ValueKind.Text,
            placeholder = stringResource(R.string.profile_name_hint),
            onSave = { onAction(SettingsAction.OnSaveName(it)) },
            onDismiss = { onAction(SettingsAction.OnDismissEditor) },
            onRemove = uiState.name?.let { { onAction(SettingsAction.OnRemoveProfileValue(editor)) } },
        )
        is SettingsEditor.Age -> SetwiseValueDialog(
            title = stringResource(R.string.profile_age),
            current = uiState.age?.toString().orEmpty(),
            kind = ValueKind.Integer,
            unit = stringResource(R.string.unit_years),
            placeholder = "30",
            errorMessage = stringResource(R.string.profile_age_invalid).takeIf { editor.isInvalid },
            onSave = { onAction(SettingsAction.OnSaveAge(it)) },
            onDismiss = { onAction(SettingsAction.OnDismissEditor) },
            onRemove = uiState.age?.let { { onAction(SettingsAction.OnRemoveProfileValue(editor)) } },
        )
        is SettingsEditor.Height -> SetwiseValueDialog(
            title = stringResource(R.string.profile_height),
            current = uiState.heightCm?.toWeightInput().orEmpty(),
            kind = ValueKind.Decimal,
            unit = stringResource(R.string.unit_cm),
            placeholder = "175",
            errorMessage = stringResource(R.string.profile_height_invalid).takeIf { editor.isInvalid },
            onSave = { onAction(SettingsAction.OnSaveHeight(it)) },
            onDismiss = { onAction(SettingsAction.OnDismissEditor) },
            onRemove = uiState.heightCm?.let { { onAction(SettingsAction.OnRemoveProfileValue(editor)) } },
        )
        SettingsEditor.DefaultRest -> SetwiseChoiceDialog(
            title = stringResource(R.string.default_rest),
            message = stringResource(R.string.default_rest_detail),
            options = listOf<Pair<Int?, String>>(null to stringResource(R.string.rest_per_exercise)) +
                UserSettings.REST_CHOICES.map { it to restLabel(it) },
            selected = uiState.restSecOverride,
            onSelect = { onAction(SettingsAction.OnSaveDefaultRest(it)) },
            onDismiss = { onAction(SettingsAction.OnDismissEditor) },
        )
        SettingsEditor.SexChoice -> SetwiseChoiceDialog(
            title = stringResource(R.string.profile_sex),
            message = stringResource(R.string.profile_sex_detail),
            options = Sex.entries.map { it to stringResource(it.labelRes()) },
            selected = uiState.sex,
            onSelect = { onAction(SettingsAction.OnSaveSex(it)) },
            onDismiss = { onAction(SettingsAction.OnDismissEditor) },
            onRemove = uiState.sex?.let { { onAction(SettingsAction.OnRemoveProfileValue(editor)) } },
        )
        null -> Unit
    }
}

/** "On-device AI   Ready / Download / Downloading 42% / Not on this phone", and what it means. */
@Composable
private fun AiRow(ai: AiStatusUi, onDownload: () -> Unit) {
    val download = ai.download
    val (value, supporting) = when {
        ai.availability == ModelAvailability.Ready -> R.string.ai_ready to R.string.ai_ready_detail
        download is DownloadState.Failed -> R.string.ai_try_again to when (download.kind) {
            DownloadFailure.NotEnoughSpace -> R.string.ai_download_no_space
            DownloadFailure.NeedsSystemUpdate -> R.string.ai_download_needs_update
            DownloadFailure.Other -> R.string.ai_download_failed
        }
        download is DownloadState.WaitingToStart -> R.string.ai_waiting to R.string.ai_waiting_detail
        download is DownloadState.Downloading || ai.availability == ModelAvailability.Downloading ->
            R.string.ai_downloading to R.string.ai_downloading_detail
        ai.availability == ModelAvailability.Downloadable -> R.string.ai_download to R.string.ai_downloadable_detail
        ai.availability == ModelAvailability.Unavailable -> R.string.ai_unavailable to R.string.ai_unavailable_detail
        else -> R.string.ai_checking to null
    }
    val percent = (download as? DownloadState.Downloading)?.percent
    val valueText = if (ai.availability != ModelAvailability.Ready && percent != null) {
        stringResource(R.string.ai_downloading_percent, percent)
    } else {
        stringResource(value)
    }
    SetwiseSettingsRow(
        label = stringResource(R.string.ai_on_device),
        value = valueText,
        supporting = supporting?.let { stringResource(it) },
        // Only a download is something to do here; otherwise the row just informs.
        onClick = { if (ai.canDownload) onDownload() },
        showDivider = false,
    )
}

/** "Mon, Wed, Fri", "Every day" or "Not set". */
@Composable
private fun trainingDaysLabel(days: Set<DayOfWeek>): String {
    val locale = currentLocale()
    return when (days.size) {
        0 -> stringResource(R.string.not_set)
        7 -> stringResource(R.string.every_day)
        else -> days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }
}

@Composable
private fun TrainingDaysDialog(selected: Set<DayOfWeek>, onAction: (SettingsAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(SettingsAction.OnDismissEditor) },
        title = { Text(stringResource(R.string.training_days)) },
        text = { SetwiseDayPicker(selected = selected, onToggle = { onAction(SettingsAction.OnDayToggle(it)) }) },
        confirmButton = { DialogButton(R.string.save) { onAction(SettingsAction.OnSaveTrainingDays) } },
        dismissButton = { DialogButton(R.string.cancel) { onAction(SettingsAction.OnDismissEditor) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun DialogButton(text: Int, onClick: () -> Unit) {
    SetwiseButton(text = stringResource(text), onClick = onClick, style = SetwiseButtonStyle.Text, size = SetwiseButtonSize.Medium)
}

// Previews: one per scenario

@ScreenPreviews
@Composable
private fun SettingsScreenPreview() = SetwiseScreenPreview {
    SettingsScreen(
        uiState = SettingsUiState(
            isLoading = false,
            bodyWeightKg = 72.5,
            trainingDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        ),
        onAction = {},
    )
}

@ScreenPreviews
@Composable
private fun SettingsScreenNothingSetPreview() = SetwiseScreenPreview {
    SettingsScreen(uiState = SettingsUiState(isLoading = false), onAction = {})
}

private fun Sex.labelRes() = when (this) {
    Sex.Male -> R.string.sex_male
    Sex.Female -> R.string.sex_female
}

/** "1:30". */
private fun restLabel(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)
