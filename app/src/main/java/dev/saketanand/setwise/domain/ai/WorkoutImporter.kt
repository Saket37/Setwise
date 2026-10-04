package dev.saketanand.setwise.domain.ai

import dev.saketanand.setwise.domain.model.CreateExerciseResult
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseKeywords
import dev.saketanand.setwise.domain.model.ExerciseNames
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.NewExercise
import dev.saketanand.setwise.domain.model.SharedExercise
import dev.saketanand.setwise.domain.model.SharedWorkout
import dev.saketanand.setwise.domain.model.StrongShareParser
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import java.time.Duration
import java.time.ZoneId

/**
 * Imports workouts shared from another app (Strong's "Share workout" text). [plan] reads them
 * in code ([StrongShareParser]) and matches each exercise name like the quick log (exercises
 * done first, then the library; the model only judges unclear names); [import] saves the plan,
 * creating the exercises that had no match, oldest workout first so records come out right.
 * Workouts already imported (same start minute) are skipped.
 */
class WorkoutImporter(
    private val exercises: ExerciseAssistant,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
) {

    data class Plan(val workouts: List<PlannedWorkout>) {
        val toImport: List<PlannedWorkout> get() = workouts.filterNot { it.alreadyImported }

        /** Exercise names with no match: created on import. */
        val newExercises: List<String> get() = toImport.flatMap { it.exercises }.filter { it.match == null }.map { it.shared.name }.distinct()
    }

    data class PlannedWorkout(val shared: SharedWorkout, val exercises: List<PlannedExercise>, val alreadyImported: Boolean)

    /** [match]: the library exercise it is; null: created as the user's own. */
    data class PlannedExercise(val shared: SharedExercise, val match: Exercise?)

    data class Result(val workouts: Int, val sets: Int, val newExercises: Int)

    suspend fun plan(text: String, library: List<Exercise>, done: List<Exercise>, zone: ZoneId): Plan {
        val shared = StrongShareParser.parse(text)
        val matches = mutableMapOf<String, Exercise?>()
        return Plan(
            shared.map { workout ->
                PlannedWorkout(
                    shared = workout,
                    exercises = workout.exercises.map { exercise ->
                        PlannedExercise(exercise, matches.getOrPut(exercise.name.lowercase()) { match(exercise.name, library, done) })
                    },
                    alreadyImported = workoutRepository.hasWorkoutStartedAt(workout.startedAt.atZone(zone).toInstant()),
                )
            },
        )
    }

    suspend fun import(plan: Plan, zone: ZoneId): Result {
        // The exercises with no match, made once each.
        val created = plan.newExercises.associateWith { name -> createExercise(name, plan) }
        var sets = 0
        plan.toImport.sortedBy { it.shared.startedAt }.forEach { workout ->
            val start = workout.shared.startedAt.atZone(zone).toInstant()
            val exercises = workout.exercises.mapNotNull { planned ->
                val id = planned.match?.id ?: created[planned.shared.name] ?: return@mapNotNull null
                id to planned.shared.sets
            }
            if (exercises.isEmpty()) return@forEach
            workoutRepository.importWorkout(workout.shared.name, start, start.plus(estimatedLength(workout.shared)), exercises)
            sets += exercises.sumOf { it.second.size }
        }
        return Result(plan.toImport.size, sets, created.size)
    }

    private suspend fun match(name: String, library: List<Exercise>, done: List<Exercise>): Exercise? {
        library.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
        val heard = ExerciseNames.soundAlikeFixed(name, library).ifEmpty { name }
        return exercises.clearMatch(heard, done) ?: exercises.findMatch(heard, library)
    }

    /** A new exercise from a name and how its sets were logged; its group and gear guessed from the name. */
    private suspend fun createExercise(name: String, plan: Plan): Long? {
        val sets = plan.toImport.flatMap { it.exercises }.filter { it.shared.name == name }.flatMap { it.shared.sets }
        val guess = ExerciseKeywords.guess(name)
        val type = when {
            sets.any { it.distanceKm != null } || guess.type == ExerciseType.CARDIO -> ExerciseType.CARDIO
            sets.all { it.reps == null && it.seconds != null } -> ExerciseType.BODYWEIGHT
            sets.none { it.weightKg != null } -> ExerciseType.BODYWEIGHT
            else -> guess.type ?: ExerciseType.STRENGTH
        }
        val result = exerciseRepository.createExercise(
            NewExercise(
                name = name,
                type = type,
                isTimed = type == ExerciseType.BODYWEIGHT && sets.all { it.reps == null && it.seconds != null },
                muscleGroup = guess.muscleGroup ?: if (type == ExerciseType.CARDIO) "Cardio" else "Full Body",
                equipment = guess.equipment ?: if (type == ExerciseType.STRENGTH) "None" else "Bodyweight",
                restSec = if (type == ExerciseType.CARDIO) 0 else DEFAULT_REST_SEC,
            ),
        )
        return when (result) {
            is CreateExerciseResult.Created -> result.exerciseId
            is CreateExerciseResult.NameTaken -> result.existing.id
        }
    }

    companion object {
        private const val DEFAULT_REST_SEC = 90

        /** Strong's share has no duration: about 2½ minutes a set, at least 20 minutes. */
        fun estimatedLength(workout: SharedWorkout): Duration =
            Duration.ofSeconds(maxOf(20 * 60L, workout.setCount * 150L))
    }
}
