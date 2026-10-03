package dev.saketanand.setwise.ui.exercises

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.DateProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Screen: [ExerciseDetailScreenRoot]. Live: a workout finished or edited elsewhere updates the
 * chart and sessions, and the chart's weeks move on at midnight.
 *
 * @param exerciseId from [Route.ExerciseDetail], passed in by SetwiseNavHost (Koin parametersOf).
 */
class ExerciseDetailViewModel(
    private val exerciseId: Long,
    exerciseRepository: ExerciseRepository,
    workoutRepository: WorkoutRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    val state: StateFlow<ExerciseDetailUiState> = combine(
        exerciseRepository.observeExercise(exerciseId),
        workoutRepository.observeExerciseSessions(exerciseId),
        dateProvider.today(),
    ) { exercise, sessions, today ->
        if (exercise == null) {
            ExerciseDetailUiState(isLoading = false, isMissing = true)
        } else {
            exerciseDetailUi(exercise, sessions, today, dateProvider.zone)
        }
    }
        .catch { e ->
            Log.e(TAG, "Loading exercise $exerciseId failed", e)
            emit(ExerciseDetailUiState(isLoading = false, isMissing = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    private companion object {
        const val TAG = "ExerciseDetailViewModel"
    }
}
