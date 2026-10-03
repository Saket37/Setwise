package dev.saketanand.setwise.ui.summary

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.PrKind
import dev.saketanand.setwise.ui.ObserveAsEvents
import dev.saketanand.setwise.ui.designsystem.components.SectionLabel
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseIconButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseIconButtonDefaults
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTimePickerDialog
import dev.saketanand.setwise.ui.designsystem.preview.ScreenPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.ui.workout.SetKind
import dev.saketanand.setwise.util.toShortDayLabel
import dev.saketanand.setwise.util.toShortTimeLabel
import java.time.LocalDate
import java.time.LocalTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.WorkoutSummary].
 * @param onDone "Done" or ✕: back to where the summary was opened from (Home after Finish).
 */
@Composable
fun WorkoutSummaryScreenRoot(
    onOpenExercise: (exerciseId: Long) -> Unit,
    onDone: () -> Unit,
    viewModel: WorkoutSummaryViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val done = dropUnlessResumed(block = onDone)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            // TODO: replace with a snackbar once the screen has a SnackbarHost.
            WorkoutSummaryEvent.TemplateSaved -> Toast.makeText(context, R.string.template_saved, Toast.LENGTH_SHORT).show()
            WorkoutSummaryEvent.SaveFailed -> Toast.makeText(context, R.string.save_failed, Toast.LENGTH_SHORT).show()
            // Not through dropUnlessResumed: events arrive from STARTED, where it would ignore them.
            WorkoutSummaryEvent.Closed -> onDone()
        }
    }

    WorkoutSummaryScreen(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                WorkoutSummaryAction.OnDoneClick -> done()
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * Artboard 9 "Workout complete": name and times (editable), Duration / Volume / Sets / Exercises,
 * personal records, the exercises done, "Save as template" and "Done".
 * (The on-device insight card comes with milestone 11, calories with milestone 10.)
 */
@Composable
fun WorkoutSummaryScreen(
    uiState: WorkoutSummaryUiState,
    onAction: (WorkoutSummaryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (!uiState.isLoading) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SummaryHeader(uiState = uiState, onAction = onAction)
                SummaryStats(uiState = uiState)
                if (uiState.records.isNotEmpty()) PersonalRecordsSection(records = uiState.records)
                if (uiState.exercises.isNotEmpty()) ExercisesSection(exercises = uiState.exercises)
            }
            SummaryButtons(uiState = uiState, onAction = onAction)
        }
    }

    uiState.editTimes?.let { edit ->
        EditTimesDialog(edit = edit, onAction = onAction)
        edit.picking?.let { field ->
            SetwiseTimePickerDialog(
                title = stringResource(if (field == TimeField.Start) R.string.pick_start_time else R.string.end_time),
                initial = if (field == TimeField.Start) edit.start else edit.end,
                onConfirm = { onAction(WorkoutSummaryAction.OnTimePicked(it)) },
                onDismiss = { onAction(WorkoutSummaryAction.OnTimePickerDismiss) },
            )
        }
    }
}

/** "WORKOUT COMPLETE ✕", the name, "Fri, 2 Oct · 6:42 PM – 7:51 PM ✎". */
@Composable
private fun SummaryHeader(uiState: WorkoutSummaryUiState, onAction: (WorkoutSummaryAction) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(
                text = stringResource(R.string.workout_complete),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            SetwiseIconButton(
                icon = R.drawable.ic_close,
                contentDescription = stringResource(R.string.close),
                onClick = { onAction(WorkoutSummaryAction.OnDoneClick) },
                size = 44.dp,
                iconSize = 20.dp,
                colors = SetwiseIconButtonDefaults.plainColors(),
            )
        }
        Text(
            text = uiState.name,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        if (uiState.date != null && uiState.startTime != null && uiState.endTime != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(
                        R.string.workout_date_and_times,
                        uiState.date.toShortDayLabel(),
                        uiState.startTime.toShortTimeLabel(),
                        uiState.endTime.toShortTimeLabel(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SetwiseIconButton(
                    icon = R.drawable.ic_edit,
                    contentDescription = stringResource(R.string.edit_workout_times),
                    onClick = { onAction(WorkoutSummaryAction.OnEditTimesClick) },
                    size = 32.dp,
                    iconSize = 14.dp,
                    colors = SetwiseIconButtonDefaults.tonalColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        }
    }
}

/** "Save as template" (only for workouts not started from a template) and "Done". */
@Composable
private fun SummaryButtons(uiState: WorkoutSummaryUiState, onAction: (WorkoutSummaryAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (uiState.canSaveAsTemplate) {
            SetwiseButton(
                text = stringResource(if (uiState.isTemplateSaved) R.string.saved_as_template else R.string.save_as_template),
                onClick = { onAction(WorkoutSummaryAction.OnSaveAsTemplateClick) },
                style = SetwiseButtonStyle.Outlined,
                startIcon = if (uiState.isTemplateSaved) R.drawable.ic_check else null,
                enabled = !uiState.isTemplateSaved,
                textStyle = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
        }
        SetwiseButton(
            text = stringResource(R.string.done),
            onClick = { onAction(WorkoutSummaryAction.OnDoneClick) },
            modifier = Modifier.weight(1f),
        )
    }
}

// Previews: one per scenario

@ScreenPreviews
@Composable
private fun WorkoutSummaryScreenPreview() = SetwiseScreenPreview {
    WorkoutSummaryScreen(uiState = previewState, onAction = {})
}

@ScreenPreviews
@Composable
private fun WorkoutSummaryFromTemplatePreview() = SetwiseScreenPreview {
    WorkoutSummaryScreen(uiState = previewState.copy(canSaveAsTemplate = false, records = emptyList()), onAction = {})
}

private val previewState = WorkoutSummaryUiState(
    isLoading = false,
    name = "Push Day",
    date = LocalDate.of(2026, 10, 2),
    startTime = LocalTime.of(18, 42),
    endTime = LocalTime.of(19, 51),
    duration = 69.minutes + 14.seconds,
    volumeKg = 8_420.0,
    completedSets = 18,
    exerciseCount = 3,
    records = listOf(
        RecordUi("Bench Press (Barbell)", PrKind.Weight, SetKind.WeightReps, PreviousSet(62.5, 8), PreviousSet(60.0, 8)),
        RecordUi("Pull-up", PrKind.Reps, SetKind.Bodyweight, PreviousSet(null, 12), PreviousSet(null, 10)),
    ),
    exercises = listOf(
        SummaryExerciseUi(1, "Bench Press (Barbell)", 4, "62.5 × 8"),
        SummaryExerciseUi(2, "Overhead Press (Barbell)", 3, "40 × 7"),
        SummaryExerciseUi(3, "Pull-up", 3, "12"),
    ),
    canSaveAsTemplate = true,
)
