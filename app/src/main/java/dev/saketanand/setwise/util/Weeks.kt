package dev.saketanand.setwise.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/*
 * The app's week runs Monday to Sunday (the design's week strip), the same on every screen:
 * Home's "this week" stats and History's week strip.
 */

/** Monday 00:00 of [today]'s week, and the next Monday, as instants in [zone]. */
fun weekRange(today: LocalDate, zone: ZoneId): Pair<Instant, Instant> {
    val monday = today.mondayOfWeek()
    return monday.atStartOfDay(zone).toInstant() to monday.plusWeeks(1).atStartOfDay(zone).toInstant()
}

/** Monday to Sunday of [today]'s week. */
fun weekDays(today: LocalDate): List<LocalDate> {
    val monday = today.mondayOfWeek()
    return (0L until 7L).map { monday.plusDays(it) }
}

private fun LocalDate.mondayOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
