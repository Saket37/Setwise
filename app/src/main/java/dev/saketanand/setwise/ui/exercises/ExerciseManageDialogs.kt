package dev.saketanand.setwise.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonDefaults
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseConfirmDialog
import dev.saketanand.setwise.ui.designsystem.components.SetwiseSearchField
import dev.saketanand.setwise.ui.designsystem.components.SetwiseSelectionIndicator
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import kotlinx.collections.immutable.persistentListOf

/** The delete or merge dialog for a custom exercise (#146), whichever [manage] is. */
@Composable
internal fun ManageExerciseDialog(exerciseName: String, manage: ManageExerciseUi, onAction: (ExerciseDetailAction) -> Unit) {
    when (manage) {
        is ManageExerciseUi.ConfirmDelete -> SetwiseConfirmDialog(
            title = stringResource(R.string.delete_exercise_title, exerciseName),
            message = if (manage.templates == 0) {
                stringResource(R.string.delete_exercise_message)
            } else {
                pluralStringResource(R.plurals.delete_exercise_in_templates, manage.templates, manage.templates)
            },
            confirmText = stringResource(R.string.delete),
            onConfirm = { onAction(ExerciseDetailAction.OnConfirmDelete) },
            onDismiss = { onAction(ExerciseDetailAction.OnDismissDialog) },
            isDestructive = true,
        )
        is ManageExerciseUi.Merge -> MergeDialog(exerciseName, manage, onAction)
    }
}

/**
 * "Merge Bnech press into…": why it can't just be deleted, a search, and the exercises logged
 * the same way; Merge once one is picked.
 */
@Composable
private fun MergeDialog(exerciseName: String, merge: ManageExerciseUi.Merge, onAction: (ExerciseDetailAction) -> Unit) {
    val search = rememberTextFieldState(merge.query)
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(search) {
        snapshotFlow { search.text.toString() }.collect { currentOnAction(ExerciseDetailAction.OnMergeQueryChange(it)) }
    }
    AlertDialog(
        onDismissRequest = { onAction(ExerciseDetailAction.OnDismissDialog) },
        title = { Text(stringResource(R.string.merge_exercise_title, exerciseName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(pluralStringResource(R.plurals.merge_exercise_message, merge.workouts, exerciseName, merge.workouts))
                SetwiseSearchField(state = search, placeholder = stringResource(R.string.search_exercises))
                if (merge.candidates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.merge_exercise_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp).selectableGroup()) {
                        items(merge.candidates, key = { it.id }) { candidate ->
                            CandidateRow(
                                candidate = candidate,
                                selected = candidate.id == merge.selectedId,
                                onClick = { onAction(ExerciseDetailAction.OnMergeTargetClick(candidate.id)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            SetwiseButton(
                text = stringResource(R.string.merge),
                onClick = { onAction(ExerciseDetailAction.OnConfirmMerge) },
                enabled = merge.selectedId != null,
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
                colors = SetwiseButtonDefaults.destructiveColors(SetwiseButtonStyle.Text),
            )
        },
        dismissButton = {
            SetwiseButton(
                text = stringResource(R.string.cancel),
                onClick = { onAction(ExerciseDetailAction.OnDismissDialog) },
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun CandidateRow(candidate: MergeCandidateUi, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetwiseSelectionIndicator(selected = selected, size = 24.dp)
        Column(Modifier.weight(1f)) {
            Text(candidate.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(candidate.muscleGroup, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Previews: one per scenario

@PreviewComponents
@Composable
private fun DeleteExerciseDialogPreview() = SetwisePreview {
    ManageExerciseDialog(exerciseName = "Bnech press", manage = ManageExerciseUi.ConfirmDelete(templates = 2), onAction = {})
}

@PreviewComponents
@Composable
private fun MergeExerciseDialogPreview() = SetwisePreview {
    ManageExerciseDialog(
        exerciseName = "Bnech press",
        manage = ManageExerciseUi.Merge(
            workouts = 3,
            candidates = persistentListOf(
                MergeCandidateUi(1, "Bench Press (Barbell)", "Chest"),
                MergeCandidateUi(2, "Bench Press (Dumbbell)", "Chest"),
                MergeCandidateUi(3, "Incline Bench Press (Barbell)", "Chest"),
            ),
            selectedId = 1,
        ),
        onAction = {},
    )
}
