package dev.saketanand.setwise.ui.exercises

import androidx.compose.runtime.Composable
import dev.saketanand.setwise.ui.navigation.Route
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.ExercisePicker].
 * @param onExercisesPicked Returns the selection to the caller.
 * @param onCreateExercise "Create new", pre-filled with the search text.
 */
@Composable
fun ExercisePickerScreenRoot(
    onExercisesPicked: (exerciseIds: List<Long>) -> Unit,
    onCreateExercise: (initialName: String) -> Unit,
    onBack: () -> Unit,
    viewModel: ExercisePickerViewModel = koinViewModel(),
) {
    // TODO
}
