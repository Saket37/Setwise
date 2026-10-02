package dev.saketanand.setwise.ui.exercises

import androidx.compose.runtime.Composable
import dev.saketanand.setwise.ui.navigation.Route
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.ExerciseDetail].
 */
@Composable
fun ExerciseDetailScreenRoot(
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = koinViewModel(),
) {
    // TODO
}
