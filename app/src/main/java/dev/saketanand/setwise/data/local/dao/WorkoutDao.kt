package dev.saketanand.setwise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.saketanand.setwise.data.local.entity.SetEntity
import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.local.entity.WorkoutExerciseEntity
import dev.saketanand.setwise.data.local.relation.ActiveWorkoutRow
import dev.saketanand.setwise.data.local.relation.WorkoutStatsRow
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
     * Deletes a workout that is still running; its exercises and sets go with it (CASCADE).
     * `endedAt IS NULL` makes it impossible to delete a finished workout through this.
     * Returns how many rows were deleted (0 or 1).
     */
    @Query("DELETE FROM workouts WHERE id = :workoutId AND endedAt IS NULL")
    suspend fun deleteRunningWorkout(workoutId: Long): Int
}
