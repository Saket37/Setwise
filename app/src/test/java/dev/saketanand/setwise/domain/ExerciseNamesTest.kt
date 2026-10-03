package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseGuess
import dev.saketanand.setwise.domain.model.ExerciseKeywords
import dev.saketanand.setwise.domain.model.ExerciseNames
import dev.saketanand.setwise.domain.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseNamesTest {

    private val library = listOf(
        "Bench Press (Barbell)", "Bench Press (Dumbbell)", "Incline Dumbbell Press", "Overhead Press (Barbell)",
        "Romanian Deadlift (Barbell)", "Deadlift (Barbell)", "Lat Pulldown (Cable)", "Pull-up",
    ).mapIndexed { i, name -> Exercise(i + 1L, name, ExerciseType.STRENGTH, "Chest", "Barbell", 90, false, false, null, null, null) }

    @Test
    fun `shorthand and synonyms are spelled out`() {
        assertEquals(listOf("bench", "dumbbell", "press"), ExerciseNames.words("flat db press"))
        assertEquals(listOf("overhead", "press"), ExerciseNames.words("OHP"))
        assertEquals(listOf("lat", "pull", "down"), ExerciseNames.words("Lat Pulldown"))
        assertTrue(ExerciseNames.sameName("dumbbell bench press", "Bench Press (Dumbbell)"))
        assertTrue(ExerciseNames.sameName("pullup", "Pull-up"))
    }

    @Test
    fun `candidates are the library exercises sharing the most words`() {
        val flat = ExerciseNames.candidates("flat db press", library)
        assertEquals("Bench Press (Dumbbell)", flat.first().first.name)
        assertEquals(1.0, flat.first().second, 0.0) // the same words
        assertTrue(flat.size <= 5)

        assertEquals("Romanian Deadlift (Barbell)", ExerciseNames.candidates("bb rdl", library).first().first.name)
        assertEquals("Lat Pulldown (Cable)", ExerciseNames.candidates("lat pull down", library).first().first.name)
        assertTrue(ExerciseNames.candidates("zercher squat", library).isEmpty())
    }

    @Test
    fun `words speech-to-text mishears are put right against exercise names`() {
        val names = library + listOf("Back Squat (Barbell)", "Plank", "Treadmill").mapIndexed { i, name -> library.first().copy(id = 100L + i, name = name) }

        assertEquals("squat", ExerciseNames.soundAlikeFixed("squad", names))
        assertEquals("plank", ExerciseNames.soundAlikeFixed("flank", names))
        assertEquals("plank", ExerciseNames.soundAlikeFixed("Blank", names))
        assertEquals("pull ups", ExerciseNames.soundAlikeFixed("Full Ops", names))
        // Known words stay; a word with no single close one stays too.
        assertEquals("bench press", ExerciseNames.soundAlikeFixed("bench press", names))
        assertEquals("left pull down", ExerciseNames.soundAlikeFixed("left pull down", names))
    }

    @Test
    fun `a name that only adds its equipment is the same exercise`() {
        assertEquals("Deadlift (Barbell)", ExerciseNames.onlyOneBesidesEquipment("deadlift", library)?.name)
        assertEquals(null, ExerciseNames.onlyOneBesidesEquipment("bench press", library)) // barbell or dumbbell?
        assertEquals(null, ExerciseNames.onlyOneBesidesEquipment("dumbbell deadlift", library))
        assertTrue(ExerciseNames.sameName("pull ups", "Pull-up"))
    }

    @Test
    fun `the only name with every typed word is a match`() {
        assertEquals("Romanian Deadlift (Barbell)", ExerciseNames.onlyOneCovering("barbell romanian", library)?.name)
        assertEquals(null, ExerciseNames.onlyOneCovering("bench", library)) // two bench presses
        assertEquals(null, ExerciseNames.onlyOneCovering("zercher squat", library))
    }

    @Test
    fun `keywords guess what they recognise and leave the rest`() {
        assertEquals(ExerciseGuess("Chest", ExerciseType.STRENGTH, false, "Dumbbell"), ExerciseKeywords.guess("flat db press"))
        assertEquals(ExerciseGuess("Core", ExerciseType.BODYWEIGHT, true, "Bodyweight"), ExerciseKeywords.guess("side plank"))
        assertEquals(ExerciseGuess("Cardio", ExerciseType.CARDIO, false, null), ExerciseKeywords.guess("incline walk"))
        assertEquals(ExerciseGuess("Hamstrings", ExerciseType.STRENGTH, false, "Machine"), ExerciseKeywords.guess("seated leg curl machine"))
        assertEquals(ExerciseGuess("Biceps", ExerciseType.STRENGTH, false, "Cable"), ExerciseKeywords.guess("cable curl"))
        assertEquals(ExerciseGuess("Quads", null, false, null), ExerciseKeywords.guess("zercher squat"))
        assertTrue(ExerciseKeywords.guess("jefferson thing").isEmpty)
    }
}
