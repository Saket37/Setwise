package dev.saketanand.setwise.data.repository

import androidx.room.withTransaction
import dev.saketanand.setwise.data.local.SetwiseDatabase
import dev.saketanand.setwise.data.local.dao.TemplateDao
import dev.saketanand.setwise.data.local.dao.WorkoutDao
import dev.saketanand.setwise.data.local.entity.SetEntity
import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.local.entity.WorkoutExerciseEntity
import dev.saketanand.setwise.data.mapper.toDomain
import dev.saketanand.setwise.data.mapper.toFinishedWorkout
import dev.saketanand.setwise.domain.model.ActiveWorkout
import dev.saketanand.setwise.domain.model.FinishedWorkout
import dev.saketanand.setwise.domain.model.WorkoutStats
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class WorkoutRepositoryImpl(
    private val database: SetwiseDatabase,
    private val workoutDao: WorkoutDao,
    private val templateDao: TemplateDao,
) : WorkoutRepository {

    override fun observeLastFinishedWorkout(): Flow<FinishedWorkout?> =
        workoutDao.observeLastFinished().map { it?.toFinishedWorkout() }

    override fun observeActiveWorkout(): Flow<ActiveWorkout?> =
        workoutDao.observeActive().map { it?.toDomain() }

    override fun observeStats(from: Instant, to: Instant): Flow<WorkoutStats> =
        workoutDao.observeStats(from.toEpochMilli(), to.toEpochMilli()).map { it.toDomain() }

    override suspend fun startWorkout(
        templateId: Long?,
        startedAt: Instant,
        discardRunningWorkoutId: Long?,
    ): Long =
        // One transaction: if anything fails, nothing is deleted and no half-created workout is
        // left behind.
        database.withTransaction {
            discardRunningWorkoutId?.let { workoutDao.deleteRunningWorkout(it) }
            val template = templateId?.let { templateDao.getTemplateWithExercises(it) }
            val workoutId = workoutDao.insertWorkout(
                WorkoutEntity(
                    name = template?.template?.name ?: DEFAULT_WORKOUT_NAME,
                    templateId = template?.template?.id,
                    startedAt = startedAt.toEpochMilli(),
                )
            )
            template?.items
                ?.sortedBy { it.item.position }
                ?.forEachIndexed { index, row ->
                    val workoutExerciseId = workoutDao.insertWorkoutExercise(
                        WorkoutExerciseEntity(
                            workoutId = workoutId,
                            exerciseId = row.exercise.id,
                            position = index,
                        )
                    )
                    // Empty sets to fill in during the workout, one per planned set.
                    workoutDao.insertSets(
                        (1..row.item.targetSets).map { number ->
                            SetEntity(workoutExerciseId = workoutExerciseId, setNumber = number)
                        }
                    )
                }
            workoutId
        }

    private companion object {
        // TODO: let the user rename it on the active workout screen.
        const val DEFAULT_WORKOUT_NAME = "Workout"
    }
}
