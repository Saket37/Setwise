package dev.saketanand.setwise.ui.templates

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.ObserveAsEvents
import dev.saketanand.setwise.ui.designsystem.components.SectionLabel
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseConfirmDialog
import dev.saketanand.setwise.ui.designsystem.components.SetwiseFilterChip
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCard
import dev.saketanand.setwise.ui.designsystem.components.SetwiseMenuItem
import dev.saketanand.setwise.ui.designsystem.components.SetwiseOverflowMenu
import dev.saketanand.setwise.ui.designsystem.components.SetwiseStepper
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTextField
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTopAppBar
import dev.saketanand.setwise.ui.designsystem.preview.ScreenPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.navigation.Route
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.TemplateEditor].
 * @param pickedExerciseIds Result from the exercise picker; null when there is none.
 * @param onPickedExercisesConsumed Call after adding the picked exercises.
 * @param onAddExercises Opens the exercise picker.
 * @param onSaved Saved: back to where it was opened from.
 * @param onBack Closed without saving, or the template was deleted.
 */
@Composable
fun TemplateEditorScreenRoot(
    pickedExerciseIds: List<Long>?,
    onPickedExercisesConsumed: () -> Unit,
    onAddExercises: () -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: TemplateEditorViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val addExercises = dropUnlessResumed(block = onAddExercises)

    LaunchedEffect(pickedExerciseIds) {
        if (pickedExerciseIds != null) {
            viewModel.onAction(TemplateEditorAction.OnExercisesPicked(pickedExerciseIds))
            onPickedExercisesConsumed()
        }
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            // Not through dropUnlessResumed: events arrive from STARTED, where it would ignore them.
            TemplateEditorEvent.Saved -> onSaved()
            TemplateEditorEvent.Closed -> onBack()
            // TODO: replace with a snackbar once the screen has a SnackbarHost.
            TemplateEditorEvent.SaveFailed -> Toast.makeText(context, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
    }
    // Back asks first when there are unsaved changes (the ViewModel decides).
    BackHandler { viewModel.onAction(TemplateEditorAction.OnBackClick) }

    TemplateEditorScreen(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                TemplateEditorAction.OnAddExercisesClick -> addExercises()
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * "New template" / "Edit template" with Save: the name, a category chip, then the exercises in
 * order, each with − n sets + and ⋮ (move up, move down, remove), and "Add exercises".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorScreen(
    uiState: TemplateEditorUiState,
    onAction: (TemplateEditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        SetwiseTopAppBar(
            title = stringResource(if (uiState.isNew) R.string.new_template_title else R.string.edit_template),
            onBack = { onAction(TemplateEditorAction.OnBackClick) },
        ) {
            SetwiseButton(
                text = stringResource(R.string.save),
                onClick = { onAction(TemplateEditorAction.OnSaveClick) },
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
                textStyle = MaterialTheme.typography.titleSmall,
                enabled = uiState.canSave,
            )
            if (!uiState.isNew) {
                SetwiseOverflowMenu(
                    contentDescription = stringResource(R.string.template_options),
                    items = listOf(
                        SetwiseMenuItem(
                            label = stringResource(R.string.delete_template),
                            onClick = { onAction(TemplateEditorAction.OnDeleteClick) },
                            isDestructive = true,
                        ),
                    ),
                )
            }
        }
        if (uiState.isLoading) return@Column
        val draft = uiState.draft
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "name") {
                SetwiseTextField(
                    value = draft.name,
                    onValueChange = { onAction(TemplateEditorAction.OnNameChange(it)) },
                    label = stringResource(R.string.template_name),
                    placeholder = stringResource(R.string.template_name_placeholder),
                )
            }
            item(key = "category") {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.template_category),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.categoryOptions.forEach { option ->
                            SetwiseFilterChip(
                                label = option,
                                selected = draft.category == option,
                                onClick = { onAction(TemplateEditorAction.OnCategoryClick(option)) },
                            )
                        }
                    }
                }
            }
            item(key = "exercises-title") {
                SectionLabel(
                    text = stringResource(R.string.exercises),
                    modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
                )
            }
            if (draft.exercises.isEmpty()) {
                item(key = "no-exercises") {
                    Text(
                        text = stringResource(R.string.template_no_exercises),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(draft.exercises, key = { _, it -> it.exerciseId }) { index, exercise ->
                TemplateExerciseRow(
                    exercise = exercise,
                    isFirst = index == 0,
                    isLast = index == draft.exercises.lastIndex,
                    onAction = onAction,
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "add") {
                SetwiseButton(
                    text = stringResource(R.string.add_exercises_button),
                    onClick = { onAction(TemplateEditorAction.OnAddExercisesClick) },
                    style = SetwiseButtonStyle.Outlined,
                    size = SetwiseButtonSize.Medium,
                    startIcon = R.drawable.ic_add,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }

    when (uiState.dialog) {
        TemplateEditorDialog.DiscardChanges -> SetwiseConfirmDialog(
            title = stringResource(R.string.discard_changes_title),
            message = stringResource(R.string.discard_changes_message),
            confirmText = stringResource(R.string.discard),
            dismissText = stringResource(R.string.keep_editing),
            onConfirm = { onAction(TemplateEditorAction.OnConfirmDiscard) },
            onDismiss = { onAction(TemplateEditorAction.OnDialogDismiss) },
            isDestructive = true,
        )
        TemplateEditorDialog.Delete -> SetwiseConfirmDialog(
            title = stringResource(R.string.delete_template_title, uiState.draft.name.ifBlank { stringResource(R.string.edit_template) }),
            message = stringResource(R.string.delete_template_message),
            confirmText = stringResource(R.string.delete),
            onConfirm = { onAction(TemplateEditorAction.OnConfirmDelete) },
            onDismiss = { onAction(TemplateEditorAction.OnDialogDismiss) },
            isDestructive = true,
        )
        null -> Unit
    }
}

/** "Bench Press (Barbell) / Chest", then "− 3 sets +" and ⋮. */
@Composable
private fun TemplateExerciseRow(
    exercise: TemplateEditorExercise,
    isFirst: Boolean,
    isLast: Boolean,
    onAction: (TemplateEditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SetwiseListCard(
        headlineContent = { Text(exercise.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(exercise.muscleGroup) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SetwiseStepper(
                    value = exercise.targetSets,
                    label = pluralStringResource(R.plurals.set_count, exercise.targetSets, exercise.targetSets),
                    onDecrease = { onAction(TemplateEditorAction.OnSetsChange(exercise.exerciseId, -1)) },
                    onIncrease = { onAction(TemplateEditorAction.OnSetsChange(exercise.exerciseId, +1)) },
                    decreaseDescription = stringResource(R.string.fewer_sets, exercise.name),
                    increaseDescription = stringResource(R.string.more_sets, exercise.name),
                    range = TemplateEditorUiState.SET_RANGE,
                )
                SetwiseOverflowMenu(
                    contentDescription = stringResource(R.string.exercise_options),
                    items = listOfNotNull(
                        SetwiseMenuItem(stringResource(R.string.move_up), { onAction(TemplateEditorAction.OnMoveUp(exercise.exerciseId)) })
                            .takeIf { !isFirst },
                        SetwiseMenuItem(stringResource(R.string.move_down), { onAction(TemplateEditorAction.OnMoveDown(exercise.exerciseId)) })
                            .takeIf { !isLast },
                        SetwiseMenuItem(
                            label = stringResource(R.string.remove_exercise),
                            onClick = { onAction(TemplateEditorAction.OnRemoveExercise(exercise.exerciseId)) },
                            isDestructive = true,
                        ),
                    ),
                )
            }
        },
        contentPadding = PaddingValues(start = 14.dp, top = 10.dp, end = 0.dp, bottom = 10.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

// Previews: one per scenario

@ScreenPreviews
@Composable
private fun TemplateEditorPreview() = SetwiseScreenPreview {
    TemplateEditorScreen(
        uiState = TemplateEditorUiState(
            isLoading = false,
            isNew = false,
            draft = TemplateEditorDraft(
                name = "Push Day",
                category = "Push",
                exercises = listOf(
                    TemplateEditorExercise(1, "Bench Press (Barbell)", "Chest", 4),
                    TemplateEditorExercise(2, "Overhead Press (Barbell)", "Shoulders", 3),
                    TemplateEditorExercise(3, "Triceps Pushdown", "Arms", 3),
                ),
            ),
        ),
        onAction = {},
    )
}

@ScreenPreviews
@Composable
private fun TemplateEditorNewPreview() = SetwiseScreenPreview {
    TemplateEditorScreen(uiState = TemplateEditorUiState(isLoading = false), onAction = {})
}
