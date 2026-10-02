package dev.saketanand.setwise.domain.repository

import dev.saketanand.setwise.domain.model.ActiveWorkout
import dev.saketanand.setwise.domain.model.FinishedWorkout
import dev.saketanand.setwise.domain.model.WorkoutStats
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface WorkoutRepository {

    fun observeLastFinishedWorkout(): Flow<FinishedWorkout?>

    fun observeActiveWorkout(): Flow<ActiveWorkout?>

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
}
