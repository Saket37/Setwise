package dev.saketanand.setwise.ui.history

import androidx.compose.runtime.Composable
import dev.saketanand.setwise.ui.navigation.Route
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.History].
 * @param onOpenWorkout Opens the workout summary.
 */
@Composable
fun HistoryScreenRoot(
    onOpenWorkout: (workoutId: Long) -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    // TODO
}
