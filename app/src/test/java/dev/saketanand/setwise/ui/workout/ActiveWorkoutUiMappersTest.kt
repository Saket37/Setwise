package dev.saketanand.setwise.ui.workout

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseSession
import dev.saketanand.setwise.domain.model.ExerciseType
import dev.saketanand.setwise.domain.model.LoggedSet
import dev.saketanand.setwise.domain.model.PersonalBests
import dev.saketanand.setwise.domain.model.PreviousSet
import dev.saketanand.setwise.domain.model.SessionExercise
import dev.saketanand.setwise.domain.model.WorkoutSet
import dev.saketanand.setwise.timer.NextUp
import dev.saketanand.setwise.util.parseWeight
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveWorkoutUiMappersTest {

    @Test
    fun `exercise type decides the set kind`() {
        assertEquals(SetKind.WeightReps, exercise(ExerciseType.STRENGTH).setKind)
        assertEquals(SetKind.Bodyweight, exercise(ExerciseType.BODYWEIGHT).setKind)
        assertEquals(SetKind.Duration, exercise(ExerciseType.BODYWEIGHT, timed = true).setKind)
        assertEquals(SetKind.Cardio, exercise(ExerciseType.CARDIO).setKind)
    }

    @Test
    fun `hints come from last time, then from the row above`() {
        val ui = SessionExercise(
            id = 1,
            exercise = exercise(ExerciseType.STRENGTH),
            sets = listOf(set(1), set(2, weightKg = 65.0, reps = 6), set(3)),
            previousSets = listOf(PreviousSet(60.0, 8), PreviousSet(62.5, 7)),
        ).toUi()

        val (first, second, third) = ui.sets
        assertEquals("60" to "8", first.weightHint to first.repsHint)
        assertEquals("62.5" to "7", second.weightHint to second.repsHint)
        // No set 3 last time: suggest what set 2 is being done with.
        assertEquals("65" to "6", third.weightHint to third.repsHint)
        assertNull(third.previous)
        assertEquals("60 × 8 · 62.5 × 7", ui.lastTime)
    }

    @Test
    fun `a template's target reps are the reps hint, ahead of last time`() {
        val previous = listOf(PreviousSet(60.0, 8, null), PreviousSet(60.0, 7, null))
        val sets = SessionExercise(1, exercise(ExerciseType.STRENGTH), listOf(set(1), set(2), set(3)), previous, targetReps = 5).toUi().sets

        assertEquals(listOf("5", "5", "5"), sets.map { it.repsHint })
        assertEquals(listOf("60", "60", "60"), sets.map { it.weightHint }) // weights still from last time
    }

    @Test
    fun `a progression hint shows a change, never for a workout logged afterwards`() {
        val startedAt = at(2026, 10, 4, 18, 0, ZoneId.of("Asia/Kolkata"))
        fun done(daysBefore: Long, vararg reps: Int) =
            ExerciseSession(daysBefore, startedAt.minus(Duration.ofDays(daysBefore)), reps.map { LoggedSet(60.0, it, null, null) })
        fun item(vararg history: ExerciseSession) =
            SessionExercise(1, exercise(ExerciseType.STRENGTH), listOf(set(1), set(2), set(3)), emptyList(), history = history.toList())

        val ready = item(done(3, 8, 8, 8), done(6, 8, 8, 8))
        assertEquals(62.5 to 8, ready.toUi(hintsAt = startedAt).nextSession?.let { it.weightKg to it.reps })
        assertNull(ready.toUi(hintsAt = null).nextSession)
        // Same again (reps short last time): nothing to say during the workout.
        assertNull(item(done(3, 8, 8, 6), done(6, 8, 8, 8)).toUi(hintsAt = startedAt).nextSession)
    }

    @Test
    fun `with a progression hint, the working sets hint what it suggests`() {
        val startedAt = at(2026, 10, 4, 18, 0, ZoneId.of("Asia/Kolkata"))

        // A 40 kg warm-up, then 3 × 60 × 8, twice: ready for 62.5.
        fun done(daysBefore: Long) = ExerciseSession(
            daysBefore,
            startedAt.minus(Duration.ofDays(daysBefore)),
            listOf(LoggedSet(40.0, 10, null, null)) + List(3) { LoggedSet(60.0, 8, null, null) },
        )
        val previous = listOf(PreviousSet(40.0, 10), PreviousSet(60.0, 8), PreviousSet(60.0, 8), PreviousSet(60.0, 8))
        val ui = SessionExercise(1, exercise(ExerciseType.STRENGTH), List(5) { set(it + 1) }, previous, history = listOf(done(3), done(6)))
            .toUi(hintsAt = startedAt)

        assertEquals(62.5, ui.nextSession?.weightKg)
        assertEquals(
            listOf("40" to "10", "62.5" to "8", "62.5" to "8", "62.5" to "8", "62.5" to "8"), // the warm-up keeps its own; set 5 follows set 4
            ui.sets.map { it.weightHint to it.repsHint },
        )
        // Logged afterwards: no hint, so last time's values.
        assertEquals("60", SessionExercise(1, exercise(ExerciseType.STRENGTH), List(4) { set(it + 1) }, previous, history = listOf(done(3), done(6))).toUi().sets[1].weightHint)
    }

    @Test
    fun `a ticked-off set that beats the earlier best gets the trophy right away`() {
        val bests = PersonalBests.from(listOf(PreviousSet(60.0, 8)))
        val sets = listOf(set(1, 60.0, 8).copy(isCompleted = true), set(2, 62.5, 6).copy(isCompleted = true), set(3, 65.0, 5))

        val rows = SessionExercise(1, exercise(ExerciseType.STRENGTH), sets, emptyList(), bestsBefore = bests).toUi().sets

        // Set 3 is heavier but not ticked off yet.
        assertEquals(listOf(false, true, false), rows.map { it.isPr })
    }

    @Test
    fun `typed values show as text, empty ones as empty`() {
        val row = SessionExercise(1, exercise(ExerciseType.STRENGTH), listOf(set(1, weightKg = 62.5, reps = null)), emptyList())
            .toUi().sets.single()
        assertEquals("62.5", row.weight)
        assertEquals("", row.reps)
        assertEquals(1, row.number)
    }

    @Test
    fun `timed exercises use seconds`() {
        val ui = SessionExercise(
            1, exercise(ExerciseType.BODYWEIGHT, timed = true),
            listOf(set(1).copy(durationSec = 45)),
            listOf(PreviousSet(weightKg = null, reps = null, durationSec = 60)),
        ).toUi()
        assertEquals("45", ui.sets.single().reps)
        assertEquals("60", ui.sets.single().repsHint)
        assertEquals("60s", ui.lastTime)
    }

    @Test
    fun `previous labels per kind`() {
        assertEquals("60 × 8", PreviousSet(60.0, 8).label(SetKind.WeightReps))
        assertEquals("10", PreviousSet(null, 10).label(SetKind.Bodyweight))
        assertEquals("+5 × 10", PreviousSet(5.0, 10).label(SetKind.Bodyweight))
        assertEquals("45s", PreviousSet(null, null, 45).label(SetKind.Duration))
        assertNull(PreviousSet(null, null).label(SetKind.WeightReps))
    }

    @Test
    fun `numbers are parsed with either decimal separator`() {
        assertEquals(62.5, parseWeight("62,5"))
        assertEquals(62.5, parseWeight("62.5"))
        assertNull(parseWeight(""))
        assertNull(parseWeight("."))
        assertEquals(8, parseAmount("8"))
        assertNull(parseAmount(""))
    }

    @Test
    fun `field values parse back in any locale, labels follow the locale`() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            val ui = SessionExercise(1, exercise(ExerciseType.STRENGTH), listOf(set(1, weightKg = 62.5, reps = 8)), listOf(PreviousSet(60.5, 8)))
                .toUi()
            val row = ui.sets.single()
            assertEquals("62.5", row.weight)
            assertEquals("60.5", row.weightHint)
            assertEquals(62.5, parseWeight(row.weight))
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun `0 reps doesn't count as a set`() {
        assertNull(loggedAmount(typed = "0", hint = "8")) // a typed 0 isn't replaced by the hint (#133)
        assertEquals(false, canCompleteSet("0", "8"))
        assertEquals(8, loggedAmount(typed = "", hint = "8")) // nothing typed: the hint
        assertNull(loggedAmount(typed = "0", hint = ""))
        assertEquals(10, loggedAmount(typed = "10", hint = "8"))
        assertEquals(false, canCompleteSet("", ""))
        assertEquals(true, canCompleteSet("", "8"))
    }

    @Test
    fun `picked start time is today, clamped to now`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = LocalDateTime.of(2026, 10, 3, 18, 30).atZone(zone).toInstant()
        val start = LocalDateTime.of(2026, 10, 3, 18, 0).atZone(zone).toInstant()

        assertEquals(at(2026, 10, 3, 17, 45, zone), pickedStartTime(LocalTime.of(17, 45), start, now, zone))
        assertEquals("future → now", now, pickedStartTime(LocalTime.of(23, 0), start, now, zone))
    }

    @Test
    fun `a start time in the future can't be picked, read the same way`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val start = at(2026, 10, 3, 18, 0, zone)
        val now = at(2026, 10, 3, 18, 30, zone)
        assertEquals(true, isPickableStartTime(LocalTime.of(17, 45), start, now, zone))
        assertEquals(false, isPickableStartTime(LocalTime.of(23, 0), start, now, zone)) // #140: said, not silently "now"

        // Started 23:30 last night, now 00:20: 23:15 is last night, fine; 01:00 is the future.
        assertEquals(true, isPickableStartTime(LocalTime.of(23, 15), at(2026, 10, 2, 23, 30, zone), at(2026, 10, 3, 0, 20, zone), zone))
        assertEquals(false, isPickableStartTime(LocalTime.of(1, 0), at(2026, 10, 2, 23, 30, zone), at(2026, 10, 3, 0, 20, zone), zone))
    }

    @Test
    fun `picked start time works across midnight`() {
        val zone = ZoneId.of("Asia/Kolkata")
        // Started 23:30 on the 2nd; it's now 00:20 on the 3rd.
        val start = at(2026, 10, 2, 23, 30, zone)
        val now = at(2026, 10, 3, 0, 20, zone)

        assertEquals("00:10 is tonight, not the night before", at(2026, 10, 3, 0, 10, zone), pickedStartTime(LocalTime.of(0, 10), start, now, zone))
        assertEquals("23:15 is yesterday evening", at(2026, 10, 2, 23, 15, zone), pickedStartTime(LocalTime.of(23, 15), start, now, zone))
    }

    @Test
    fun `next up is the next open set, then the next exercise with sets left, wrapping around`() {
        fun row(id: Long, done: Boolean) = SetUi(id, id.toInt(), null, "", "", "", "", isCompleted = done, isPr = false)
        fun card(id: Long, name: String, vararg sets: SetUi) =
            WorkoutExerciseUi(id, id, name, SetKind.WeightReps, restSec = 90, sets = sets.toList(), lastTime = null)

        val squat = card(1, "Squat", row(1, done = false))
        val bench = card(2, "Bench", row(2, done = true), row(3, done = false), row(4, done = false))
        val curl = card(3, "Curl", row(5, done = true))
        val all = listOf(squat, bench, curl)

        assertEquals(NextUp.Set(4), nextUpAfter(setId = 3, exercise = bench, exercises = all))
        val benchAlmostDone = card(2, "Bench", row(2, done = true), row(3, done = true), row(4, done = false))
        // Curl (after Bench) is done, so it wraps to Squat.
        assertEquals(NextUp.Exercise("Squat"), nextUpAfter(4, benchAlmostDone, listOf(squat, benchAlmostDone, curl)))
        assertEquals(NextUp.Nothing, nextUpAfter(5, card(3, "Curl", row(5, done = false)), listOf(card(3, "Curl", row(5, done = false)))))
    }

    @Test
    fun `a workout logged for a past day keeps its day when the start time changes`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val start = at(2026, 9, 30, 18, 0, zone)
        val now = at(2026, 10, 3, 12, 0, zone)

        assertEquals(at(2026, 9, 30, 17, 0, zone), pickedStartTime(LocalTime.of(17, 0), start, now, zone))
    }

    @Test
    fun `finish ends a workout logged afterwards an hour after its start, a live one now`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = at(2026, 10, 3, 12, 0, zone)

        assertEquals(at(2026, 9, 30, 19, 0, zone), finishTime(at(2026, 9, 30, 18, 0, zone), now))
        assertEquals(now, finishTime(at(2026, 10, 3, 11, 0, zone), now))
        // A live session past midnight (23:00 → 01:00) keeps its real end.
        val oneAm = at(2026, 10, 3, 1, 0, zone)
        assertEquals(oneAm, finishTime(at(2026, 10, 2, 23, 0, zone), oneAm))
    }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId) = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant()

    private fun exercise(type: ExerciseType, timed: Boolean = false) = Exercise(
        id = 10, name = "Bench Press", type = type, muscleGroup = "Chest", equipment = "Barbell",
        defaultRestSec = 120, isTimed = timed, isCustom = false, metrics = null, calorieMethod = null, met = null,
    )

    private fun set(number: Int, weightKg: Double? = null, reps: Int? = null) = WorkoutSet(
        id = number.toLong(), setNumber = number, weightKg = weightKg, reps = reps,
        durationSec = null, isCompleted = false, isPr = false,
    )
}
