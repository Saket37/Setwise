package dev.saketanand.setwise.ui.exercises

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.model.LoggedSet
import dev.saketanand.setwise.domain.model.NextSession
import dev.saketanand.setwise.ui.workout.SetKind
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
    /** The user's own exercise: it can be edited, deleted or merged (#146). */
    val isCustom: Boolean = false,
    /** The delete or merge dialog, if open. */
    val manage: ManageExerciseUi? = null,
)

/** Deleting a custom exercise: plainly when it's in no workout, otherwise by merging it into another. */
@Immutable
sealed interface ManageExerciseUi {
    /** In no workout: "Delete Bnech press?", and how many templates it's taken out of. */
    data class ConfirmDelete(val templates: Int) : ManageExerciseUi

    /** In [workouts] workouts: pick the exercise it should have been, logged the same way. */
    data class Merge(
        val workouts: Int,
        val query: String = "",
        val candidates: ImmutableList<MergeCandidateUi> = persistentListOf(),
        val selectedId: Long? = null,
    ) : ManageExerciseUi
}

/** "Bench Press (Dumbbell) · Chest". */
@Immutable
data class MergeCandidateUi(val id: Long, val name: String, val muscleGroup: String)

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
    val weeks: ImmutableList<Double?>,
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
