package dev.saketanand.setwise.ui.exercises

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.model.LoggedSet
import dev.saketanand.setwise.ui.workout.SetKind
import java.time.LocalDate
import dev.saketanand.setwise.domain.model.NextSession

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
    /** Stalled for weeks (design "Plateau · 4 weeks"); null if not. */
    val plateau: PlateauUi? = null,
    /** "Next session: 62.5 kg × 8 · 3 sets" and the rule behind it; null for cardio or before the first session. */
    val nextSession: NextSession? = null,
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

/** "Plateau · 4 weeks": the note, and where it came from. */
@Immutable
data class PlateauUi(
    val weeks: Int,
    val sessions: Int,
    /** The first session about as good as the best: "flat since 7 Sep". */
    val since: LocalDate,
    /** What it's held at, in [metric]'s unit: whole kg (estimated 1RM), reps or seconds. */
    val best: Int,
    val metric: ProgressMetric,
    /** Written on-device; null: the template from the same facts. */
    val note: String? = null,
    /** The on-device model is writing [note]. */
    val isWriting: Boolean = false,
)
