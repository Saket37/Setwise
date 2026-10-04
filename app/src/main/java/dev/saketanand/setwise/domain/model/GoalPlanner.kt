package dev.saketanand.setwise.domain.model

import java.util.Locale

/** One exercise of a planned template: sets × reps (seconds for a timed one). */
data class PlannedExercise(val exercise: Exercise, val sets: Int, val reps: Int) {
    val isTimed: Boolean get() = exercise.isTimed
}

/** One template of a plan ("Strength A"), with its estimated minutes. */
data class PlannedTemplate(val name: String, val category: String, val exercises: List<PlannedExercise>) {
    /** As Home estimates a template: each set's rest plus about 40 s of work. */
    val estimatedMinutes: Int
        get() = (exercises.sumOf { it.sets * (it.exercise.defaultRestSec + WORK_SECONDS_PER_SET) } / SECONDS_PER_MINUTE.toDouble())
            .let { kotlin.math.round(it).toInt() }
}

/** A movement a template needs, and which library exercises can fill it. */
enum class Slot(private val matches: (Exercise, String) -> Boolean) {
    Squat({ e, n -> e.muscleGroup == "Quads" && ("squat" in n && "split" !in n || "leg press" in n) }),
    Hinge({ _, n -> listOf("deadlift", "romanian", "good morning", "hip thrust", "kettlebell swing").any { it in n } && "rack pull" !in n }),
    HorizontalPush({ _, n -> ("bench press" in n || "chest press" in n || "push-up" in n || "floor press" in n) && "close-grip" !in n }),
    InclinePush({ _, n -> "incline" in n && ("press" in n || "push-up" in n) }),
    ChestFly({ e, n -> e.muscleGroup == "Chest" && "fly" in n }),
    VerticalPush({ _, n -> "overhead press" in n || "shoulder press" in n || "arnold press" in n }),
    HorizontalPull({ e, n -> e.muscleGroup == "Back" && "row" in n && "upright" !in n }),
    VerticalPull({ _, n -> ("pulldown" in n && "straight-arm" !in n) || "pull-up" in n || "chin-up" in n }),
    SingleLeg({ _, n -> "lunge" in n || "split squat" in n || "step-up" in n }),
    LegCurl({ _, n -> "leg curl" in n || "nordic" in n }),
    Calves({ e, _ -> e.muscleGroup == "Calves" }),
    Core({ e, _ -> e.muscleGroup == "Core" }),
    Biceps({ e, _ -> e.muscleGroup == "Biceps" }),
    Triceps({ e, n -> e.muscleGroup == "Triceps" && "bench press" !in n }),
    LateralRaise({ _, n -> "lateral raise" in n }),
    RearDelt({ _, n -> "rear delt" in n || "face pull" in n });

    fun fits(exercise: Exercise): Boolean =
        exercise.type != ExerciseType.CARDIO && matches(exercise, exercise.name.lowercase(Locale.ROOT))

    /** The first lifts of a template, done heavier in a strength plan. */
    val isMainLift: Boolean get() = this in MAIN_LIFTS

    private companion object {
        val MAIN_LIFTS = setOf(Squat, Hinge, HorizontalPush, VerticalPush, HorizontalPull, VerticalPull)
    }
}

/**
 * Builds templates for a [Goal] from the library, in code: a split by days a week, each day's
 * movement slots in priority order (as many as fit the minutes), and for each slot the best
 * library exercise with allowed equipment (named lifts first, then ones done before, then the
 * goal's preferred equipment). Sets × reps by goal. The on-device model may then choose among
 * each slot's [candidates]; without it, the first is used. Plain functions, unit-tested.
 */
object GoalPlanner {

    data class Day(val name: String, val category: String, val slots: List<Slot>)

    /** The split for [goal]: full body for up to 3 days, upper / lower for 4, push / pull / legs for 5+. */
    fun days(goal: Goal): List<Day> {
        val prefix = if (goal.type == GoalType.Strength) "Strength" else "Full Body"
        return when (goal.daysPerWeek) {
            1 -> listOf(Day(prefix, "Full body", FULL_BODY_A))
            in 2..3 -> listOf(Day("$prefix A", "Full body", FULL_BODY_A), Day("$prefix B", "Full body", FULL_BODY_B))
            4 -> listOf(Day("Upper", "Upper", UPPER), Day("Lower", "Lower", LOWER))
            else -> listOf(Day("Push", "Push", PUSH), Day("Pull", "Pull", PULL), Day("Legs", "Legs", LEGS))
        }
    }

    /**
     * Library exercises that can fill [slot] for [goal], best first.
     * @param doneIds exercises the person has done (they go first after named lifts).
     */
    fun candidates(slot: Slot, goal: Goal, library: List<Exercise>, doneIds: Set<Long>): List<Exercise> {
        val gearOrder = (if (slot.isMainLift) MAIN_LIFT_GEAR else ACCESSORY_GEAR).getValue(goal.type)
        return library
            .filter { slot.fits(it) && Gear.of(it.equipment) in goal.gear }
            .sortedWith(
                compareBy<Exercise>(
                    { exercise -> if (goal.focus.any { it in exercise.name.lowercase(Locale.ROOT) }) 0 else 1 },
                    { if (it.id in doneIds) 0 else 1 },
                    // Plank before other core work: a steady hold suits every goal.
                    { if (slot == Slot.Core && !it.name.startsWith("Plank")) 1 else 0 },
                    // Higher reps on a hinge: the Romanian deadlift, not a heavy conventional pull.
                    { if (slot == Slot.Hinge && goal.type != GoalType.Strength && "Romanian" !in it.name) 1 else 0 },
                    { gearOrder.indexOf(Gear.of(it.equipment)) },
                ),
            )
    }

    /**
     * The plan: for each day, its slots in order while the estimated time fits [Goal.minutes]
     * (at least [MIN_EXERCISES], at most [MAX_EXERCISES]). An exercise isn't repeated across the
     * plan while a slot has another candidate. [pick] chooses a slot's exercise from its
     * candidates (the model's choice); by default the first. [variation] shifts to later
     * candidates, for "Regenerate".
     */
    fun plan(
        goal: Goal,
        library: List<Exercise>,
        doneIds: Set<Long> = emptySet(),
        variation: Int = 0,
        pick: (day: Int, position: Int, slot: Slot, candidates: List<Exercise>) -> Exercise? = { _, _, _, _ -> null },
    ): List<PlannedTemplate> {
        val used = mutableSetOf<Long>()
        return days(goal).mapIndexed { dayIndex, day ->
            val planned = mutableListOf<PlannedExercise>()
            var seconds = 0
            for ((position, slot) in day.slots.withIndex()) {
                if (planned.size >= MAX_EXERCISES) break
                val options = candidates(slot, goal, library, doneIds).filter { it.id !in used }
                    .ifEmpty { candidates(slot, goal, library, doneIds).filter { it.id !in planned.map { p -> p.exercise.id } } }
                if (options.isEmpty()) continue
                val exercise = pick(dayIndex, position, slot, options)?.takeIf { it in options }
                    ?: options[if (goal.focus.any { it in options.first().name.lowercase(Locale.ROOT) }) 0 else variation % options.size]
                val (sets, reps) = scheme(goal.type, slot, planned.size, exercise)
                val cost = sets * (exercise.defaultRestSec + WORK_SECONDS_PER_SET)
                if (planned.size >= MIN_EXERCISES && seconds + cost > goal.minutes * SECONDS_PER_MINUTE) break
                planned += PlannedExercise(exercise, sets, reps)
                seconds += cost
                used += exercise.id
            }
            PlannedTemplate(day.name, day.category, withTimeFilled(planned, goal.minutes * SECONDS_PER_MINUTE - seconds))
        }.filter { it.exercises.isNotEmpty() }
    }

    /**
     * Time left over (every slot used): one more set at a time, main lifts first (up to
     * [MAX_MAIN_SETS]), then the others (up to [MAX_OTHER_SETS]), while it fits.
     */
    private fun withTimeFilled(planned: List<PlannedExercise>, secondsLeft: Int): List<PlannedExercise> {
        val result = planned.toMutableList()
        var left = secondsLeft
        var added = true
        while (added) {
            added = false
            val order = result.indices.sortedBy { if (result[it].sets >= MAX_OTHER_SETS - 1 && !result[it].isTimed) 0 else 1 }
            for (i in order) {
                val item = result[i]
                val cap = if (item.reps <= 5) MAX_MAIN_SETS else MAX_OTHER_SETS
                val cost = item.exercise.defaultRestSec + WORK_SECONDS_PER_SET
                if (item.sets < cap && cost <= left) {
                    result[i] = item.copy(sets = item.sets + 1)
                    left -= cost
                    added = true
                }
            }
        }
        return result
    }

    /** Sets × reps (seconds for a timed exercise) by goal; a strength plan's first two main lifts are 4 × 5. */
    fun scheme(type: GoalType, slot: Slot, position: Int, exercise: Exercise): Pair<Int, Int> = when {
        exercise.isTimed -> 3 to TIMED_SECONDS
        type == GoalType.Strength && slot.isMainLift && position < 2 -> 4 to 5
        type == GoalType.Strength && slot.isMainLift -> 3 to 8
        type == GoalType.Strength -> 3 to 10
        type == GoalType.Muscle && slot.isMainLift -> 3 to 10
        type == GoalType.Muscle -> 3 to 12
        else -> 3 to 12
    }

    private const val MIN_EXERCISES = 3
    private const val MAX_EXERCISES = 8
    private const val TIMED_SECONDS = 45
    private const val MAX_MAIN_SETS = 5
    private const val MAX_OTHER_SETS = 4

    private val FULL_BODY_A = listOf(Slot.Squat, Slot.HorizontalPush, Slot.HorizontalPull, Slot.Core, Slot.VerticalPush, Slot.Biceps, Slot.LegCurl)
    private val FULL_BODY_B = listOf(Slot.Hinge, Slot.InclinePush, Slot.VerticalPull, Slot.SingleLeg, Slot.Triceps, Slot.LateralRaise, Slot.Core)
    private val UPPER = listOf(Slot.HorizontalPush, Slot.HorizontalPull, Slot.VerticalPush, Slot.VerticalPull, Slot.Biceps, Slot.Triceps, Slot.LateralRaise)
    private val LOWER = listOf(Slot.Squat, Slot.Hinge, Slot.SingleLeg, Slot.LegCurl, Slot.Calves, Slot.Core)
    // A slot listed twice takes a different exercise the second time (one isn't repeated in a plan).
    private val PUSH = listOf(Slot.HorizontalPush, Slot.VerticalPush, Slot.InclinePush, Slot.LateralRaise, Slot.Triceps, Slot.ChestFly, Slot.Triceps)
    private val PULL = listOf(Slot.VerticalPull, Slot.HorizontalPull, Slot.RearDelt, Slot.Biceps, Slot.HorizontalPull, Slot.Biceps, Slot.Core)
    private val LEGS = listOf(Slot.Squat, Slot.Hinge, Slot.SingleLeg, Slot.LegCurl, Slot.Squat, Slot.Calves, Slot.Core)

    /** Equipment each goal prefers for its main lifts, best first: a barbell to lift heavy. */
    private val MAIN_LIFT_GEAR = mapOf(
        GoalType.Strength to listOf(Gear.Barbell, Gear.Dumbbell, Gear.Machine, Gear.Kettlebell, Gear.Cable, Gear.Bodyweight),
        GoalType.Muscle to listOf(Gear.Barbell, Gear.Dumbbell, Gear.Machine, Gear.Cable, Gear.Kettlebell, Gear.Bodyweight),
        GoalType.General to listOf(Gear.Dumbbell, Gear.Kettlebell, Gear.Machine, Gear.Bodyweight, Gear.Cable, Gear.Barbell),
    )

    /** And for accessories: dumbbells and cables, as usual. */
    private val ACCESSORY_GEAR = mapOf(
        GoalType.Strength to listOf(Gear.Dumbbell, Gear.Cable, Gear.Machine, Gear.Barbell, Gear.Kettlebell, Gear.Bodyweight),
        GoalType.Muscle to listOf(Gear.Dumbbell, Gear.Cable, Gear.Machine, Gear.Barbell, Gear.Kettlebell, Gear.Bodyweight),
        GoalType.General to listOf(Gear.Dumbbell, Gear.Bodyweight, Gear.Machine, Gear.Kettlebell, Gear.Cable, Gear.Barbell),
    )
}

private const val WORK_SECONDS_PER_SET = 40
private const val SECONDS_PER_MINUTE = 60
