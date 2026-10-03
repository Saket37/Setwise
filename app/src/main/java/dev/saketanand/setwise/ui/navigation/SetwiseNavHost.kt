package dev.saketanand.setwise.ui.navigation

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.saketanand.setwise.ui.exercises.CreateExerciseScreenRoot
import dev.saketanand.setwise.ui.exercises.ExerciseDetailScreenRoot
import dev.saketanand.setwise.ui.exercises.ExercisePickerScreenRoot
import dev.saketanand.setwise.ui.history.HistoryScreenRoot
import dev.saketanand.setwise.ui.home.HomeScreenRoot
import dev.saketanand.setwise.ui.settings.SettingsScreenRoot
import dev.saketanand.setwise.ui.summary.WorkoutSummaryScreenRoot
import dev.saketanand.setwise.ui.templates.TemplateEditorScreenRoot
import dev.saketanand.setwise.ui.templates.TemplateFromGoalScreenRoot
import dev.saketanand.setwise.ui.workout.ActiveWorkoutScreenRoot
import dev.saketanand.setwise.ui.workout.CardioEntryScreenRoot
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private val FadeThroughOut = fadeOut(tween(durationMillis = 90, easing = FastOutLinearInEasing))
private val FadeThroughIn = fadeIn(tween(durationMillis = 210, delayMillis = 90, easing = LinearOutSlowInEasing)) +
    scaleIn(tween(durationMillis = 210, delayMillis = 90, easing = LinearOutSlowInEasing), initialScale = 0.92f)

/** Height of the bottom navigation bar (Material's 80dp). Tab screens keep this much free at the bottom. */
val TabBarHeight = 80.dp

/** A tab's content, kept clear of the bottom bar floating over it (see SetwiseAppRoot). */
@Composable
private fun TabScreen(content: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(bottom = TabBarHeight)) { content() }
}

/** Key under which the exercise picker hands its selection back to the screen that opened it. */
private const val PICKED_EXERCISE_IDS = "picked_exercise_ids"

/**
 * All destinations and how they connect. Screens never touch the NavController: they expose
 * callbacks, and this file decides where each callback goes.
 */
@Composable
fun SetwiseNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Route.Home,
        modifier = modifier,
        // Material "fade through": the old screen fades out quickly (90ms), then the new one
        // fades and grows in. Short on purpose: while a screen is leaving it's drawn on top and
        // still gets touches, so with Navigation's default 700ms cross-fade a quick tap on the
        // next screen (e.g. "Resume workout" right after going back) was lost.
        enterTransition = { FadeThroughIn },
        exitTransition = { FadeThroughOut },
        popEnterTransition = { FadeThroughIn },
        popExitTransition = { FadeThroughOut },
    ) {
        // Tabs

        composable<Route.Home> {
            TabScreen {
                HomeScreenRoot(
                    onStartWorkout = { workoutId -> navController.navigate(Route.ActiveWorkout(workoutId)) },
                    onEditTemplate = { templateId -> navController.navigate(Route.TemplateEditor(templateId)) },
                    onCreateTemplateFromGoal = { navController.navigate(Route.TemplateFromGoal) },
                    onOpenExercise = { exerciseId -> navController.navigate(Route.ExerciseDetail(exerciseId)) },
                )
            }
        }

        composable<Route.History> {
            TabScreen {
                HistoryScreenRoot(
                    onOpenWorkout = { workoutId -> navController.navigate(Route.WorkoutSummary(workoutId)) },
                )
            }
        }

        composable<Route.Settings> {
            TabScreen { SettingsScreenRoot() }
        }

        // Workout flow

        composable<Route.ActiveWorkout> { entry ->
            val route = entry.toRoute<Route.ActiveWorkout>()
            val pickedExerciseIds = entry.pickedExerciseIds()
            ActiveWorkoutScreenRoot(
                viewModel = koinViewModel { parametersOf(route.workoutId) },
                pickedExerciseIds = pickedExerciseIds,
                onPickedExercisesConsumed = { entry.clearPickedExerciseIds() },
                onAddExercises = { navController.navigate(Route.ExercisePicker) },
                onOpenCardioEntry = { id -> navController.navigate(Route.CardioEntry(id)) },
                onOpenExercise = { exerciseId -> navController.navigate(Route.ExerciseDetail(exerciseId)) },
                onFinished = { workoutId ->
                    // Replace the active workout with its summary, so Back doesn't return to it.
                    navController.navigate(Route.WorkoutSummary(workoutId)) {
                        popUpTo<Route.ActiveWorkout> { inclusive = true }
                    }
                },
                onMinimize = { navController.popBackStack() },
            )
        }

        composable<Route.CardioEntry> {
            CardioEntryScreenRoot(onBack = { navController.popBackStack() })
        }

        composable<Route.WorkoutSummary> { entry ->
            val route = entry.toRoute<Route.WorkoutSummary>()
            WorkoutSummaryScreenRoot(
                viewModel = koinViewModel { parametersOf(route.workoutId) },
                onOpenExercise = { exerciseId -> navController.navigate(Route.ExerciseDetail(exerciseId)) },
                onDone = { navController.popBackStack() },
            )
        }

        // Exercises

        composable<Route.ExercisePicker> {
            ExercisePickerScreenRoot(
                onExercisesPicked = { ids ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(PICKED_EXERCISE_IDS, ids.toLongArray())
                    navController.popBackStack()
                },
                onCreateExercise = { name -> navController.navigate(Route.CreateExercise(name)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Route.CreateExercise> {
            CreateExerciseScreenRoot(
                // The picker's list is a Room Flow, so the new exercise shows up there by itself.
                onExerciseCreated = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Route.ExerciseDetail> {
            ExerciseDetailScreenRoot(onBack = { navController.popBackStack() })
        }

        // Templates

        composable<Route.TemplateEditor> { entry ->
            val pickedExerciseIds = entry.pickedExerciseIds()
            TemplateEditorScreenRoot(
                pickedExerciseIds = pickedExerciseIds,
                onPickedExercisesConsumed = { entry.clearPickedExerciseIds() },
                onAddExercises = { navController.navigate(Route.ExercisePicker) },
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Route.TemplateFromGoal> {
            TemplateFromGoalScreenRoot(
                onDraftCreated = { templateId ->
                    navController.navigate(Route.TemplateEditor(templateId)) {
                        popUpTo<Route.TemplateFromGoal> { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** Exercise ids the picker returned to this destination, or null if there are none. */
@Composable
private fun NavBackStackEntry.pickedExerciseIds(): List<Long>? {
    val ids by savedStateHandle.getStateFlow<LongArray?>(PICKED_EXERCISE_IDS, null)
        .collectAsStateWithLifecycle()
    return ids?.toList()
}

private fun NavBackStackEntry.clearPickedExerciseIds() {
    savedStateHandle[PICKED_EXERCISE_IDS] = null
}
