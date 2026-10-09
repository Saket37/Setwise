package dev.saketanand.setwise.ui.exercises

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.ai.PlateauNoteWriter
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseSession
import dev.saketanand.setwise.domain.model.Plateau
import dev.saketanand.setwise.domain.model.Progression
import dev.saketanand.setwise.domain.repository.ExerciseEditor
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.DateProvider
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen: [ExerciseDetailScreenRoot]. Live: a workout finished or edited elsewhere updates the
 * chart, sessions, plateau and next session, and the chart's weeks move on at midnight. A
 * plateau's note is written on-device in the background ([PlateauNoteWriter]); until then, or
 * without the model, the screen shows its template.
 *
 * A custom exercise can be edited, deleted when it's in no workout, or else merged into one
 * logged the same way (#146). Either way it's gone afterwards, and the screen closes.
 *
 * @param exerciseId from [Route.ExerciseDetail], passed in by SetwiseNavHost (Koin parametersOf).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModel(
    private val exerciseId: Long,
    private val exerciseRepository: ExerciseRepository,
    workoutRepository: WorkoutRepository,
    private val dateProvider: DateProvider,
    private val plateauNotes: PlateauNoteWriter,
    private val editor: ExerciseEditor,
) : ViewModel() {

    private val dialog = MutableStateFlow<Dialog?>(null)
    private val eventChannel = Channel<ExerciseDetailEvent>(Channel.BUFFERED)
    val events: Flow<ExerciseDetailEvent> = eventChannel.receiveAsFlow()

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

    /** The merge dialog's choices: exercises logged the same way, by the dialog's search. */
    private val candidates: Flow<List<MergeCandidateUi>> = combine(
        dialog.map { (it as? Dialog.Merge)?.query }.distinctUntilChanged(),
        data.map { it.first }.distinctUntilChanged(),
    ) { query, exercise -> query to exercise }
        .flatMapLatest { (query, exercise) ->
            if (query == null || exercise == null) {
                flowOf(emptyList())
            } else {
                exerciseRepository.observeExercises(query, null).map { library -> library.filter { it.isMergeTargetFor(exercise) }.map { it.toCandidate() } }
            }
        }

    private val manage: Flow<ManageExerciseUi?> = combine(dialog, candidates) { dialog, candidates ->
        when (dialog) {
            null -> null
            is Dialog.Delete -> ManageExerciseUi.ConfirmDelete(dialog.templates)
            is Dialog.Merge -> ManageExerciseUi.Merge(
                workouts = dialog.workouts,
                query = dialog.query,
                candidates = candidates.toImmutableList(),
                selectedId = dialog.selectedId?.takeIf { id -> candidates.any { it.id == id } },
            )
        }
    }

    val state: StateFlow<ExerciseDetailUiState> = combine(data, note, manage) { (exercise, sessions, today), note, manage ->
        if (exercise == null) {
            ExerciseDetailUiState(isLoading = false, isMissing = true)
        } else {
            val ui = exerciseDetailUi(exercise, sessions, today, dateProvider.zone, dateProvider.now())
            ui.copy(plateau = ui.plateau?.copy(note = note.text, isWriting = note.isWriting), isCustom = exercise.isCustom, manage = manage)
        }
    }
        .catch { e ->
            Log.e(TAG, "Loading exercise $exerciseId failed", e)
            emit(ExerciseDetailUiState(isLoading = false, isMissing = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    fun onAction(action: ExerciseDetailAction) {
        when (action) {
            ExerciseDetailAction.OnDeleteClick -> change { openDeleteDialog() }
            ExerciseDetailAction.OnDismissDialog -> dialog.value = null
            ExerciseDetailAction.OnConfirmDelete -> change {
                // Added to a workout since the dialog opened: merging is the way now.
                if (!editor.deleteExercise(exerciseId)) openDeleteDialog()
            }
            is ExerciseDetailAction.OnMergeQueryChange -> dialog.update { (it as? Dialog.Merge)?.copy(query = action.query) ?: it }
            is ExerciseDetailAction.OnMergeTargetClick -> dialog.update { (it as? Dialog.Merge)?.copy(selectedId = action.exerciseId) ?: it }
            ExerciseDetailAction.OnConfirmMerge -> {
                // Merge is only enabled while the pick is in the (searched) list.
                val target = (dialog.value as? Dialog.Merge)?.selectedId ?: return
                change { if (!editor.mergeExercise(exerciseId, target)) eventChannel.send(ExerciseDetailEvent.ChangeFailed) }
            }
            // Navigation: ExerciseDetailScreenRoot handles it.
            ExerciseDetailAction.OnEditClick -> Unit
        }
    }

    /** "Delete?" when it's in no workout, otherwise the merge dialog. */
    private suspend fun openDeleteDialog() {
        val usage = editor.usage(exerciseId)
        dialog.value = if (usage.workouts == 0) Dialog.Delete(usage.templates) else Dialog.Merge(usage.workouts)
    }

    /** Deleted or merged: the exercise is gone, so [state] goes missing and the screen closes. */
    private fun change(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { e ->
                Log.e(TAG, "Changing exercise $exerciseId failed", e)
                dialog.value = null
                eventChannel.send(ExerciseDetailEvent.ChangeFailed)
            }
        }
    }

    private sealed interface Dialog {
        data class Delete(val templates: Int) : Dialog

        data class Merge(val workouts: Int, val query: String = "", val selectedId: Long? = null) : Dialog
    }

    private data class NoteInput(val exerciseName: String, val plateau: Plateau, val last: ExerciseSession)

    private data class Note(val text: String? = null, val isWriting: Boolean = false)

    private companion object {
        const val TAG = "ExerciseDetailViewModel"
    }
}

/** Another exercise logged the same way: its sets fit this one's. */
private fun Exercise.isMergeTargetFor(exercise: Exercise): Boolean = id != exercise.id && type == exercise.type && isTimed == exercise.isTimed

private fun Exercise.toCandidate() = MergeCandidateUi(id = id, name = name, muscleGroup = muscleGroup)
