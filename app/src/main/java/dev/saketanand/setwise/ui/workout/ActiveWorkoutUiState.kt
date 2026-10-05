package dev.saketanand.setwise.ui.workout

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.ai.QuickLogResult
import dev.saketanand.setwise.domain.model.CardioValues
import dev.saketanand.setwise.domain.model.NextSession
import dev.saketanand.setwise.domain.model.SetFact
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
    /** The quick-log bar's state: reading, the "Understood as" card, or why it wasn't understood. */
    val quickLog: QuickLogUi = QuickLogUi(),
    /** A finished workout opened to edit its sets: no clock or rest timer; Save instead of Finish. */
    val isEditingFinished: Boolean = false,
    /** The mic listens on-device (needs the microphone permission); else the phone's recognizer is used. */
    val onDeviceSpeech: Boolean = false,
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

    /** Tap on the title. */
    data class Rename(val currentName: String) : ActiveWorkoutDialog

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
    /** Cardio only: what was logged in this workout; null until then. */
    val cardio: CardioValues? = null,
    /** "Try 62.5 kg × 8 today": a change the progression rules suggest; null if none. */
    val nextSession: NextSession? = null,
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

/** The quick-log bar (design "Quick log"). */
@Immutable
data class QuickLogUi(
    /** A line is being read (the model can take a second or two). */
    val isReading: Boolean = false,
    /** "Understood as": shown until added or edited. */
    val preview: QuickLogPreview? = null,
    /** Why the last line couldn't be used; cleared when the line is edited. */
    val problem: QuickLogResult.Reason? = null,
    /** On-device speech recognition is listening to the mic. */
    val isListening: Boolean = false,
    /** What it has heard so far, while listening. */
    val heard: String = "",
    /** Why listening gave nothing. */
    val micProblem: MicProblem? = null,
)

enum class MicProblem {
    /** Silence, or nothing it could make out. */
    NothingHeard,

    /** Recognition failed (or the mic couldn't be used). */
    Failed,
}

/** "Overhead Press (Barbell) · Matched from “ohp” · in today's workout", then the sets (or cardio). */
@Immutable
data class QuickLogPreview(
    val exerciseName: String,
    /** The words it was matched from; null when it's the open exercise. */
    val matchedFrom: String?,
    /** In today's workout already (else it's added). */
    val isInWorkout: Boolean,
    val kind: SetKind,
    val sets: List<SetFact> = emptyList(),
    val cardio: CardioValues? = null,
    /** Read by the on-device model (the line had words the parser couldn't place). */
    val byModel: Boolean = false,
)
