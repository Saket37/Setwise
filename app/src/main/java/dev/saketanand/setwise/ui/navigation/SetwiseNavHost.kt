package dev.saketanand.setwise.ui.navigation

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

private const val TransitionMillis = 200

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
        // Short fades (Material's ~200ms instead of Navigation's default 700ms). On back, the
        // screen being left disappears at once: during a cross-fade it's drawn on top and still
        // gets touches, so a quick tap on the screen underneath (e.g. "Resume workout") was lost.
        enterTransition = { fadeIn(tween(TransitionMillis)) },
        exitTransition = { fadeOut(tween(TransitionMillis)) },
        popEnterTransition = { fadeIn(tween(TransitionMillis)) },
        popExitTransition = { ExitTransition.None },
    ) {
        // Tabs

        composable<Route.Home> {
            HomeScreenRoot(
                onStartWorkout = { workoutId -> navController.navigate(Route.ActiveWorkout(workoutId)) },
                onEditTemplate = { templateId -> navController.navigate(Route.TemplateEditor(templateId)) },
                onCreateTemplateFromGoal = { navController.navigate(Route.TemplateFromGoal) },
                onOpenExercise = { exerciseId -> navController.navigate(Route.ExerciseDetail(exerciseId)) },
            )
        }

        composable<Route.History> {
            HistoryScreenRoot(
                onOpenWorkout = { workoutId -> navController.navigate(Route.WorkoutSummary(workoutId)) },
            )
        }

        composable<Route.Settings> {
            SettingsScreenRoot()
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

        composable<Route.WorkoutSummary> {
            WorkoutSummaryScreenRoot(
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
