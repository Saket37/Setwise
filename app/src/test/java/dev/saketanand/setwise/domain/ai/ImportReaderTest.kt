package dev.saketanand.setwise.domain.ai

import dev.saketanand.setwise.domain.model.OcrLine
import dev.saketanand.setwise.domain.model.SharedSet
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportReaderTest {

    private val model = FakeOnDeviceModel()
    private var screenshots: Map<String, List<OcrLine>> = emptyMap()
    private var files: Map<String, String> = emptyMap()
    private val reader = ImportReader(
        textReader = object : TextReader { override suspend fun read(uri: String) = screenshots.getValue(uri) },
        fileReader = object : FileTextReader { override suspend fun read(uri: String, maxChars: Int) = files.getValue(uri).take(maxChars) },
        model = model,
    )

    /** Prose no code reader takes apart. */
    private val notes = "Leg day 2 Oct 2026, 6pm: squatted 100 for sets of 5, 5 and then 4"

    @Test
    fun `CSV and Strong's text are read in code`() = runTest {
        model.availability = ModelAvailability.Ready
        val csv = reader.fromText("Date,Exercise,Weight (kg),Reps\n2026-09-29,Bench Press,60,8")
        assertEquals(ImportReader.Source.Csv, csv.source)
        val strong = reader.fromText("Evening Workout\nWednesday, 30 September 2026 at 8:01 pm\n\nBench Press (Barbell)\nSet 1: 60 kg × 8 reps")
        assertEquals(ImportReader.Source.StrongShare, strong.source)
        assertTrue(model.requests.isEmpty())
    }

    @Test
    fun `other text goes to the model, kept only with the text's numbers`() = runTest {
        model.availability = ModelAvailability.Ready
        model.answer = { answer(sets = listOf(squat(5), squat(5), squat(4)).joinToString()) }

        val read = reader.fromText(notes)

        assertEquals(ImportReader.Source.Model, read.source)
        val leg = read.workouts.single()
        assertEquals(LocalDateTime.of(2026, 10, 2, 18, 0), leg.startedAt)
        assertEquals(listOf(SharedSet(100.0, 5), SharedSet(100.0, 5), SharedSet(100.0, 4)), leg.exercises.single().sets)

        // 105 isn't in the text: the workout is dropped, and nothing reads it.
        model.answer = { answer(sets = squat(5, kg = 105)) }
        val invented = reader.fromText(notes)
        assertTrue(invented.workouts.isEmpty())
        assertNull(invented.source)
    }

    @Test
    fun `a chosen or shared file is read like text`() = runTest {
        files = mapOf("export.csv" to "Date,Exercise,Weight (kg),Reps\n2026-09-29,Bench Press,60,8")
        val read = reader.fromFile("export.csv")
        assertEquals(ImportReader.Source.Csv, read.source)
        assertEquals(SharedSet(60.0, 8), read.workouts.single().exercises.single().sets.single())
    }

    @Test
    fun `a hand-written log is read in code`() = runTest {
        model.availability = ModelAvailability.Ready
        val read = reader.fromText("Leg day 2 Oct 2026, 6pm\nsquats 100kg 5, 5, 4\nfelt great")
        assertEquals(ImportReader.Source.Log, read.source)
        assertEquals(1, read.unreadLines)
        assertTrue(model.requests.isEmpty())
    }

    @Test
    fun `screenshots are read on the phone, then like text`() = runTest {
        screenshots = mapOf(
            "shot1" to listOf(OcrLine("Wednesday, 30 September 2026 at 8:01 pm", 0, 60, 500, 90), OcrLine("Evening Workout", 0, 10, 300, 40)),
            "shot2" to listOf(OcrLine("Bench Press (Barbell)", 0, 10, 300, 40), OcrLine("Set 1: 60 kg × 8 reps", 0, 60, 300, 90)),
        )

        val read = reader.fromImages(listOf("shot1", "shot2"))

        assertEquals(ImportReader.Source.StrongShare, read.source)
        assertEquals(SharedSet(60.0, 8), read.workouts.single().exercises.single().sets.single())
    }

    @Test
    fun `a screenshot without a header continues the workout, picked first or after`() = runTest {
        screenshots = mapOf(
            "header" to listOf(
                OcrLine("Evening Workout", 0, 10, 300, 40), OcrLine("Wednesday, 30 September 2026 at 8:01 pm", 0, 60, 500, 90),
                OcrLine("Bicep Curl (Barbell)", 0, 110, 300, 140), OcrLine("Set 1: 10 kg × 15 reps", 0, 160, 300, 190),
            ),
            "rest" to listOf(OcrLine("Skullcrusher (Barbell)", 0, 10, 300, 40), OcrLine("Set 1: 20 kg × 14 reps", 0, 60, 300, 90)),
        )

        listOf(listOf("header", "rest"), listOf("rest", "header")).forEach { order ->
            val workout = reader.fromImages(order).workouts.single()
            assertEquals(order.toString(), listOf("Bicep Curl (Barbell)", "Skullcrusher (Barbell)").toSet(), workout.exercises.map { it.name }.toSet())
        }
    }

    private fun squat(reps: Int, kg: Int = 100) = """{"exercise": "Squat", "weightKg": $kg, "reps": $reps, "seconds": 0}"""

    private fun answer(sets: String) =
        """{"name": "Leg day", "date": "2026-10-02", "time": "18:00", "sets": [$sets]}"""
}
