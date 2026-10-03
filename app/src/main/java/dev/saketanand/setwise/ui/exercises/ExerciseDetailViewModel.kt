package dev.saketanand.setwise.ui.exercises

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.ai.PlateauNoteWriter
import dev.saketanand.setwise.domain.model.ExerciseSession
import dev.saketanand.setwise.domain.model.Plateau
import dev.saketanand.setwise.domain.model.Progression
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.DateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * Screen: [ExerciseDetailScreenRoot]. Live: a workout finished or edited elsewhere updates the
 * chart, sessions, plateau and next session, and the chart's weeks move on at midnight. A
 * plateau's note is written on-device in the background ([PlateauNoteWriter]); until then, or
 * without the model, the screen shows its template.
 *
 * @param exerciseId from [Route.ExerciseDetail], passed in by SetwiseNavHost (Koin parametersOf).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModel(
    private val exerciseId: Long,
    exerciseRepository: ExerciseRepository,
    workoutRepository: WorkoutRepository,
    private val dateProvider: DateProvider,
    private val plateauNotes: PlateauNoteWriter,
) : ViewModel() {

    private val data = combine(
        exerciseRepository.observeExercise(exerciseId),
        workoutRepository.observeExerciseSessions(exerciseId),
        dateProvider.today(),
    ) { exercise, sessions, today -> Triple(exercise, sessions, today) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** The plateau's note: written once per plateau (a new session can change it). */
    private val note = data
        .map { (exercise, sessions, _) ->
            exercise?.let { Progression.plateau(it, sessions, dateProvider.now()) }?.let { NoteInput(exercise.name, it, sessions.first()) }
        }
        .distinctUntilChanged()
        .transformLatest { input ->
            if (input == null) return@transformLatest emit(Note())
            plateauNotes.cached(input.exerciseName, input.plateau, input.last)?.let { return@transformLatest emit(Note(it)) }
            if (!plateauNotes.canWrite()) return@transformLatest emit(Note())
            emit(Note(isWriting = true))
            emit(Note(plateauNotes.write(input.exerciseName, input.plateau, input.last)))
        }

    val state: StateFlow<ExerciseDetailUiState> = combine(data, note) { (exercise, sessions, today), note ->
        if (exercise == null) {
            ExerciseDetailUiState(isLoading = false, isMissing = true)
        } else {
            val ui = exerciseDetailUi(exercise, sessions, today, dateProvider.zone, dateProvider.now())
            ui.copy(plateau = ui.plateau?.copy(note = note.text, isWriting = note.isWriting))
        }
    }
        .catch { e ->
            Log.e(TAG, "Loading exercise $exerciseId failed", e)
            emit(ExerciseDetailUiState(isLoading = false, isMissing = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    private data class NoteInput(val exerciseName: String, val plateau: Plateau, val last: ExerciseSession)

    private data class Note(val text: String? = null, val isWriting: Boolean = false)

    private companion object {
        const val TAG = "ExerciseDetailViewModel"
    }
}
