package dev.saketanand.setwise.ui.workout

import dev.saketanand.setwise.util.parseWeight
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.timer.RestNotificationRefresher
import dev.saketanand.setwise.timer.RestTimer
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.DateProvider
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.saketanand.setwise.domain.ai.QuickLogInterpreter
import dev.saketanand.setwise.domain.ai.QuickLogResult
import dev.saketanand.setwise.domain.ai.SuggestionSource
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.first
import dev.saketanand.setwise.domain.ai.Heard
import dev.saketanand.setwise.domain.ai.ModelAvailability
import dev.saketanand.setwise.domain.ai.SpeechInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.withTimeoutOrNull
import dev.saketanand.setwise.domain.repository.UserSettingsRepository

/**
 * Screen: [ActiveWorkoutScreenRoot].
 *
 * The database is the source of truth: every edit is saved at once (so nothing is lost if the
 * app is killed mid-workout), and the screen redraws from the Room flow. All writes go through
 * one queue, so they reach the database in the order the user made them. The queue runs in
 * [writeScope] (app-wide), not viewModelScope: minimising right after typing clears this
 * ViewModel, and the last edits must still be saved.
 *
 * @param workoutId from [Route.ActiveWorkout], passed in by SetwiseNavHost (Koin parametersOf)
 *   rather than read with toRoute(), which needs an Android Bundle and can't run in unit tests.
 */
class ActiveWorkoutViewModel(
    private val workoutId: Long,
    private val workoutRepository: WorkoutRepository,
    private val dateProvider: DateProvider,
    private val savedStateHandle: SavedStateHandle,
    private val writeScope: CoroutineScope,
    private val restTimer: RestTimer,
    private val restNotifications: RestNotificationRefresher,
    private val quickLogInterpreter: QuickLogInterpreter,
    private val exerciseRepository: ExerciseRepository,
    private val speechInput: SpeechInput,
    userSettings: UserSettingsRepository,
    /** A finished workout opened from its summary to edit its sets. */
    private val isEditingFinished: Boolean = false,
) : ViewModel() {

    /** Settings' default rest; null: each exercise's own. */
    private var restSecOverride: Int? = null

    /** On-device listening in progress, if any. */
    private var listening: Job? = null
    private var speechDownloadStarted = false

    /** What the "Understood as" card shows, kept to add on confirm. */
    private var pendingQuickLog: QuickLogResult? = null

    /** The line the card (or "couldn't understand") is about. */
    private var readLine: String? = null

    /** Exercise the user opened or closed; null = automatic (first one with sets left). */
    private val expandedChoice = savedStateHandle.getStateFlow<Long?>(KEY_EXPANDED, null)

    /** Dialogs and other screen-only state. */
    private val overlays = MutableStateFlow(Overlays())

    /** Latest data from the database, for actions that need the stored values. */
    private var session: WorkoutSession? = null
    private var isClosing = false
    private var exerciseIdsAtStart: List<Long>? = null

    private val eventChannel = Channel<ActiveWorkoutEvent>(Channel.BUFFERED)
    val events: Flow<ActiveWorkoutEvent> = eventChannel.receiveAsFlow()

    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    val state: StateFlow<ActiveWorkoutUiState> = combine(
        workoutRepository.observeSession(workoutId).onEach { session ->
            this.session = session
            // Deleted (discarded), or finished while it was running here: nothing to edit.
            if (session == null || (session.endedAt != null && !isEditingFinished)) close()
            // What it had when editing began: records of later workouts may depend on those.
            if (session != null && exerciseIdsAtStart == null) exerciseIdsAtStart = session.exercises.map { it.exercise.id }
        },
        expandedChoice,
        overlays,
        // Only this workout's rest (a stale one from another workout is ignored).
        restTimer.state.map { rest -> rest?.takeIf { it.workoutId == workoutId } },
    ) { session, expandedChoice, overlays, rest ->
        if (session == null) return@combine ActiveWorkoutUiState(isLoading = false)
        val loggedAfterwards = isLoggedAfterwards(session.startedAt, dateProvider.now())
        // Progression hints are for the session at hand, not one logged afterwards or being edited.
        val exercises = session.exercises.map { it.toUi(hintsAt = session.startedAt.takeUnless { loggedAfterwards || isEditingFinished }) }
        val startedAt = session.startedAt.atZone(dateProvider.zone)
        ActiveWorkoutUiState(
            isLoading = false,
            name = session.name,
            startedAtMillis = session.startedAt.toEpochMilli(),
            startTime = startedAt.toLocalTime(),
            // Logged afterwards (a past day): show the date, not a days-long clock.
            pastDay = startedAt.toLocalDate().takeIf { loggedAfterwards },
            exercises = exercises,
            expandedExerciseId = expandedExerciseId(exercises, expandedChoice),
            dialog = overlays.dialog,
            isStartTimePickerVisible = overlays.isStartTimePickerVisible,
            isFinishing = overlays.isFinishing,
            rest = rest?.toUi(),
            quickLog = overlays.quickLog,
            onDeviceSpeech = overlays.onDeviceSpeech,
            isEditingFinished = isEditingFinished,
        )
    }
        .catch { e ->
            Log.e(TAG, "Loading workout $workoutId failed", e)
            emit(ActiveWorkoutUiState(isLoading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())

    init {
        viewModelScope.launch { userSettings.settings.collect { restSecOverride = it.restSecOverride } }
        // On-device speech for the mic, if the phone has it ready.
        viewModelScope.launch {
            val ready = speechInput.availability() == ModelAvailability.Ready
            overlays.update { it.copy(onDeviceSpeech = ready) }
        }
        // The write queue: one write at a time, in order. Main.immediate: the writes also update
        // SavedStateHandle, which belongs on the main thread (Room does its own threading).
        writeScope.launch(Dispatchers.Main.immediate) {
            for (write in writes) {
                try {
                    write()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Saving workout $workoutId failed", e)
                    eventChannel.trySend(ActiveWorkoutEvent.SaveFailed)
                }
            }
        }
    }

    override fun onCleared() {
        // No new writes; the queued ones still run, then the loop above ends.
        writes.close()
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

            is ActiveWorkoutAction.OnQuickLogSubmit -> readQuickLog(action.text)
            ActiveWorkoutAction.OnStartListening -> startListening()
            ActiveWorkoutAction.OnStopListening -> viewModelScope.launch { speechInput.stop() }
            ActiveWorkoutAction.OnPhoneSpeechUsed -> prepareOnDeviceSpeech()
            ActiveWorkoutAction.OnQuickLogConfirm -> confirmQuickLog()
            ActiveWorkoutAction.OnQuickLogEdit -> {
                pendingQuickLog = null
                overlays.update { it.copy(quickLog = QuickLogUi()) }
            }
            is ActiveWorkoutAction.OnQuickLogEdited -> {
                val quickLog = overlays.value.quickLog
                if (!quickLog.isReading && action.text.trim() != readLine && (quickLog.preview != null || quickLog.problem != null)) {
                    pendingQuickLog = null
                    overlays.update { it.copy(quickLog = QuickLogUi()) }
                }
            }

            ActiveWorkoutAction.OnRenameClick -> showDialog(ActiveWorkoutDialog.Rename(state.value.name))
            is ActiveWorkoutAction.OnRenameConfirm -> {
                showDialog(null)
                write { workoutRepository.renameWorkout(workoutId, action.name) }
            }

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

            is ActiveWorkoutAction.OnRestAdjust -> restTimer.adjust(action.deltaSec)
            ActiveWorkoutAction.OnRestSkip -> restTimer.skip()
            // The running rest's notification was posted before this was allowed: post it again.
            ActiveWorkoutAction.OnNotificationsAllowed -> restNotifications.refresh()

            // Navigation: ActiveWorkoutScreenRoot handles these.
            ActiveWorkoutAction.OnMinimizeClick,
            ActiveWorkoutAction.OnAddExerciseClick,
            is ActiveWorkoutAction.OnLogCardioClick,
            is ActiveWorkoutAction.OnExerciseHistoryClick -> Unit
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
        val amount = loggedAmount(reps, set.repsHint) ?: return // nothing to log
        write {
            workoutRepository.setCompleted(setId, exercise.kind, completedAt = dateProvider.now(), weightKg, amount)
        }
        // Last open set of the open exercise done → move on to the next one with sets left.
        val wasLastOpenSet = exercise.sets.count { !it.isCompleted } == 1
        if (wasLastOpenSet && state.value.expandedExerciseId == exercise.id) {
            savedStateHandle[KEY_EXPANDED] = null
        }
        // Logged afterwards (a past day): nobody is resting now, so no timer, notification or
        // notification-permission prompt.
        val startedAt = session?.startedAt
        if (isEditingFinished || (startedAt != null && isLoggedAfterwards(startedAt, dateProvider.now()))) return
        // Rest before the next set (a new rest replaces a running one).
        // Settings' default rest, if set, for everything that rests (cardio doesn't).
        val restSec = restSecOverride?.takeIf { exercise.restSec > 0 } ?: exercise.restSec
        restTimer.start(workoutId, restSec, nextUpAfter(setId, exercise, state.value.exercises))
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
            isEditingFinished && state.incompleteSets > 0 -> showDialog(ActiveWorkoutDialog.FinishWithIncompleteSets(state.incompleteSets))
            isEditingFinished -> finish()
            state.completedSets == 0 -> showDialog(ActiveWorkoutDialog.NothingLogged)
            state.incompleteSets > 0 -> showDialog(ActiveWorkoutDialog.FinishWithIncompleteSets(state.incompleteSets))
            else -> finish()
        }
    }

    private fun finish() {
        if (isEditingFinished) return saveEdits()
        if (overlays.value.isFinishing) return
        overlays.update { it.copy(dialog = null, isFinishing = true) }
        // Set before the workout is marked finished, so seeing it finished doesn't also "close".
        isClosing = true
        write {
            // Goes through the queue, so edits made just before Finish are saved first.
            val startedAt = session?.startedAt ?: dateProvider.now()
            val endedAt = finishTime(startedAt, dateProvider.now())
            val finished = runCatching { workoutRepository.finishWorkout(workoutId, endedAt) }
                .onFailure { e -> Log.e(TAG, "Finishing workout $workoutId failed", e) }
                .getOrDefault(false)
            if (finished) {
                restTimer.cancel(workoutId)
                // Stays "finishing" (Finish disabled) until the summary opens.
                eventChannel.trySend(ActiveWorkoutEvent.Finished(workoutId))
            } else {
                isClosing = false
                overlays.update { it.copy(isFinishing = false) }
                eventChannel.trySend(ActiveWorkoutEvent.SaveFailed)
            }
        }
    }

    /** Editing a finished workout: tidy its sets, recheck records, and go back to its summary. */
    private fun saveEdits() {
        if (overlays.value.isFinishing) return
        overlays.update { it.copy(dialog = null, isFinishing = true) }
        isClosing = true
        write {
            val exerciseIds = exerciseIdsAtStart.orEmpty()
            val saved = runCatching { workoutRepository.finishEditing(workoutId, exerciseIds) }
                .onFailure { e -> Log.e(TAG, "Saving the edits of workout $workoutId failed", e) }
                .getOrDefault(false)
            if (saved) {
                eventChannel.trySend(ActiveWorkoutEvent.Finished(workoutId))
            } else {
                isClosing = false
                overlays.update { it.copy(isFinishing = false) }
                eventChannel.trySend(ActiveWorkoutEvent.SaveFailed)
            }
        }
    }

    private fun discard() {
        showDialog(null)
        write {
            workoutRepository.discardWorkout(workoutId)
            restTimer.cancel(workoutId)
            close()
        }
    }

    /** Leaves the screen once, however many reasons there are (discard + the row disappearing). */
    private fun close() {
        if (isClosing) return
        isClosing = true
        eventChannel.trySend(ActiveWorkoutEvent.Closed)
    }

    private fun changeStartTime(time: LocalTime) {
        overlays.update { it.copy(isStartTimePickerVisible = false) }
        val current = session?.startedAt ?: return
        val startedAt = pickedStartTime(time, current, dateProvider.now(), dateProvider.zone)
        write { workoutRepository.updateStartTime(workoutId, startedAt) }
    }

    private fun showDialog(dialog: ActiveWorkoutDialog?) = overlays.update { it.copy(dialog = dialog) }

    // Quick log

    private fun readQuickLog(text: String) {
        val current = session ?: return
        if (text.isBlank() || overlays.value.quickLog.isReading) return
        readLine = text.trim()
        overlays.update { it.copy(quickLog = QuickLogUi(isReading = true)) }
        viewModelScope.launch {
            val result = interpret(text.trim(), current)
            pendingQuickLog = result
            overlays.update { it.copy(quickLog = result.toUi()) }
        }
    }

    private suspend fun interpret(text: String, current: WorkoutSession): QuickLogResult = runCatching {
        val recent = exerciseRepository.observeRecentExercises(RECENT_EXERCISES).first().map { it.exercise }
        val library = exerciseRepository.observeExercises("", null).first()
        quickLogInterpreter.interpret(text, current, state.value.expandedExerciseId, recent, library)
    }.getOrElse { e ->
        if (e is CancellationException) throw e
        Log.e(TAG, "Reading quick log '$text' failed", e)
        QuickLogResult.NotUnderstood(QuickLogResult.Reason.NothingToLog)
    }

    /**
     * Listens on-device: what's heard shows as it comes; after a finished stretch of speech and
     * a short pause (or Stop, or [MAX_LISTEN_MS]) it goes in the bar and is read.
     */
    private fun startListening() {
        if (listening?.isActive == true) return
        pendingQuickLog = null
        overlays.update { it.copy(quickLog = QuickLogUi(isListening = true)) }
        listening = viewModelScope.launch {
            val said = StringBuilder()
            var failed = false
            try {
                withTimeoutOrNull(MAX_LISTEN_MS) {
                    coroutineScope {
                        var pause: Job? = null
                        speechInput.listen().collect { heard ->
                            pause?.cancel()
                            when (heard) {
                                is Heard.Partial -> showHeard("$said ${heard.text}")
                                is Heard.Final -> {
                                    said.append(' ').append(heard.text.trim())
                                    showHeard(said.toString())
                                    pause = launch {
                                        delay(PAUSE_AFTER_SPEECH_MS)
                                        speechInput.stop()
                                    }
                                }
                            }
                        }
                        pause?.cancel()
                    }
                } ?: speechInput.stop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Listening failed", e)
                failed = true
            }
            val text = said.toString().trim()
            overlays.update {
                it.copy(
                    quickLog = QuickLogUi(
                        micProblem = when {
                            text.isNotEmpty() -> null
                            failed -> MicProblem.Failed
                            else -> MicProblem.NothingHeard
                        },
                    ),
                )
            }
            if (text.isNotEmpty()) {
                eventChannel.trySend(ActiveWorkoutEvent.QuickLogHeard(text))
                readQuickLog(text)
            }
        }
    }

    private fun showHeard(text: String) =
        overlays.update { it.copy(quickLog = it.quickLog.copy(heard = text.replace(Regex("\\s+"), " ").trim())) }

    /** The phone's recognizer was used: get on-device speech ready for next time (once). */
    private fun prepareOnDeviceSpeech() {
        if (speechDownloadStarted) return
        speechDownloadStarted = true
        writeScope.launch {
            if (speechInput.availability() != ModelAvailability.Downloadable) return@launch
            val last = speechInput.download().lastOrNull()
            Log.i(TAG, "Speech recognition download: $last")
        }
    }

    private fun QuickLogResult.toUi(): QuickLogUi = when (this) {
        is QuickLogResult.NotUnderstood -> QuickLogUi(problem = reason)
        is QuickLogResult.Sets -> QuickLogUi(
            preview = QuickLogPreview(
                exerciseName = target.exercise.name,
                matchedFrom = target.matchedFrom,
                isInWorkout = target.workoutExerciseId != null,
                kind = target.exercise.setKind,
                sets = sets,
                byModel = source == SuggestionSource.Model,
            ),
        )
        is QuickLogResult.Cardio -> QuickLogUi(
            preview = QuickLogPreview(
                exerciseName = target.exercise.name,
                matchedFrom = target.matchedFrom,
                isInWorkout = target.workoutExerciseId != null,
                kind = SetKind.Cardio,
                cardio = values,
            ),
        )
    }

    /** Adds what the card shows, through the write queue (after any edit typed just before). */
    private fun confirmQuickLog() {
        val result = pendingQuickLog ?: return
        pendingQuickLog = null
        overlays.update { it.copy(quickLog = QuickLogUi()) }
        eventChannel.trySend(ActiveWorkoutEvent.QuickLogAdded)
        write {
            val now = dateProvider.now()
            val itemId = when (result) {
                is QuickLogResult.Sets ->
                    workoutRepository.logSets(workoutId, result.target.workoutExerciseId, result.target.exercise.id, result.sets, now)
                is QuickLogResult.Cardio -> {
                    val id = result.target.workoutExerciseId
                        ?: workoutRepository.addExercises(workoutId, listOf(result.target.exercise.id)).single()
                    workoutRepository.logCardio(id, result.values, now)
                    id
                }
                is QuickLogResult.NotUnderstood -> return@write
            }
            // Open what was just logged, so it can be checked or changed.
            savedStateHandle[KEY_EXPANDED] = itemId
        }
    }

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
        val quickLog: QuickLogUi = QuickLogUi(),
        val onDeviceSpeech: Boolean = false,
    )

    private companion object {
        const val TAG = "ActiveWorkoutViewModel"
        const val KEY_EXPANDED = "expanded_exercise"
        const val ALL_COLLAPSED = -1L

        /** Listening stops on its own after this long. */
        const val MAX_LISTEN_MS = 20_000L

        /** A pause this long after a finished stretch of speech ends listening. */
        const val PAUSE_AFTER_SPEECH_MS = 1_200L

        /** Exercises done before that a quick-logged name is matched against before the library. */
        const val RECENT_EXERCISES = 50
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
