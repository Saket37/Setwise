package dev.saketanand.setwise.testing

import dev.saketanand.setwise.domain.model.ActiveWorkout
import dev.saketanand.setwise.domain.model.FinishedWorkout
import dev.saketanand.setwise.domain.model.WorkoutHistoryItem
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutStats
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration

/**
 * WorkoutRepository that does nothing. Test fakes extend it and override only what their test
 * uses, so adding a repository method doesn't break every fake.
 */
open class StubWorkoutRepository : WorkoutRepository {
    override fun observeLastFinishedWorkout(): Flow<FinishedWorkout?> = flowOf(null)
    override fun observeActiveWorkout(): Flow<ActiveWorkout?> = flowOf(null)
    override fun observeHistory(): Flow<List<WorkoutHistoryItem>> = flowOf(emptyList())
    override fun observeStats(from: Instant, to: Instant): Flow<WorkoutStats> =
        flowOf(WorkoutStats(workouts = 0, timeTrained = Duration.ZERO, prs = 0))
    override suspend fun startWorkout(templateId: Long?, startedAt: Instant, discardRunningWorkoutId: Long?): Long = 0
    override fun observeSession(workoutId: Long): Flow<WorkoutSession?> = flowOf(null)
    override suspend fun addExercises(workoutId: Long, exerciseIds: List<Long>): List<Long> = emptyList()
    override suspend fun removeExercise(workoutExerciseId: Long) = Unit
    override suspend fun addSet(workoutExerciseId: Long) = Unit
    override suspend fun updateSetValues(setId: Long, weightKg: Double?, reps: Int?, durationSec: Int?) = Unit
    override suspend fun setCompleted(setId: Long, completedAt: Instant?, weightKg: Double?, reps: Int?, durationSec: Int?) = Unit
    override suspend fun deleteSet(setId: Long) = Unit
    override suspend fun updateStartTime(workoutId: Long, startedAt: Instant) = Unit
    override suspend fun finishWorkout(workoutId: Long, endedAt: Instant): Boolean = true
    override suspend fun updateFinishedTimes(workoutId: Long, startedAt: Instant, endedAt: Instant): Boolean = true
    override suspend fun discardWorkout(workoutId: Long) = Unit
}
