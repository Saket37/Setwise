package dev.saketanand.setwise.ui.summary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.ai.PersonFacts
import dev.saketanand.setwise.domain.ai.WorkoutInsightWriter
import dev.saketanand.setwise.domain.model.WorkoutFacts
import dev.saketanand.setwise.domain.repository.TemplateRepository
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.DateProvider
import dev.saketanand.setwise.util.parseWeight
import java.time.LocalTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen: [WorkoutSummaryScreenRoot]. Shown after Finish and when opening a workout from History.
 *
 * @param workoutId from [Route.WorkoutSummary], passed in by SetwiseNavHost (Koin parametersOf).
 */
class WorkoutSummaryViewModel(
    private val workoutId: Long,
    private val workoutRepository: WorkoutRepository,
    private val templateRepository: TemplateRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val insightWriter: WorkoutInsightWriter,
    private val dateProvider: DateProvider,
) : ViewModel() {

    /** The model is asked once per screen; its text is saved, so reopening shows it straight away. */
    private var insightRequested = false

    /** Screen-only state on top of the workout: the edit-times dialog, the saved template. */
    private val overlays = MutableStateFlow(Overlays())

    private var isClosing = false
    private val eventChannel = Channel<WorkoutSummaryEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutSummaryEvent> = eventChannel.receiveAsFlow()

    val state: StateFlow<WorkoutSummaryUiState> = combine(
        workoutRepository.observeSession(workoutId).onEach { session ->
            // Deleted, or somehow still running: there's nothing to summarise.
            if (session == null || session.endedAt == null) close()
        },
        overlays,
        userSettingsRepository.settings.map { it.bodyWeightKg },
        workoutRepository.observeHistory(),
    ) { session, overlays, bodyWeightKg, history ->
        if (session == null || session.endedAt == null) return@combine WorkoutSummaryUiState(isLoading = false)
        val facts = WorkoutFacts.of(session, history)
        if (session.insight == null) requestInsight(facts)
        session.toSummaryUi(dateProvider.zone).copy(
            insight = InsightUi(facts, session.insight).takeIf { session.insight != null || facts.hasSomethingToSay },
            needsBodyWeight = bodyWeightKg == null && session.calories == null,
            bodyWeightDialog = overlays.bodyWeightDialog,
            isTemplateSaved = overlays.isTemplateSaved,
            editTimes = overlays.editTimes,
            isRenaming = overlays.isRenaming,
            isConfirmingDelete = overlays.isConfirmingDelete,
        )
    }
        .catch { e ->
            Log.e(TAG, "Loading the summary of workout $workoutId failed", e)
            emit(WorkoutSummaryUiState(isLoading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSummaryUiState())

    fun onAction(action: WorkoutSummaryAction) {
        when (action) {
            WorkoutSummaryAction.OnSaveAsTemplateClick -> saveAsTemplate()

            WorkoutSummaryAction.OnEditTimesClick -> {
                val state = state.value
                val start = state.startTime ?: return
                val end = state.endTime ?: return
                overlays.update { it.copy(editTimes = EditTimesUi(start, end)) }
            }
            is WorkoutSummaryAction.OnPickTime -> updateEditTimes { it.copy(picking = action.field) }
            is WorkoutSummaryAction.OnTimePicked -> updateEditTimes { it.withPicked(action.time) }
            WorkoutSummaryAction.OnTimePickerDismiss -> updateEditTimes { it.copy(picking = null) }
            WorkoutSummaryAction.OnSaveTimes -> saveTimes()
            WorkoutSummaryAction.OnEditTimesDismiss -> overlays.update { it.copy(editTimes = null) }

            WorkoutSummaryAction.OnAddBodyWeightClick -> overlays.update { it.copy(bodyWeightDialog = BodyWeightDialogUi()) }
            WorkoutSummaryAction.OnBodyWeightDismiss -> overlays.update { it.copy(bodyWeightDialog = null) }
            is WorkoutSummaryAction.OnSaveBodyWeight -> saveBodyWeight(action.text)

            WorkoutSummaryAction.OnDeleteClick -> overlays.update { it.copy(isConfirmingDelete = true) }
            WorkoutSummaryAction.OnDeleteDismiss -> overlays.update { it.copy(isConfirmingDelete = false) }
            WorkoutSummaryAction.OnConfirmDelete -> {
                overlays.update { it.copy(isConfirmingDelete = false) }
                viewModelScope.launch {
                    // Gone from the database → observeSession emits null → the summary closes.
                    runCatching { workoutRepository.deleteFinishedWorkout(workoutId) }
                        .onFailure { e ->
                            Log.e(TAG, "Deleting workout $workoutId failed", e)
                            eventChannel.send(WorkoutSummaryEvent.SaveFailed)
                        }
                }
            }

            WorkoutSummaryAction.OnRenameClick -> overlays.update { it.copy(isRenaming = true) }
            WorkoutSummaryAction.OnRenameDismiss -> overlays.update { it.copy(isRenaming = false) }
            is WorkoutSummaryAction.OnRenameConfirm -> {
                overlays.update { it.copy(isRenaming = false) }
                viewModelScope.launch {
                    runCatching { workoutRepository.renameWorkout(workoutId, action.name) }
                        .onFailure { e ->
                            Log.e(TAG, "Renaming workout $workoutId failed", e)
                            eventChannel.send(WorkoutSummaryEvent.SaveFailed)
                        }
                }
            }

            // Navigation: WorkoutSummaryScreenRoot handles these.
            WorkoutSummaryAction.OnDoneClick, is WorkoutSummaryAction.OnExerciseClick, WorkoutSummaryAction.OnEditSetsClick -> Unit
        }
    }

    private fun saveAsTemplate() {
        val current = overlays.value
        if (current.isTemplateSaved || current.isSavingTemplate || !state.value.canSaveAsTemplate) return
        overlays.update { it.copy(isSavingTemplate = true) }
        viewModelScope.launch {
            runCatching { templateRepository.createFromWorkout(workoutId, dateProvider.now()) }
                .onSuccess {
                    overlays.update { it.copy(isTemplateSaved = true, isSavingTemplate = false) }
                    eventChannel.send(WorkoutSummaryEvent.TemplateSaved)
                }
                .onFailure { e ->
                    Log.e(TAG, "Saving workout $workoutId as a template failed", e)
                    overlays.update { it.copy(isSavingTemplate = false) }
                    eventChannel.send(WorkoutSummaryEvent.SaveFailed)
                }
        }
    }

    private fun saveTimes() {
        val edit = overlays.value.editTimes ?: return
        val date = state.value.date ?: return
        val times = editedTimes(date, edit.start, edit.end, dateProvider.zone, dateProvider.now())
        if (times == null) {
            updateEditTimes { it.copy(isInvalid = true) }
            return
        }
        overlays.update { it.copy(editTimes = null) }
        // New times clear the saved insight (it mentions length and rests): ask the model again.
        insightRequested = false
        viewModelScope.launch {
            val (startedAt, endedAt) = times
            val saved = runCatching { workoutRepository.updateFinishedTimes(workoutId, startedAt, endedAt) }
                .onFailure { e -> Log.e(TAG, "Saving the times of workout $workoutId failed", e) }
                .getOrDefault(false)
            if (!saved) eventChannel.send(WorkoutSummaryEvent.SaveFailed)
        }
    }

    /** Saved in Settings; CalorieSync then estimates this workout (and others missing one). */
    private fun saveBodyWeight(text: String) {
        val kg = parseWeight(text)
        if (kg == null) {
            overlays.update { it.copy(bodyWeightDialog = BodyWeightDialogUi(isInvalid = true)) }
            return
        }
        viewModelScope.launch {
            val saved = runCatching { userSettingsRepository.setBodyWeightKg(kg) }
                .onFailure { e -> Log.e(TAG, "Saving the body weight failed", e) }
                .getOrDefault(false)
            overlays.update { it.copy(bodyWeightDialog = if (saved) null else BodyWeightDialogUi(isInvalid = true)) }
        }
    }

    /** Has the model write the insight (if it's there), and saves it: the session then re-emits with it. */
    private fun requestInsight(facts: WorkoutFacts) {
        if (insightRequested) return
        insightRequested = true
        viewModelScope.launch {
            val text = runCatching { insightWriter.write(facts, PersonFacts.from(userSettingsRepository.settings.first())) }
                .onFailure { e -> Log.w(TAG, "Writing the insight of workout $workoutId failed", e) }
                .getOrNull() ?: return@launch
            runCatching { workoutRepository.setInsight(workoutId, text) }
                .onFailure { e -> Log.e(TAG, "Saving the insight of workout $workoutId failed", e) }
        }
    }

    private fun updateEditTimes(change: (EditTimesUi) -> EditTimesUi) =
        overlays.update { overlays -> overlays.copy(editTimes = overlays.editTimes?.let(change)) }

    private fun EditTimesUi.withPicked(time: LocalTime): EditTimesUi = when (picking) {
        TimeField.Start -> copy(start = time, picking = null, isInvalid = false)
        TimeField.End -> copy(end = time, picking = null, isInvalid = false)
        null -> this
    }

    private fun close() {
        if (isClosing) return
        isClosing = true
        eventChannel.trySend(WorkoutSummaryEvent.Closed)
    }

    private data class Overlays(
        val editTimes: EditTimesUi? = null,
        val isRenaming: Boolean = false,
        val isConfirmingDelete: Boolean = false,
        val bodyWeightDialog: BodyWeightDialogUi? = null,
        val isSavingTemplate: Boolean = false,
        val isTemplateSaved: Boolean = false,
    )

    private companion object {
        const val TAG = "WorkoutSummaryViewModel"
    }
}
