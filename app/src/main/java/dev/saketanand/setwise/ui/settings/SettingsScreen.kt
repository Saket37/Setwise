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

/** Destination: [Route.Settings]. */
@Composable
fun SettingsScreenRoot(
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(uiState = uiState, onAction = viewModel::onAction)
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
            SetwiseSettingsRow(
                label = stringResource(R.string.body_weight),
                value = uiState.bodyWeightKg?.let { stringResource(R.string.weight_kg, it.toWeightLabel(currentLocale())) }
                    ?: stringResource(R.string.not_set),
                onClick = { onAction(SettingsAction.OnBodyWeightClick) },
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
                showDivider = false,
            )
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
        null -> Unit
    }
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
