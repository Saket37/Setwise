package dev.saketanand.setwise.ui.exercises

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.ai.SuggestionSource
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.ui.ObserveAsEvents
import dev.saketanand.setwise.ui.designsystem.components.IconTile
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseFilterChip
import dev.saketanand.setwise.ui.designsystem.components.SetwiseStepper
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTextField
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTopAppBar
import dev.saketanand.setwise.ui.designsystem.preview.PreviewScreens
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.toClockLabel
import kotlin.time.Duration.Companion.seconds
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.CreateExercise].
 * @param onExerciseCreated Created, or a library exercise chosen: back to the picker with it.
 */
@Composable
fun CreateExerciseScreenRoot(
    onExerciseCreated: (exerciseId: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: CreateExerciseViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val close = dropUnlessResumed(block = onBack)
    BackHandler(onBack = close)
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            // Not through dropUnlessResumed: events arrive from STARTED, where it would ignore them.
            is CreateExerciseEvent.Done -> onExerciseCreated(event.exerciseId)
            // TODO: replace with a snackbar once the screen has a SnackbarHost.
            CreateExerciseEvent.SaveFailed -> Toast.makeText(context, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
    }
    CreateExerciseScreen(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                CreateExerciseAction.OnCloseClick -> close()
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * Design "New exercise": the name, "Already in your library? … Use this" when a similar one
 * exists, then how it's logged, muscle group, equipment and rest, and "Create “Name”".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateExerciseScreen(
    uiState: CreateExerciseUiState,
    onAction: (CreateExerciseAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        SetwiseTopAppBar(
            title = stringResource(if (uiState.isEditing) R.string.edit_exercise else R.string.new_exercise),
            onBack = { onAction(CreateExerciseAction.OnCloseClick) },
            navigationIcon = R.drawable.ic_close,
            navigationContentDescription = stringResource(R.string.close),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SetwiseTextField(
                value = uiState.name,
                onValueChange = { onAction(CreateExerciseAction.OnNameChange(it)) },
                label = stringResource(R.string.exercise_name),
                placeholder = stringResource(R.string.exercise_name_placeholder),
            )
            uiState.match?.let { match ->
                MatchCard(
                    match = match,
                    isNameTaken = uiState.isNameTaken,
                    // Editing: the name is only taken; there's nothing to use instead.
                    onUse = if (uiState.isEditing) null else { { onAction(CreateExerciseAction.OnUseMatchClick) } },
                )
            }
            // Right under the name: the fields below are pre-filled from it.
            uiState.suggestionSource?.let { source ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (source == SuggestionSource.Model) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ai_sparkle),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = stringResource(if (source == SuggestionSource.Model) R.string.suggested_by_model else R.string.suggested_from_name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Editing: its sets were logged this way, so it stays.
            if (!uiState.isEditing) KindChips(selected = uiState.kind, onAction = onAction)
            if (uiState.kind != ExerciseKindOption.Cardio) {
                ChipGroup(stringResource(R.string.muscle_group)) {
                    uiState.muscleGroups.forEach { group ->
                        SetwiseFilterChip(
                            label = group,
                            selected = uiState.muscleGroup == group,
                            onClick = { onAction(CreateExerciseAction.OnMuscleGroupClick(group)) },
                        )
                    }
                }
            }
            ChipGroup(stringResource(R.string.equipment)) {
                CreateExerciseUiState.EQUIPMENT_OPTIONS.forEach { equipment ->
                    SetwiseFilterChip(
                        label = equipment,
                        selected = uiState.equipment == equipment,
                        onClick = { onAction(CreateExerciseAction.OnEquipmentClick(equipment)) },
                    )
                }
            }
            if (uiState.kind != ExerciseKindOption.Cardio) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.rest_between_sets),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    SetwiseStepper(
                        value = uiState.restSec / CreateExerciseUiState.REST_STEP_SEC,
                        label = uiState.restSec.seconds.toClockLabel(),
                        onDecrease = { onAction(CreateExerciseAction.OnRestChange(-1)) },
                        onIncrease = { onAction(CreateExerciseAction.OnRestChange(+1)) },
                        decreaseDescription = stringResource(R.string.decrease_rest),
                        increaseDescription = stringResource(R.string.increase_rest),
                        range = CreateExerciseUiState.REST_STEPS,
                    )
                }
            }
        }
        SetwiseButton(
            text = saveLabel(uiState),
            onClick = { onAction(CreateExerciseAction.OnCreateClick) },
            enabled = uiState.canCreate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        )
    }
}

/** "Logged as": weight × reps, bodyweight, timed or cardio. */
@Composable
private fun KindChips(selected: ExerciseKindOption, onAction: (CreateExerciseAction) -> Unit) {
    ChipGroup(stringResource(R.string.logged_as)) {
        ExerciseKindOption.entries.forEach { kind ->
            SetwiseFilterChip(
                label = stringResource(kind.labelRes()),
                selected = selected == kind,
                onClick = { onAction(CreateExerciseAction.OnKindClick(kind)) },
            )
        }
    }
}

/** "Save" when editing, otherwise "Create “Name”" (or "Create exercise" with no name yet). */
@Composable
@ReadOnlyComposable
private fun saveLabel(uiState: CreateExerciseUiState): String {
    val name = uiState.name.trim()
    return when {
        uiState.isEditing -> stringResource(R.string.save)
        name.isEmpty() -> stringResource(R.string.create_exercise)
        else -> stringResource(R.string.create_exercise_named, name)
    }
}

/** "Already in your library? Bench Press (Dumbbell)   Use this". */
@Composable
private fun MatchCard(match: Exercise, isNameTaken: Boolean, onUse: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon = R.drawable.ic_search, contentColor = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(if (isNameTaken) R.string.name_taken else R.string.already_in_library),
                style = MaterialTheme.typography.bodySmall,
                color = if (isNameTaken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(match.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onUse != null) {
            SetwiseButton(
                text = stringResource(R.string.use_this),
                onClick = onUse,
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chips()
        }
    }
}

private fun ExerciseKindOption.labelRes(): Int = when (this) {
    ExerciseKindOption.WeightReps -> R.string.kind_weight_reps
    ExerciseKindOption.Bodyweight -> R.string.kind_bodyweight
    ExerciseKindOption.Timed -> R.string.kind_timed
    ExerciseKindOption.Cardio -> R.string.kind_cardio
}

// Previews: one per scenario

@PreviewScreens
@Composable
private fun CreateExercisePreview() = SetwiseScreenPreview {
    CreateExerciseScreen(
        uiState = CreateExerciseUiState(
            name = "flat db press",
            muscleGroup = "Chest",
            equipment = "Dumbbell",
            muscleGroups = listOf("Chest", "Back", "Shoulders", "Quads", "Hamstrings", "Biceps", "Triceps", "Core"),
            match = Exercise(1, "Bench Press (Dumbbell)", ExerciseType.STRENGTH, "Chest", "Dumbbell", 120, false, false, null, null, null),
        ),
        onAction = {},
    )
}

@PreviewScreens
@Composable
private fun EditExercisePreview() = SetwiseScreenPreview {
    CreateExerciseScreen(
        uiState = CreateExerciseUiState(
            name = "Bench Press (Dumbbell)",
            muscleGroup = "Chest",
            equipment = "Dumbbell",
            restSec = 90,
            muscleGroups = listOf("Chest", "Back", "Shoulders", "Quads", "Hamstrings", "Biceps", "Triceps", "Core"),
            match = Exercise(1, "Bench Press (Dumbbell)", ExerciseType.STRENGTH, "Chest", "Dumbbell", 120, false, false, null, null, null),
            isNameTaken = true,
            isEditing = true,
        ),
        onAction = {},
    )
}
