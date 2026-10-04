package dev.saketanand.setwise.domain.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A training week in numbers, worked out in code for the weekly summary (design artboard 13):
 * what the card shows and what the recap may say.
 */
data class WeekFacts(
    /** Monday. */
    val weekStart: LocalDate,
    val workouts: Int,
    val timeTrained: Duration,
    val prs: Int,
    val volumeKg: Double,
    /** Against the week before; null if that week had no volume. */
    val volumeChangePercent: Int?,
    /** The week's best set (a record first). */
    val bestSet: BestSetFact?,
    /** The body part whose volume moved most against the week before (5% or more). */
    val bodyPartChange: BodyPartChange?,
    /** A lift trained this week that has stalled ([Progression.plateau]). */
    val plateau: PlateauFact?,
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)
}

data class BestSetFact(val exerciseName: String, val weightKg: Double?, val reps: Int?, val seconds: Int?, val isPr: Boolean)

/** "Legs volume vs last week: +18%". */
data class BodyPartChange(val part: BodyPart, val percent: Int)

data class PlateauFact(val exerciseId: Long, val exerciseName: String, val weeks: Int)

enum class BodyPart(val groups: Set<String>) {
    Legs(setOf("Quads", "Hamstrings", "Glutes", "Calves")),
    Chest(setOf("Chest")),
    Back(setOf("Back")),
    Shoulders(setOf("Shoulders")),
    Arms(setOf("Biceps", "Triceps", "Forearms")),
    Core(setOf("Core")),
}

/** Plain functions, unit-tested. */
object WeeklySummaryRules {

    /** The week before [today]'s, Monday: the one the card sums up. */
    fun lastWeekStart(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)

    /**
     * The week starting [weekStart], from the training log (newest workout first) and the
     * finished workouts; null if nothing was done that week.
     * @param exercises for the plateau rule (an exercise's type decides what "best" means).
     */
    fun facts(
        weekStart: LocalDate,
        log: List<LoggedSetRecord>,
        history: List<WorkoutHistoryItem>,
        exercises: List<Exercise>,
        zone: ZoneId,
    ): WeekFacts? {
        fun inWeek(at: Instant, start: LocalDate) = at.atZone(zone).toLocalDate().let { !it.isBefore(start) && it.isBefore(start.plusWeeks(1)) }
        val workouts = history.filter { inWeek(it.startedAt, weekStart) }
        if (workouts.isEmpty()) return null
        val sets = log.filter { inWeek(it.startedAt, weekStart) }
        val before = log.filter { inWeek(it.startedAt, weekStart.minusWeeks(1)) }
        val volume = sets.volume()
        val volumeBefore = before.volume()

        val best = sets.maxWithOrNull(compareBy({ it.isPr }, { score(it) }))?.let {
            BestSetFact(it.exerciseName, it.weightKg?.takeIf { kg -> kg > 0 }, it.reps, it.durationSec, it.isPr)
        }
        val partChange = BodyPart.entries.mapNotNull { part ->
            val now = sets.filter { it.muscleGroup in part.groups }.volume()
            val then = before.filter { it.muscleGroup in part.groups }.volume()
            if (now <= 0 || then <= 0) null else BodyPartChange(part, ((now - then) / then * 100).roundToInt())
        }.filter { abs(it.percent) >= MIN_CHANGE_PERCENT }.maxByOrNull { abs(it.percent) }

        // A stalled lift among the week's, as of the week's end.
        val weekEndAt = weekStart.plusWeeks(1).atStartOfDay(zone).toInstant()
        val byId = exercises.associateBy { it.id }
        val plateau = sets.map { it.exerciseId }.distinct().firstNotNullOfOrNull { id ->
            val exercise = byId[id] ?: return@firstNotNullOfOrNull null
            val sessions = log.filter { it.exerciseId == id && it.startedAt.isBefore(weekEndAt) }.toSessions()
            Progression.plateau(exercise, sessions, weekEndAt)?.let { PlateauFact(id, exercise.name, it.weeks) }
        }

        return WeekFacts(
            weekStart = weekStart,
            workouts = workouts.size,
            timeTrained = workouts.fold(Duration.ZERO) { total, workout -> total + Duration.between(workout.startedAt, workout.endedAt) },
            prs = workouts.sumOf { it.personalRecords },
            volumeKg = volume,
            volumeChangePercent = if (volumeBefore > 0) ((volume - volumeBefore) / volumeBefore * 100).roundToInt() else null,
            bestSet = best,
            bodyPartChange = partChange,
            plateau = plateau,
        )
    }

    private fun List<LoggedSetRecord>.volume() = sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }

    private fun score(set: LoggedSetRecord): Double =
        PersonalRecords.estimatedOneRepMax(set.weightKg, set.reps) ?: set.reps?.toDouble() ?: set.durationSec?.toDouble() ?: 0.0

    /** Log rows (newest workout first) as sessions, for [Progression]. */
    private fun List<LoggedSetRecord>.toSessions() = groupBy { it.workoutId }.map { (workoutId, sets) ->
        ExerciseSession(workoutId, sets.first().startedAt, sets.map { LoggedSet(it.weightKg, it.reps, it.durationSec, it.distanceKm) })
    }

    private const val MIN_CHANGE_PERCENT = 5
}
