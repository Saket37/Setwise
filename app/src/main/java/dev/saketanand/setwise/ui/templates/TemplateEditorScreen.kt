package dev.saketanand.setwise.ui.templates

import androidx.compose.runtime.Composable
import dev.saketanand.setwise.ui.navigation.Route
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.TemplateEditor].
 * @param pickedExerciseIds Result from the exercise picker; null when there is none.
 * @param onPickedExercisesConsumed Call after adding the picked exercises.
 * @param onAddExercises Opens the exercise picker.
 */
@Composable
fun TemplateEditorScreenRoot(
    pickedExerciseIds: List<Long>?,
    onPickedExercisesConsumed: () -> Unit,
    onAddExercises: () -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: TemplateEditorViewModel = koinViewModel(),
) {
    // TODO
}
