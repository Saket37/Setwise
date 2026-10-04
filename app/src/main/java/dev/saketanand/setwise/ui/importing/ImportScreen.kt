package dev.saketanand.setwise.ui.importing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.ai.WorkoutImporter
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTag
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTopAppBar
import java.time.format.DateTimeFormatter
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ImportScreenRoot(sharedText: String, onBack: () -> Unit, onOpenHistory: () -> Unit) {
    val viewModel: ImportViewModel = koinViewModel { parametersOf(sharedText) }
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    ImportScreen(
        uiState = uiState,
        initialText = sharedText,
        onRead = viewModel::read,
        onImport = viewModel::import,
        onBack = dropUnlessResumed(block = onBack),
        onOpenHistory = dropUnlessResumed(block = onOpenHistory),
    )
}

/**
 * "Import from Strong": share a workout from Strong to Setwise (or paste its shared text, one or
 * several), check what was read and how each exercise maps, then import.
 */
@Composable
fun ImportScreen(
    uiState: ImportUiState,
    initialText: String,
    onRead: (String) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = rememberTextFieldState(initialText)
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SetwiseTopAppBar(title = stringResource(R.string.import_title), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            val result = uiState.result
            if (result != null) {
                item(key = "done") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        Text(
                            text = pluralStringResource(R.plurals.import_done_workouts, result.workouts, result.workouts) + " " +
                                pluralStringResource(R.plurals.import_done_sets, result.sets, result.sets) +
                                if (result.newExercises > 0) " " + pluralStringResource(R.plurals.import_done_new, result.newExercises, result.newExercises) else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        SetwiseButton(text = stringResource(R.string.import_see_history), onClick = onOpenHistory, modifier = Modifier.fillMaxWidth())
                    }
                }
                return@LazyColumn
            }
            item(key = "how") {
                Text(stringResource(R.string.import_how), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "text") {
                val hint = stringResource(R.string.import_paste_hint)
                BasicTextField(
                    state = text,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 5, maxHeightInLines = 10),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
                        .padding(14.dp)
                        .semantics { contentDescription = hint },
                    decorator = { inner ->
                        Box {
                            if (text.text.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    },
                )
            }
            item(key = "read") {
                SetwiseButton(
                    text = stringResource(R.string.import_read),
                    onClick = { onRead(text.text.toString()) },
                    style = if (uiState.plan == null) SetwiseButtonStyle.Filled else SetwiseButtonStyle.Tonal,
                    size = SetwiseButtonSize.Medium,
                    enabled = text.text.isNotBlank() && !uiState.isReading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            when {
                uiState.isReading -> item(key = "reading") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.import_reading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                uiState.nothingFound -> item(key = "none") { Problem(stringResource(R.string.import_nothing_found)) }
                uiState.failed -> item(key = "failed") { Problem(stringResource(R.string.import_failed)) }
            }
            uiState.plan?.let { plan ->
                items(plan.workouts, key = { it.shared.startedAt.toString() + it.shared.name }) { workout -> PlannedWorkoutCard(workout) }
                item(key = "note") {
                    Text(stringResource(R.string.import_duration_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        uiState.plan?.takeIf { uiState.result == null }?.let { plan ->
            SetwiseButton(
                text = if (plan.toImport.isEmpty()) {
                    stringResource(R.string.import_nothing_new)
                } else {
                    pluralStringResource(R.plurals.import_button, plan.toImport.size, plan.toImport.size)
                },
                onClick = onImport,
                enabled = plan.toImport.isNotEmpty() && !uiState.isImporting,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun Problem(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

/** "Evening Workout · Wed 30 Sep, 8:01 pm · 6 exercises, 18 sets", and each exercise's match. */
@Composable
private fun PlannedWorkoutCard(workout: WorkoutImporter.PlannedWorkout) {
    val locale = currentLocale()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(workout.shared.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            if (workout.alreadyImported) SetwiseTag(text = stringResource(R.string.import_already))
        }
        Text(
            text = workout.shared.startedAt.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy, h:mm a", locale)) + " · " +
                pluralStringResource(R.plurals.import_exercises, workout.exercises.size, workout.exercises.size) + ", " +
                pluralStringResource(R.plurals.import_sets, workout.shared.setCount, workout.shared.setCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        workout.exercises.forEach { exercise ->
            Column(modifier = Modifier.heightIn(min = 36.dp)) {
                Text(exercise.shared.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = exercise.match?.let { stringResource(R.string.import_matched, it.name) } ?: stringResource(R.string.import_new_exercise),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (exercise.match == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
