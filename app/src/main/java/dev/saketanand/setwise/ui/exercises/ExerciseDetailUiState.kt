package dev.saketanand.setwise.ui.exercises

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.model.LoggedSet
import dev.saketanand.setwise.ui.workout.SetKind
import java.time.LocalDate

/** Everything [ExerciseDetailScreen] draws. */
@Immutable
data class ExerciseDetailUiState(
    val isLoading: Boolean = true,
    /** The exercise is gone (deleted): the screen closes. */
    val isMissing: Boolean = false,
    val name: String = "",
    val muscleGroup: String = "",
    val equipment: String = "",
    val kind: SetKind = SetKind.WeightReps,
    /** Null until it has been done at least once. */
    val progress: ProgressUi? = null,
    /** Newest first. */
    val sessions: List<ExerciseSessionUi> = emptyList(),
)

/** What the chart shows, by kind of exercise. */
enum class ProgressMetric {
    /** Best estimated 1RM (kg) per week: weight × reps. */
    EstimatedOneRepMax,

    /** Most reps in a set per week: bodyweight. */
    Reps,

    /** Longest hold (s) per week: timed. */
    Duration,

    /** Total distance (km) per week: cardio with distance. */
    Distance,

    /** Total minutes per week: cardio without distance. */
    Minutes,
}

/** "Estimated 1RM · last 8 weeks  48 kg" and one bar per week, oldest first. */
@Immutable
data class ProgressUi(
    val metric: ProgressMetric,
    /** One per week, oldest first; null = not done that week. */
    val weeks: List<Double?>,
    /** Monday of the first bar's week. */
    val firstWeek: LocalDate,
    /** The newest week's value; null if not done in these weeks. */
    val latest: Double?,
)

/** "Fri 2 Oct   40 × 6 · 40 × 6 · 37.5 × 8". */
@Immutable
data class ExerciseSessionUi(
    val workoutId: Long,
    val date: LocalDate,
    val sets: List<LoggedSet>,
)
