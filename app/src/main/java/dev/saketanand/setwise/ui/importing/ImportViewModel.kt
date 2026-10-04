package dev.saketanand.setwise.ui.importing

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.ai.WorkoutImporter
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.util.DateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ImportUiState(
    val isReading: Boolean = false,
    val plan: WorkoutImporter.Plan? = null,
    /** Read, but nothing in it looked like a shared workout. */
    val nothingFound: Boolean = false,
    val isImporting: Boolean = false,
    val result: WorkoutImporter.Result? = null,
    val failed: Boolean = false,
)

/**
 * Screen: [ImportScreenRoot]. Shared Strong workouts: read ([WorkoutImporter.plan]), checked,
 * then imported. Text shared to Setwise arrives as [sharedText] and is read right away.
 */
class ImportViewModel(
    sharedText: String,
    private val importer: WorkoutImporter,
    private val exerciseRepository: ExerciseRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    init {
        if (sharedText.isNotBlank()) read(sharedText)
    }

    fun read(text: String) {
        if (text.isBlank() || _state.value.isReading) return
        _state.value = ImportUiState(isReading = true)
        viewModelScope.launch {
            val plan = try {
                val library = exerciseRepository.observeExercises("", null).first()
                val done = exerciseRepository.observeRecentExercises(DONE_EXERCISES).first().map { it.exercise }
                importer.plan(text, library, done, dateProvider.zone)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Reading the shared workouts failed", e)
                null
            }
            _state.value = when {
                plan == null -> ImportUiState(failed = true)
                plan.workouts.isEmpty() -> ImportUiState(nothingFound = true)
                else -> ImportUiState(plan = plan)
            }
        }
    }

    fun import() {
        val plan = _state.value.plan ?: return
        if (_state.value.isImporting || plan.toImport.isEmpty()) return
        _state.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            val result = try {
                importer.import(plan, dateProvider.zone)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Importing failed", e)
                null
            }
            _state.value = if (result == null) ImportUiState(plan = plan, failed = true) else ImportUiState(result = result)
        }
    }

    private companion object {
        const val TAG = "ImportViewModel"
        const val DONE_EXERCISES = 200
    }
}
