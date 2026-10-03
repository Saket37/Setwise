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
    val bestsByExercise = history.groupBy { it.exerciseId }.mapValues { (_, rows) ->
        PersonalBests.from(rows.map { PreviousSet(weightKg = it.weightKg, reps = it.reps, durationSec = it.durationSec) })
    }
    return WorkoutSession(
        id = workout.id,
        name = workout.name,
        templateId = workout.templateId,
        startedAt = Instant.ofEpochMilli(workout.startedAt),
        endedAt = workout.endedAt?.let(Instant::ofEpochMilli),
        exercises = items
            .sortedWith(compareBy({ it.item.position }, { it.item.id }))
            .map { row ->
                SessionExercise(
                    id = row.item.id,
                    exercise = row.exercise.toDomain(),
                    sets = row.sets.sortedWith(compareBy({ it.setNumber }, { it.id })).map { it.toDomain() },
                    previousSets = previousByItem[row.item.id].orEmpty()
                        .sortedBy { it.setNumber }
                        .map { PreviousSet(weightKg = it.weightKg, reps = it.reps, durationSec = it.durationSec) },
                    bestsBefore = bestsByExercise[row.exercise.id] ?: PersonalBests.None,
                )
            },
    )
}

fun SetEntity.toDomain(): WorkoutSet = WorkoutSet(
    id = id,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    durationSec = durationSec,
    isCompleted = isCompleted,
    isPr = isPr,
)
