package dev.saketanand.setwise.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.ObserveAsEvents
import dev.saketanand.setwise.ui.designsystem.components.HorizontalGap
import dev.saketanand.setwise.ui.designsystem.components.VerticalGap
import dev.saketanand.setwise.ui.designsystem.preview.ScreenPreviews
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.designsystem.theme.spacing
import dev.saketanand.setwise.ui.designsystem.theme.wordmark
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.toShortDayLabel
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.minutes

/**
 * Destination: [Route.Home].
 * @param onStartWorkout Workout created (empty or from a template); open it.
 * @param onEditTemplate Edit a template, or Route.NEW_TEMPLATE_ID for a new one.
 * @param onCreateTemplateFromGoal "Generate from goal".
 * @param onOpenExercise From the weekly summary card (e.g. plateau plan).
 */
@Composable
fun HomeScreenRoot(
    onStartWorkout: (workoutId: Long) -> Unit,
    onEditTemplate: (templateId: Long) -> Unit,
    onCreateTemplateFromGoal: () -> Unit,
    onOpenExercise: (exerciseId: Long) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is HomeEvent.WorkoutStarted -> onStartWorkout(event.workoutId)
            // TODO: replace with a snackbar once the screen has a SnackbarHost.
            HomeEvent.StartWorkoutFailed ->
                Toast.makeText(context, R.string.start_workout_failed, Toast.LENGTH_SHORT).show()

            // TODO (milestone 3): open onEditTemplate(event.templateId) once the editor exists.
            is HomeEvent.TemplateCreated ->
                Toast.makeText(context, R.string.template_saved, Toast.LENGTH_SHORT).show()
            HomeEvent.SaveTemplateFailed ->
                Toast.makeText(context, R.string.save_template_failed, Toast.LENGTH_SHORT).show()
        }
    }

    HomeScreen(
        uiState = uiState,
        onAction = { action ->
            when (action) {
                // Pure navigation: go straight to the nav callbacks.
                is HomeAction.OnResumeWorkout -> onStartWorkout(action.workoutId)
                is HomeAction.OnTemplateClick -> onEditTemplate(action.templateId)
                HomeAction.OnCreateTemplateClick -> onEditTemplate(Route.NEW_TEMPLATE_ID)
                HomeAction.OnCreateTemplateFromGoalClick -> onCreateTemplateFromGoal()
                is HomeAction.OnPlateauExerciseClick -> onOpenExercise(action.exerciseId)
                // Everything else needs the ViewModel.
                else -> viewModel.onAction(action)
            }
        },
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(MaterialTheme.spacing.screenHorizontal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_setwise_mark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            HorizontalGap(MaterialTheme.spacing.xs)
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.wordmark,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.weight(1f))
            Text(
                text = uiState.today.toShortDayLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        VerticalGap(20.dp)

        // Which layout to show under the header (see HomeUiState.content).
        when (uiState.content) {
            HomeContent.Loading -> Unit // TODO: optional placeholder; showing nothing avoids a flash
            HomeContent.FirstRun -> HomeFirstRunContent(exerciseCount = uiState.exerciseCount, onAction = onAction)
            HomeContent.Dashboard -> HomeDashboardContent(uiState = uiState, onAction = onAction)
        }
    }

    if (uiState.isStartSheetVisible) {
        StartWorkoutSheet(uiState = uiState, onAction = onAction)
    }

    uiState.discardDialog?.let { dialog ->
        DiscardWorkoutDialog(dialog = dialog, onAction = onAction)
    }

    uiState.checkIn?.let { checkIn ->
        DayCheckInSheet(checkIn = checkIn, onAction = onAction)
    }
}

// Previews: one per scenario

@ScreenPreviews
@Composable
private fun HomeScreenFirstRunPreview() = SetwiseScreenPreview {
    HomeScreen(uiState = HomeUiState(isLoading = false), onAction = {})
}

@ScreenPreviews
@Composable
private fun HomeScreenDashboardPreview() = SetwiseScreenPreview {
    HomeScreen(
        uiState = HomeUiState(
            isLoading = false,
            lastWorkout = previewLastWorkout,
            weekStats = WeekStatsUi(workouts = 3, timeTrained = 204.minutes, newPrs = 2),
            templates = previewTemplates,
        ),
        onAction = {},
    )
}

@ScreenPreviews
@Composable
private fun HomeScreenPlanYourRoutinePreview() = SetwiseScreenPreview {
    // Trained, but no templates saved yet → "Plan your routine" + "Save Pull Day as a template".
    HomeScreen(
        uiState = HomeUiState(
            isLoading = false,
            lastWorkout = previewLastWorkout,
            weekStats = WeekStatsUi(workouts = 1, timeTrained = 62.minutes, newPrs = 0),
        ),
        onAction = {},
    )
}

@ScreenPreviews
@Composable
private fun HomeScreenResumePreview() = SetwiseScreenPreview {
    HomeScreen(
        uiState = HomeUiState(
            isLoading = false,
            activeWorkout = ActiveWorkoutUi(
                workoutId = 7,
                name = "Push Day",
                startedAtMillis = 0,
                completedSets = 6
            ),
            lastWorkout = previewLastWorkout,
            templates = previewTemplates,
        ),
        onAction = {},
    )
}

private val previewLastWorkout = LastWorkoutUi(workoutId = 1, name = "Pull Day", daysAgo = 2)

private val previewTemplates = listOf(
    TemplateUi(
        id = 1, name = "Push Day", category = "PUSH",
        exercisePreview = listOf("Bench", "Incline DB", "OHP", "Dips"), moreExerciseCount = 2,
        exerciseCount = 6, setCount = 20, estimatedMinutes = 65, lastUsedDaysAgo = 4,
    ),
    TemplateUi(
        id = 2, name = "Leg Day", category = "LEGS",
        exercisePreview = listOf("Squat", "RDL", "Leg press", "Calf raise"), moreExerciseCount = 1,
        exerciseCount = 5, setCount = 18, estimatedMinutes = 70, lastUsedDaysAgo = 6,
    ),
)