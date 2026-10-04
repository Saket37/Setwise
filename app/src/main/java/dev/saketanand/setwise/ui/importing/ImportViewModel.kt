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
import dev.saketanand.setwise.domain.ai.ImportReader

@Immutable
data class ImportUiState(
    val isReading: Boolean = false,
    val plan: WorkoutImporter.Plan? = null,
    /** Read, but nothing in it looked like a shared workout. */
    val nothingFound: Boolean = false,
    val isImporting: Boolean = false,
    val result: WorkoutImporter.Result? = null,
    val failed: Boolean = false,
    /** What read the workouts (the on-device model: check them carefully). */
    val source: ImportReader.Source? = null,
    /** Lines of a hand-written log that couldn't be read (not imported). */
    val unreadLines: Int = 0,
)

/**
 * Screen: [ImportScreenRoot]. Shared Strong workouts: read ([WorkoutImporter.plan]), checked,
 * then imported. Text shared to Setwise arrives as [sharedText] and is read right away.
 */
class ImportViewModel(
    sharedText: String,
    sharedImages: List<String>,
    private val reader: ImportReader,
    private val importer: WorkoutImporter,
    private val exerciseRepository: ExerciseRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    init {
        when {
            sharedImages.isNotEmpty() -> readImages(sharedImages)
            sharedText.isNotBlank() -> read(sharedText)
        }
    }

    /** Pasted or shared text, or a CSV file's content. */
    fun read(text: String) {
        if (text.isBlank()) return
        readWith { reader.fromText(text) }
    }

    /** Screenshots, in the order picked. */
    fun readImages(uris: List<String>) {
        if (uris.isEmpty()) return
        readWith { reader.fromImages(uris) }
    }

    private fun readWith(read: suspend () -> ImportReader.Read) {
        if (_state.value.isReading) return
        _state.value = ImportUiState(isReading = true)
        viewModelScope.launch {
            var source: ImportReader.Source? = null
            var unread = 0
            val plan = try {
                val workouts = read().also { source = it.source; unread = it.unreadLines }.workouts
                val library = exerciseRepository.observeExercises("", null).first()
                val done = exerciseRepository.observeRecentExercises(DONE_EXERCISES).first().map { it.exercise }
                importer.plan(workouts, library, done, dateProvider.zone)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Reading the shared workouts failed", e)
                null
            }
            _state.value = when {
                plan == null -> ImportUiState(failed = true)
                plan.workouts.isEmpty() -> ImportUiState(nothingFound = true)
                else -> ImportUiState(plan = plan, source = source, unreadLines = unread)
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
            _state.value = if (result == null) _state.value.copy(isImporting = false, failed = true) else ImportUiState(result = result)
        }
    }

    private companion object {
        const val TAG = "ImportViewModel"
        const val DONE_EXERCISES = 200
    }
}
