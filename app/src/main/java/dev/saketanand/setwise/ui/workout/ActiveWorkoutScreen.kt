package dev.saketanand.setwise.ui.workout

import androidx.compose.runtime.Composable
import dev.saketanand.setwise.ui.navigation.Route
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.ActiveWorkout].
 * @param pickedExerciseIds Result from the exercise picker; null when there is none.
 * @param onPickedExercisesConsumed Call after adding the picked exercises, so they are not added twice.
 * @param onAddExercises Opens the exercise picker.
 * @param onOpenExercise Exercise detail / progression.
 * @param onFinished After Finish; opens the summary.
 * @param onMinimize Back to the tabs; the workout keeps running.
 */
@Composable
fun ActiveWorkoutScreenRoot(
    pickedExerciseIds: List<Long>?,
    onPickedExercisesConsumed: () -> Unit,
    onAddExercises: () -> Unit,
    onOpenCardioEntry: (workoutExerciseId: Long) -> Unit,
    onOpenExercise: (exerciseId: Long) -> Unit,
    onFinished: (workoutId: Long) -> Unit,
    onMinimize: () -> Unit,
    viewModel: ActiveWorkoutViewModel = koinViewModel(),
) {
    // TODO
}
