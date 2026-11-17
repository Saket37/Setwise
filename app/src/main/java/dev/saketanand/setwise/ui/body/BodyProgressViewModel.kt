package dev.saketanand.setwise.ui.body

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.model.BodyProgress
import dev.saketanand.setwise.domain.model.ProgressRange
import dev.saketanand.setwise.domain.repository.BodyRepository
import dev.saketanand.setwise.ui.designsystem.components.ChartPoint
import dev.saketanand.setwise.util.DateProvider
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Screen: [BodyProgressScreenRoot]. Each body measure over a chosen range. */
class BodyProgressViewModel(
    bodyRepository: BodyRepository,
    dateProvider: DateProvider,
) : ViewModel() {

    private val range = MutableStateFlow(ProgressRange.ThreeMonths)

    val state: StateFlow<BodyProgressUiState> = combine(bodyRepository.observeMeasurements(), range, dateProvider.today()) { checks, range, today ->
        val segments = BodyProgress.segments(checks, range, today)
        val reports = checks.filter { it.details.segments.isNotEmpty() && range.start(today)?.let { start -> !it.measuredOn.isBefore(start) } != false }
        BodyProgressUiState(
            isLoading = false,
            range = range,
            measures = BodyProgress.measures(checks, range, today).mapNotNull { progress ->
                val latest = progress.latest ?: return@mapNotNull null // never recorded: no card
                MeasureUi(
                    measure = progress.measure,
                    latest = latest,
                    change = progress.change,
                    since = progress.since,
                    until = progress.points.lastOrNull()?.first,
                    points = progress.points.map { (day, value) -> ChartPoint(day.toEpochDay(), value) }.toImmutableList(),
                )
            }.toImmutableList(),
            segments = segments.map { SegmentUi(it.group, it.first, it.latest, it.change) }.toImmutableList(),
            segmentsFrom = reports.minOfOrNull { it.measuredOn }.takeIf { segments.isNotEmpty() },
            segmentsTo = reports.maxOfOrNull { it.measuredOn }.takeIf { segments.isNotEmpty() },
        )
    }
        .catch { e ->
            Log.e(TAG, "Loading body progress failed", e)
            emit(BodyProgressUiState(isLoading = false))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyProgressUiState())

    fun onRangeChange(new: ProgressRange) {
        range.value = new
    }

    private companion object {
        const val TAG = "BodyProgressViewModel"
    }
}
