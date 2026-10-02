package dev.saketanand.setwise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
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

    /** Distinct muscle groups for the filter chips . */
    @Query("SELECT DISTINCT muscleGroup FROM exercises ORDER BY muscleGroup")
    fun observeMuscleGroups(): Flow<List<String>>

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
