package dev.saketanand.setwise.ui.exercises

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseSearchField
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTopAppBar
import dev.saketanand.setwise.ui.designsystem.preview.PreviewScreens
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.navigation.Route
import kotlinx.collections.immutable.persistentListOf
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.ExercisePicker].
 * @param onExercisesPicked Returns the selection to the caller.
 * @param createdExerciseId Result from "New exercise": selected here; null when there is none.
 * @param onCreatedExerciseConsumed Call after selecting it, so it isn't handled twice.
 * @param onCreateExercise "Create new", pre-filled with the search text.
 */
@Composable
fun ExercisePickerScreenRoot(
    createdExerciseId: Long?,
    onCreatedExerciseConsumed: () -> Unit,
    onExercisesPicked: (exerciseIds: List<Long>) -> Unit,
    onCreateExercise: (initialName: String) -> Unit,
    onBack: () -> Unit,
    initialQuery: String = "",
    onOpenExercise: (exerciseId: Long) -> Unit = {},
    viewModel: ExercisePickerViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    // The latest callback, not the one from when the effect started (#35).
    val onCreatedConsumed by rememberUpdatedState(onCreatedExerciseConsumed)
    LaunchedEffect(createdExerciseId) {
        if (createdExerciseId != null) {
            viewModel.onAction(ExercisePickerAction.OnExerciseCreated(createdExerciseId))
            onCreatedConsumed()
        }
    }

    // The search text lives here, not in the ViewModel: the field must change in the same frame
    // as the keystroke (a round trip through a StateFlow can drop letters or move the cursor).
    // It's saved across process death and the ViewModel gets every change.
    val searchState = rememberTextFieldState(initialQuery)
    LaunchedEffect(searchState) {
        snapshotFlow { searchState.text.toString() }
            .collect { viewModel.onAction(ExercisePickerAction.OnQueryChange(it)) }
    }

    // dropUnlessResumed: a double tap (or Add + Back) during the exit animation must not pop
    // the back stack twice.
    val addExercises = dropUnlessResumed { onExercisesPicked(viewModel.state.value.selectedIds) }
    // "zercher" typed (or from the quick log) is created as "Zercher": names start with a capital.
    val createExercise = dropUnlessResumed { onCreateExercise(viewModel.state.value.query.trim().replaceFirstChar { it.titlecase() }) }
    val goBack = dropUnlessResumed(block = onBack)
    val lifecycleOwner = LocalLifecycleOwner.current
    val openExercise: (Long) -> Unit = { id ->
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onOpenExercise(id)
    }

    ExercisePickerScreen(
        uiState = uiState,
        searchState = searchState,
        onAction = { action ->
            when (action) {
                ExercisePickerAction.OnAddClick -> addExercises()
                ExercisePickerAction.OnCreateNewClick -> createExercise()
                ExercisePickerAction.OnBackClick -> goBack()
                is ExercisePickerAction.OnExerciseLongPress -> openExercise(action.exerciseId)
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * Artboard "Add exercise": search, muscle-group chips, Recent + All exercises, multi-select,
 * and "Add N exercises" once something is picked.
 *
 * @param searchState the search field's text (see [ExercisePickerScreenRoot] for why it's here).
 */
@Composable
fun ExercisePickerScreen(
    uiState: ExercisePickerUiState,
    searchState: TextFieldState,
    onAction: (ExercisePickerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Keep the Add button above the keyboard. The app Scaffold already pads for the
            // navigation bar, so only the part of the keyboard above it is added here.
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
    ) {
        SetwiseTopAppBar(
            title = stringResource(R.string.add_exercise),
            onBack = { onAction(ExercisePickerAction.OnBackClick) },
        ) {
            SetwiseButton(
                text = stringResource(R.string.create_new),
                onClick = { onAction(ExercisePickerAction.OnCreateNewClick) },
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
                textStyle = MaterialTheme.typography.titleSmall,
            )
        }

        SetwiseSearchField(
            state = searchState,
            placeholder = stringResource(R.string.search_exercises),
            modifier = Modifier.padding(horizontal = PickerHorizontalPadding),
        )

        MuscleGroupChips(
            muscleGroups = uiState.muscleGroups,
            selected = uiState.selectedMuscleGroup,
            onClick = { onAction(ExercisePickerAction.OnMuscleGroupClick(it)) },
            // The chips' 48dp touch targets add 6dp above and below: 6 + 6 = the design's 12dp gap.
            modifier = Modifier.padding(top = 6.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading -> Unit // first frame only; an empty state here would flash
                uiState.showNoResults -> NoExercisesFound(
                    query = uiState.query.trim(),
                    onCreateClick = { onAction(ExercisePickerAction.OnCreateNewClick) },
                )
                else -> ExerciseList(uiState = uiState, onAction = onAction)
            }
        }

        AnimatedVisibility(
            visible = uiState.selectedCount > 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            // While hiding, the count is already 0; the only way to 0 is from 1, so show that.
            val count = uiState.selectedCount.coerceAtLeast(1)
            SetwiseButton(
                text = pluralStringResource(R.plurals.add_exercises, count, count),
                onClick = { onAction(ExercisePickerAction.OnAddClick) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = PickerHorizontalPadding, end = PickerHorizontalPadding, top = 12.dp, bottom = 24.dp),
            )
        }
    }
}

// Previews: one per scenario

@PreviewScreens
@Composable
private fun ExercisePickerScreenPreview() = SetwiseScreenPreview {
    ExercisePickerScreen(
        uiState = ExercisePickerUiState(
            isLoading = false,
            muscleGroups = previewMuscleGroups,
            recent = previewRecent,
            exercises = previewExercises,
            selectedIds = listOf(1, 2),
        ),
        searchState = rememberTextFieldState(),
        onAction = {},
    )
}

@PreviewScreens
@Composable
private fun ExercisePickerFilteredPreview() = SetwiseScreenPreview {
    ExercisePickerScreen(
        uiState = ExercisePickerUiState(
            isLoading = false,
            muscleGroups = previewMuscleGroups,
            selectedMuscleGroup = "Chest",
            recent = previewRecent,
            exercises = previewExercises.filter { it.muscleGroup == "Chest" },
        ),
        searchState = rememberTextFieldState(),
        onAction = {},
    )
}

@PreviewScreens
@Composable
private fun ExercisePickerNoResultsPreview() = SetwiseScreenPreview {
    ExercisePickerScreen(
        uiState = ExercisePickerUiState(isLoading = false, query = "zercher", muscleGroups = previewMuscleGroups),
        searchState = rememberTextFieldState("zercher"),
        onAction = {},
    )
}

private val previewMuscleGroups = persistentListOf("Back", "Chest", "Shoulders", "Quads", "Core", "Cardio")

private val previewRecent = listOf(
    ExerciseRowUi(1, "Bench Press (Barbell)", "BP", "Chest", "Barbell", LastSetUi("60", 8), isSelected = true),
    ExerciseRowUi(2, "Overhead Press", "OP", "Shoulders", "Barbell", LastSetUi("40", 6), isSelected = true),
    ExerciseRowUi(3, "Pull-up", "PU", "Back", "Bodyweight", LastSetUi(null, 10), isSelected = false),
)

private val previewExercises = listOf(
    ExerciseRowUi(4, "Arnold Press", "AP", "Shoulders", "Dumbbell", null, isSelected = false),
    ExerciseRowUi(1, "Bench Press (Barbell)", "BP", "Chest", "Barbell", null, isSelected = true),
    ExerciseRowUi(5, "Cable Fly", "CF", "Chest", "Cable", null, isSelected = false),
    ExerciseRowUi(6, "Plank", "PL", "Core", null, null, isSelected = false),
    ExerciseRowUi(7, "Treadmill", "TR", "Cardio", "Machine", null, isSelected = false),
)
