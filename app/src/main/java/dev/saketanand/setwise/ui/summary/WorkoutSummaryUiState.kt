package dev.saketanand.setwise.ui.summary

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.model.CardioValues
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.PrKind
import dev.saketanand.setwise.ui.workout.SetKind
import java.time.LocalDate
import java.time.LocalTime
import kotlin.time.Duration

/** Everything [WorkoutSummaryScreen] draws. */
@Immutable
data class WorkoutSummaryUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val date: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val duration: Duration = Duration.ZERO,
    /** Sum of weight × reps over ticked-off sets. */
    val volumeKg: Double = 0.0,
    val completedSets: Int = 0,
    val exerciseCount: Int = 0,
    /** Estimated once a body weight is known (CalorieSync); null until then. */
    val caloriesKcal: Int? = null,
    /** No estimate because no body weight is set: the calories tile asks for it. */
    val needsBodyWeight: Boolean = false,
    /** The body weight dialog from that tile; null when closed. */
    val bodyWeightDialog: BodyWeightDialogUi? = null,
    val records: List<RecordUi> = emptyList(),
    val exercises: List<SummaryExerciseUi> = emptyList(),
    /** False for workouts started from a template (saving would just duplicate it). */
    val canSaveAsTemplate: Boolean = false,
    val isTemplateSaved: Boolean = false,
    val editTimes: EditTimesUi? = null,
    /** The rename dialog (tap on the name). */
    val isRenaming: Boolean = false,
    /** "Delete workout?" from the ⋮ menu. */
    val isConfirmingDelete: Boolean = false,
)

/** "Bench Press (Barbell) · Previous best 60 × 8 · 62.5 × 8 WEIGHT". */
@Immutable
data class RecordUi(
    val exerciseName: String,
    val kind: PrKind,
    val setKind: SetKind,
    val achieved: PreviousSet,
    val previousBest: PreviousSet,
)

/** "Bench Press (Barbell) · 4 sets · best 62.5 × 8". */
@Immutable
data class SummaryExerciseUi(
    /** The workout-exercise row id. */
    val id: Long,
    /** For opening Exercise detail. */
    val exerciseId: Long,
    val name: String,
    val setCount: Int,
    /** "62.5 × 8", "12", "90s"; null for cardio (logged on its own screen). */
    val best: String?,
    /** Cardio: what was logged ("30:00 · 3.9 km"), shown instead of sets. */
    val cardio: CardioValues? = null,
)

/** The "edit start and end time" dialog, with the times picked so far. */
@Immutable
data class EditTimesUi(
    val start: LocalTime,
    val end: LocalTime,
    /** Which time's picker is open, if any. */
    val picking: TimeField? = null,
    /** The picked times don't make a valid workout (end not after start). */
    val isInvalid: Boolean = false,
)

enum class TimeField { Start, End }

@Immutable
data class BodyWeightDialogUi(val isInvalid: Boolean = false)
