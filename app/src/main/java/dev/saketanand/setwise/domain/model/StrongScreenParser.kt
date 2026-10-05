package dev.saketanand.setwise.domain.model

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Reads screenshots of a finished workout in Strong's app (its workout screen, #126), from the
 * lines text recognition finds and where they are:
 *
 *     Evening Workout
 *     Thursday, 1 October 2026 at 7:50 pm
 *     Bench Press (Barbell)                1RM
 *     1   60 kg × 8                        75
 *     [🏆 1RM] [🏆 Weight]                       record badges
 *     Push Up
 *     1   20 reps [🏆 Reps]
 *     🕒 1h 5m    4200 kg    🏆 3 PRs
 *     PERFORM AGAIN
 *
 * Lines are put into rows by height on the screen. The right-hand column (Strong's 1RM, and its
 * header) and the badges are left out; the footer's "1h 5m" is the workout's length. A screenshot
 * without the title and date continues the workout of the nearest one before it (else after),
 * and sets both screenshots show (by set number) count once. Set values are read as in
 * [StrongShareParser]. Empty when no screenshot has a title, date and a set row.
 */
object StrongScreenParser {

    fun parse(screenshots: List<List<OcrLine>>): List<SharedWorkout> {
        val screens = screenshots.map { screen(rows(it)) }
        if (screens.none { it.start != null && it.blocks.isNotEmpty() }) return emptyList()

        val workouts = screens.map { if (it.start != null) Draft(it) else null }.toMutableList()
        screens.forEachIndexed { i, screen ->
            if (screen.start != null) return@forEachIndexed
            val owner = (i - 1 downTo 0).firstNotNullOfOrNull { workouts[it] }
                ?: (i + 1 until screens.size).firstNotNullOfOrNull { workouts[it] }
                ?: return@forEachIndexed
            owner.add(screen)
        }
        return workouts.filterNotNull().mapNotNull { it.toWorkout() }
    }

    /** A set as Strong numbers it: "1", "2"… or W (warm-up), D (drop), F (failure). */
    private data class NumberedSet(val label: String, val set: SharedSet)

    /** An exercise's rows on one screenshot; [name] null: sets continuing the previous screenshot's last exercise. */
    private data class Block(val name: String?, val sets: List<NumberedSet>)

    private data class Screen(val title: String?, val start: LocalDateTime?, val blocks: List<Block>, val duration: Duration?)

    private class Draft(first: Screen) {
        private val name = first.title.orEmpty()
        private val start = checkNotNull(first.start)
        private var duration = first.duration
        private val exercises = mutableListOf<Pair<String, MutableList<NumberedSet>>>()

        init {
            add(first, continuing = false)
        }

        /** [continuing]: a screenshot after another; its first block may be the same exercise, carried over. */
        fun add(screen: Screen, continuing: Boolean = true) {
            screen.duration?.let { duration = it }
            screen.blocks.forEachIndexed { i, block ->
                // The same exercise across two screenshots: sets already read count once.
                val carriedOver = exercises.lastOrNull()?.takeIf { continuing && i == 0 && block.name in setOf(null, it.first) }
                if (carriedOver != null) {
                    carriedOver.second += block.sets.filterNot { new -> new.label.all(Char::isDigit) && carriedOver.second.any { it.label == new.label } }
                } else if (block.name != null) {
                    exercises += block.name to block.sets.toMutableList() // a header alone waits for its sets
                }
            }
        }

        fun toWorkout(): SharedWorkout? {
            val read = exercises.filter { it.second.isNotEmpty() }
                .map { (exercise, sets) -> SharedExercise(exercise, sets.map { it.set }) }
            return if (read.isEmpty()) null else SharedWorkout(name, start, read, duration)
        }
    }

    private fun screen(rows: List<String>): Screen {
        // The title is the row above the date; what's above the title (the status bar) isn't the workout.
        val dateRow = (1 until rows.size).firstOrNull { StrongShareParser.dateTime(rows[it]) != null }
        val (blocks, duration) = blocks(if (dateRow == null) rows else rows.drop(dateRow + 1))
        return Screen(dateRow?.let { rows[it - 1] }, dateRow?.let { StrongShareParser.dateTime(rows[it]) }, blocks, duration)
    }

    /** The exercises (headers and their set rows) up to the footer, and the footer's length. */
    private fun blocks(rows: List<String>): Pair<List<Block>, Duration?> {
        val blocks = mutableListOf<Block>()
        var name: String? = null
        val sets = mutableListOf<NumberedSet>()
        fun close() {
            if (sets.isNotEmpty()) blocks += Block(name, sets.toList())
            sets.clear()
        }
        for (row in rows) {
            val setRow = SET_ROW.matchEntire(row)
            val duration = durationIn(row)
            when {
                STATUS_BAR.containsMatchIn(row) || isBadges(row) -> Unit
                setRow != null -> StrongShareParser.set(setRow.groupValues[2])?.let { sets += NumberedSet(setRow.groupValues[1].uppercase(), it) }
                // The footer: the workout ends here ("Perform again" below).
                duration != null -> {
                    close()
                    return blocks to duration
                }
                else -> {
                    close()
                    name = row
                }
            }
        }
        // A header at the bottom: its sets are on the next screenshot.
        if (sets.isEmpty() && name != null) blocks += Block(name, emptyList()) else close()
        return blocks to null
    }

    /**
     * The screenshot's rows, top to bottom, each its lines left to right. A number (or column
     * header) on the far right is Strong's 1RM column: left out.
     */
    private fun rows(lines: List<OcrLine>): List<String> {
        val width = lines.maxOfOrNull { it.right } ?: return emptyList()
        val rows = mutableListOf<MutableList<OcrLine>>()
        lines.sortedBy { it.centerY }.forEach { line ->
            val row = rows.lastOrNull()?.takeIf { row ->
                abs(row.first().centerY - line.centerY) <= maxOf(row.first().height, line.height) * ROW_TOLERANCE
            }
            if (row != null) row += line else rows += mutableListOf(line)
        }
        return rows.map { row ->
            val sorted = row.sortedBy { it.left }
            sorted.filterIndexed { i, line -> i == 0 || line.left < width * RIGHT_COLUMN || !RIGHT_COLUMN_TEXT.matches(line.text.trim()) }
                .joinToString(" ") { it.text.trim() }
        }.filter { it.isNotBlank() }
    }

    /** A row of record badges ("🏆 1RM 🏆 Weight"; the trophy reads as "?" or another sign). */
    private fun isBadges(row: String): Boolean {
        val words = row.split(' ').filter { word -> word.any(Char::isLetterOrDigit) }
        return words.isNotEmpty() && words.all { it.lowercase() in BADGES }
    }

    /** The footer's workout length: "1h 5m", "45m", "2h". */
    private fun durationIn(row: String): Duration? {
        val m = DURATION.find(row) ?: return null
        val hours = m.groupValues[1].toLongOrNull() ?: 0
        val minutes = m.groupValues[2].toLongOrNull() ?: 0
        return Duration.ofHours(hours).plusMinutes(minutes).takeIf { !it.isZero }
    }

    /** "1 60 kg x 8", "W 20 kg x 10", "2 20 reps ? Reps". */
    private val SET_ROW = Regex("^(\\d{1,2}|[WDFwdf])\\s+(.+)$")

    /** The phone's clock, top left: "7:23". No exercise name starts with a time. */
    private val STATUS_BAR = Regex("^\\d{1,2}:\\d{2}(?:\\s|$)")
    private val DURATION = Regex("^(?:(\\d{1,2})\\s*h)?\\s*(?:(\\d{1,2})\\s*m(?:in)?)?\\b(?=\\s|$)")
    private val RIGHT_COLUMN_TEXT = Regex("^(?:[\\d.,:]+|1RM|Pace|Volume)$", RegexOption.IGNORE_CASE)
    private val BADGES = setOf("1rm", "weight", "reps", "volume", "duration", "distance", "pace", "prs", "pr")

    /** Lines whose centres are this close, by their height, are one row. */
    private const val ROW_TOLERANCE = 0.6

    /** Where the 1RM column starts, as a share of the screenshot's text width. */
    private const val RIGHT_COLUMN = 0.6
}
