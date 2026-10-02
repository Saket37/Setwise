package dev.saketanand.setwise.ui.home

import dev.saketanand.setwise.domain.model.Template
import dev.saketanand.setwise.domain.model.TemplateExercise
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HomeUiMappersTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val saturday = LocalDate.of(2026, 10, 3)

    @Test
    fun `week runs from Monday 00_00 to the next Monday`() {
        val (start, end) = weekRange(saturday, zone)
        assertEquals(LocalDateTime.of(2026, 9, 28, 0, 0), start.atZone(zone).toLocalDateTime())
        assertEquals(LocalDateTime.of(2026, 10, 5, 0, 0), end.atZone(zone).toLocalDateTime())
    }

    @Test
    fun `on a Monday the week starts that day`() {
        val monday = LocalDate.of(2026, 9, 28)
        assertEquals(monday.atStartOfDay(zone).toInstant(), weekRange(monday, zone).first)
    }

    @Test
    fun `days ago counts calendar days, not 24-hour blocks`() {
        val lateYesterday = LocalDateTime.of(2026, 10, 2, 23, 30).atZone(zone).toInstant()
        val earlyToday = LocalDateTime.of(2026, 10, 3, 0, 15).atZone(zone).toInstant()
        assertEquals(1, daysAgo(lateYesterday, saturday, zone))
        assertEquals(0, daysAgo(earlyToday, saturday, zone))
    }

    @Test
    fun `short exercise name drops the equipment`() {
        assertEquals("Bench Press", shortExerciseName("Bench Press (Barbell)"))
        assertEquals("Pull-up", shortExerciseName("Pull-up"))
    }

    @Test
    fun `template card preview, counts and time estimate`() {
        val template = Template(
            id = 1, name = "Push Day", category = "Push", lastUsedAt = null,
            exercises = listOf(
                TemplateExercise(1, "Bench Press (Barbell)", targetSets = 4, restSec = 150),
                TemplateExercise(2, "Incline Dumbbell Press", targetSets = 3, restSec = 120),
                TemplateExercise(3, "Overhead Press (Barbell)", targetSets = 3, restSec = 150),
                TemplateExercise(4, "Lateral Raise (Dumbbell)", targetSets = 3, restSec = 60),
            ),
        )
        val ui = template.toUi(saturday, zone)
        assertEquals(listOf("Bench Press", "Incline Dumbbell Press", "Overhead Press"), ui.exercisePreview)
        assertEquals(1, ui.moreExerciseCount)
        assertEquals(4, ui.exerciseCount)
        assertEquals(13, ui.setCount)
        // 4×190 + 3×160 + 3×190 + 3×100 = 2110 s ≈ 35 min
        assertEquals(35, ui.estimatedMinutes)
        assertEquals(null, ui.lastUsedDaysAgo)
    }
}
