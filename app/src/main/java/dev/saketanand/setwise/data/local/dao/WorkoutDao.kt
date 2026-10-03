package dev.saketanand.setwise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.saketanand.setwise.data.local.entity.SetEntity
import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.local.entity.WorkoutExerciseEntity
import dev.saketanand.setwise.data.local.relation.ActiveWorkoutRow
import dev.saketanand.setwise.data.local.relation.ExerciseHistorySetRow
import dev.saketanand.setwise.data.local.relation.ExerciseLogRow
import dev.saketanand.setwise.data.local.relation.PreviousSetRow
import dev.saketanand.setwise.data.local.relation.WorkoutHistoryRow
import dev.saketanand.setwise.data.local.relation.WorkoutStatsRow
import dev.saketanand.setwise.data.local.relation.WorkoutWithExercises
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // Writes. Insert parents before children: workout → workout_exercises → sets.

    @Insert
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Insert
    suspend fun insertWorkoutExercise(item: WorkoutExerciseEntity): Long

    @Insert
    suspend fun insertSets(sets: List<SetEntity>)

    @Insert
    suspend fun insertSet(set: SetEntity): Long

    // Reads for the Workout tab

    /** Most recently finished workout ("Last session: Pull Day · 2 days ago"). */
    @Query("SELECT * FROM workouts WHERE endedAt IS NOT NULL ORDER BY endedAt DESC LIMIT 1")
    fun observeLastFinished(): Flow<WorkoutEntity?>

    /** Workout in progress (endedAt is null), with how many sets are ticked off. */
    @Query(
        """
        SELECT w.id, w.name, w.startedAt,
            (SELECT COUNT(*) FROM sets s
             JOIN workout_exercises we ON s.workoutExerciseId = we.id
             WHERE we.workoutId = w.id AND s.isCompleted = 1) AS completedSets
        FROM workouts w
        WHERE w.endedAt IS NULL
        ORDER BY w.startedAt DESC
        LIMIT 1
        """
    )
    fun observeActive(): Flow<ActiveWorkoutRow?>

    /**
     * Finished workouts that started in [fromMillis, toMillis): how many, total time, and PR sets.
     */
    @Query(
        """
        SELECT
            COUNT(*) AS workouts,
            COALESCE(SUM(endedAt - startedAt), 0) AS totalMillis,
            (SELECT COUNT(*) FROM sets s
             JOIN workout_exercises we ON s.workoutExerciseId = we.id
             JOIN workouts w2 ON we.workoutId = w2.id
             WHERE s.isPr = 1 AND w2.endedAt IS NOT NULL
               AND w2.startedAt >= :fromMillis AND w2.startedAt < :toMillis) AS prs
        FROM workouts
        WHERE endedAt IS NOT NULL AND startedAt >= :fromMillis AND startedAt < :toMillis
        """
    )
    fun observeStats(fromMillis: Long, toMillis: Long): Flow<WorkoutStatsRow>

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun count(): Int

    /**
     * Every finished workout, newest first, with its totals over ticked-off sets: sets, volume
     * (weight × reps), cardio distance and personal records. One row per workout.
     */
    @Query(
        """
        SELECT w.id AS id, w.name AS name, w.startedAt AS startedAt, w.endedAt AS endedAt, w.calories AS calories,
            COUNT(s.id) AS completedSets,
            COALESCE(SUM(COALESCE(s.weightKg, 0) * COALESCE(s.reps, 0)), 0) AS volumeKg,
            COALESCE(SUM(COALESCE(s.distanceKm, 0)), 0) AS distanceKm,
            COALESCE(SUM(s.isPr), 0) AS personalRecords
        FROM workouts w
        LEFT JOIN workout_exercises we ON we.workoutId = w.id
        LEFT JOIN sets s ON s.workoutExerciseId = we.id AND s.isCompleted = 1
        WHERE w.endedAt IS NOT NULL
        GROUP BY w.id
        ORDER BY w.startedAt DESC
        """
    )
    fun observeHistory(): Flow<List<WorkoutHistoryRow>>

    // Active workout: reads

    /** The workout with its exercises and sets; re-emits on every change to any of them. */
    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    fun observeWorkoutWithExercises(workoutId: Long): Flow<WorkoutWithExercises?>

    /**
     * "Previous" column: for each exercise in this workout, the completed sets of the last
     * *finished* session of that exercise (never this workout itself), in set order.
     */
    @Query(
        """
        SELECT cur.id AS workoutExerciseId, s.setNumber AS setNumber,
               s.weightKg AS weightKg, s.reps AS reps, s.durationSec AS durationSec
        FROM workout_exercises cur
        JOIN sets s ON s.workoutExerciseId = (
            SELECT we.id FROM workout_exercises we
            JOIN workouts w ON w.id = we.workoutId
            WHERE we.exerciseId = cur.exerciseId AND w.endedAt IS NOT NULL AND w.id != cur.workoutId
            ORDER BY w.startedAt DESC, we.id DESC
            LIMIT 1
        )
        WHERE cur.workoutId = :workoutId AND s.isCompleted = 1
        ORDER BY cur.id, s.setNumber
        """
    )
    fun observePreviousSets(workoutId: Long): Flow<List<PreviousSetRow>>

    /**
     * Every completed set, from workouts finished and started before [workoutId], of the
     * exercises in [workoutId]: what its personal records are measured against.
     */
    @Query(
        """
        SELECT we.exerciseId AS exerciseId, s.weightKg AS weightKg, s.reps AS reps, s.durationSec AS durationSec
        FROM sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        JOIN workouts w ON w.id = we.workoutId
        WHERE s.isCompleted = 1 AND w.endedAt IS NOT NULL AND w.id != :workoutId
          AND w.startedAt < (SELECT startedAt FROM workouts WHERE id = :workoutId)
          AND we.exerciseId IN (SELECT exerciseId FROM workout_exercises WHERE workoutId = :workoutId)
        """
    )
    fun observeHistorySets(workoutId: Long): Flow<List<ExerciseHistorySetRow>>

    /**
     * Every completed set of [exerciseId] in finished workouts: newest workout first, then in the
     * order done (exercise position, set number).
     */
    @Query(
        """
        SELECT w.id AS workoutId, w.startedAt AS startedAt, s.weightKg AS weightKg, s.reps AS reps,
               s.durationSec AS durationSec, s.distanceKm AS distanceKm
        FROM sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        JOIN workouts w ON w.id = we.workoutId
        WHERE we.exerciseId = :exerciseId AND s.isCompleted = 1 AND w.endedAt IS NOT NULL
        ORDER BY w.startedAt DESC, w.id DESC, we.position, s.setNumber
        """
    )
    fun observeExerciseLog(exerciseId: Long): Flow<List<ExerciseLogRow>>

    /** One-off read of [observeHistorySets], e.g. while finishing a workout. */
    @Query(
        """
        SELECT we.exerciseId AS exerciseId, s.weightKg AS weightKg, s.reps AS reps, s.durationSec AS durationSec
        FROM sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        JOIN workouts w ON w.id = we.workoutId
        WHERE s.isCompleted = 1 AND w.endedAt IS NOT NULL AND w.id != :workoutId
          AND w.startedAt < (SELECT startedAt FROM workouts WHERE id = :workoutId)
          AND we.exerciseId IN (SELECT exerciseId FROM workout_exercises WHERE workoutId = :workoutId)
        """
    )
    suspend fun getHistorySets(workoutId: Long): List<ExerciseHistorySetRow>

    /** One-off read of [observeWorkoutWithExercises]. */
    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    suspend fun getWorkoutWithExercises(workoutId: Long): WorkoutWithExercises?

    /** Completed sets in the last finished session of an exercise (0 if never done). */
    @Query(
        """
        SELECT COUNT(*) FROM sets
        WHERE isCompleted = 1 AND workoutExerciseId = (
            SELECT we.id FROM workout_exercises we
            JOIN workouts w ON w.id = we.workoutId
            WHERE we.exerciseId = :exerciseId AND w.endedAt IS NOT NULL
            ORDER BY w.startedAt DESC, we.id DESC
            LIMIT 1
        )
        """
    )
    suspend fun lastSessionSetCount(exerciseId: Long): Int

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun nextExercisePosition(workoutId: Long): Int

    @Query("SELECT COALESCE(MAX(setNumber), 0) + 1 FROM sets WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun nextSetNumber(workoutExerciseId: Long): Int

    @Query("SELECT workoutExerciseId FROM sets WHERE id = :setId")
    suspend fun workoutExerciseIdOfSet(setId: Long): Long?

    @Query("SELECT id FROM sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY setNumber, id")
    suspend fun setIdsInOrder(workoutExerciseId: Long): List<Long>

    @Query("SELECT id FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun workoutExerciseIds(workoutId: Long): List<Long>

    // Active workout: writes

    /** Values typed into a set row (saved as the user types). */
    @Query("UPDATE sets SET weightKg = :weightKg, reps = :reps, durationSec = :durationSec WHERE id = :setId")
    suspend fun updateSetValues(setId: Long, weightKg: Double?, reps: Int?, durationSec: Int?)

    /** Ticks a set off (with the values it was done with) or un-ticks it. */
    @Query(
        """
        UPDATE sets SET isCompleted = :completed, completedAt = :completedAt,
            weightKg = :weightKg, reps = :reps, durationSec = :durationSec
        WHERE id = :setId
        """
    )
    suspend fun updateSetCompletion(
        setId: Long,
        completed: Boolean,
        completedAt: Long?,
        weightKg: Double?,
        reps: Int?,
        durationSec: Int?,
    )

    @Query("UPDATE sets SET setNumber = :setNumber WHERE id = :setId")
    suspend fun updateSetNumber(setId: Long, setNumber: Int)

    @Query("DELETE FROM sets WHERE id = :setId")
    suspend fun deleteSet(setId: Long)

    // Cardio entry

    @Query("SELECT * FROM workout_exercises WHERE id = :workoutExerciseId")
    fun observeWorkoutExercise(workoutExerciseId: Long): Flow<WorkoutExerciseEntity?>

    @Query("SELECT * FROM sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY setNumber, id")
    fun observeSetsOf(workoutExerciseId: Long): Flow<List<SetEntity>>

    @Query("SELECT * FROM sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY setNumber, id")
    suspend fun getSetsOf(workoutExerciseId: Long): List<SetEntity>

    /** The latest logged entry of [exerciseId] in a finished workout other than [workoutId]. */
    @Query(
        """
        SELECT s.* FROM sets s
        JOIN workout_exercises we ON we.id = s.workoutExerciseId
        JOIN workouts w ON w.id = we.workoutId
        WHERE we.exerciseId = :exerciseId AND s.isCompleted = 1 AND w.endedAt IS NOT NULL AND w.id != :workoutId
        ORDER BY w.startedAt DESC, s.id DESC
        LIMIT 1
        """
    )
    fun observeLastLoggedSet(exerciseId: Long, workoutId: Long): Flow<SetEntity?>

    @Query(
        """
        UPDATE sets SET durationSec = :durationSec, inclinePct = :inclinePct, speedMinKmh = :speedMinKmh,
            speedMaxKmh = :speedMaxKmh, distanceKm = :distanceKm, level = :level,
            isCompleted = 1, completedAt = :completedAt
        WHERE id = :setId
        """
    )
    suspend fun logCardio(
        setId: Long,
        durationSec: Int?,
        inclinePct: Double?,
        speedMinKmh: Double?,
        speedMaxKmh: Double?,
        distanceKm: Double?,
        level: Int?,
        completedAt: Long,
    )

    /** Removes an exercise from a workout; its sets go with it (CASCADE). */
    @Query("DELETE FROM workout_exercises WHERE id = :workoutExerciseId")
    suspend fun deleteWorkoutExercise(workoutExerciseId: Long)

    /** Back-dates (or corrects) the start of a running workout. */
    @Query("UPDATE workouts SET startedAt = :startedAt WHERE id = :workoutId AND endedAt IS NULL")
    suspend fun updateStartedAt(workoutId: Long, startedAt: Long)

    /** On Finish: sets that weren't ticked off aren't part of the workout. */
    @Query(
        """
        DELETE FROM sets WHERE isCompleted = 0
          AND workoutExerciseId IN (SELECT id FROM workout_exercises WHERE workoutId = :workoutId)
        """
    )
    suspend fun deleteIncompleteSets(workoutId: Long)

    /** On Finish, after [deleteIncompleteSets]: exercises left with no sets are dropped. */
    @Query(
        """
        DELETE FROM workout_exercises WHERE workoutId = :workoutId
          AND id NOT IN (SELECT DISTINCT workoutExerciseId FROM sets)
        """
    )
    suspend fun deleteExercisesWithoutSets(workoutId: Long)

    @Query(
        """
        UPDATE sets SET isPr = 0
        WHERE workoutExerciseId IN (SELECT id FROM workout_exercises WHERE workoutId = :workoutId)
        """
    )
    suspend fun clearPersonalRecords(workoutId: Long)

    @Query("UPDATE sets SET isPr = 1 WHERE id IN (:setIds)")
    suspend fun markPersonalRecords(setIds: List<Long>)

    /** Corrects the times of a finished workout (Summary → edit times). */
    @Query("UPDATE workouts SET startedAt = :startedAt, endedAt = :endedAt WHERE id = :workoutId AND endedAt IS NOT NULL")
    suspend fun updateFinishedTimes(workoutId: Long, startedAt: Long, endedAt: Long): Int

    /** Returns 1 if the workout was running and is now finished, 0 otherwise. */
    @Query("UPDATE workouts SET endedAt = :endedAt WHERE id = :workoutId AND endedAt IS NULL")
    suspend fun markFinished(workoutId: Long, endedAt: Long): Int

    /**
     * Deletes a workout that is still running; its exercises and sets go with it (CASCADE).
     * `endedAt IS NULL` makes it impossible to delete a finished workout through this.
     * Returns how many rows were deleted (0 or 1).
     */
    @Query("DELETE FROM workouts WHERE id = :workoutId AND endedAt IS NULL")
    suspend fun deleteRunningWorkout(workoutId: Long): Int
}
