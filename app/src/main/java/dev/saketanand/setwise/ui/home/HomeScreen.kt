package dev.saketanand.setwise.ui.home

import androidx.compose.runtime.Composable
import dev.saketanand.setwise.ui.navigation.Route
import org.koin.androidx.compose.koinViewModel

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
    // TODO
}
