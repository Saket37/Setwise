package dev.saketanand.setwise.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.designsystem.components.HorizontalGap
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.VerticalGap
import dev.saketanand.setwise.ui.designsystem.theme.spacing

/*
 * Sections of the Workout tab. The layout logic (which section shows when) is done;
 * every visual piece below is a stub with a TODO for you to build from the design canvas.
 */

// Layouts (picked by HomeScreen from uiState.content)

/** Artboard 1b: nothing tracked, no templates, nothing running. */
@Composable
fun HomeFirstRunContent(
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    count: Int
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
    ) {
        FirstRunHero()
        StartWorkoutButton(
            text = stringResource(R.string.start_an_empty_workout),
            isStarting = false,
            onClick = { onAction(HomeAction.OnStartEmptyWorkout) },
            startIcon = R.drawable.ic_play
        )
        PlanYourRoutineSection(lastWorkout = null, onAction = onAction)
        ExerciseLibraryHint(count = count)
    }
}

/** Artboard 1 (+ 13): the normal Workout tab. */
@Composable
fun HomeDashboardContent(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
    ) {
        // A running workout comes first: it's the most likely thing the user wants.
        uiState.activeWorkout?.let { active ->
            ResumeWorkoutCard(
                activeWorkout = active,
                onResume = { onAction(HomeAction.OnResumeWorkout(active.workoutId)) },
            )
        }

        HomeGreeting(lastWorkout = uiState.lastWorkout)

        // Start of a new week: recap of the last one (artboard 13).
        uiState.weeklySummary?.let { summary ->
            WeeklySummaryCard(summary = summary, onAction = onAction)
        }

        WeekStatsRow(stats = uiState.weekStats)

        StartWorkoutButton(
            text = "Start workout", // TODO: move to strings.xml
            isStarting = uiState.isStartingWorkout,
            onClick = { onAction(HomeAction.OnStartWorkoutClick) },
            startIcon = R.drawable.ic_play
        )

        if (uiState.showPlanYourRoutine) {
            // Has trained but never saved a template.
            PlanYourRoutineSection(lastWorkout = uiState.lastWorkout, onAction = onAction)
        } else {
            TemplatesSection(templates = uiState.templates, onAction = onAction)
        }
    }
}

// Shared sections

/**
 * "Plan your routine": create a template / build from a goal. When the user has a finished
 * workout, also offers "Save <name> as a template" first (the quickest way to a template).
 */
@Composable
fun PlanYourRoutineSection(
    lastWorkout: LastWorkoutUi?,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        // TODO: section label "OR PLAN YOUR ROUTINE" / "PLAN YOUR ROUTINE" (labelSmall, onSurfaceVariant)
        lastWorkout?.let { workout ->
            SaveLastWorkoutAsTemplateCard(
                workoutName = workout.name,
                onClick = { onAction(HomeAction.OnSaveLastWorkoutAsTemplate(workout.workoutId)) },
            )
        }
        PlanRoutineCards(
            onCreateTemplate = { onAction(HomeAction.OnCreateTemplateClick) },
            onBuildFromGoal = { onAction(HomeAction.OnCreateTemplateFromGoalClick) },
        )
    }
}

// Pieces to build (TODO)

/** Artboard 1b: logo with first bar filled, others outlined (ic_setwise_mark_empty), headline, subtitle. */
@Composable
fun FirstRunHero(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painterResource(R.drawable.ic_setwise_mark_empty), contentDescription = null)
        VerticalGap(18.dp)
        Text(
            stringResource(R.string.your_first_set_starts_here),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        VerticalGap(MaterialTheme.spacing.xs)
        Text(
            stringResource(R.string.first_run_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "128 exercises ready, or add your own" footnote (artboard 1b). */
@Composable
fun ExerciseLibraryHint(modifier: Modifier = Modifier, count: Int) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_nav_workout),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            contentDescription = null
        )
        HorizontalGap(MaterialTheme.spacing.xs)
        Text(
            pluralStringResource(R.plurals.exercises_ready, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Full-width Volt pill button with a play icon. Disable and/or show progress while [isStarting]. */
@Composable
fun StartWorkoutButton(
    text: String,
    isStarting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    startIcon: Int
) {
    SetwiseButton(
        modifier = modifier,
        text = text,
        onclick = onClick,
        startIcon = startIcon,
        enabled = !isStarting
    )
}

/** "Resume Push Day · running 12:34 · 6 sets done" card. */
@Composable
fun ResumeWorkoutCard(
    activeWorkout: ActiveWorkoutUi,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO
}

/** "Ready to train?" + "Last session: Pull Day · 2 days ago" (subtitle only when lastWorkout != null). */
@Composable
fun HomeGreeting(
    lastWorkout: LastWorkoutUi?,
    modifier: Modifier = Modifier,
) {
    // TODO
}

/** Three tiles: workouts this week · time trained · new PRs (PR count in Ember). */
@Composable
fun WeekStatsRow(
    stats: WeekStatsUi,
    modifier: Modifier = Modifier,
) {
    // TODO
}

/**
 * Artboard 13: "Your week" card. Recap text from the LLM (placeholder while isGeneratingRecap),
 * highlights, and "See <exercise> plan" when there's a plateau.
 * Actions: OnPlateauExerciseClick, OnWeeklySummaryDismiss.
 */
@Composable
fun WeeklySummaryCard(
    summary: WeeklySummaryUi,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO
}

/**
 * "Templates" header with a "New" button (OnCreateTemplateClick), then one card per template:
 * tap = OnTemplateClick, ▶ button = OnStartFromTemplate.
 */
@Composable
fun TemplatesSection(
    templates: List<TemplateUi>,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO
}

/** "Save Pull Day as a template": one tap turns the last workout into a template. */
@Composable
fun SaveLastWorkoutAsTemplateCard(
    workoutName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO
}

/** Two cards side by side: "Create a template" and "✦ Build from a goal" (artboard 1b). */
@Composable
fun PlanRoutineCards(
    onCreateTemplate: () -> Unit,
    onBuildFromGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            stringResource(R.string.plan_your_routine).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        VerticalGap(10.dp)

    }

}

/**
 * Artboard 2: ModalBottomSheet with "Empty workout", the templates, and "Starts now · Change".
 * Actions: OnStartEmptyWorkout, OnStartFromTemplate, OnStartTimeChange, OnStartSheetDismiss.
 */
@Composable
fun StartWorkoutSheet(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    // TODO
}
