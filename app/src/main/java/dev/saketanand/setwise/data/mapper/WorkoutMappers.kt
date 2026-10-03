package dev.saketanand.setwise.data.mapper

import dev.saketanand.setwise.data.local.entity.SetEntity
import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.local.relation.ActiveWorkoutRow
import dev.saketanand.setwise.data.local.relation.ExerciseHistorySetRow
import dev.saketanand.setwise.data.local.relation.PreviousSetRow
import dev.saketanand.setwise.data.local.relation.TemplateWithExercises
import dev.saketanand.setwise.data.local.relation.WorkoutHistoryRow
import dev.saketanand.setwise.data.local.relation.WorkoutStatsRow
import dev.saketanand.setwise.data.local.relation.WorkoutWithExercises
import dev.saketanand.setwise.domain.model.Intensity
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.ActiveWorkout
import dev.saketanand.setwise.domain.model.FinishedWorkout
import dev.saketanand.setwise.domain.model.PersonalBests
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.Template
import dev.saketanand.setwise.domain.model.TemplateExercise
import dev.saketanand.setwise.domain.model.WorkoutHistoryItem
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.model.WorkoutSet
import dev.saketanand.setwise.domain.model.WorkoutStats
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import dev.saketanand.setwise.domain.model.CardioValues
import dev.saketanand.setwise.domain.model.ExerciseSession
import dev.saketanand.setwise.domain.model.LoggedSet

/** Only call for finished workouts (endedAt != null). */
fun WorkoutEntity.toFinishedWorkout(): FinishedWorkout = FinishedWorkout(
    id = id,
    name = name,
    startedAt = Instant.ofEpochMilli(startedAt),
    endedAt = Instant.ofEpochMilli(requireNotNull(endedAt) { "Workout $id isn't finished" }),
)

fun ActiveWorkoutRow.toDomain(): ActiveWorkout = ActiveWorkout(
    id = id,
    name = name,
    startedAt = Instant.ofEpochMilli(startedAt),
    completedSets = completedSets,
)

fun WorkoutHistoryRow.toDomain(): WorkoutHistoryItem = WorkoutHistoryItem(
    id = id,
    name = name,
    startedAt = Instant.ofEpochMilli(startedAt),
    endedAt = Instant.ofEpochMilli(endedAt),
    completedSets = completedSets,
    volumeKg = volumeKg,
    distanceKm = distanceKm,
    personalRecords = personalRecords,
    calories = calories,
)

fun WorkoutStatsRow.toDomain(): WorkoutStats = WorkoutStats(
    workouts = workouts,
    timeTrained = totalMillis.milliseconds,
    prs = prs,
)

fun TemplateWithExercises.toDomain(lastUsedAtMillis: Long?): Template = Template(
    id = template.id,
    name = template.name,
    category = template.category,
    exercises = items
        .sortedBy { it.item.position }
        .map {
            TemplateExercise(
                exerciseId = it.exercise.id,
                name = it.exercise.name,
                targetSets = it.item.targetSets,
                restSec = it.exercise.defaultRestSec,
                muscleGroup = it.exercise.muscleGroup,
                isCardio = it.exercise.type == ExerciseType.CARDIO,
            )
        },
    lastUsedAt = lastUsedAtMillis?.let(Instant::ofEpochMilli),
)

/** Joins the workout tree with the "previous" rows; sorts what Room returns unordered. */
fun WorkoutWithExercises.toSession(
    previous: List<PreviousSetRow>,
    history: List<ExerciseHistorySetRow> = emptyList(),
): WorkoutSession {
    val previousByItem = previous.groupBy { it.workoutExerciseId }
    val historyByExercise = history.groupBy { it.exerciseId }
    val bestsByExercise = historyByExercise.mapValues { (_, rows) ->
        PersonalBests.from(rows.map { PreviousSet(weightKg = it.weightKg, reps = it.reps, durationSec = it.durationSec) })
    }
    // Rows come newest workout first, sets in order; groupBy keeps both orders.
    val sessionsByExercise = historyByExercise.mapValues { (_, rows) ->
        rows.groupBy { it.workoutId }.map { (workoutId, sets) ->
            ExerciseSession(
                workoutId = workoutId,
                startedAt = Instant.ofEpochMilli(sets.first().startedAt),
                sets = sets.map { LoggedSet(it.weightKg, it.reps, it.durationSec, distanceKm = null) },
            )
        }
    }
    return WorkoutSession(
        id = workout.id,
        name = workout.name,
        templateId = workout.templateId,
        startedAt = Instant.ofEpochMilli(workout.startedAt),
        endedAt = workout.endedAt?.let(Instant::ofEpochMilli),
        calories = workout.calories,
        intensity = Intensity.fromStored(workout.intensity),
        insight = workout.summary,
        exercises = items
            .sortedWith(compareBy({ it.item.position }, { it.item.id }))
            .map { row ->
                SessionExercise(
                    id = row.item.id,
                    exercise = row.exercise.toDomain(),
                    sets = row.sets.sortedWith(compareBy({ it.setNumber }, { it.id }))
                        .map { it.toDomain(isCardio = row.exercise.type == ExerciseType.CARDIO) },
                    previousSets = previousByItem[row.item.id].orEmpty()
                        .sortedBy { it.setNumber }
                        .map { PreviousSet(weightKg = it.weightKg, reps = it.reps, durationSec = it.durationSec) },
                    bestsBefore = bestsByExercise[row.exercise.id] ?: PersonalBests.None,
                    history = sessionsByExercise[row.exercise.id].orEmpty(),
                )
            },
    )
}

fun SetEntity.toDomain(isCardio: Boolean = false): WorkoutSet = WorkoutSet(
    id = id,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    durationSec = durationSec,
    isCompleted = isCompleted,
    isPr = isPr,
    cardio = if (isCardio) toCardioValues().takeUnless { it.isEmpty } else null,
    completedAt = completedAt?.let(Instant::ofEpochMilli),
)

fun SetEntity.toCardioValues() = CardioValues(
    durationSec = durationSec,
    inclinePct = inclinePct,
    speedMinKmh = speedMinKmh,
    speedMaxKmh = speedMaxKmh,
    distanceKm = distanceKm,
    level = level,
)
