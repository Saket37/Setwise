package dev.saketanand.setwise.ui.workout

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalTime

/** Everything [ActiveWorkoutScreen] draws. */
@Immutable
data class ActiveWorkoutUiState(
    /** True until the workout has loaded once. */
    val isLoading: Boolean = true,
    val name: String = "",
    /** For the running clock. */
    val startedAtMillis: Long = 0,
    /** "Started 6:42 PM", and the time picker's starting value. */
    val startTime: LocalTime? = null,
    /** Set when the workout is logged for an earlier day ("Started Wed, 30 Sep · 6:00 PM"). */
    val pastDay: LocalDate? = null,
    val exercises: List<WorkoutExerciseUi> = emptyList(),
    /** The one exercise shown with its set table; the others are collapsed. Null = all collapsed. */
    val expandedExerciseId: Long? = null,
    val dialog: ActiveWorkoutDialog? = null,
    val isStartTimePickerVisible: Boolean = false,
    /** Finish was confirmed and is being saved; ignore further taps. */
    val isFinishing: Boolean = false,
    /** The rest countdown after a set; null when not resting. */
    val rest: RestUi? = null,
) {
    val completedSets: Int get() = exercises.sumOf { it.completedSets }

    val incompleteSets: Int get() = exercises.sumOf { it.sets.size - it.completedSets }
}

/** The confirmations this screen can show (one at a time). */
@Immutable
sealed interface ActiveWorkoutDialog {
    /** Finish with sets that aren't ticked off: they'll be dropped. */
    data class FinishWithIncompleteSets(val incompleteSets: Int) : ActiveWorkoutDialog

    /** Finish with nothing ticked off: offer to discard instead. */
    data object NothingLogged : ActiveWorkoutDialog

    /** "Discard workout" at the bottom of the list. */
    data object ConfirmDiscard : ActiveWorkoutDialog

    /** Removing an exercise that already has ticked-off sets. */
    data class RemoveExercise(
        val workoutExerciseId: Long,
        val exerciseName: String,
        val completedSets: Int,
    ) : ActiveWorkoutDialog
}

/** How an exercise is logged, which decides the set table's columns. */
enum class SetKind {
    /** kg × reps (barbell, dumbbell, machine…). */
    WeightReps,

    /** reps, with optional added weight (pull-up, dip). */
    Bodyweight,

    /** seconds (plank). */
    Duration,

    /** Logged on the cardio screen, not in a set table. */
    Cardio,
}

/**
 * The rest bar: "REST · NEXT SET 3  0:56". Times are on the boot clock (SystemClock.elapsedRealtime),
 * so the bar can count down by itself without the ViewModel ticking.
 */
@Immutable
data class RestUi(
    val endsAtElapsed: Long,
    val totalMillis: Long,
    /** "next set 3"; null when the next thing is another exercise or nothing. */
    val nextSetNumber: Int?,
    /** "next: Overhead Press"; null otherwise. */
    val nextExerciseName: String?,
)

/** One exercise card. */
@Immutable
data class WorkoutExerciseUi(
    /** The workout-exercise row id. */
    val id: Long,
    val exerciseId: Long,
    val name: String,
    val kind: SetKind,
    /** Rest after each set; 0 = no rest timer (cardio). */
    val restSec: Int,
    val sets: List<SetUi>,
    /** Last session in one line: "40 × 6 · 40 × 6 · 37.5 × 8"; null if never done. */
    val lastTime: String?,
) {
    val completedSets: Int get() = sets.count { it.isCompleted }
}

/**
 * One set row. Values are text as they appear in the fields ("" = empty).
 * For [SetKind.Duration], [reps] and [repsHint] hold seconds.
 */
@Immutable
data class SetUi(
    val id: Long,
    /** 1, 2, 3… */
    val number: Int,
    /** Same set last time: "60 × 8"; null if there was none. */
    val previous: String?,
    val weight: String,
    val reps: String,
    /** Shown greyed out in an empty field, and used when the set is ticked off empty. */
    val weightHint: String,
    val repsHint: String,
    val isCompleted: Boolean,
    val isPr: Boolean,
)
