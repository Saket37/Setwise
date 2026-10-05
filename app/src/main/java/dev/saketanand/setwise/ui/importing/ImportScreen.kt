package dev.saketanand.setwise.ui.importing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import dev.saketanand.setwise.domain.ai.ImportReader
import dev.saketanand.setwise.domain.ai.WorkoutImporter
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.SharedExercise
import dev.saketanand.setwise.domain.model.SharedSet
import dev.saketanand.setwise.domain.model.SharedWorkout
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTag
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTopAppBar
import dev.saketanand.setwise.ui.designsystem.preview.PreviewScreens
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.util.toShortDurationLabel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.toKotlinDuration
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ImportScreenRoot(shared: SharedImport, onBack: () -> Unit, onOpenHistory: () -> Unit) {
    val viewModel: ImportViewModel = koinViewModel { parametersOf(shared) }
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val chooseFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readFile(uri.toString())
    }
    val chooseScreenshots = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_SCREENSHOTS)) { uris ->
        viewModel.readImages(uris.map { it.toString() })
    }
    ImportScreen(
        uiState = uiState,
        initialText = shared.text,
        onRead = viewModel::read,
        onChooseFile = { chooseFile.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "application/vnd.ms-excel")) },
        onChooseScreenshots = { chooseScreenshots.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onImport = viewModel::import,
        onBack = dropUnlessResumed(block = onBack),
        onOpenHistory = dropUnlessResumed(block = onOpenHistory),
    )
}

/**
 * Import workouts from a CSV export, screenshots, shared or pasted text: check what was read and
 * how each exercise maps, then import.
 */
@Composable
fun ImportScreen(
    uiState: ImportUiState,
    initialText: String,
    onRead: (String) -> Unit,
    onChooseFile: () -> Unit,
    onChooseScreenshots: () -> Unit,
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
            item(key = "sources") {
                Sources(enabled = !uiState.isReading, onChooseFile = onChooseFile, onChooseScreenshots = onChooseScreenshots)
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
            item(key = "read-notes") { ReadNotes(unreadLines = uiState.unreadLines, byModel = uiState.source == ImportReader.Source.Model) }
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
                // The length, when the source says (CSV, Strong's workout screen); else it's estimated.
                workout.shared.duration?.let { it.toKotlinDuration().toShortDurationLabel() + " · " }.orEmpty() +
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
                // Only these numbers came from the model; the rest Setwise read itself.
                if (exercise.shared.readByModel) {
                    Text(stringResource(R.string.import_exercise_by_model), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

/** Choose a CSV export or screenshots. */
@Composable
private fun Sources(enabled: Boolean, onChooseFile: () -> Unit, onChooseScreenshots: () -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        SetwiseButton(
            text = stringResource(R.string.import_choose_file),
            onClick = onChooseFile,
            style = SetwiseButtonStyle.Tonal,
            size = SetwiseButtonSize.Medium,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        SetwiseButton(
            text = stringResource(R.string.import_choose_screenshots),
            onClick = onChooseScreenshots,
            style = SetwiseButtonStyle.Tonal,
            size = SetwiseButtonSize.Medium,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

/** What to check after reading: lines of a log that weren't read, and workouts the model read. */
@Composable
private fun ReadNotes(unreadLines: Int, byModel: Boolean, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        val style = MaterialTheme.typography.bodyMedium
        if (unreadLines > 0) {
            Text(pluralStringResource(R.plurals.import_unread_lines, unreadLines, unreadLines), style = style, color = MaterialTheme.colorScheme.tertiary)
        }
        if (byModel) Text(stringResource(R.string.import_by_model), style = style, color = MaterialTheme.colorScheme.tertiary)
    }
}

/** The most screenshots read at once. */
private const val MAX_SCREENSHOTS = 20

/** A log read by Gemini Nano: the rows Setwise read itself, the pull-ups the model read. */
internal val SampleImportByModelState = ImportUiState(
    source = ImportReader.Source.Model,
    plan = WorkoutImporter.Plan(
        listOf(
            WorkoutImporter.PlannedWorkout(
                shared = SharedWorkout(
                    name = "Back day",
                    startedAt = LocalDateTime.of(2026, 9, 30, 18, 0),
                    exercises = listOf(
                        SharedExercise("Barbell rows", List(3) { SharedSet(60.0, 10) }),
                        SharedExercise("Pull-ups", listOf(SharedSet(reps = 8)), readByModel = true),
                    ),
                ),
                exercises = listOf(
                    WorkoutImporter.PlannedExercise(
                        SharedExercise("Barbell rows", List(3) { SharedSet(60.0, 10) }),
                        Exercise(1, "Bent Over Row (Barbell)", ExerciseType.STRENGTH, "Back", "Barbell", 120, false, false, null, null, null),
                    ),
                    WorkoutImporter.PlannedExercise(
                        SharedExercise("Pull-ups", listOf(SharedSet(reps = 8)), readByModel = true),
                        Exercise(2, "Pull-up", ExerciseType.BODYWEIGHT, "Back", "Bodyweight", 120, false, false, null, null, null),
                    ),
                ),
                alreadyImported = false,
            ),
        ),
    ),
)

@PreviewScreens
@Composable
private fun ImportScreenByModelPreview() = SetwiseScreenPreview {
    ImportScreen(
        uiState = SampleImportByModelState,
        initialText = "Back day, the 30th\nBarbell rows, 3 sets of 10 with 60\nPull-ups: did 8 at the end",
        onRead = {}, onChooseFile = {}, onChooseScreenshots = {}, onImport = {}, onBack = {}, onOpenHistory = {},
    )
}
