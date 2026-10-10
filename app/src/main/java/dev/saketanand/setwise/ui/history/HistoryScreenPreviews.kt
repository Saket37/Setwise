package dev.saketanand.setwise.ui.history

import androidx.compose.runtime.Composable
import dev.saketanand.setwise.domain.ai.HistoryReply
import dev.saketanand.setwise.domain.model.DayState
import dev.saketanand.setwise.ui.designsystem.preview.PreviewScreens
import dev.saketanand.setwise.ui.designsystem.preview.PreviewToday
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import java.time.YearMonth
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.toImmutableList

// Previews: one per scenario

@PreviewScreens
@Composable
private fun HistoryScreenPreview() = SetwiseScreenPreview {
    HistoryScreen(uiState = SampleHistoryState, onAction = {})
}

@PreviewScreens
@Composable
private fun HistoryEmptyDayPreview() = SetwiseScreenPreview {
    HistoryScreen(uiState = SampleHistoryState.copy(selectedDate = PreviewToday.minusDays(2)), onAction = {})
}

@PreviewScreens
@Composable
private fun HistoryAskNotUnderstoodPreview() = SetwiseScreenPreview {
    HistoryScreen(uiState = SampleHistoryState.copy(ask = AskUi(question = "what should I eat", reply = HistoryReply.NotUnderstood)), onAction = {})
}

@PreviewScreens
@Composable
private fun HistoryAskUnknownExercisePreview() = SetwiseScreenPreview {
    HistoryScreen(
        uiState = SampleHistoryState.copy(ask = AskUi(question = "best zercher squat", reply = HistoryReply.UnknownExercise("zercher squat"))),
        onAction = {},
    )
}

/** Two weeks with three workouts (made up): previews and UI tests. */
internal val SampleHistoryState: HistoryUiState = run {
    val today = PreviewToday
    val workouts = listOf(
        HistoryWorkoutUi(1, "Push Day", today.minusDays(1), 64.minutes, 6_240.0, 0.0, 410, 2),
        HistoryWorkoutUi(2, "Pull Day", today.minusDays(3), 58.minutes, 5_120.0, 0.0, 380, 0),
        HistoryWorkoutUi(3, "Run", today.minusDays(4), 31.minutes, 0.0, 5.2, 330, 0),
    )
    HistoryUiState(
        isLoading = false,
        days = (0L..13L).map { back ->
            val date = today.minusDays(back)
            val state = when {
                back == 0L -> DayState.None
                workouts.any { it.date == date } -> DayState.Trained
                back == 2L -> DayState.Unanswered
                else -> DayState.Rest
            }
            DayUi(date = date, isToday = back == 0L, state = state, isSelected = false)
        }.toImmutableList(),
        months = listOf(HistoryMonthUi(YearMonth.from(today), workouts)),
        today = today,
    )
}
