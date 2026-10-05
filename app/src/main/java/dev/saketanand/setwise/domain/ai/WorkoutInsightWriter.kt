package dev.saketanand.setwise.domain.ai

import android.util.Log
import dev.saketanand.setwise.domain.model.ExerciseChange
import dev.saketanand.setwise.domain.model.Measure
import dev.saketanand.setwise.domain.model.SetFact
import dev.saketanand.setwise.domain.model.WorkoutFacts
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

/**
 * The summary screen's insight in words, by the on-device model: 2–3 short sentences from
 * [WorkoutFacts] (worked out in code). The model only phrases them, so an answer is kept only
 * if every number in it is one of the facts' numbers, and it's short plain prose ([accept]).
 * Null when the model isn't there, fails, or the answer doesn't pass: the screen then shows
 * its template from the same facts.
 */
class WorkoutInsightWriter(private val model: OnDeviceModel) {

    /** @param person the profile's name, to address them. */
    suspend fun write(facts: WorkoutFacts, person: PersonFacts = PersonFacts()): String? {
        if (model.availability() != ModelAvailability.Ready) return null
        val factLines = factLines(facts, person)
        val startedAt = System.nanoTime()
        val answer = try {
            model.generate(prompt(factLines))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The model couldn't write an insight", e)
            return null
        }
        val accepted = accept(answer, factLines)
        Log.d(
            TAG,
            "${facts.workoutName}: ${if (accepted != null) "used" else "rejected"} in " +
                "${(System.nanoTime() - startedAt) / 1_000_000} ms: ${answer.take(400)}",
        )
        return accepted
    }

    companion object {
        private const val TAG = "WorkoutInsightWriter"
        private const val MAX_CHARS = 360
        private const val MAX_SENTENCES = 4

        /**
         * Shaped for Gemini Nano (ML Kit prompt guide): a short system instruction, one worked
         * example, then the facts in `<facts>` tags. The fixed part stays under 200 words.
         */
        fun prompt(factLines: String): ModelRequest = ModelRequest(
            system = SYSTEM,
            prompt = "## Example\n$EXAMPLE\n\n## Facts\n<facts>\n$factLines\n</facts>",
            temperature = 0.2f,
            maxOutputTokens = 120,
        )

        /** One fact per line, numbers written once and exactly. */
        fun factLines(facts: WorkoutFacts, person: PersonFacts = PersonFacts()): String = buildList {
            person.nameLine()?.let(::add)
            add(
                listOfNotNull(
                    "Workout: ${facts.workoutName}, ${facts.minutes} min",
                    facts.intensity?.let { "intensity ${it.storedName}" },
                    facts.medianRestSec?.let { "rest about ${it.clock()}" },
                ).joinToString(", "),
            )
            // The most notable few in full; the rest as a count, so the recap stays short.
            facts.changes.take(MAX_CHANGES).forEach { add(it.line()) }
            facts.changes.drop(MAX_CHANGES).takeIf { it.isNotEmpty() }?.let { others ->
                val parts = listOfNotNull(
                    others.count { it.isImprovement }.takeIf { it > 0 }?.let { "$it up" },
                    others.count { it.isSame }.takeIf { it > 0 }?.let { "$it the same" },
                    others.count { !it.isImprovement && !it.isSame }.takeIf { it > 0 }?.let { "$it down" },
                )
                add("Other exercises: ${parts.joinToString(", ")}")
            }
            // Named on the screen already: a count is enough here.
            if (facts.records.isNotEmpty()) add("Personal records: ${facts.records.size}")
            val previous = facts.previous
            val percent = facts.volumeChangePercent
            if (previous != null && percent != null) {
                add("Volume: ${facts.volumeKg.kg()}, last ${previous.name} ${previous.volumeKg.kg()} (${percent.signed()}%)")
            }
        }.joinToString("\n")

        /**
         * The answer cleaned up, or null if it isn't a short plain recap of these facts: no
         * number that isn't in [factLines] (no invented weights or reps), at most
         * [MAX_SENTENCES] sentences and [MAX_CHARS] characters, no lists or markup.
         */
        fun accept(answer: String, factLines: String): String? {
            val text = answer.trim().trim('"', '\'', '`').replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty() || text.length > MAX_CHARS) return null
            if (Regex("[<>#*•]|^- ").containsMatchIn(text)) return null
            if (text.split(Regex("(?<=[.!?])\\s+")).size > MAX_SENTENCES) return null
            val allowed = numbers(factLines)
            if (numbers(text).any { it !in allowed }) return null
            return text
        }

        private fun numbers(text: String): Set<String> =
            Regex("\\d+(?:[.,]\\d+)?").findAll(text).map { it.value.replace(',', '.') }.toSet()

        private fun ExerciseChange.line(): String {
            val delta = when (measure) {
                Measure.Weight -> when {
                    weightDeltaKg != 0.0 -> "${weightDeltaKg.signedKg()} kg"
                    repsDelta != 0 -> "${repsDelta.signed()} reps"
                    else -> "same"
                }
                Measure.Reps -> if (repsDelta != 0) "${repsDelta.signed()} reps" else "same"
                Measure.Seconds -> if (secondsDelta != 0) "${secondsDelta.signed()} s" else "same"
            }
            return "$exercise: best ${now.words(measure)}, last time ${before.words(measure)} ($delta)"
        }

        private fun SetFact.words(measure: Measure): String = when (measure) {
            Measure.Seconds -> "${seconds ?: 0} s"
            Measure.Reps -> if (weightKg != null && weightKg > 0) "${weightKg.kgNumber()} kg x ${reps ?: 0}" else "${reps ?: 0} reps"
            Measure.Weight -> "${(weightKg ?: 0.0).kgNumber()} kg x ${reps ?: 0}"
        }

        private fun Long.clock() = "%d:%02d".format(Locale.ROOT, this / 60, this % 60)
        private fun Int.signed() = if (this > 0) "+$this" else "$this"
        private fun Double.kgNumber() = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.ROOT, this)
        private fun Double.signedKg() = (if (this > 0) "+" else "-") + abs(this).kgNumber()
        private fun Double.kg() = "${Math.round(this)} kg"

        /** Exercises compared in full; the others go in as a count. */
        private const val MAX_CHANGES = 2

        private const val SYSTEM =
            "You write a short recap of one gym workout for the person who did it. " +
                "Use only the facts given, with numbers exactly as written. " +
                "2 short sentences, under 35 words, plain text. Don't say when it happened. " +
                "Write to them as \"you\". If a name is given, you may address them by it once (\"Sam, you...\"); " +
                "never write about them by name. No greetings, advice, lists or emoji."

        private val EXAMPLE = """
            <facts>
            Workout: Pull Day, 62 min, intensity moderate, rest about 2:00
            Deadlift: best 140 kg x 5, last time 135 kg x 5 (+5 kg)
            Pull-up: best 12 reps, last time 10 reps (+2 reps)
            Other exercises: 2 up, 1 the same
            Personal records: 1
            Volume: 9100 kg, last Pull Day 8700 kg (+5%)
            </facts>
            Deadlift went up 5 kg to 140 kg x 5 and pull-ups climbed to 12 reps. Volume was 5% higher than last Pull Day, at a moderate pace.
        """.trimIndent()
    }
}
