package dev.saketanand.setwise.domain.repository

import dev.saketanand.setwise.domain.model.ExerciseSession
import dev.saketanand.setwise.domain.model.SetFact
import dev.saketanand.setwise.domain.model.ActiveWorkout
import dev.saketanand.setwise.domain.model.FinishedWorkout
import dev.saketanand.setwise.domain.model.WorkoutHistoryItem
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutStats
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import dev.saketanand.setwise.domain.model.CardioEntry
import dev.saketanand.setwise.domain.model.CardioValues
import dev.saketanand.setwise.domain.model.CalorieEstimate
import dev.saketanand.setwise.domain.model.LoggedSetRecord
import dev.saketanand.setwise.domain.model.SharedSet

interface WorkoutRepository {

    fun observeLastFinishedWorkout(): Flow<FinishedWorkout?>

    fun observeActiveWorkout(): Flow<ActiveWorkout?>

    /** Finished workouts, newest first (History tab). */
    fun observeHistory(): Flow<List<WorkoutHistoryItem>>

    /** Finished sessions of one exercise, newest first (only those with a completed set). */
    fun observeExerciseSessions(exerciseId: Long): Flow<List<ExerciseSession>>

    /** Finished workouts that started in [from, to). */
    fun observeStats(from: Instant, to: Instant): Flow<WorkoutStats>

    /**
     * Creates a workout and returns its id. With a [templateId], the template's exercises are
     * copied in with empty sets (one per planned set), ready to fill in.
     *
     * @param discardRunningWorkoutId a running workout to delete first, in the same transaction
     *   ("Discard and start"): either both happen or neither does.
     */
    suspend fun startWorkout(
        templateId: Long?,
        startedAt: Instant,
        discardRunningWorkoutId: Long? = null,
    ): Long

    // Active workout

    /** The workout with its exercises, sets and "previous" sets; null once it's deleted. */
    fun observeSession(workoutId: Long): Flow<WorkoutSession?>

    /**
     * Appends exercises (in the given order), each with as many empty sets as last time
     * (or [DEFAULT_SET_COUNT] for a new exercise). Returns the new workout-exercise ids.
     */
    suspend fun addExercises(workoutId: Long, exerciseIds: List<Long>): List<Long>

    /** Removes an exercise and its sets from the workout. */
    suspend fun removeExercise(workoutExerciseId: Long)

    /** Adds an empty set at the end of an exercise. */
    suspend fun addSet(workoutExerciseId: Long)

    /** Saves what's typed into a set row; null = empty field. */
    suspend fun updateSetValues(setId: Long, weightKg: Double?, reps: Int?, durationSec: Int?)

    /**
     * Ticks a set off with the values it was done with ([completedAt] non-null), or un-ticks it
     * ([completedAt] null, values kept).
     */
    suspend fun setCompleted(setId: Long, completedAt: Instant?, weightKg: Double?, reps: Int?, durationSec: Int?)

    /** Deletes a set; the sets after it are renumbered so there are no gaps. */
    suspend fun deleteSet(setId: Long)

    suspend fun updateStartTime(workoutId: Long, startedAt: Instant)

    /**
     * Ends the workout: sets that weren't ticked off are deleted, then exercises left without
     * sets, the remaining sets are renumbered, and personal-record sets are marked (isPr).
     * Returns false if it wasn't running.
     */
    suspend fun finishWorkout(workoutId: Long, endedAt: Instant): Boolean

    /**
     * Corrects the start and end of a finished workout (its records are re-checked, since
     * "earlier workouts" may have changed). Returns false if it isn't finished.
     */
    suspend fun updateFinishedTimes(workoutId: Long, startedAt: Instant, endedAt: Instant): Boolean

    /**
     * Re-marks a finished workout's record sets (isPr) with the record rules, e.g. after its
     * data was written some other way (sample data; later, editing a finished workout).
     */
    suspend fun refreshPersonalRecords(workoutId: Long)

    /** Deletes a running workout and everything in it. */
    suspend fun discardWorkout(workoutId: Long)

    /**
     * Deletes a finished workout (from its summary). Records of later workouts with the same
     * exercises are checked again. Returns false if it wasn't a finished workout.
     */
    suspend fun deleteFinishedWorkout(workoutId: Long): Boolean

    /** Finished workouts that don't have a calorie estimate yet. */
    fun observeWorkoutsWithoutCalories(): Flow<List<Long>>

    suspend fun setCalories(workoutId: Long, estimate: CalorieEstimate, source: String)

    /** Saves the on-device model's insight for the summary screen. */
    suspend fun setInsight(workoutId: Long, insight: String)

    /** Renames a workout (running or finished); a blank name is ignored. */
    suspend fun renameWorkout(workoutId: Long, name: String)

    // Quick log

    /**
     * Logs [sets] of an exercise as done, in one transaction: into its open sets first, then as
     * new ones. [workoutExerciseId] null adds [exerciseId] to the workout first. Returns the
     * workout-exercise id.
     */
    suspend fun logSets(
        workoutId: Long,
        workoutExerciseId: Long?,
        exerciseId: Long,
        sets: List<SetFact>,
        completedAt: Instant,
    ): Long

    // Cardio

    /** The cardio screen's data for one workout exercise; null once it's removed. */
    fun observeCardioEntry(workoutExerciseId: Long): Flow<CardioEntry?>

    /** Logs (or corrects) the cardio entry: one completed entry per cardio exercise. */
    suspend fun logCardio(workoutExerciseId: Long, values: CardioValues, completedAt: Instant)

    companion object {
        /** Sets added for an exercise the user has never done. */
        const val DEFAULT_SET_COUNT = 3
    }

    /** Every completed set of every finished workout, newest workout first (history questions). */
    suspend fun getTrainingLog(): List<LoggedSetRecord>

    /**
     * Saves a finished workout from elsewhere (another app's share), all sets done, in one
     * transaction, then works out its personal records. Returns its id.
     */
    suspend fun importWorkout(name: String, startedAt: Instant, endedAt: Instant, exercises: List<Pair<Long, List<SharedSet>>>): Long

    /** Whether a workout started in the same minute as [startedAt] (imported before). */
    suspend fun hasWorkoutStartedAt(startedAt: Instant): Boolean
}
