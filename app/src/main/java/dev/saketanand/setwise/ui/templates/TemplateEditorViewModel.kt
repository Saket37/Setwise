package dev.saketanand.setwise.ui.templates

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.TemplateRepository
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.util.DateProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Screen: [TemplateEditorScreenRoot]. Creates a template, or edits one: name, category, and its
 * exercises with their set counts. Nothing is written until Save. The draft (and what it was
 * opened with) survive the app being killed in the background.
 *
 * @param templateId from [Route.TemplateEditor]; [Route.NEW_TEMPLATE_ID] for a new template.
 */
class TemplateEditorViewModel(
    private val templateId: Long,
    private val templateRepository: TemplateRepository,
    private val exerciseRepository: ExerciseRepository,
    private val dateProvider: DateProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val isNew = templateId == Route.NEW_TEMPLATE_ID

    private val _state = MutableStateFlow(TemplateEditorUiState(isNew = isNew))
    val state: StateFlow<TemplateEditorUiState> = _state.asStateFlow()

    private val eventChannel = Channel<TemplateEditorEvent>(Channel.BUFFERED)
    val events: Flow<TemplateEditorEvent> = eventChannel.receiveAsFlow()

    /** What the template was when opened; hasChanges compares against it. */
    private var original = TemplateEditorDraft()

    init {
        val saved = savedStateHandle.get<String>(KEY_DRAFT)?.let { json.decodeFromString<TemplateEditorDraft>(it) }
        val savedOriginal = savedStateHandle.get<String>(KEY_ORIGINAL)?.let { json.decodeFromString<TemplateEditorDraft>(it) }
        if (saved != null && savedOriginal != null) {
            original = savedOriginal
            show(saved)
        } else if (isNew) {
            setOriginal(TemplateEditorDraft())
        } else {
            viewModelScope.launch {
                val template = runCatching { templateRepository.getTemplate(templateId) }
                    .onFailure { e -> Log.e(TAG, "Loading template $templateId failed", e) }
                    .getOrNull()
                if (template == null) {
                    eventChannel.send(TemplateEditorEvent.Closed) // deleted meanwhile
                } else {
                    setOriginal(template.toEditorDraft())
                }
            }
        }
    }

    fun onAction(action: TemplateEditorAction) {
        when (action) {
            is TemplateEditorAction.OnNameChange -> edit { it.copy(name = action.name) }
            is TemplateEditorAction.OnCategoryClick -> edit { it.withCategoryToggled(action.category) }
            is TemplateEditorAction.OnSetsChange -> edit { it.withSetsChanged(action.exerciseId, action.delta) }
            is TemplateEditorAction.OnMoveUp -> edit { it.withMoved(action.exerciseId, -1) }
            is TemplateEditorAction.OnMoveDown -> edit { it.withMoved(action.exerciseId, +1) }
            is TemplateEditorAction.OnRemoveExercise -> edit { it.withRemoved(action.exerciseId) }
            is TemplateEditorAction.OnExercisesPicked -> addExercises(action.exerciseIds)
            TemplateEditorAction.OnSaveClick -> save()

            TemplateEditorAction.OnBackClick ->
                if (_state.value.hasChanges) {
                    _state.update { it.copy(dialog = TemplateEditorDialog.DiscardChanges) }
                } else {
                    eventChannel.trySend(TemplateEditorEvent.Closed)
                }
            TemplateEditorAction.OnConfirmDiscard -> {
                _state.update { it.copy(dialog = null) }
                eventChannel.trySend(TemplateEditorEvent.Closed)
            }

            TemplateEditorAction.OnDeleteClick -> if (!isNew) _state.update { it.copy(dialog = TemplateEditorDialog.Delete) }
            TemplateEditorAction.OnConfirmDelete -> delete()
            TemplateEditorAction.OnDialogDismiss -> _state.update { it.copy(dialog = null) }

            // Navigation: TemplateEditorScreenRoot handles it.
            TemplateEditorAction.OnAddExercisesClick -> Unit
        }
    }

    private fun addExercises(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { exerciseRepository.getExercises(ids) }
                .onSuccess { picked -> edit { it.withAdded(picked) } }
                .onFailure { e -> Log.e(TAG, "Loading picked exercises failed", e) }
        }
    }

    private fun save() {
        val state = _state.value
        if (!state.canSave) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching { templateRepository.saveTemplate(state.draft.toDraft(templateId), dateProvider.now()) }
                .onSuccess { eventChannel.send(TemplateEditorEvent.Saved) }
                .onFailure { e ->
                    Log.e(TAG, "Saving template $templateId failed", e)
                    _state.update { it.copy(isSaving = false) }
                    eventChannel.send(TemplateEditorEvent.SaveFailed)
                }
        }
    }

    private fun delete() {
        _state.update { it.copy(dialog = null, isSaving = true) }
        viewModelScope.launch {
            runCatching { templateRepository.deleteTemplate(templateId) }
                .onSuccess { eventChannel.send(TemplateEditorEvent.Closed) }
                .onFailure { e ->
                    Log.e(TAG, "Deleting template $templateId failed", e)
                    _state.update { it.copy(isSaving = false) }
                    eventChannel.send(TemplateEditorEvent.SaveFailed)
                }
        }
    }

    private fun setOriginal(draft: TemplateEditorDraft) {
        original = draft
        savedStateHandle[KEY_ORIGINAL] = json.encodeToString(draft)
        show(draft)
    }

    private fun edit(change: (TemplateEditorDraft) -> TemplateEditorDraft) {
        if (_state.value.isLoading) return
        show(change(_state.value.draft))
    }

    private fun show(draft: TemplateEditorDraft) {
        savedStateHandle[KEY_DRAFT] = json.encodeToString(draft)
        _state.update { it.copy(isLoading = false, draft = draft, hasChanges = draft != original) }
    }

    private companion object {
        const val TAG = "TemplateEditorViewModel"
        const val KEY_DRAFT = "draft"
        const val KEY_ORIGINAL = "original"
        val json = Json
    }
}
