package dev.saketanand.setwise.domain.ai

import dev.saketanand.setwise.domain.model.OcrLine
import dev.saketanand.setwise.domain.model.SharedSet
import dev.saketanand.setwise.testing.FakeOnDeviceModel
import dev.saketanand.setwise.util.DateProvider
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
        dateProvider = Today,
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
    fun `lines code can read keep code's numbers, wherever the model put them`() = runTest {
        model.availability = ModelAvailability.Ready
        // A date the log reader doesn't read ("the 30th"), so the model reads the workout. On a
        // Pixel, it gave the rows as 60 kg × 3 reps (#43).
        model.answer = {
            """{"name": "Back day", "date": "2026-09-30", "time": "12:00", "sets": [""" +
                """{"exercise": "Barbell rows", "weightKg": 60, "reps": 3, "seconds": 0}, """ +
                """{"exercise": "Pull-ups", "weightKg": 0, "reps": 8, "seconds": 0}]}"""
        }

        val read = reader.fromText("Back day, the 30th\nBarbell rows, 3 sets of 10 with 60\nPull-ups: did 8 at the end")

        val (rows, pullUps) = read.workouts.single().exercises
        assertEquals(List(3) { SharedSet(60.0, 10) }, rows.sets)
        assertEquals(false, rows.readByModel) // Setwise read these itself
        assertEquals(true, pullUps.readByModel) // marked in the preview to check
        // Code can't read this line ("did 8 at the end"): the model's set stays.
        assertEquals(listOf(SharedSet(null, 8)), pullUps.sets)
    }

    @Test
    fun `code's sets count only when it reads every line naming the exercise`() {
        val text = "Bench 60x8\nBench felt heavy, then some more"
        assertNull(ImportReader.setsReadInCode("Bench", text)) // the second line isn't readable
        assertEquals(listOf(SharedSet(60.0, 8), SharedSet(62.5, 6)), ImportReader.setsReadInCode("Bench", "Bench 60x8\nbench 62.5x6"))
        assertNull(ImportReader.setsReadInCode("Squat", text)) // not in the text
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
    fun `a screenshot of Strong's workout screen is read by its layout, with its length`() = runTest {
        screenshots = mapOf(
            "shot" to listOf(
                OcrLine("Evening Workout", 49, 420, 703, 504), OcrLine("Thursday, 1 October 2026 at 7:50 pm", 43, 605, 666, 640),
                OcrLine("Bench Press (Barbell)", 44, 693, 400, 732), OcrLine("1RM", 875, 699, 946, 726),
                OcrLine("1 60 kg x 8", 59, 768, 269, 804), OcrLine("75", 874, 771, 914, 798),
                OcrLine("1h 3m", 139, 2048, 230, 2084), OcrLine("5 PRS", 623, 2052, 717, 2079),
            ),
        )

        val read = reader.fromImages(listOf("shot"))

        val workout = read.workouts.single()
        assertEquals(ImportReader.Source.StrongShare, read.source)
        assertEquals(listOf("Bench Press (Barbell)"), workout.exercises.map { it.name }) // not "1RM", "h m", "prs" (#126)
        assertEquals(SharedSet(60.0, 8), workout.exercises.single().sets.single())
        assertEquals(Duration.ofMinutes(63), workout.duration)
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

    @Test
    fun `a log dated without a year is read in code`() = runTest {
        model.availability = ModelAvailability.Ready

        val read = reader.fromText("Back day 30 Sep\nBarbell rows, 3 sets of 10 with 60")

        assertEquals(ImportReader.Source.Log, read.source)
        assertEquals(List(3) { SharedSet(60.0, 10) }, read.workouts.single().exercises.single().sets)
        assertTrue(model.requests.isEmpty())
    }

    private object Today : DateProvider {
        override val zone: ZoneId = ZoneId.of("Asia/Kolkata")
        override fun now(): Instant = LocalDate.of(2026, 10, 5).atTime(9, 0).atZone(zone).toInstant()
        override fun today(): Flow<LocalDate> = flowOf(LocalDate.of(2026, 10, 5))
    }
}
