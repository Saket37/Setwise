package dev.saketanand.setwise.ui.exercises

import androidx.compose.runtime.Composable
import dev.saketanand.setwise.ui.navigation.Route
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.CreateExercise].
 */
@Composable
fun CreateExerciseScreenRoot(
    onExerciseCreated: (exerciseId: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: CreateExerciseViewModel = koinViewModel(),
) {
    // TODO
}
