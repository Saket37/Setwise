package dev.saketanand.setwise.ui.summary

import androidx.compose.runtime.Composable
import dev.saketanand.setwise.ui.navigation.Route
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.WorkoutSummary].
 */
@Composable
fun WorkoutSummaryScreenRoot(
    onOpenExercise: (exerciseId: Long) -> Unit,
    onDone: () -> Unit,
    viewModel: WorkoutSummaryViewModel = koinViewModel(),
) {
    // TODO
}
