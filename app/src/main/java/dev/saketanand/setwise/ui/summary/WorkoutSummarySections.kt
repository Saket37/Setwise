package dev.saketanand.setwise.ui.summary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.PrKind
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCard
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCardDefaults
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCardOutline
import dev.saketanand.setwise.ui.designsystem.components.StatTile
import dev.saketanand.setwise.ui.designsystem.theme.numberMedium
import dev.saketanand.setwise.ui.designsystem.theme.numberSmall
import dev.saketanand.setwise.ui.designsystem.theme.pr
import dev.saketanand.setwise.ui.workout.label
import dev.saketanand.setwise.ui.workout.summary
import dev.saketanand.setwise.util.toClockLabel
import dev.saketanand.setwise.util.toShortTimeLabel
import java.text.NumberFormat
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList

/**
 * 2 × 2 tiles: Duration, Volume, Sets, and Calories: the estimate, "Add your weight" (tap)
 * when there's no body weight, else the exercise count.
 */
@Composable
fun SummaryStats(uiState: WorkoutSummaryUiState, onAddBodyWeight: () -> Unit, modifier: Modifier = Modifier) {
    val tile = Modifier.fillMaxHeight()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // IntrinsicSize.Min + fillMaxHeight: both tiles in a row get the taller one's height.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile(stringResource(R.string.stat_duration), uiState.duration.toClockLabel(), modifier = tile.weight(1f))
            SummaryTile(
                label = stringResource(R.string.stat_volume),
                value = NumberFormat.getIntegerInstance().format(uiState.volumeKg.roundToLong()),
                unit = stringResource(R.string.unit_kg),
                modifier = tile.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile(stringResource(R.string.stat_sets), uiState.completedSets.toString(), modifier = tile.weight(1f))
            if (uiState.caloriesKcal != null) {
                SummaryTile(
                    label = stringResource(R.string.stat_calories),
                    value = uiState.caloriesKcal.toString(),
                    unit = stringResource(R.string.unit_kcal),
                    modifier = tile.weight(1f),
                )
            } else if (uiState.needsBodyWeight) {
                StatTile(
                    value = stringResource(R.string.add_your_weight),
                    label = stringResource(R.string.stat_calories),
                    labelFirst = true,
                    valueStyle = MaterialTheme.typography.titleMedium,
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = tile
                        .weight(1f)
                        .clip(MaterialTheme.shapes.large)
                        .clickable(onClickLabel = stringResource(R.string.add_your_weight), onClick = onAddBodyWeight),
                )
            } else {
                SummaryTile(stringResource(R.string.stat_exercises), uiState.exerciseCount.toString(), modifier = tile.weight(1f))
            }
        }
    }
}

/** Design: caption above a Barlow 28 value. */
@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier, unit: String? = null) {
    StatTile(
        value = value,
        label = label,
        unit = unit,
        labelFirst = true,
        valueStyle = MaterialTheme.typography.headlineSmall,
        modifier = modifier,
    )
}

/** "🏆 2 personal records" and one outlined row per record. */
@Composable
fun PersonalRecordsSection(records: ImmutableList<RecordUi>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_trophy),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.pr,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = pluralStringResource(R.plurals.personal_records, records.size, records.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }
        records.forEach { record -> PersonalRecordRow(record) }
    }
}

@Composable
private fun PersonalRecordRow(record: RecordUi) {
    SetwiseListCard(
        headlineContent = {
            Text(record.exerciseName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.previous_best, record.previousBest.label(record.setKind).orEmpty()),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = record.achieved.label(record.setKind).orEmpty(),
                    style = MaterialTheme.typography.numberMedium,
                    color = MaterialTheme.colorScheme.pr,
                )
                Text(
                    text = stringResource(record.kind.labelRes()).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = SetwiseListCardDefaults.transparentColors(),
        outline = SetwiseListCardOutline.Solid(MaterialTheme.colorScheme.outline),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    )
}

private fun PrKind.labelRes(): Int = when (this) {
    PrKind.Weight -> R.string.pr_kind_weight
    PrKind.EstimatedOneRepMax -> R.string.pr_kind_estimated_1rm
    PrKind.Reps -> R.string.pr_kind_reps
    PrKind.Duration -> R.string.pr_kind_duration
}

/** "Exercises", then "Bench Press (Barbell)   4 sets · best 62.5 × 8" per row; a row opens its detail. */
@Composable
fun ExercisesSection(exercises: ImmutableList<SummaryExerciseUi>, onExerciseClick: (exerciseId: Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.exercises),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .semantics { heading() },
        )
        exercises.forEach { exercise ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExerciseClick(exercise.exerciseId) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                val sets = pluralStringResource(R.plurals.set_count, exercise.setCount, exercise.setCount)
                Text(
                    text = exercise.cardio?.summary()
                        ?: exercise.best?.let { stringResource(R.string.exercise_sets_and_best, sets, it) }
                        ?: sets,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        }
    }
}

/** "Workout times": Start / End rows (tap → time picker), Save / Cancel. */
@Composable
fun EditTimesDialog(edit: EditTimesUi, onAction: (WorkoutSummaryAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(WorkoutSummaryAction.OnEditTimesDismiss) },
        title = { Text(stringResource(R.string.workout_times)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeRow(stringResource(R.string.start), edit.start.toShortTimeLabel()) {
                    onAction(WorkoutSummaryAction.OnPickTime(TimeField.Start))
                }
                TimeRow(stringResource(R.string.end), edit.end.toShortTimeLabel()) {
                    onAction(WorkoutSummaryAction.OnPickTime(TimeField.End))
                }
                if (edit.isInvalid) {
                    Text(
                        text = stringResource(R.string.workout_times_invalid),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            SetwiseButton(
                text = stringResource(R.string.save),
                onClick = { onAction(WorkoutSummaryAction.OnSaveTimes) },
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
            )
        },
        dismissButton = {
            SetwiseButton(
                text = stringResource(R.string.cancel),
                onClick = { onAction(WorkoutSummaryAction.OnEditTimesDismiss) },
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun TimeRow(label: String, time: String, onClick: () -> Unit) {
    SetwiseListCard(
        onClick = onClick,
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(time, style = MaterialTheme.typography.numberSmall, color = MaterialTheme.colorScheme.onSurface)
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        },
        colors = SetwiseListCardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    )
}
