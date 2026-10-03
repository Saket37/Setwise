package dev.saketanand.setwise.ui.workout

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen: [ActiveWorkoutScreenRoot].
 *
 * The database is the source of truth: every edit is saved at once (so nothing is lost if the
 * app is killed mid-workout), and the screen redraws from the Room flow. All writes go through
 * one queue, so they reach the database in the order the user made them.
 *
 * @param workoutId from [Route.ActiveWorkout], passed in by SetwiseNavHost (Koin parametersOf)
 *   rather than read with toRoute(), which needs an Android Bundle and can't run in unit tests.
 */
class ActiveWorkoutViewModel(
    private val workoutId: Long,
    private val workoutRepository: WorkoutRepository,
    private val dateProvider: DateProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Exercise the user opened or closed; null = automatic (first one with sets left). */
    private val expandedChoice = savedStateHandle.getStateFlow<Long?>(KEY_EXPANDED, null)

    /** Dialogs and other screen-only state. */
    private val overlays = MutableStateFlow(Overlays())

    /** Latest data from the database, for actions that need the stored values. */
    private var session: WorkoutSession? = null
    private var isClosing = false

    private val eventChannel = Channel<ActiveWorkoutEvent>(Channel.BUFFERED)
    val events: Flow<ActiveWorkoutEvent> = eventChannel.receiveAsFlow()

    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    val state: StateFlow<ActiveWorkoutUiState> = combine(
        workoutRepository.observeSession(workoutId).onEach { session ->
            this.session = session
            // Deleted (e.g. discarded): nothing left to show.
            if (session == null) close()
        },
        expandedChoice,
        overlays,
    ) { session, expandedChoice, overlays ->
        if (session == null) return@combine ActiveWorkoutUiState(isLoading = false)
        val exercises = session.exercises.map { it.toUi() }
        val startedAt = session.startedAt.atZone(dateProvider.zone)
        ActiveWorkoutUiState(
            isLoading = false,
            name = session.name,
            startedAtMillis = session.startedAt.toEpochMilli(),
            startTime = startedAt.toLocalTime(),
            exercises = exercises,
            expandedExerciseId = expandedExerciseId(exercises, expandedChoice),
            dialog = overlays.dialog,
            isStartTimePickerVisible = overlays.isStartTimePickerVisible,
            isFinishing = overlays.isFinishing,
        )
    }
        .catch { e ->
            Log.e(TAG, "Loading workout $workoutId failed", e)
            emit(ActiveWorkoutUiState(isLoading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())

    init {
        // The write queue: one write at a time, in order.
        viewModelScope.launch {
            for (write in writes) {
                runCatching { write() }.onFailure { e ->
                    Log.e(TAG, "Saving workout $workoutId failed", e)
                    eventChannel.send(ActiveWorkoutEvent.SaveFailed)
                }
            }
        }
    }

    fun onAction(action: ActiveWorkoutAction) {
        when (action) {
            ActiveWorkoutAction.OnFinishClick -> requestFinish()
            ActiveWorkoutAction.OnConfirmFinish -> finish()

            ActiveWorkoutAction.OnStartTimeClick -> overlays.update { it.copy(isStartTimePickerVisible = true) }
            ActiveWorkoutAction.OnStartTimePickerDismiss -> overlays.update { it.copy(isStartTimePickerVisible = false) }
            is ActiveWorkoutAction.OnStartTimeChange -> changeStartTime(action.time)

            ActiveWorkoutAction.OnDiscardWorkoutClick -> showDialog(ActiveWorkoutDialog.ConfirmDiscard)
            ActiveWorkoutAction.OnConfirmDiscard -> discard()
            ActiveWorkoutAction.OnConfirmRemoveExercise -> {
                val dialog = overlays.value.dialog as? ActiveWorkoutDialog.RemoveExercise ?: return
                showDialog(null)
                write { workoutRepository.removeExercise(dialog.workoutExerciseId) }
            }
            ActiveWorkoutAction.OnDismissDialog -> showDialog(null)

            is ActiveWorkoutAction.OnExerciseHeaderClick -> {
                val isOpen = state.value.expandedExerciseId == action.workoutExerciseId
                savedStateHandle[KEY_EXPANDED] = if (isOpen) ALL_COLLAPSED else action.workoutExerciseId
            }
            is ActiveWorkoutAction.OnExercisesPicked -> addExercises(action.exerciseIds)
            is ActiveWorkoutAction.OnRemoveExerciseClick -> requestRemoveExercise(action.workoutExerciseId)

            is ActiveWorkoutAction.OnAddSetClick -> write { workoutRepository.addSet(action.workoutExerciseId) }
            is ActiveWorkoutAction.OnSetValuesChange -> saveValues(action.setId, action.weight, action.reps)
            is ActiveWorkoutAction.OnSetDoneToggle -> toggleDone(action.setId, action.weight, action.reps)
            is ActiveWorkoutAction.OnDeleteSet -> write { workoutRepository.deleteSet(action.setId) }

            // Navigation: ActiveWorkoutScreenRoot handles these.
            ActiveWorkoutAction.OnMinimizeClick,
            ActiveWorkoutAction.OnAddExerciseClick,
            is ActiveWorkoutAction.OnLogCardioClick -> Unit
        }
    }

    // Sets

    private fun saveValues(setId: Long, weight: String, reps: String) {
        val kind = findSet(setId)?.first?.kind ?: return
        write { workoutRepository.updateSetValues(setId, kind, parseWeight(weight), parseAmount(reps)) }
    }

    private fun toggleDone(setId: Long, weight: String, reps: String) {
        val (exercise, set) = findSet(setId) ?: return
        if (set.isCompleted) {
            // Un-tick, keeping the values.
            write {
                workoutRepository.setCompleted(setId, exercise.kind, completedAt = null, parseWeight(set.weight), parseAmount(set.reps))
            }
            return
        }
        // Empty fields mean "same as the hint" (usually last time's numbers).
        val weightKg = parseWeight(weight) ?: parseWeight(set.weightHint)
        val amount = parseAmount(reps) ?: parseAmount(set.repsHint) ?: return // nothing to log
        write {
            workoutRepository.setCompleted(setId, exercise.kind, completedAt = dateProvider.now(), weightKg, amount)
        }
        // Last open set of the open exercise done → move on to the next one with sets left.
        val wasLastOpenSet = exercise.sets.count { !it.isCompleted } == 1
        if (wasLastOpenSet && state.value.expandedExerciseId == exercise.id) {
            savedStateHandle[KEY_EXPANDED] = null
        }
        // TODO (milestone 6): start the rest timer here.
    }

    private fun findSet(setId: Long): Pair<WorkoutExerciseUi, SetUi>? =
        state.value.exercises.firstNotNullOfOrNull { exercise ->
            exercise.sets.firstOrNull { it.id == setId }?.let { exercise to it }
        }

    // Exercises

    private fun addExercises(exerciseIds: List<Long>) {
        if (exerciseIds.isEmpty()) return
        write {
            val newIds = workoutRepository.addExercises(workoutId, exerciseIds)
            // Open the first one added: that's what the user wants to log next.
            savedStateHandle[KEY_EXPANDED] = newIds.firstOrNull()
        }
    }

    private fun requestRemoveExercise(workoutExerciseId: Long) {
        val exercise = state.value.exercises.firstOrNull { it.id == workoutExerciseId } ?: return
        if (exercise.completedSets == 0) {
            write { workoutRepository.removeExercise(workoutExerciseId) }
        } else {
            showDialog(ActiveWorkoutDialog.RemoveExercise(workoutExerciseId, exercise.name, exercise.completedSets))
        }
    }

    // Workout

    private fun requestFinish() {
        val state = state.value
        when {
            state.isFinishing -> Unit
            state.completedSets == 0 -> showDialog(ActiveWorkoutDialog.NothingLogged)
            state.incompleteSets > 0 -> showDialog(ActiveWorkoutDialog.FinishWithIncompleteSets(state.incompleteSets))
            else -> finish()
        }
    }

    private fun finish() {
        if (overlays.value.isFinishing) return
        overlays.update { it.copy(dialog = null, isFinishing = true) }
        write {
            try {
                // Goes through the queue, so edits made just before Finish are saved first.
                if (workoutRepository.finishWorkout(workoutId, dateProvider.now())) {
                    isClosing = true
                    eventChannel.send(ActiveWorkoutEvent.Finished(workoutId))
                }
            } finally {
                overlays.update { it.copy(isFinishing = false) }
            }
        }
    }

    private fun discard() {
        showDialog(null)
        write {
            workoutRepository.discardWorkout(workoutId)
            close()
        }
    }

    /** Leaves the screen once, however many reasons there are (discard + the row disappearing). */
    private fun close() {
        if (isClosing) return
        isClosing = true
        viewModelScope.launch { eventChannel.send(ActiveWorkoutEvent.Closed) }
    }

    private fun changeStartTime(time: LocalTime) {
        overlays.update { it.copy(isStartTimePickerVisible = false) }
        val current = session?.startedAt ?: return
        val zone = dateProvider.zone
        // Same day, new time; a start in the future makes no sense, so that becomes "now".
        val picked = current.atZone(zone).with(time).toInstant()
        val now = dateProvider.now()
        write { workoutRepository.updateStartTime(workoutId, if (picked.isAfter(now)) now else picked) }
    }

    private fun showDialog(dialog: ActiveWorkoutDialog?) = overlays.update { it.copy(dialog = dialog) }

    private fun write(block: suspend () -> Unit) {
        writes.trySend(block)
    }

    /** The user's choice if it still exists, else the first exercise with sets left (or the last one). */
    private fun expandedExerciseId(exercises: List<WorkoutExerciseUi>, choice: Long?): Long? = when {
        choice == ALL_COLLAPSED -> null
        choice != null && exercises.any { it.id == choice } -> choice
        else -> exercises.firstOrNull { it.kind != SetKind.Cardio && it.completedSets < it.sets.size }?.id
            ?: exercises.lastOrNull()?.id
    }

    private data class Overlays(
        val dialog: ActiveWorkoutDialog? = null,
        val isStartTimePickerVisible: Boolean = false,
        val isFinishing: Boolean = false,
    )

    private companion object {
        const val TAG = "ActiveWorkoutViewModel"
        const val KEY_EXPANDED = "expanded_exercise"
        const val ALL_COLLAPSED = -1L
    }
}

/** Puts the reps field's number in the right column: reps, or seconds for timed exercises. */
private suspend fun WorkoutRepository.updateSetValues(setId: Long, kind: SetKind, weightKg: Double?, amount: Int?) =
    if (kind == SetKind.Duration) {
        updateSetValues(setId, weightKg = null, reps = null, durationSec = amount)
    } else {
        updateSetValues(setId, weightKg = weightKg, reps = amount, durationSec = null)
    }

private suspend fun WorkoutRepository.setCompleted(
    setId: Long,
    kind: SetKind,
    completedAt: Instant?,
    weightKg: Double?,
    amount: Int?,
) = if (kind == SetKind.Duration) {
    setCompleted(setId, completedAt, weightKg = null, reps = null, durationSec = amount)
} else {
    setCompleted(setId, completedAt, weightKg = weightKg, reps = amount, durationSec = null)
}
