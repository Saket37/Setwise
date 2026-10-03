package dev.saketanand.setwise.domain.ai

import android.util.Log
import dev.saketanand.setwise.domain.model.ExerciseSession
import dev.saketanand.setwise.domain.model.Measure
import dev.saketanand.setwise.domain.model.Plateau
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

/**
 * Exercise detail's plateau note in words, by the on-device model: 2 short sentences from facts
 * worked out in code ([Plateau], the last session, and fixed ideas to get it moving). The model
 * only phrases them, so a note is kept only if every number in it is one of the facts' and it's
 * short plain prose ([WorkoutInsightWriter.accept]). Null when the model isn't there, fails, or
 * the note doesn't pass: the screen then shows its template. Notes are kept for the app's run,
 * so reopening the screen doesn't ask again.
 */
class PlateauNoteWriter(private val model: OnDeviceModel) {

    private val written = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > MAX_KEPT
    }

    suspend fun canWrite(): Boolean = model.availability() == ModelAvailability.Ready

    /** A note written earlier for the same facts, without asking the model. */
    fun cached(exerciseName: String, plateau: Plateau, last: ExerciseSession): String? =
        synchronized(written) { written[factLines(exerciseName, plateau, last)] }

    suspend fun write(exerciseName: String, plateau: Plateau, last: ExerciseSession): String? {
        val facts = factLines(exerciseName, plateau, last)
        synchronized(written) { written[facts] }?.let { return it }
        if (!canWrite()) return null
        val startedAt = System.nanoTime()
        val answer = try {
            model.generate(prompt(facts))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The model couldn't write a plateau note", e)
            return null
        }
        val accepted = WorkoutInsightWriter.accept(answer, facts)
        Log.d(TAG, "$exerciseName: ${if (accepted != null) "used" else "rejected"} in ${(System.nanoTime() - startedAt) / 1_000_000} ms: ${answer.take(400)}")
        if (accepted != null) synchronized(written) { written[facts] = accepted }
        return accepted
    }

    companion object {
        private const val TAG = "PlateauNoteWriter"
        private const val MAX_KEPT = 32

        fun prompt(factLines: String): ModelRequest = ModelRequest(
            system = SYSTEM,
            prompt = "## Example\n$EXAMPLE\n\n## Facts\n<facts>\n$factLines\n</facts>",
            temperature = 0.2f,
            maxOutputTokens = 100,
        )

        /** One fact per line, numbers written once and exactly. */
        fun factLines(exerciseName: String, plateau: Plateau, last: ExerciseSession): String {
            val best = when (plateau.measure) {
                Measure.Weight -> "estimated 1RM about ${plateau.best.roundToInt()} kg"
                Measure.Reps -> "most reps about ${plateau.best.roundToInt()}"
                Measure.Seconds -> "longest hold about ${plateau.best.roundToInt()} s"
            }
            val perWeek = (plateau.sessions.toDouble() / plateau.weeks.coerceAtLeast(1)).roundToInt()
            return listOfNotNull(
                "Exercise: ${exerciseName.substringBefore(" (")}",
                "Best: $best, flat for ${plateau.weeks} weeks",
                // "Even with 2 a week" only reads right when it's often.
                "Sessions in those weeks: ${plateau.sessions}" + if (perWeek >= 2) " (about $perWeek a week)" else "",
                lastSession(last, plateau.measure)?.let { "Last session: $it" },
                "Ideas: ${if (plateau.measure == Measure.Weight) IDEAS_WEIGHT else IDEAS_OTHER}",
            ).joinToString("\n")
        }

        /** The working sets (at the top weight): "40 kg x 6, 6, 5", "10, 9, 8 reps", "45, 40 s". */
        private fun lastSession(session: ExerciseSession, measure: Measure): String? {
            val top = session.sets.maxOfOrNull { it.weightKg ?: 0.0 } ?: return null
            val working = session.sets.filter { abs((it.weightKg ?: 0.0) - top) < 1e-6 }
            val amounts = working.mapNotNull { if (measure == Measure.Seconds) it.durationSec else it.reps }
            if (amounts.isEmpty()) return null
            val list = amounts.joinToString(", ")
            return when {
                measure == Measure.Seconds -> "$list s"
                top > 0 -> "${top.kgNumber()} kg x $list"
                else -> "$list reps"
            }
        }

        private fun Double.kgNumber() = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.ROOT, this)

        private const val IDEAS_WEIGHT = "a lighter week, or 8 to 10 reps a set for a month"
        private const val IDEAS_OTHER = "a lighter week, or a harder variation for a few weeks"

        private const val SYSTEM =
            "You write a short note for someone whose exercise has stopped improving. " +
                "Use only the facts given, with numbers exactly as written. " +
                "2 short sentences, under 40 words, plain text: what the facts show, then the ideas. " +
                "Encouraging, not alarming. No greetings, lists, emoji or medical advice."

        private val EXAMPLE = """
            <facts>
            Exercise: Bench Press
            Best: estimated 1RM about 80 kg, flat for 5 weeks
            Sessions in those weeks: 10 (about 2 a week)
            Last session: 70 kg x 5, 5, 4
            Ideas: a lighter week, or 8 to 10 reps a set for a month
            </facts>
            Your bench press has held at about 80 kg for 5 weeks, even with 2 sessions a week. A lighter week, or 8 to 10 reps a set for a month, often gets it moving again.
        """.trimIndent()
    }
}
