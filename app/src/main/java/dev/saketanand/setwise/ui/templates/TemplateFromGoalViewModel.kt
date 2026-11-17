package dev.saketanand.setwise.ui.templates

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.ai.GoalPlanAssistant
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.Goal
import dev.saketanand.setwise.domain.model.GoalAdvice
import dev.saketanand.setwise.domain.model.GoalPlanner
import dev.saketanand.setwise.domain.model.GoalReader
import dev.saketanand.setwise.domain.model.PlannedTemplate
import dev.saketanand.setwise.domain.model.TemplateDraft
import dev.saketanand.setwise.domain.model.TemplateDraftExercise
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.TemplateRepository
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.util.DateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen: [TemplateFromGoalScreenRoot]. As the goal is typed, it's read ([GoalReader]) and planned
 * in code from the library; after a pause, the on-device model may refine the choices
 * ([GoalPlanAssistant]). "Regenerate" shows code's next candidates. Saving creates every
 * template (with target reps) and opens the first in the editor.
 */
class TemplateFromGoalViewModel(
    private val templateRepository: TemplateRepository,
    private val exerciseRepository: ExerciseRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val assistant: GoalPlanAssistant,
    private val dateProvider: DateProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(TemplateFromGoalUiState())
    val state: StateFlow<TemplateFromGoalUiState> = _state.asStateFlow()

    private val eventChannel = Channel<TemplateFromGoalEvent>(Channel.BUFFERED)
    val events: Flow<TemplateFromGoalEvent> = eventChannel.receiveAsFlow()

    private var text = ""
    private var variation = 0
    private var plan: List<PlannedTemplate> = emptyList()
    private var planning: Job? = null
    private var library: List<Exercise>? = null
    private var doneIds: Set<Long> = emptySet()

    fun onAction(action: TemplateFromGoalAction) {
        when (action) {
            is TemplateFromGoalAction.OnGoalChange -> {
                if (action.text == text) return
                text = action.text
                variation = 0
                replan(debounce = true)
            }
            TemplateFromGoalAction.OnRegenerateClick -> {
                variation++
                replan(debounce = false)
            }
            TemplateFromGoalAction.OnSaveClick -> save()
        }
    }

    /** Code's draft (after a pause while typing), then the model's choices when it's there. */
    private fun replan(debounce: Boolean) {
        planning?.cancel()
        if (text.isBlank()) {
            plan = emptyList()
            _state.update { TemplateFromGoalUiState() }
            return
        }
        planning = viewModelScope.launch {
            if (debounce) delay(TYPING_PAUSE_MS)
            // The library, the settings or the model failing leaves the last draft shown.
            @Suppress("TooGenericExceptionCaught")
            try {
                val settings = userSettingsRepository.settings.first()
                val goal = GoalReader.read(text, defaultDays = settings.trainingDays.size.takeIf { it > 0 })
                val advice = GoalAdvice.of(text, settings.bodyWeightKg)
                _state.update { it.copy(advice = advice?.let(::GoalAdviceUi)) }
                val exercises = library()
                val modelChooses = variation == 0 && assistant.canChoose()
                show(goal, GoalPlanner.plan(goal, exercises, doneIds, variation), byModel = false, isChoosing = modelChooses)
                if (modelChooses) {
                    val byModel = assistant.plan(text, goal, exercises, doneIds)
                    show(goal, byModel.templates, byModel = byModel.byModel, isChoosing = false)
                    // Then the goal note in words, one model call at a time.
                    advice?.let { assistant.note(it) }?.let { note -> _state.update { it.copy(advice = GoalAdviceUi(advice, note)) } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Planning the goal failed", e)
                _state.update { it.copy(isChoosing = false) }
            }
        }
    }

    private suspend fun library(): List<Exercise> = library ?: run {
        doneIds = exerciseRepository.observeRecentExercises(DONE_EXERCISES).first().mapTo(HashSet()) { it.exercise.id }
        exerciseRepository.observeExercises("", null).first().also { library = it }
    }

    private fun show(goal: Goal, templates: List<PlannedTemplate>, byModel: Boolean, isChoosing: Boolean) {
        plan = templates
        _state.update {
            it.copy(
                understood = GoalChipsUi(goal.type, goal.daysPerWeek, goal.minutes, goal.gear),
                templates = templates.map { t ->
                    PlannedTemplateUi(t.name, t.estimatedMinutes, t.exercises.map { e -> PlannedExerciseUi(e.exercise.name, e.sets, e.reps, e.isTimed, e.isCardio) })
                },
                byModel = byModel,
                isChoosing = isChoosing,
            )
        }
    }

    private fun save() {
        val templates = plan
        if (templates.isEmpty() || _state.value.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            // Any repository failure: say so; nothing more is saved.
            @Suppress("TooGenericExceptionCaught")
            val ids = try {
                templates.map { template ->
                    val draft = TemplateDraft(
                        id = 0,
                        name = template.name,
                        category = template.category,
                        // A cardio finisher is one entry, without target reps (its minutes are a suggestion).
                        exercises = template.exercises.map {
                            if (it.isCardio) TemplateDraftExercise(it.exercise.id, 1) else TemplateDraftExercise(it.exercise.id, it.sets, it.reps)
                        },
                    )
                    templateRepository.saveTemplate(draft, dateProvider.now())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Saving the goal's templates failed", e)
                null
            }
            _state.update { it.copy(isSaving = false) }
            eventChannel.send(ids?.firstOrNull()?.let(TemplateFromGoalEvent::Saved) ?: TemplateFromGoalEvent.SaveFailed)
        }
    }

    private companion object {
        const val TAG = "TemplateFromGoalVM"
        const val TYPING_PAUSE_MS = 600L
        const val DONE_EXERCISES = 200
    }
}
