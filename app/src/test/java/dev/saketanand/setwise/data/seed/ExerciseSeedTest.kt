package dev.saketanand.setwise.data.seed

import dev.saketanand.setwise.data.mapper.toEntity
import dev.saketanand.setwise.domain.model.ExerciseType
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Checks the real assets/exercises.json parses and maps cleanly. Runs on the JVM, no emulator. */
class ExerciseSeedTest {

    private lateinit var seed: ExerciseSeedFile

    @Before
    fun setUp() {
        // Unit tests run with the app/ module as the working directory.
        val text = File("src/main/assets/exercises.json").readText()
        seed = json.decodeFromString<ExerciseSeedFile>(text)
    }

    @Test
    fun `seed file has 204 exercises`() {
        assertEquals(204, seed.exercises.size)
    }

    @Test
    fun `every rename leads to an exercise in the file, and away from one that isn't`() {
        val names = seed.exercises.map { it.name }.toSet()
        seed.renames.forEach { rename ->
            assertTrue(rename.to, rename.to in names)
            assertTrue(rename.from, rename.from !in names)
        }
    }

    @Test
    fun `every equipment is one the app offers, or a piece of its own`() {
        val own = setOf("Ab Wheel", "Sled", "Jump Rope")
        seed.exercises.forEach { assertTrue(it.name, it.equipment in dev.saketanand.setwise.domain.model.ExerciseCatalog.EQUIPMENT || it.equipment in own) }
    }

    @Test
    fun `exercise names are unique ignoring case`() {
        val names = seed.exercises.map { it.name.lowercase() }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `every exercise maps to an entity`() {
        // Throws if any type / metric / calorieMethod string isn't a valid enum value.
        val entities = seed.exercises.map { it.toEntity() }
        assertTrue(entities.none { it.isCustom })
    }

    @Test
    fun `every cardio exercise has metrics and a calorie method`() {
        val cardio = seed.exercises.map { it.toEntity() }.filter { it.type == ExerciseType.CARDIO }
        assertTrue(cardio.isNotEmpty())
        cardio.forEach {
            assertTrue("${it.name} has no metrics", !it.metrics.isNullOrEmpty())
            assertTrue("${it.name} has no calorie method", it.calorieMethod != null)
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
