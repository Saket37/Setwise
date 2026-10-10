package dev.saketanand.setwise.ui.body

import androidx.compose.runtime.Composable
import dev.saketanand.setwise.domain.model.BmrEstimate
import dev.saketanand.setwise.domain.model.BodyMeasurement
import dev.saketanand.setwise.domain.model.ReportDetails
import dev.saketanand.setwise.ui.designsystem.components.ChartPoint
import dev.saketanand.setwise.ui.designsystem.preview.PreviewScreens
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import java.time.LocalDate
import kotlinx.collections.immutable.toImmutableList

// Previews: one per scenario

@PreviewScreens
@Composable
private fun BodyScreenPreview() = SetwiseScreenPreview {
    BodyScreen(uiState = SampleBodyState, onAction = {}, onBack = {}, onTakePhoto = {}, onChoosePhoto = {})
}

@PreviewScreens
@Composable
private fun BodyScreenEmptyPreview() = SetwiseScreenPreview {
    BodyScreen(uiState = BodyUiState(isLoading = false), onAction = {}, onBack = {}, onTakePhoto = {}, onChoosePhoto = {})
}

/** Monthly reports with typed-in weights between (made-up values, as in the progress sample). */
private val SampleBodyState: BodyUiState = run {
    fun day(month: Int, d: Int) = LocalDate.of(2026, month, d)
    fun report(on: LocalDate, weight: Double, fat: Double, muscle: Double, score: Int) = BodyMeasurement(
        id = on.toEpochDay(), measuredOn = on, weightKg = weight, bodyFatPercent = fat, muscleMassKg = muscle, bmrKcal = 1750, visceralFat = 6.0,
        source = BodyMeasurement.Source.Report, details = ReportDetails(fitnessScore = score),
    )
    val history = listOf(
        report(on = day(10, 5), weight = 78.4, fat = 18.5, muscle = 35.1, score = 78),
        BodyMeasurement(id = day(9, 28).toEpochDay(), measuredOn = day(9, 28), weightKg = 78.8),
        report(on = day(9, 7), weight = 79.3, fat = 18.9, muscle = 34.9, score = 76),
        BodyMeasurement(id = day(8, 17).toEpochDay(), measuredOn = day(8, 17), weightKg = 79.6),
        BodyMeasurement(id = day(7, 27).toEpochDay(), measuredOn = day(7, 27), weightKg = 80.0),
        report(on = day(7, 6), weight = 80.5, fat = 19.7, muscle = 34.6, score = 74),
    )
    BodyUiState(
        isLoading = false,
        history = history,
        bmr = BmrEstimate(kcal = 1750, source = BmrEstimate.Source.Report),
        latest = LatestBody(measuredOn = day(10, 5), fromReport = true, weightKg = 78.4, bodyFatPercent = 18.5, muscleMassKg = 35.1, visceralFat = 6.0),
        progress = RecentProgress(
            since = day(7, 6),
            weightChange = -2.1,
            fatChange = -1.2,
            muscleChange = 0.5,
            weightPoints = history.reversed().map { ChartPoint(it.measuredOn.toEpochDay(), it.weightKg ?: 0.0) }.toImmutableList(),
        ),
        weightChanges = history.zipWithNext { newer, older -> newer.id to (newer.weightKg ?: 0.0) - (older.weightKg ?: 0.0) }.toMap(),
        latestReport = history.first(),
    )
}
