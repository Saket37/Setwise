package dev.saketanand.setwise.data.repository

import androidx.room.withTransaction
import dev.saketanand.setwise.data.local.SetwiseDatabase
import dev.saketanand.setwise.data.local.dao.TemplateDao
import dev.saketanand.setwise.data.local.dao.WorkoutDao
import dev.saketanand.setwise.data.local.entity.SetEntity
import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.local.entity.WorkoutExerciseEntity
import dev.saketanand.setwise.data.mapper.toCardioValues
import dev.saketanand.setwise.data.mapper.toDomain
import dev.saketanand.setwise.data.mapper.toFinishedWorkout
import dev.saketanand.setwise.data.mapper.toSession
import dev.saketanand.setwise.domain.model.CardioEntry
import dev.saketanand.setwise.domain.model.CardioValues
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.ExerciseSession
import dev.saketanand.setwise.domain.model.LoggedSet
import dev.saketanand.setwise.domain.model.ActiveWorkout
import dev.saketanand.setwise.domain.model.FinishedWorkout
import dev.saketanand.setwise.domain.model.WorkoutHistoryItem
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutStats
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    override fun observeHistory(): Flow<List<WorkoutHistoryItem>> =
        workoutDao.observeHistory().map { rows -> rows.map { it.toDomain() } }

    override fun observeExerciseSessions(exerciseId: Long): Flow<List<ExerciseSession>> =
        workoutDao.observeExerciseLog(exerciseId).map { rows ->
            // Rows come newest workout first, sets in order; groupBy keeps both orders.
            rows.groupBy { it.workoutId }.map { (workoutId, sets) ->
                ExerciseSession(
                    workoutId = workoutId,
                    startedAt = Instant.ofEpochMilli(sets.first().startedAt),
                    sets = sets.map { LoggedSet(it.weightKg, it.reps, it.durationSec, it.distanceKm) },
                )
            }
        }

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
                    // Empty sets to fill in during the workout, one per planned set. Cardio is one
                    // block (logged on its own screen), whatever an older template says.
                    val setCount = if (row.exercise.type == ExerciseType.CARDIO) 1 else row.item.targetSets
                    workoutDao.insertSets(
                        (1..setCount).map { number ->
                            SetEntity(workoutExerciseId = workoutExerciseId, setNumber = number)
                        }
                    )
                }
            workoutId
        }

    override fun observeSession(workoutId: Long): Flow<WorkoutSession?> =
        combine(
            workoutDao.observeWorkoutWithExercises(workoutId),
            workoutDao.observePreviousSets(workoutId),
            workoutDao.observeHistorySets(workoutId),
        ) { workout, previous, history -> workout?.toSession(previous, history) }

    override suspend fun addExercises(workoutId: Long, exerciseIds: List<Long>): List<Long> =
        database.withTransaction {
            val firstPosition = workoutDao.nextExercisePosition(workoutId)
            exerciseIds.mapIndexed { index, exerciseId ->
                val workoutExerciseId = workoutDao.insertWorkoutExercise(
                    WorkoutExerciseEntity(workoutId = workoutId, exerciseId = exerciseId, position = firstPosition + index)
                )
                // Cardio is one entry (logged on its own screen); others repeat last time's set count.
                val isCardio = database.exerciseDao().getById(exerciseId)?.type == ExerciseType.CARDIO
                val setCount = if (isCardio) {
                    1
                } else {
                    workoutDao.lastSessionSetCount(exerciseId).takeIf { it > 0 } ?: WorkoutRepository.DEFAULT_SET_COUNT
                }
                workoutDao.insertSets(
                    (1..setCount).map { number -> SetEntity(workoutExerciseId = workoutExerciseId, setNumber = number) }
                )
                workoutExerciseId
            }
        }

    override suspend fun removeExercise(workoutExerciseId: Long) =
        workoutDao.deleteWorkoutExercise(workoutExerciseId)

    override suspend fun addSet(workoutExerciseId: Long) {
        database.withTransaction {
            val number = workoutDao.nextSetNumber(workoutExerciseId)
            workoutDao.insertSets(listOf(SetEntity(workoutExerciseId = workoutExerciseId, setNumber = number)))
        }
    }

    override suspend fun updateSetValues(setId: Long, weightKg: Double?, reps: Int?, durationSec: Int?) =
        workoutDao.updateSetValues(setId, weightKg, reps, durationSec)

    override suspend fun setCompleted(
        setId: Long,
        completedAt: Instant?,
        weightKg: Double?,
        reps: Int?,
        durationSec: Int?,
    ) = workoutDao.updateSetCompletion(
        setId = setId,
        completed = completedAt != null,
        completedAt = completedAt?.toEpochMilli(),
        weightKg = weightKg,
        reps = reps,
        durationSec = durationSec,
    )

    override suspend fun deleteSet(setId: Long) {
        database.withTransaction {
            val workoutExerciseId = workoutDao.workoutExerciseIdOfSet(setId) ?: return@withTransaction
            workoutDao.deleteSet(setId)
            renumberSets(workoutExerciseId)
        }
    }

    override suspend fun updateStartTime(workoutId: Long, startedAt: Instant) =
        workoutDao.updateStartedAt(workoutId, startedAt.toEpochMilli())

    override suspend fun finishWorkout(workoutId: Long, endedAt: Instant): Boolean =
        database.withTransaction {
            // Only a running workout is finished and tidied; a finished one is left untouched.
            if (workoutDao.markFinished(workoutId, endedAt.toEpochMilli()) == 0) return@withTransaction false
            workoutDao.deleteIncompleteSets(workoutId)
            workoutDao.deleteExercisesWithoutSets(workoutId)
            workoutDao.workoutExerciseIds(workoutId).forEach { renumberSets(it) }
            markPersonalRecords(workoutId)
            true
        }

    override suspend fun updateFinishedTimes(workoutId: Long, startedAt: Instant, endedAt: Instant): Boolean =
        database.withTransaction {
            val updated = workoutDao.updateFinishedTimes(workoutId, startedAt.toEpochMilli(), endedAt.toEpochMilli()) == 1
            if (updated) markPersonalRecords(workoutId)
            updated
        }

    override suspend fun refreshPersonalRecords(workoutId: Long) {
        database.withTransaction { markPersonalRecords(workoutId) }
    }

    /** Sets isPr on this workout's record sets (and clears it elsewhere in it). Call inside a transaction. */
    private suspend fun markPersonalRecords(workoutId: Long) {
        val session = workoutDao.getWorkoutWithExercises(workoutId)
            ?.toSession(previous = emptyList(), history = workoutDao.getHistorySets(workoutId))
            ?: return
        workoutDao.clearPersonalRecords(workoutId)
        val recordSetIds = session.exercises.mapNotNull { it.personalRecord?.set?.id }
        if (recordSetIds.isNotEmpty()) workoutDao.markPersonalRecords(recordSetIds)
    }

    override suspend fun discardWorkout(workoutId: Long) {
        workoutDao.deleteRunningWorkout(workoutId)
    }

    override suspend fun renameWorkout(workoutId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) workoutDao.renameWorkout(workoutId, trimmed)
    }

    @OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
    override fun observeCardioEntry(workoutExerciseId: Long): Flow<CardioEntry?> =
        workoutDao.observeWorkoutExercise(workoutExerciseId).flatMapLatest { item ->
            if (item == null) return@flatMapLatest flowOf(null)
            combine(
                database.exerciseDao().observeById(item.exerciseId),
                workoutDao.observeSetsOf(workoutExerciseId),
                workoutDao.observeLastLoggedSet(item.exerciseId, item.workoutId),
            ) { exercise, sets, last ->
                exercise ?: return@combine null
                CardioEntry(
                    workoutId = item.workoutId,
                    exercise = exercise.toDomain(),
                    logged = sets.firstOrNull { it.isCompleted }?.toCardioValues(),
                    lastTime = last?.toCardioValues(),
                )
            }
        }

    override suspend fun logCardio(workoutExerciseId: Long, values: CardioValues, completedAt: Instant) =
        database.withTransaction {
            // One entry per cardio exercise: keep the first row, drop any others.
            val sets = workoutDao.getSetsOf(workoutExerciseId)
            sets.drop(1).forEach { workoutDao.deleteSet(it.id) }
            val setId = sets.firstOrNull()?.id
                ?: workoutDao.insertSet(SetEntity(workoutExerciseId = workoutExerciseId, setNumber = 1))
            workoutDao.logCardio(
                setId = setId,
                durationSec = values.durationSec,
                inclinePct = values.inclinePct,
                speedMinKmh = values.speedMinKmh,
                speedMaxKmh = values.speedMaxKmh,
                distanceKm = values.distanceKm,
                level = values.level,
                completedAt = completedAt.toEpochMilli(),
            )
        }

    /** 1, 2, 3… in the current order. Call inside a transaction. */
    private suspend fun renumberSets(workoutExerciseId: Long) {
        workoutDao.setIdsInOrder(workoutExerciseId).forEachIndexed { index, setId ->
            workoutDao.updateSetNumber(setId, index + 1)
        }
    }

    private companion object {
        /** For empty workouts; tap the title to rename. */
        const val DEFAULT_WORKOUT_NAME = "Workout"
    }
}
