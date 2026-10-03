package dev.saketanand.setwise.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.rememberElapsedTime
import dev.saketanand.setwise.ui.designsystem.components.HorizontalGap
import dev.saketanand.setwise.ui.designsystem.components.IconTile
import dev.saketanand.setwise.ui.designsystem.components.RoutineCard
import dev.saketanand.setwise.ui.designsystem.components.RoutineCardDefaults
import dev.saketanand.setwise.ui.designsystem.components.SectionLabel
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseConfirmDialog
import dev.saketanand.setwise.ui.designsystem.components.SetwiseIconButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCard
import dev.saketanand.setwise.ui.designsystem.components.SetwiseListCardDefaults
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTag
import dev.saketanand.setwise.ui.designsystem.components.StatTile
import dev.saketanand.setwise.ui.designsystem.components.VerticalGap
import dev.saketanand.setwise.ui.designsystem.theme.pr
import dev.saketanand.setwise.ui.designsystem.theme.spacing
import dev.saketanand.setwise.util.toClockLabel
import dev.saketanand.setwise.util.toShortDurationLabel

/*
 * Sections of the Workout tab. The layout logic (which section shows when) is done;
 * every visual piece below is a stub with a TODO for you to build from the design canvas.
 */

// Layouts (picked by HomeScreen from uiState.content)

/** Artboard 1b: nothing tracked, no templates, nothing running. */
@Composable
fun HomeFirstRunContent(
    exerciseCount: Int,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // "Fill or scroll": the column is at least as tall as the screen area, so the hero's
    // weight(1f) can take the spare height and center itself (design 1b). If the content is
    // taller (small phone, large font), it simply scrolls instead.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FirstRunHero(modifier = Modifier.weight(1f))
            StartWorkoutButton(
                text = stringResource(R.string.start_an_empty_workout),
                isStarting = false,
                onClick = { onAction(HomeAction.OnStartEmptyWorkout) },
                startIcon = R.drawable.ic_play
            )
            PlanYourRoutineSection(lastWorkout = null, onAction = onAction)
            // Hidden until the library count has loaded, so "0 exercises" never flashes.
            if (exerciseCount > 0) {
                ExerciseLibraryHint(count = exerciseCount)
            }
        }
    }
}

/**
 * Artboard 1 (+ 13): the normal Workout tab.
 *
 * A LazyColumn: each section is one item and each template card is its own item, so only the
 * cards on screen are composed however many templates the user has. Spacing is per item
 * (20dp between sections, 10dp between template cards) because one LazyColumn can only
 * have one Arrangement.
 */
@Composable
fun HomeDashboardContent(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        // A running workout comes first: it's the most likely thing the user wants.
        uiState.activeWorkout?.let { active ->
            item(key = "resume", contentType = "resume") {
                ResumeWorkoutCard(
                    activeWorkout = active,
                    onResume = { onAction(HomeAction.OnResumeWorkout(active.workoutId)) },
                    modifier = Modifier.animateItem().padding(bottom = SectionGap),
                )
            }
        }

        item(key = "greeting", contentType = "greeting") {
            HomeGreeting(lastWorkout = uiState.lastWorkout, modifier = Modifier.padding(bottom = SectionGap))
        }

        // Start of a new week: recap of the last one (artboard 13).
        uiState.weeklySummary?.let { summary ->
            item(key = "weeklySummary", contentType = "weeklySummary") {
                WeeklySummaryCard(
                    summary = summary,
                    onAction = onAction,
                    modifier = Modifier.animateItem().padding(bottom = SectionGap),
                )
            }
        }

        item(key = "weekStats", contentType = "weekStats") {
            WeekStatsRow(stats = uiState.weekStats, modifier = Modifier.padding(bottom = SectionGap))
        }

        item(key = "startWorkout", contentType = "startWorkout") {
            // Only one workout can run at a time: while one is running, this resumes it.
            val running = uiState.activeWorkout
            StartWorkoutButton(
                text = stringResource(if (running != null) R.string.resume_workout else R.string.start_workout),
                isStarting = uiState.isStartingWorkout,
                onClick = {
                    onAction(
                        if (running != null) HomeAction.OnResumeWorkout(running.workoutId)
                        else HomeAction.OnStartWorkoutClick
                    )
                },
                startIcon = R.drawable.ic_play,
                modifier = Modifier.padding(bottom = SectionGap),
            )
        }

        if (uiState.showPlanYourRoutine) {
            // Has trained but never saved a template.
            item(key = "planYourRoutine", contentType = "planYourRoutine") {
                PlanYourRoutineSection(lastWorkout = uiState.lastWorkout, onAction = onAction)
            }
        } else {
            item(key = "templatesHeader", contentType = "templatesHeader") {
                TemplatesHeader(
                    onNewClick = { onAction(HomeAction.OnCreateTemplateClick) },
                    modifier = Modifier.padding(bottom = CardGap),
                )
            }
            items(
                items = uiState.templates,
                key = { template -> "template-${template.id}" },
                contentType = { "template" },
            ) { template ->
                TemplateCard(
                    template = template,
                    onAction = onAction,
                    modifier = Modifier.animateItem().padding(bottom = CardGap),
                )
            }
        }
    }
}

/** Space between dashboard sections (design: 20). */
private val SectionGap = 20.dp

/** Space between template cards (design: 10). */
private val CardGap = 10.dp

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
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // First run: comes right after "Start an empty workout", hence "Or …".
        val label = if (lastWorkout == null) R.string.or_plan_your_routine else R.string.plan_your_routine
        SectionLabel(stringResource(label))
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

// Pieces

/** Artboard 1b: logo with first bar filled, others outlined (ic_setwise_mark_empty), headline, subtitle. */
@Composable
fun FirstRunHero(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.ic_setwise_mark_empty), contentDescription = null)
        VerticalGap(18.dp)
        Text(
            stringResource(R.string.your_first_set_starts_here),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        VerticalGap(MaterialTheme.spacing.xs)
        Text(
            stringResource(R.string.first_run_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** "128 exercises ready, or add your own" footnote (artboard 1b). */
@Composable
fun ExerciseLibraryHint(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_nav_workout),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
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
        text = text,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        startIcon = startIcon,
        enabled = !isStarting,
    )
}

/** "Resume Push Day · running 12:34 · 6 sets done" card. */
@Composable
fun ResumeWorkoutCard(
    activeWorkout: ActiveWorkoutUi,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SetwiseListCard(
        onClick = onResume,
        modifier = modifier,
        colors = SetwiseListCardDefaults.highlightedColors(),
        contentPadding = PaddingValues(16.dp),
        overlineContent = {
            SectionLabel(stringResource(R.string.workout_in_progress), color = LocalContentColor.current)
        },
        headlineContent = { Text(activeWorkout.name) },
        supportingContent = {
            RunningForText(
                startedAtMillis = activeWorkout.startedAtMillis,
                completedSets = activeWorkout.completedSets,
            )
        },
        // Decorative: the card's text already says what tapping does.
        trailingContent = {
            IconTile(
                icon = R.drawable.ic_play,
                size = 44.dp,
                cornerRadius = 22.dp,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    )
}

/**
 * "Running 12:34 · 6 sets done", ticking once a second. Kept in its own composable so the tick
 * redraws only this line, not the whole Resume card.
 */
@Composable
private fun RunningForText(startedAtMillis: Long, completedSets: Int) {
    val running = rememberElapsedTime(startedAtMillis)
    Text(
        text = stringResource(
            R.string.running_for,
            running.toClockLabel(),
            pluralStringResource(R.plurals.sets_done, completedSets, completedSets),
        ),
    )
}

/** "Ready to train?" + "Last session: Pull Day · 2 days ago" (subtitle only when lastWorkout != null). */
@Composable
fun HomeGreeting(
    lastWorkout: LastWorkoutUi?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.ready_to_train),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        lastWorkout?.let {
            Text(
                text = stringResource(R.string.last_session, it.name, relativeDaysText(it.daysAgo)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "today", "yesterday", "3 days ago". */
@Composable
fun relativeDaysText(daysAgo: Int): String = when (daysAgo) {
    0 -> stringResource(R.string.today)
    1 -> stringResource(R.string.yesterday)
    else -> pluralStringResource(R.plurals.days_ago, daysAgo, daysAgo)
}

/** Three tiles: workouts this week · time trained · new PRs (PR count in Ember). */
@Composable
fun WeekStatsRow(
    stats: WeekStatsUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(
            value = stats.workouts.toString(),
            label = stringResource(R.string.stat_workouts_this_week),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StatTile(
            value = stats.timeTrained.toShortDurationLabel(),
            label = stringResource(R.string.stat_time_trained),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StatTile(
            value = stats.newPrs.toString(),
            label = stringResource(R.string.stat_new_prs),
            valueColor = MaterialTheme.colorScheme.pr,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
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

/** "Templates" title with a "+ New" button (OnCreateTemplateClick). */
@Composable
fun TemplatesHeader(
    onNewClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.templates),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        SetwiseButton(
            text = stringResource(R.string.new_template),
            onClick = onNewClick,
            style = SetwiseButtonStyle.Outlined,
            size = SetwiseButtonSize.Small,
            startIcon = R.drawable.ic_add,
        )
    }
}

/**
 * Card: name + tag, "Bench · Incline DB · OHP · +2 · 4 days ago", and a ▶ start button.
 * Tap = OnTemplateClick (edit), ▶ = OnStartFromTemplate.
 */
@Composable
fun TemplateCard(
    template: TemplateUi,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = buildList {
        addAll(template.exercisePreview)
        if (template.moreExerciseCount > 0) add(stringResource(R.string.more_count, template.moreExerciseCount))
        template.lastUsedDaysAgo?.let { add(relativeDaysText(it)) }
    }.joinToString(" · ")

    SetwiseListCard(
        onClick = { onAction(HomeAction.OnTemplateClick(template.id)) },
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 14.dp, bottom = 14.dp),
        textSpacing = 3.dp,
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = template.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                template.category?.let { SetwiseTag(it) }
            }
        },
        supportingContent = { Text(text = detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            SetwiseIconButton(
                icon = R.drawable.ic_play,
                contentDescription = stringResource(R.string.start_template, template.name),
                onClick = { onAction(HomeAction.OnStartFromTemplate(template.id)) },
            )
        },
    )
}

/** "Save Pull Day as a template": one tap turns the last workout into a template. */
@Composable
fun SaveLastWorkoutAsTemplateCard(
    workoutName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoutineCard(
        title = stringResource(R.string.save_workout_as_template, workoutName),
        description = stringResource(R.string.save_workout_as_template_description),
        icon = R.drawable.ic_add,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Two cards side by side: "Create a template" and "✦ Build from a goal" (artboard 1b). */
@Composable
fun PlanRoutineCards(
    onCreateTemplate: () -> Unit,
    onBuildFromGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // IntrinsicSize.Min + fillMaxHeight: both cards get the height of the taller one.
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RoutineCard(
            title = stringResource(R.string.create_a_template),
            description = stringResource(R.string.create_a_template_description),
            icon = R.drawable.ic_add,
            onClick = onCreateTemplate,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        RoutineCard(
            title = stringResource(R.string.build_from_a_goal),
            description = stringResource(R.string.build_from_a_goal_description),
            icon = R.drawable.ic_ai_sparkle,
            onClick = onBuildFromGoal,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = RoutineCardDefaults.highlightedColors(),
        )
    }
}

/**
 * "Discard Pull Day?": shown when starting a workout while another is running.
 * Confirm = OnConfirmDiscardAndStart, Cancel / back / outside = OnDismissDiscardDialog.
 */
@Composable
fun DiscardWorkoutDialog(
    dialog: DiscardDialogUi,
    onAction: (HomeAction) -> Unit,
) {
    SetwiseConfirmDialog(
        title = stringResource(R.string.discard_workout_title, dialog.runningWorkoutName),
        message = stringResource(
            R.string.discard_workout_message,
            dialog.runningWorkoutName,
            pluralStringResource(R.plurals.sets_done, dialog.runningCompletedSets, dialog.runningCompletedSets),
        ),
        confirmText = stringResource(R.string.discard_and_start),
        onConfirm = { onAction(HomeAction.OnConfirmDiscardAndStart) },
        onDismiss = { onAction(HomeAction.OnDismissDiscardDialog) },
        isDestructive = true,
    )
}
