package dev.saketanand.setwise.data.mapper

import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.local.relation.ActiveWorkoutRow
import dev.saketanand.setwise.data.local.relation.TemplateWithExercises
import dev.saketanand.setwise.data.local.relation.WorkoutStatsRow
import dev.saketanand.setwise.domain.model.ActiveWorkout
import dev.saketanand.setwise.domain.model.FinishedWorkout
import dev.saketanand.setwise.domain.model.Template
import dev.saketanand.setwise.domain.model.TemplateExercise
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
