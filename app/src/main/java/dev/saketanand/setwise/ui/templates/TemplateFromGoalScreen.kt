package dev.saketanand.setwise.ui.templates

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saketanand.setwise.R
import dev.saketanand.setwise.domain.model.Gear
import dev.saketanand.setwise.domain.model.GoalType
import dev.saketanand.setwise.ui.ObserveAsEvents
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTag
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTagDefaults
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTopAppBar
import dev.saketanand.setwise.ui.designsystem.preview.ScreenPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.designsystem.theme.numberSmall
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.TemplateFromGoal]. "Build from a goal": type a goal, see templates drafted
 * from the library, save them.
 * @param onDraftCreated Opens the first saved template in the template editor.
 */
@Composable
fun TemplateFromGoalScreenRoot(
    onDraftCreated: (templateId: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: TemplateFromGoalViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val goal = rememberTextFieldState()
    val context = LocalContext.current
    val onDraftCreatedLatest by rememberUpdatedState(onDraftCreated)
    LaunchedEffect(goal) {
        snapshotFlow { goal.text.toString() }.collect { viewModel.onAction(TemplateFromGoalAction.OnGoalChange(it)) }
    }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is TemplateFromGoalEvent.Saved -> onDraftCreatedLatest(event.firstTemplateId)
            TemplateFromGoalEvent.SaveFailed -> Toast.makeText(context, R.string.goal_save_failed, Toast.LENGTH_SHORT).show()
        }
    }
    TemplateFromGoalScreen(uiState = uiState, goal = goal, onAction = viewModel::onAction, onBack = onBack)
}

@Composable
fun TemplateFromGoalScreen(
    uiState: TemplateFromGoalUiState,
    goal: TextFieldState,
    onAction: (TemplateFromGoalAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        SetwiseTopAppBar(title = stringResource(R.string.goal_title), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            item(key = "goal") { GoalField(goal) }
            uiState.understood?.let { understood -> item(key = "understood") { Understood(understood) } }
            if (uiState.templates.isNotEmpty()) {
                item(key = "draft") { Draft(uiState, onRegenerate = { onAction(TemplateFromGoalAction.OnRegenerateClick) }) }
            }
        }
        SetwiseButton(
            text = stringResource(R.string.goal_save),
            onClick = { onAction(TemplateFromGoalAction.OnSaveClick) },
            enabled = uiState.canSave,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        )
    }
}

/** "What do you want to train for?" and the goal as typed. */
@Composable
private fun GoalField(goal: TextFieldState, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.goal_label)
    val hint = stringResource(R.string.goal_hint)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            state = goal,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 5),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics { contentDescription = label },
            decorator = { inner ->
                Box {
                    if (goal.text.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                }
            },
        )
    }
}

/** "3 days / week", "45 min", "Barbell + dumbbells", "Strength": what the goal was read as. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Understood(understood: GoalChipsUi, modifier: Modifier = Modifier) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        val style = SetwiseTagDefaults.accent()
        SetwiseTag(pluralStringResource(R.plurals.goal_days, understood.daysPerWeek, understood.daysPerWeek), style = style)
        SetwiseTag(stringResource(R.string.goal_minutes, understood.minutes), style = style)
        SetwiseTag(gearLabel(understood.gear), style = style)
        SetwiseTag(stringResource(understood.type.labelRes()), style = style)
    }
}

/** "✦ Draft · 2 templates   Regenerate", each template with its exercises, then the note. */
@Composable
private fun Draft(uiState: TemplateFromGoalUiState, onRegenerate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_ai_sparkle), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Text(
                text = pluralStringResource(R.plurals.goal_draft, uiState.templates.size, uiState.templates.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            )
            if (uiState.isChoosing) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                SetwiseButton(
                    text = stringResource(R.string.goal_regenerate),
                    onClick = onRegenerate,
                    style = SetwiseButtonStyle.Text,
                    size = SetwiseButtonSize.Small,
                )
            }
        }
        uiState.templates.forEach { template -> PlannedTemplate(template) }
        val note = if (uiState.byModel) R.string.goal_note_by_model else R.string.goal_note
        Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlannedTemplate(template: PlannedTemplateUi, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(template.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Text(
                text = pluralStringResource(R.plurals.goal_template_meta, template.exercises.size, template.exercises.size, template.estimatedMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        template.exercises.forEach { exercise ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(exercise.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    text = if (exercise.isTimed) {
                        stringResource(R.string.goal_scheme_seconds, exercise.sets, exercise.reps)
                    } else {
                        stringResource(R.string.goal_scheme, exercise.sets, exercise.reps)
                    },
                    style = MaterialTheme.typography.numberSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun gearLabel(gear: Set<Gear>): String {
    val named = gear - Gear.Bodyweight
    return when {
        gear.size == Gear.entries.size -> stringResource(R.string.goal_gear_any)
        named.isEmpty() -> stringResource(R.string.goal_gear_bodyweight)
        else -> named.map { stringResource(it.labelRes()) }.joinToString(" + ").replaceFirstChar { it.titlecase() }
    }
}

private fun Gear.labelRes() = when (this) {
    Gear.Barbell -> R.string.gear_barbell
    Gear.Dumbbell -> R.string.gear_dumbbells
    Gear.Kettlebell -> R.string.gear_kettlebells
    Gear.Cable -> R.string.gear_cables
    Gear.Machine -> R.string.gear_machines
    Gear.Bodyweight -> R.string.gear_bodyweight
}

private fun GoalType.labelRes() = when (this) {
    GoalType.Strength -> R.string.goal_type_strength
    GoalType.Muscle -> R.string.goal_type_muscle
    GoalType.General -> R.string.goal_type_general
}

// Previews: one per scenario

@ScreenPreviews
@Composable
private fun TemplateFromGoalPreview() = SetwiseScreenPreview {
    TemplateFromGoalScreen(
        uiState = SampleTemplateFromGoalState,
        goal = rememberTextFieldState("Get stronger at squat and bench, 45 minutes, I only have dumbbells and a barbell"),
        onAction = {},
        onBack = {},
    )
}

@ScreenPreviews
@Composable
private fun TemplateFromGoalEmptyPreview() = SetwiseScreenPreview {
    TemplateFromGoalScreen(uiState = TemplateFromGoalUiState(), goal = rememberTextFieldState(), onAction = {}, onBack = {})
}

/** A strength goal's draft: previews and UI tests. */
internal val SampleTemplateFromGoalState = TemplateFromGoalUiState(
    understood = GoalChipsUi(GoalType.Strength, 3, 45, setOf(Gear.Barbell, Gear.Dumbbell, Gear.Bodyweight)),
    templates = listOf(
        PlannedTemplateUi(
            "Strength A", 44,
            listOf(
                PlannedExerciseUi("Back Squat (Barbell)", 5, 5, false),
                PlannedExerciseUi("Bench Press (Barbell)", 4, 5, false),
                PlannedExerciseUi("Bent-over Row (Barbell)", 3, 8, false),
                PlannedExerciseUi("Plank", 3, 45, true),
            ),
        ),
    ),
)
