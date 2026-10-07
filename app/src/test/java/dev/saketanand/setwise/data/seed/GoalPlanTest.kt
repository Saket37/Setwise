package dev.saketanand.setwise.data.seed

import dev.saketanand.setwise.data.mapper.toDomain
import dev.saketanand.setwise.data.mapper.toEntity
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.Gear
import dev.saketanand.setwise.domain.model.Goal
import dev.saketanand.setwise.domain.model.GoalPlanner
import dev.saketanand.setwise.domain.model.GoalReader
import dev.saketanand.setwise.domain.model.GoalType
import dev.saketanand.setwise.domain.model.Slot
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Goals read in code, and plans built from the real library (assets/exercises.json). */
class GoalPlanTest {

    private val library: List<Exercise> = Json { ignoreUnknownKeys = true }
        .decodeFromString<ExerciseSeedFile>(File("src/main/assets/exercises.json").readText())
        .exercises.mapIndexed { i, dto -> dto.toEntity().copy(id = i + 1L).toDomain() }

    @Test
    fun `a typed goal is read into type, days, minutes, equipment and named lifts`() {
        val design = GoalReader.read("Get stronger at squat and bench, 45 minutes, I only have dumbbells and a barbell")
        assertEquals(Goal(GoalType.Strength, 3, 45, setOf(Gear.Barbell, Gear.Dumbbell, Gear.Bodyweight), listOf("squat", "bench press")), design)

        assertEquals(Goal(GoalType.Muscle, 4, 60), GoalReader.read("build muscle 4 days a week, about an hour"))
        assertEquals(Goal(GoalType.FatLoss, 3, 30, setOf(Gear.Bodyweight)), GoalReader.read("lose fat, three times per week, half an hour, at home"))
        // As reported (#125): typed "loose", it was read as Muscle.
        assertEquals(GoalType.FatLoss, GoalReader.read("I need to loose 12kg weight,, how can I do in 2 months?").type)
        assertEquals(GoalType.General, GoalReader.read("get fit and healthy").type)
        assertEquals(90, GoalReader.read("1.5 hours").minutes)
        // Unnamed days come from the profile's training days.
        assertEquals(5, GoalReader.read("get fit", defaultDays = 5).daysPerWeek)
    }

    @Test
    fun `the design's goal gives Strength A and B with the named lifts first, 4 x 5`() {
        val goal = GoalReader.read("Get stronger at squat and bench, 45 minutes, I only have dumbbells and a barbell")
        val (a, b) = GoalPlanner.plan(goal, library)

        assertEquals("Strength A", a.name)
        assertEquals(listOf("Back Squat (Barbell)", "Bench Press (Barbell)"), a.exercises.take(2).map { it.exercise.name })
        assertTrue(a.exercises.take(2).all { it.reps == 5 && it.sets >= 4 })
        assertEquals("Strength B", b.name)
        assertEquals("Deadlift (Barbell)", b.exercises.first().exercise.name)
        listOf(a, b).forEach { template ->
            assertTrue(template.name, template.estimatedMinutes in 35..48)
            assertTrue(template.exercises.all { Gear.of(it.exercise.equipment) in goal.gear })
        }
        val all = (a.exercises + b.exercises).map { it.exercise.id }
        assertEquals("no exercise twice in a plan", all.size, all.toSet().size)
    }

    @Test
    fun `the split follows the days a week`() {
        fun names(days: Int) = GoalPlanner.plan(Goal(daysPerWeek = days), library).map { it.name }
        assertEquals(listOf("Full Body"), names(1))
        assertEquals(listOf("Full Body A", "Full Body B"), names(3))
        assertEquals(listOf("Upper", "Lower"), names(4))
        assertEquals(listOf("Push", "Pull", "Legs"), names(5))
    }

    @Test
    fun `more minutes, more work, and the plan fits the time`() {
        listOf(30, 45, 60, 75).forEach { minutes ->
            GoalPlanner.plan(Goal(daysPerWeek = 4, minutes = minutes), library).forEach { template ->
                assertTrue("$minutes min: ${template.name} ~${template.estimatedMinutes}", template.estimatedMinutes <= minutes)
                assertTrue(template.exercises.size >= 3)
            }
        }
    }

    @Test
    fun `at home, only bodyweight exercises, and timed ones get seconds`() {
        val plan = GoalPlanner.plan(GoalReader.read("3 days at home, 30 min"), library)
        val exercises = plan.flatMap { it.exercises }
        assertTrue(exercises.all { Gear.of(it.exercise.equipment) == Gear.Bodyweight })
        assertTrue(exercises.filter { it.isTimed }.all { it.reps == 45 })
    }

    @Test
    fun `a muscle plan hinges with a Romanian deadlift, and the model's pick is used only from the candidates`() {
        val goal = Goal(GoalType.Muscle, daysPerWeek = 4)
        val lower = GoalPlanner.plan(goal, library)[1]
        assertTrue(lower.exercises.any { it.exercise.name.startsWith("Romanian Deadlift") })

        val legPress = library.first { it.name == "Leg Press" }
        val withPick = GoalPlanner.plan(goal, library, pick = { _, _, slot, _ -> if (slot == Slot.Squat) legPress else library.first() })
        assertEquals(legPress, withPick[1].exercises.first().exercise)
        // A pick that isn't one of the slot's candidates (library.first(): not a pull) is ignored.
        assertTrue(withPick[0].exercises.none { it.exercise == library.first() && !Slot.HorizontalPush.fits(it.exercise) })
    }

    @Test
    fun `regenerating varies the exercises that weren't named`() {
        val goal = GoalReader.read("Get stronger at squat and bench, 45 minutes")
        val first = GoalPlanner.plan(goal, library)
        val second = GoalPlanner.plan(goal, library, variation = 1)
        assertEquals(first[0].exercises.take(2).map { it.exercise }, second[0].exercises.take(2).map { it.exercise }) // named lifts stay
        assertTrue(first.flatMap { it.exercises }.map { it.exercise } != second.flatMap { it.exercises }.map { it.exercise })
    }

    @Test
    fun `a fat-loss plan uses higher reps and ends each day with 10 minutes of cardio`() {
        val goal = GoalReader.read("lose 12 kg, 5 days a week, 45 minutes")
        val plan = GoalPlanner.plan(goal, library)

        assertEquals(listOf("Push", "Pull", "Legs"), plan.map { it.name })
        plan.forEach { template ->
            val finisher = template.exercises.last()
            assertTrue(finisher.exercise.name, finisher.isCardio)
            assertEquals(1 to 10, finisher.sets to finisher.reps)
            assertTrue(template.exercises.dropLast(1).none { it.isCardio })
            assertTrue("${template.name}: ${template.estimatedMinutes} min", template.estimatedMinutes in 35..50)
        }
        assertEquals(3, plan.map { it.exercises.last().exercise.id }.toSet().size) // a different cardio each day
        val accessory = plan.first().exercises.first { !it.isCardio && !it.isTimed && it.reps > 10 }
        assertEquals(15, accessory.reps)
    }

    @Test
    fun `at home, the finisher is cardio without machines`() {
        val plan = GoalPlanner.plan(GoalReader.read("lose fat at home, 3 days, 40 minutes"), library)
        assertEquals(listOf("Jump Rope", "Outdoor Run"), plan.map { it.exercises.last().exercise.name }) // never Swimming
    }
}
