package dev.saketanand.setwise.domain

import dev.saketanand.setwise.domain.model.CsvWorkoutParser
import dev.saketanand.setwise.domain.model.SharedExercise
import dev.saketanand.setwise.domain.model.SharedSet
import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsvWorkoutParserTest {

    @Test
    fun `Strong's export`() {
        val csv = """
            "Date","Workout Name","Duration","Exercise Name","Set Order","Weight","Reps","Distance","Seconds","Notes","Workout Notes","RPE"
            "2026-09-30 20:01:23","Evening Workout","1h 5m","Bicep Curl (Barbell)","1","10","15","0","0","","",""
            "2026-09-30 20:01:23","Evening Workout","1h 5m","Bicep Curl (Barbell)","2","15","15","0","0","","",""
            "2026-09-30 20:01:23","Evening Workout","1h 5m","Plank","1","0","0","0","90","","",""
            "2026-10-01 07:15:00","Morning Workout","45m","Treadmill","1","0","0","2.5","900","","",""
        """.trimIndent()

        val workouts = CsvWorkoutParser.parse(csv)!!

        assertEquals(2, workouts.size)
        val evening = workouts[0]
        assertEquals("Evening Workout", evening.name)
        assertEquals(LocalDateTime.of(2026, 9, 30, 20, 1, 23), evening.startedAt)
        assertEquals(Duration.ofMinutes(65), evening.duration)
        assertEquals(
            listOf(
                SharedExercise("Bicep Curl (Barbell)", listOf(SharedSet(10.0, 15), SharedSet(15.0, 15))),
                SharedExercise("Plank", listOf(SharedSet(seconds = 90))),
            ),
            evening.exercises,
        )
        assertEquals(SharedSet(seconds = 900, distanceKm = 2.5), workouts[1].exercises.single().sets.single())
    }

    @Test
    fun `Hevy's export, with an end time and pounds`() {
        val csv = """
            "title","start_time","end_time","description","exercise_title","superset_id","exercise_notes","set_index","set_type","weight_lbs","reps","distance_miles","duration_seconds","rpe"
            "Push","30 Sep 2026, 18:00","30 Sep 2026, 19:10","","Bench Press (Barbell)","","","0","normal","135","8","","",""
            "Push","30 Sep 2026, 18:00","30 Sep 2026, 19:10","","Bench Press (Barbell)","","","1","normal","135","7","","",""
        """.trimIndent()

        val push = CsvWorkoutParser.parse(csv)!!.single()

        assertEquals(LocalDateTime.of(2026, 9, 30, 18, 0), push.startedAt)
        assertEquals(Duration.ofMinutes(70), push.duration)
        assertEquals(listOf(SharedSet(61.2, 8), SharedSet(61.2, 7)), push.exercises.single().sets)
    }

    @Test
    fun `FitNotes' export, semicolons and dates only`() {
        val csv = """
            Date;Exercise;Category;Weight (kg);Reps;Distance;Distance Unit;Time
            2026-09-29;Flat Barbell Bench Press;Chest;60,0;8;;;
            2026-09-29;Flat Barbell Bench Press;Chest;62,5;6;;;
            2026-09-29;Running;Cardio;;;5;km;0:25:00
        """.trimIndent()

        val day = CsvWorkoutParser.parse(csv)!!.single()

        assertEquals(LocalDateTime.of(2026, 9, 29, 12, 0), day.startedAt)
        assertEquals("Workout", day.name)
        assertEquals(listOf(SharedSet(60.0, 8), SharedSet(62.5, 6)), day.exercises[0].sets)
        assertEquals(SharedSet(seconds = 1500, distanceKm = 5.0), day.exercises[1].sets.single())
    }

    @Test
    fun `not a workout CSV`() {
        assertNull(CsvWorkoutParser.parse("name,email\nSaket,a@b.c"))
        assertNull(CsvWorkoutParser.parse("Evening Workout\nWednesday, 30 September 2026 at 8:01 pm"))
    }
}
