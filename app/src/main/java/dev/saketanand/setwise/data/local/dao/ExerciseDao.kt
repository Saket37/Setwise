package dev.saketanand.setwise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.data.local.relation.RecentExerciseRow
import kotlinx.coroutines.flow.Flow

/**
 * Exercise library queries.
 *
 * - `fun …: Flow<…>` (not suspend): the UI observes it; Room re-runs the query off the main
 *   thread and emits again whenever the `exercises` table changes.
 * - `suspend fun`: one-off reads/writes.
 */
@Dao
interface ExerciseDao {

    /**
     * Exercise list with search + muscle-group filter.
     *
     * @param query text to search in the name; "" matches everything (pattern becomes '%%').
     *   SQLite LIKE is case-insensitive for ASCII, so "bench" finds "Bench Press".
     * @param muscleGroup selected filter chip; null means "All".
     */
    @Query(
        """
        SELECT * FROM exercises
        WHERE (:muscleGroup IS NULL OR muscleGroup = :muscleGroup)
          AND name LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE
        """
    )
    fun observeExercises(query: String, muscleGroup: String?): Flow<List<ExerciseEntity>>

    /**
     * Muscle groups for the filter chips, biggest first (Back, Chest, Shoulders… before Calves),
     * so the most useful chips are the ones visible without scrolling.
     */
    @Query("SELECT muscleGroup FROM exercises GROUP BY muscleGroup ORDER BY COUNT(*) DESC, muscleGroup")
    fun observeMuscleGroups(): Flow<List<String>>

    /**
     * Exercises from finished workouts, most recently done first, with the top set of the
     * latest session (for "last 60 kg × 8").
     *
     * - Inner query: one row per exercise. With MAX() in a GROUP BY, SQLite takes the other
     *   columns (here the workout_exercises id) from the row that has the max, i.e. the most
     *   recent session.
     * - The two sub-selects pick the same set from that session: heaviest, then most reps.
     *   NULL weights (bodyweight) sort last under DESC, so they're ranked by reps.
     */
    @Query(
        """
        SELECT e.*, r.lastUsedAt AS lastUsedAt,
            (SELECT s.weightKg FROM sets s
             WHERE s.workoutExerciseId = r.workoutExerciseId AND s.isCompleted = 1
             ORDER BY s.weightKg DESC, s.reps DESC, s.id LIMIT 1) AS lastWeightKg,
            (SELECT s.reps FROM sets s
             WHERE s.workoutExerciseId = r.workoutExerciseId AND s.isCompleted = 1
             ORDER BY s.weightKg DESC, s.reps DESC, s.id LIMIT 1) AS lastReps
        FROM (
            SELECT we.id AS workoutExerciseId, we.exerciseId AS exerciseId, MAX(w.startedAt) AS lastUsedAt
            FROM workout_exercises we
            JOIN workouts w ON w.id = we.workoutId
            WHERE w.endedAt IS NOT NULL
            GROUP BY we.exerciseId
        ) r
        JOIN exercises e ON e.id = r.exerciseId
        ORDER BY r.lastUsedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentExercises(limit: Int): Flow<List<RecentExerciseRow>>

    /**
     * Seeding. IGNORE + the unique index on `name` makes this safe to re-run:
     * exercises that already exist are skipped instead of duplicated.
     * Returns the new row ids, with -1 for each skipped exercise.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<ExerciseEntity>): List<Long>

    /** Number of exercises in the library. */
    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    /** Live number of exercises; emits again when exercises are added (seeding, custom ones). */
    @Query("SELECT COUNT(*) FROM exercises")
    fun observeCount(): Flow<Int>

    /** Exact name match, e.g. "Bench Press (Barbell)". Null if there's none. */
    @Query("SELECT * FROM exercises WHERE name = :name")
    suspend fun getByName(name: String): ExerciseEntity?

    /** Null if no exercise has this id. */
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntity?

    /**
     * Custom exercise created by the user. Returns the new row id.
     * Uses the default ABORT strategy on purpose: a duplicate name throws
     * SQLiteConstraintException so the UI can say "already in your library"
     * (IGNORE would silently return -1).
     */
    @Insert
    suspend fun insert(exercise: ExerciseEntity): Long
}
