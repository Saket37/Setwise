package dev.saketanand.setwise.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.components.HorizontalGap
import dev.saketanand.setwise.ui.designsystem.components.IconTile
import dev.saketanand.setwise.ui.designsystem.components.SectionLabel
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCard
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCardDefaults
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCardOutline
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTimePickerDialog
import dev.saketanand.setwise.ui.designsystem.theme.numberSmall
import dev.saketanand.setwise.util.toShortTimeLabel
import java.time.LocalTime
import kotlinx.coroutines.launch

/**
 * Artboard 2: "Start a workout" bottom sheet. Empty workout, one row per template, and the
 * start time ("Starts now · 6:42 PM", with "Change" to back-date it).
 * Actions: OnStartEmptyWorkout, OnStartFromTemplate, OnStartTimeChange, OnStartSheetDismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartWorkoutSheet(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }
    val enabled = !uiState.isStartingWorkout && !isClosing

    // Slide the sheet away first, then start: otherwise the next screen opens while the sheet
    // is still up, and it's seen closing when you come back.
    val closeThen: (HomeAction) -> Unit = { action ->
        if (!isClosing) {
            isClosing = true
            scope.launch { sheetState.hide() }.invokeOnCompletion { onAction(action) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { onAction(HomeAction.OnStartSheetDismiss) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.start_a_workout),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.start_sheet_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            EmptyWorkoutRow(enabled = enabled, onClick = { closeThen(HomeAction.OnStartEmptyWorkout) })

            if (uiState.templates.isNotEmpty()) {
                SectionLabel(stringResource(R.string.from_a_template))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.templates.forEach { template ->
                        SheetTemplateRow(
                            template = template,
                            enabled = enabled,
                            onClick = { closeThen(HomeAction.OnStartFromTemplate(template.id)) },
                        )
                    }
                }
            }

            StartTimeRow(
                customStartTime = uiState.customStartTime,
                onChangeClick = { showTimePicker = true },
            )
        }
    }

    if (showTimePicker) {
        SetwiseTimePickerDialog(
            title = stringResource(R.string.pick_start_time),
            initial = uiState.customStartTime ?: LocalTime.now(),
            onConfirm = { time ->
                // A start time in the future makes no sense; treat it as "now".
                onAction(HomeAction.OnStartTimeChange(time.takeIf { it.isBefore(LocalTime.now()) }))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
            isAllowed = { !it.isAfter(LocalTime.now()) },
            notAllowedMessage = stringResource(R.string.start_time_in_future),
        )
    }
}

/** "+ Empty workout · Add exercises as you go", with a dashed border (design). */
@Composable
private fun EmptyWorkoutRow(enabled: Boolean, onClick: () -> Unit) {
    SetwiseListCard(
        onClick = onClick,
        enabled = enabled,
        colors = SetwiseListCardDefaults.transparentColors(),
        outline = SetwiseListCardOutline.Dashed(MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(14.dp),
        horizontalSpacing = 14.dp,
        leadingContent = {
            IconTile(
                icon = R.drawable.ic_add,
                size = 44.dp,
                iconSize = 22.dp,
                cornerRadius = 12.dp,
                contentColor = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = { Text(stringResource(R.string.empty_workout)) },
        supportingContent = { Text(stringResource(R.string.empty_workout_description)) },
    )
}

/** "Push Day · 6 exercises · 20 sets · ~65 min ›" */
@Composable
private fun SheetTemplateRow(template: TemplateUi, enabled: Boolean, onClick: () -> Unit) {
    val detail = buildList {
        add(pluralStringResource(R.plurals.exercise_count, template.exerciseCount, template.exerciseCount))
        if (template.setCount > 0) add(pluralStringResource(R.plurals.set_count, template.setCount, template.setCount))
        template.estimatedMinutes?.let { add(stringResource(R.string.approx_minutes, it)) }
    }.joinToString(" · ")

    SetwiseListCard(
        onClick = onClick,
        enabled = enabled,
        colors = SetwiseListCardDefaults.raisedColors(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        horizontalSpacing = 14.dp,
        headlineContent = { Text(template.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
    )
}

/** "🕑 Starts now · 6:42 PM          Change" */
@Composable
private fun StartTimeRow(customStartTime: LocalTime?, onChangeClick: () -> Unit) {
    val time = customStartTime ?: remember { LocalTime.now() }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_nav_history),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            HorizontalGap(10.dp)
            Text(
                text = stringResource(if (customStartTime == null) R.string.starts_now else R.string.starts_at),
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalGap(4.dp)
            Text(
                text = time.toShortTimeLabel(),
                style = MaterialTheme.typography.numberSmall,
                modifier = Modifier.weight(1f),
            )
            SetwiseButton(
                text = stringResource(R.string.change),
                onClick = onChangeClick,
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
                textStyle = MaterialTheme.typography.titleSmall,
            )
        }
    }
}
