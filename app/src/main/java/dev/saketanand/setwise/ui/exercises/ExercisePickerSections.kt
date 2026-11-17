package dev.saketanand.setwise.ui.exercises

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.components.IconTile
import dev.saketanand.setwise.ui.designsystem.components.SectionLabel
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseEmptyState
import dev.saketanand.setwise.ui.designsystem.components.SetwiseFilterChip
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCard
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCardDefaults
import dev.saketanand.setwise.ui.designsystem.components.SetwiseSelectionIndicator
import dev.saketanand.setwise.ui.designsystem.preview.PreviewComponents
import dev.saketanand.setwise.ui.designsystem.preview.SetwisePreview
import dev.saketanand.setwise.ui.designsystem.theme.numberSmall
import kotlinx.collections.immutable.ImmutableList

/** Side margin of the picker's content (design: 20). */
internal val PickerHorizontalPadding = 20.dp

/** "All" + one chip per muscle group, scrolling sideways. */
@Composable
fun MuscleGroupChips(
    muscleGroups: ImmutableList<String>,
    selected: String?,
    onClick: (muscleGroup: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = PickerHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            SetwiseFilterChip(
                label = stringResource(R.string.filter_all),
                selected = selected == null,
                onClick = { onClick(null) },
            )
        }
        items(items = muscleGroups, key = { it }) { muscleGroup ->
            SetwiseFilterChip(
                label = muscleGroup,
                selected = muscleGroup == selected,
                onClick = { onClick(muscleGroup) },
            )
        }
    }
}

/**
 * "RECENT" (only on the unfiltered list) and the library. Keys are prefixed because an
 * exercise can be in both sections, and LazyColumn keys must be unique.
 */
@Composable
fun ExerciseList(
    uiState: ExercisePickerUiState,
    onAction: (ExercisePickerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // New search or chip → back to the top. Keyed on the filter the list was *loaded* for, so it
    // runs once the new results are in. The last handled filter is saved, so rotating the phone
    // doesn't jump to the top.
    var scrolledForFilter by rememberSaveable { mutableStateOf(uiState.listFilter) }
    LaunchedEffect(uiState.listFilter) {
        if (uiState.listFilter != scrolledForFilter) {
            scrolledForFilter = uiState.listFilter
            listState.scrollToItem(0)
        }
    }

    val onToggle: (Long) -> Unit = { id -> onAction(ExercisePickerAction.OnExerciseToggle(id)) }
    val onOpen: (Long) -> Unit = { id -> onAction(ExercisePickerAction.OnExerciseLongPress(id)) }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(start = PickerHorizontalPadding, end = PickerHorizontalPadding, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (uiState.showRecent) {
            item(key = "recentHeader", contentType = "header") {
                SectionLabel(stringResource(R.string.recent), Modifier.padding(vertical = 4.dp))
            }
            items(uiState.recent, key = { "recent-${it.id}" }, contentType = { "exercise" }) { exercise ->
                ExercisePickerRow(exercise = exercise, onToggle = onToggle, onOpen = onOpen)
            }
            item(key = "allHeader", contentType = "header") {
                SectionLabel(stringResource(R.string.all_exercises), Modifier.padding(top = 10.dp, bottom = 4.dp))
            }
        }
        items(uiState.exercises, key = { "all-${it.id}" }, contentType = { "exercise" }) { exercise ->
            ExercisePickerRow(exercise = exercise, onToggle = onToggle, onOpen = onOpen)
        }
    }
}

/**
 * Initials tile, name, "Chest · last 60 kg × 8" (or "Chest · Barbell"), and a check circle.
 * The whole row toggles; screen readers hear it as a checkbox ("checked" / "not checked").
 * A long press opens the exercise (#146).
 */
@Composable
fun ExercisePickerRow(
    exercise: ExerciseRowUi,
    onToggle: (exerciseId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (exerciseId: Long) -> Unit = {},
) {
    val containerColor by animateColorAsState(
        if (exercise.isSelected) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
        label = "rowContainer",
    )
    SetwiseListCard(
        onClick = { onToggle(exercise.id) },
        onLongClick = { onOpen(exercise.id) },
        onLongClickLabel = stringResource(R.string.open_exercise),
        modifier = modifier.semantics {
            role = Role.Checkbox
            toggleableState = ToggleableState(exercise.isSelected)
        },
        colors = SetwiseListCardDefaults.colors(containerColor = containerColor),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        leadingContent = {
            IconTile(size = 40.dp, cornerRadius = 12.dp, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                Text(exercise.initials, style = MaterialTheme.typography.numberSmall)
            }
        },
        headlineContent = { Text(exercise.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = exerciseDetail(exercise),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { SetwiseSelectionIndicator(selected = exercise.isSelected) },
    )
}

/** "Chest · last 60 kg × 8", "Back · last 10 reps", or "Chest · Barbell". */
@Composable
@ReadOnlyComposable
private fun exerciseDetail(exercise: ExerciseRowUi): String {
    val lastSet = exercise.lastSet
    val second = when {
        lastSet?.weight != null && lastSet.reps != null -> stringResource(R.string.last_weight_reps, lastSet.weight, lastSet.reps)
        lastSet?.weight != null -> stringResource(R.string.last_weight, lastSet.weight)
        lastSet?.reps != null -> pluralStringResource(R.plurals.last_reps, lastSet.reps, lastSet.reps)
        else -> exercise.equipment
    }
    return listOfNotNull(exercise.muscleGroup, second).joinToString(" · ")
}

/** No search results: offer to create the exercise under the typed name. */
@Composable
fun NoExercisesFound(
    query: String,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (query.isEmpty()) {
        // Only possible with an empty library (e.g. seeding failed).
        SetwiseEmptyState(title = stringResource(R.string.no_exercises), modifier = modifier)
        return
    }
    SetwiseEmptyState(
        title = stringResource(R.string.no_exercises_match, query),
        message = stringResource(R.string.no_exercises_match_message),
        icon = R.drawable.ic_search,
        modifier = modifier,
        action = {
            SetwiseButton(
                text = stringResource(R.string.create_exercise_named, query),
                onClick = onCreateClick,
                style = SetwiseButtonStyle.Outlined,
                size = SetwiseButtonSize.Medium,
                startIcon = R.drawable.ic_add,
            )
        },
    )
}

@PreviewComponents
@Composable
private fun ExercisePickerRowPreview() = SetwisePreview {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ExercisePickerRow(
            ExerciseRowUi(1, "Bench Press (Barbell)", "BP", "Chest", "Barbell", LastSetUi("62.5", 8), isSelected = true),
            onToggle = {},
        )
        ExercisePickerRow(
            ExerciseRowUi(2, "Pull-up", "PU", "Back", "Bodyweight", LastSetUi(null, 10), isSelected = false),
            onToggle = {},
        )
        ExercisePickerRow(
            ExerciseRowUi(3, "Plank", "PL", "Core", null, null, isSelected = false),
            onToggle = {},
        )
    }
}
