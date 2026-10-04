package dev.saketanand.setwise.data.dev

import android.util.Log
import androidx.room.withTransaction
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.data.local.SetwiseDatabase
import dev.saketanand.setwise.data.local.dao.ExerciseDao
import dev.saketanand.setwise.data.local.dao.TemplateDao
import dev.saketanand.setwise.data.local.dao.WorkoutDao
import dev.saketanand.setwise.data.local.entity.ExerciseEntity
import dev.saketanand.setwise.data.local.entity.SetEntity
import dev.saketanand.setwise.data.local.entity.TemplateEntity
import dev.saketanand.setwise.data.local.entity.TemplateExerciseEntity
import dev.saketanand.setwise.data.local.entity.WorkoutEntity
import dev.saketanand.setwise.data.local.entity.WorkoutExerciseEntity
import dev.saketanand.setwise.domain.model.ExerciseType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max

/**
 * DEBUG ONLY: fills an empty database with fake templates and workout history so the Workout
 * tab's dashboard can be checked without logging real workouts. Called from SetwiseApp only when
 * BuildConfig.DEBUG && [ENABLED].
 *
 * To see the first-run screen instead: set [ENABLED] to false, then clear the app's data
 * (Settings → Apps → Setwise → Storage → Clear storage), or reinstall.
 */
class DevDataSeeder(
    private val database: SetwiseDatabase,
    private val exerciseDao: ExerciseDao,
    private val templateDao: TemplateDao,
    private val workoutDao: WorkoutDao,
    private val workoutRepository: WorkoutRepository,
) {

    suspend fun seedIfEmpty() {
        try {
            if (workoutDao.count() > 0) return
            database.withTransaction { seed() }
            Log.i(TAG, "Seeded fake templates and workout history")
        } catch (e: Exception) {
            Log.e(TAG, "Fake data seeding failed", e)
        }
    }

    private suspend fun seed() {
        val exercises = PLANS.flatMap { it.exercises }.map { it.name }.distinct()
            .mapNotNull { exerciseDao.getByName(it) }
            .associateBy { it.name }

        // Templates
        val templateIds = PLANS.associate { plan ->
            val templateId = templateDao.insertTemplate(
                TemplateEntity(name = plan.name, category = plan.category, createdAt = System.currentTimeMillis())
            )
            templateDao.insertTemplateExercises(
                plan.exercises.mapIndexedNotNull { index, planned ->
                    val exercise = exercises[planned.name] ?: return@mapIndexedNotNull null
                    TemplateExerciseEntity(
                        templateId = templateId,
                        exerciseId = exercise.id,
                        position = index,
                        targetSets = planned.sets,
                    )
                }
            )
            plan.name to templateId
        }

        // Finished workouts, oldest first, so weights can go up session by session.
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val sessionsByPlan = HISTORY.groupBy { it.planName }
        val workoutIds = mutableListOf<Long>()
        HISTORY.sortedByDescending { it.daysAgo }.forEach { session ->
            val plan = PLANS.first { it.name == session.planName }
            val occurrences = sessionsByPlan.getValue(plan.name).sortedByDescending { it.daysAgo }
            val sessionIndex = occurrences.indexOf(session)
            val sessionsLeft = occurrences.size - 1 - sessionIndex // 0 = most recent

            val start = today.minusDays(session.daysAgo.toLong()).atTime(LocalTime.of(18, 30)).atZone(zone)
            val workoutId = workoutDao.insertWorkout(
                WorkoutEntity(
                    name = plan.name,
                    templateId = templateIds[plan.name],
                    startedAt = start.toInstant().toEpochMilli(),
                    endedAt = start.plusMinutes(session.minutes.toLong()).toInstant().toEpochMilli(),
                    calories = session.minutes * 6,
                    caloriesSource = "formula",
                )
            )
            workoutIds += workoutId
            plan.exercises.forEachIndexed { position, planned ->
                val exercise = exercises[planned.name] ?: return@forEachIndexed
                val workoutExerciseId = workoutDao.insertWorkoutExercise(
                    WorkoutExerciseEntity(workoutId = workoutId, exerciseId = exercise.id, position = position)
                )
                val completedAt = start.toInstant().toEpochMilli()
                workoutDao.insertSets(
                    (1..planned.sets).map { number ->
                        fakeSet(exercise, planned, workoutExerciseId, number, sessionsLeft, completedAt)
                    }
                )
            }
        }
        // Records by the app's own rules, so History, Home and the summary all agree.
        workoutIds.forEach { workoutRepository.refreshPersonalRecords(it) }
    }

    private fun fakeSet(
        exercise: ExerciseEntity,
        planned: PlannedExercise,
        workoutExerciseId: Long,
        number: Int,
        sessionsLeft: Int,
        completedAt: Long,
    ): SetEntity {
        val base = SetEntity(
            workoutExerciseId = workoutExerciseId,
            setNumber = number,
            isCompleted = true,
            completedAt = completedAt,
        )
        return when {
            exercise.type == ExerciseType.CARDIO -> base.copy(
                durationSec = 30 * 60 - sessionsLeft * 60,
                inclinePct = 6.0,
                speedMinKmh = 5.5,
                speedMaxKmh = 8.0,
                distanceKm = 4.2 - sessionsLeft * 0.1,
            )
            exercise.isTimed -> base.copy(durationSec = 45 + 5 * (2 - sessionsLeft).coerceAtLeast(0))
            planned.weightKg == null -> base.copy(reps = 10 - (number / 3))
            planned.trend == Trend.Ready -> base.copy(
                // The last two sessions at the planned weight, every rep: ready to add weight.
                weightKg = planned.weightKg - (sessionsLeft - 1).coerceAtLeast(0) * 2.5,
                reps = if (number == planned.sets && sessionsLeft > 1) 7 else 8,
            )
            planned.trend == Trend.Plateau -> {
                // Stuck at an estimated 1RM of about 48 kg: 40 × 6 and 42.5 × 4 in turn.
                val heavy = sessionsLeft % 2 == 1
                base.copy(
                    weightKg = if (heavy) planned.weightKg + 2.5 else planned.weightKg,
                    reps = (if (heavy) 4 else 6) - (if (number == planned.sets) 1 else 0),
                )
            }
            else -> base.copy(
                // 2.5 kg lighter for every session before the most recent one.
                weightKg = max(planned.weightKg * 0.8, planned.weightKg - sessionsLeft * 2.5),
                reps = if (number == planned.sets) 7 else 8,
            )
        }
    }

    /** How an exercise's sessions go, so the progression hints have something to show. */
    private enum class Trend { Progressing, Ready, Plateau }

    private data class PlannedExercise(val name: String, val sets: Int, val weightKg: Double?, val trend: Trend = Trend.Progressing)
    private data class Plan(val name: String, val category: String, val exercises: List<PlannedExercise>)
    private data class Session(val planName: String, val daysAgo: Int, val minutes: Int)

    companion object {
        /** Flip to false (and clear app data) to see the first-run screen in debug builds. */
        const val ENABLED = true

        private const val TAG = "DevDataSeeder"

        private val PLANS = listOf(
            Plan(
                "Push Day", "Push", listOf(
                    PlannedExercise("Bench Press (Barbell)", 4, 62.5, Trend.Ready),
                    PlannedExercise("Incline Bench Press (Dumbbell)", 3, 24.0),
                    PlannedExercise("Overhead Press (Barbell)", 3, 40.0, Trend.Plateau),
                    PlannedExercise("Lateral Raise (Dumbbell)", 3, 10.0),
                    PlannedExercise("Triceps Pushdown (Cable - Rope)", 3, 25.0),
                    PlannedExercise("Chest Dip", 3, null),
                )
            ),
            Plan(
                "Pull Day", "Pull", listOf(
                    PlannedExercise("Deadlift (Barbell)", 3, 120.0),
                    PlannedExercise("Bent-over Row (Barbell)", 3, 60.0),
                    PlannedExercise("Pull-up", 3, null),
                    PlannedExercise("Face Pull (Rope)", 3, 20.0),
                    PlannedExercise("Bicep Curl (Barbell)", 3, 30.0),
                )
            ),
            Plan(
                "Leg Day", "Legs", listOf(
                    PlannedExercise("Back Squat (Barbell)", 4, 100.0),
                    PlannedExercise("Romanian Deadlift (Barbell)", 3, 80.0),
                    PlannedExercise("Leg Press", 3, 160.0),
                    PlannedExercise("Lying Leg Curl", 3, 40.0),
                    PlannedExercise("Standing Calf Raise (Machine)", 3, 60.0),
                )
            ),
            Plan(
                "Cardio + Core", "Cardio", listOf(
                    PlannedExercise("Treadmill", 1, null),
                    PlannedExercise("Plank", 3, null),
                    PlannedExercise("Hanging Leg Raise", 3, null),
                )
            ),
        )

        /** Most recent first. */
        private val HISTORY = listOf(
            Session("Pull Day", daysAgo = 2, minutes = 62),
            Session("Push Day", daysAgo = 4, minutes = 69),
            Session("Leg Day", daysAgo = 6, minutes = 74),
            Session("Cardio + Core", daysAgo = 8, minutes = 41),
            Session("Pull Day", daysAgo = 9, minutes = 58),
            Session("Push Day", daysAgo = 11, minutes = 65),
            Session("Leg Day", daysAgo = 13, minutes = 71),
            Session("Pull Day", daysAgo = 16, minutes = 60),
            Session("Push Day", daysAgo = 18, minutes = 66),
            Session("Leg Day", daysAgo = 20, minutes = 70),
            Session("Push Day", daysAgo = 25, minutes = 64),
            Session("Push Day", daysAgo = 32, minutes = 63),
        )

        /** Exercises whose latest session gets a PR (top set). */
    }
}
