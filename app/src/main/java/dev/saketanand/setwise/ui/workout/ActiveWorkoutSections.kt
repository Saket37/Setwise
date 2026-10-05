package dev.saketanand.setwise.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.components.NumberKind
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseCheckButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseConfirmDialog
import dev.saketanand.setwise.ui.designsystem.components.SetwiseHintBanner
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCard
import dev.saketanand.setwise.ui.designsystem.components.SetwiseMenuItem
import dev.saketanand.setwise.ui.designsystem.components.SetwiseNumberField
import dev.saketanand.setwise.ui.designsystem.components.SetwiseOverflowMenu
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTextInputDialog
import dev.saketanand.setwise.ui.designsystem.preview.ComponentPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import dev.saketanand.setwise.ui.designsystem.theme.numberMedium
import dev.saketanand.setwise.ui.designsystem.theme.pr
import dev.saketanand.setwise.ui.exercises.hint
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.drop

/*
 * Pieces of the active workout screen: exercise cards, the set table and its rows, dialogs.
 */

/** Set table column widths (design): SET 36 · PREVIOUS fills · KG 64 · REPS 52 · ✓ 48 (touch target). */
private object SetColumns {
    val Number = 36.dp
    val Weight = 64.dp
    val Reps = 52.dp
    val Check = 48.dp
    val Gap = 8.dp
}

/** Open exercise: name + ⋮, the set table and "Add set" (cardio: "Log cardio" instead). */
@Composable
fun ExpandedExerciseCard(
    exercise: WorkoutExerciseUi,
    onAction: (ActiveWorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
    /** Editing a finished workout: ticked-off sets stay editable. */
    editableWhenDone: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ExerciseCardHeader(exercise = exercise, onAction = onAction)

        // Until its sets are done: what the progression rules suggest (opens its progress).
        val hint = exercise.nextSession?.takeIf { exercise.completedSets < exercise.sets.size }?.hint()
        if (hint != null) {
            SetwiseHintBanner(
                text = hint,
                onClick = { onAction(ActiveWorkoutAction.OnExerciseHistoryClick(exercise.exerciseId)) },
                onClickLabel = stringResource(R.string.progression_hint_open),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
            )
        }

        if (exercise.kind == SetKind.Cardio) {
            // Logged: what was done, and Edit. Not yet: Log cardio.
            val logged = exercise.cardio
            if (logged != null) {
                Text(
                    text = logged.summary(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            } else {
                exercise.lastTime?.let { LastTimeText(it, Modifier.padding(horizontal = 4.dp)) }
            }
            SetwiseButton(
                text = stringResource(if (logged != null) R.string.edit_cardio else R.string.log_cardio),
                onClick = { onAction(ActiveWorkoutAction.OnLogCardioClick(exercise.id)) },
                style = SetwiseButtonStyle.Tonal,
                size = SetwiseButtonSize.Medium,
                shape = MaterialTheme.shapes.medium,
                startIcon = if (logged != null) R.drawable.ic_edit else null,
                textStyle = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        SetTableHeader(kind = exercise.kind)
        exercise.sets.forEach { set ->
            // key: each row keeps its own text fields and swipe state, even when rows before it
            // are deleted.
            key(set.id) {
                SwipeToDeleteSet(onDelete = { onAction(ActiveWorkoutAction.OnDeleteSet(set.id)) }) {
                    SetRow(set = set, kind = exercise.kind, onAction = onAction, editableWhenDone = editableWhenDone)
                }
            }
        }
        SetwiseButton(
            text = stringResource(R.string.add_set),
            onClick = { onAction(ActiveWorkoutAction.OnAddSetClick(exercise.id)) },
            style = SetwiseButtonStyle.Tonal,
            size = SetwiseButtonSize.Medium,
            shape = MaterialTheme.shapes.medium,
            startIcon = R.drawable.ic_add,
            textStyle = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
        )
    }
}

/** Closed exercise: name, "0 / 3 sets", "Last time: …". Tap opens it. */
@Composable
fun CollapsedExerciseCard(
    exercise: WorkoutExerciseUi,
    onAction: (ActiveWorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SetwiseListCard(
        onClick = { onAction(ActiveWorkoutAction.OnExerciseHeaderClick(exercise.id)) },
        modifier = modifier,
        headlineContent = {
            Text(exercise.name, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = when {
            exercise.kind != SetKind.Cardio -> exercise.lastTime?.let { { LastTimeText(it) } }
            exercise.cardio != null -> {
                { Text(exercise.cardio.summary()) }
            }
            else -> {
                { Text(stringResource(R.string.cardio_not_logged)) }
            }
        },
        trailingContent = {
            if (exercise.kind != SetKind.Cardio) {
                Text(
                    text = stringResource(R.string.sets_progress, exercise.completedSets, exercise.sets.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        textSpacing = 6.dp,
    )
}

/** Exercise name (tap to collapse) and the ⋮ menu. */
@Composable
private fun ExerciseCardHeader(exercise: WorkoutExerciseUi, onAction: (ActiveWorkoutAction) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clickable { onAction(ActiveWorkoutAction.OnExerciseHeaderClick(exercise.id)) }
                .padding(horizontal = 4.dp, vertical = 11.dp)
                .semantics { heading() },
        )
        SetwiseOverflowMenu(
            contentDescription = stringResource(R.string.exercise_options),
            items = persistentListOf(
                SetwiseMenuItem(
                    label = stringResource(R.string.exercise_history_and_progress),
                    onClick = { onAction(ActiveWorkoutAction.OnExerciseHistoryClick(exercise.exerciseId)) },
                ),
                SetwiseMenuItem(
                    label = stringResource(R.string.remove_exercise),
                    onClick = { onAction(ActiveWorkoutAction.OnRemoveExerciseClick(exercise.id)) },
                    isDestructive = true,
                ),
            ),
        )
    }
}

@Composable
private fun LastTimeText(lastTime: String, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.last_time, lastTime),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** SET · PREVIOUS · KG · REPS (+KG for bodyweight, SEC for timed). */
@Composable
private fun SetTableHeader(kind: SetKind) {
    SetTableRow(modifier = Modifier.padding(horizontal = 6.dp)) {
        val style = MaterialTheme.typography.labelSmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(stringResource(R.string.column_set).uppercase(), style = style, color = color, modifier = Modifier.width(SetColumns.Number))
        Text(stringResource(R.string.column_previous).uppercase(), style = style, color = color, modifier = Modifier.weight(1f))
        val weightLabel = when (kind) {
            SetKind.Bodyweight -> stringResource(R.string.column_added_kg)
            SetKind.Duration -> ""
            else -> stringResource(R.string.column_kg)
        }
        Text(weightLabel.uppercase(), style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.width(SetColumns.Weight))
        val amountLabel = if (kind == SetKind.Duration) R.string.column_seconds else R.string.column_reps
        Text(stringResource(amountLabel).uppercase(), style = style, color = color, textAlign = TextAlign.Center, modifier = Modifier.width(SetColumns.Reps))
        Spacer(Modifier.width(SetColumns.Check))
    }
}

/** The shared column layout of the header and the set rows. */
@Composable
private fun SetTableRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SetColumns.Gap),
        content = content,
    )
}

/**
 * One set. Open: number, previous, two number fields (hints = last time) and ✓.
 * Done: Volt-tinted row with the values as text; ✓ again re-opens it.
 */
@Composable
fun SetRow(
    set: SetUi,
    kind: SetKind,
    onAction: (ActiveWorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
    /** Editing a finished workout: a ticked-off set's values stay editable (it stays ticked). */
    editableWhenDone: Boolean = false,
) {
    val rowColor = if (set.isCompleted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    SetTableRow(
        modifier = modifier
            .background(rowColor, MaterialTheme.shapes.medium)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        SetNumber(set)
        Text(
            text = set.previous ?: "–",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (set.isCompleted && !editableWhenDone) {
            DoneValue(text = if (kind == SetKind.Duration) "" else set.weight, width = SetColumns.Weight)
            DoneValue(text = set.reps, width = SetColumns.Reps)
            CheckCell {
                SetwiseCheckButton(
                    checked = true,
                    onCheckedChange = { onAction(ActiveWorkoutAction.OnSetDoneToggle(set.id, set.weight, set.reps)) },
                    contentDescription = stringResource(R.string.a11y_set_done, set.number),
                )
            }
        } else {
            OpenSetFields(set = set, kind = kind, onAction = onAction)
        }
    }
}

/**
 * The two fields of an open set. Their text lives here (TextFieldState), not in the ViewModel,
 * so typing is never interrupted; every change is sent on and saved.
 */
@Composable
private fun RowScope.OpenSetFields(set: SetUi, kind: SetKind, onAction: (ActiveWorkoutAction) -> Unit) {
    // Created from the saved values when the row opens; after that the field owns the text.
    val weight = remember(set.id) { TextFieldState(set.weight) }
    val reps = remember(set.id) { TextFieldState(set.reps) }
    // The effect outlives recompositions; always call the latest onAction.
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(set.id) {
        snapshotFlow { weight.text.toString() to reps.text.toString() }
            .drop(1) // the initial values are already saved
            .collect { (w, r) -> currentOnAction(ActiveWorkoutAction.OnSetValuesChange(set.id, w, r)) }
    }
    val focusManager = LocalFocusManager.current

    if (kind == SetKind.Duration) {
        Spacer(Modifier.width(SetColumns.Weight))
    } else {
        SetwiseNumberField(
            state = weight,
            contentDescription = stringResource(R.string.a11y_set_weight, set.number),
            placeholder = set.weightHint,
            kind = NumberKind.Decimal,
            modifier = Modifier.width(SetColumns.Weight),
        )
    }
    SetwiseNumberField(
        state = reps,
        contentDescription = stringResource(
            if (kind == SetKind.Duration) R.string.a11y_set_seconds else R.string.a11y_set_reps,
            set.number,
        ),
        placeholder = set.repsHint,
        imeAction = ImeAction.Done,
        onKeyboardAction = { focusManager.clearFocus() },
        modifier = Modifier.width(SetColumns.Reps),
    )
    // Needs reps (typed or a hint, more than 0) to have something to log.
    val canComplete = canCompleteSet(reps.text.toString(), set.repsHint)
    CheckCell {
        SetwiseCheckButton(
            checked = set.isCompleted,
            onCheckedChange = {
                focusManager.clearFocus()
                onAction(ActiveWorkoutAction.OnSetDoneToggle(set.id, weight.text.toString(), reps.text.toString()))
            },
            contentDescription = stringResource(R.string.a11y_set_done, set.number),
            enabled = set.isCompleted || canComplete,
        )
    }
}

/** The ✓ column: the 36dp button centred in its 48dp touch-target column, same in every row. */
@Composable
private fun CheckCell(content: @Composable () -> Unit) {
    Box(modifier = Modifier.width(SetColumns.Check), contentAlignment = Alignment.Center) { content() }
}

/** "1", "2"… with a trophy on PR sets (milestone 8 sets isPr). */
@Composable
private fun SetNumber(set: SetUi) {
    Row(modifier = Modifier.width(SetColumns.Number), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = set.number.toString(),
            style = MaterialTheme.typography.numberMedium,
            color = if (set.isCompleted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
        if (set.isPr) {
            Icon(
                painterResource(R.drawable.ic_trophy),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.pr,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun DoneValue(text: String, width: Dp) {
    Box(modifier = Modifier.width(width).heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.numberMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/**
 * Swipe a set row left to delete it. Screen-reader users get the same as a "Delete set" action,
 * since they can't swipe.
 */
@Composable
private fun SwipeToDeleteSet(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val deleteLabel = stringResource(R.string.delete_set)
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        modifier = Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
        },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        content = { content() },
    )
}

/** The confirmations ([ActiveWorkoutDialog]). */
@Composable
fun ActiveWorkoutDialogs(
    dialog: ActiveWorkoutDialog,
    workoutName: String,
    onAction: (ActiveWorkoutAction) -> Unit,
) {
    val dismiss = { onAction(ActiveWorkoutAction.OnDismissDialog) }
    when (dialog) {
        is ActiveWorkoutDialog.FinishWithIncompleteSets -> SetwiseConfirmDialog(
            title = stringResource(R.string.finish_workout_title),
            message = pluralStringResource(R.plurals.finish_incomplete_message, dialog.incompleteSets, dialog.incompleteSets),
            confirmText = stringResource(R.string.finish),
            onConfirm = { onAction(ActiveWorkoutAction.OnConfirmFinish) },
            onDismiss = dismiss,
        )
        ActiveWorkoutDialog.NothingLogged -> SetwiseConfirmDialog(
            title = stringResource(R.string.nothing_logged_title),
            message = stringResource(R.string.nothing_logged_message),
            confirmText = stringResource(R.string.discard),
            onConfirm = { onAction(ActiveWorkoutAction.OnConfirmDiscard) },
            onDismiss = dismiss,
            isDestructive = true,
        )
        ActiveWorkoutDialog.ConfirmDiscard -> SetwiseConfirmDialog(
            title = stringResource(R.string.discard_workout_confirm_title, workoutName),
            message = stringResource(R.string.discard_workout_confirm_message),
            confirmText = stringResource(R.string.discard),
            onConfirm = { onAction(ActiveWorkoutAction.OnConfirmDiscard) },
            onDismiss = dismiss,
            isDestructive = true,
        )
        is ActiveWorkoutDialog.RemoveExercise -> SetwiseConfirmDialog(
            title = stringResource(R.string.remove_exercise_title, dialog.exerciseName),
            message = pluralStringResource(R.plurals.remove_exercise_message, dialog.completedSets, dialog.completedSets),
            confirmText = stringResource(R.string.remove),
            onConfirm = { onAction(ActiveWorkoutAction.OnConfirmRemoveExercise) },
            onDismiss = dismiss,
            isDestructive = true,
        )
        is ActiveWorkoutDialog.Rename -> SetwiseTextInputDialog(
            title = stringResource(R.string.rename_workout),
            label = stringResource(R.string.template_name),
            initialText = dialog.currentName,
            onConfirm = { onAction(ActiveWorkoutAction.OnRenameConfirm(it)) },
            onDismiss = dismiss,
        )
    }
}

@ComponentPreviews
@Composable
private fun SetRowsPreview() = SetwisePreview {
    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SetTableHeader(SetKind.WeightReps)
        SetRow(SetUi(1, 1, "60 × 8", "62.5", "8", "60", "8", isCompleted = true, isPr = true), SetKind.WeightReps, onAction = {})
        SetRow(SetUi(2, 2, "60 × 7", "", "", "62.5", "8", isCompleted = false, isPr = false), SetKind.WeightReps, onAction = {})
        SetRow(SetUi(3, 3, null, "", "", "", "", isCompleted = false, isPr = false), SetKind.WeightReps, onAction = {})
    }
}
