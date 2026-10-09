package dev.saketanand.setwise.domain.ai

import android.util.Log
import dev.saketanand.setwise.domain.model.WeekFacts
import java.util.Locale
import kotlinx.coroutines.CancellationException

/**
 * The weekly summary's recap in words, by the on-device model, from [WeekFacts] (worked out in
 * code). Kept only if every number in it is one of the facts' and it's short plain prose
 * ([WorkoutInsightWriter.accept]). Null when the model isn't there, fails, or the recap doesn't
 * pass: the card then shows its template from the same facts.
 */
class WeeklyRecapWriter(private val model: OnDeviceModel) {

    suspend fun canWrite(): Boolean = model.availability() == ModelAvailability.Ready

    /** @param person the profile's name and planned days, to address them and frame the week. */
    suspend fun write(facts: WeekFacts, person: PersonFacts = PersonFacts()): String? {
        if (!canWrite()) return null
        val lines = factLines(facts, person)
        val startedAt = System.nanoTime()
        val answer = try {
            model.generate(prompt(lines))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The model couldn't write the weekly recap", e)
            return null
        }
        // Numbers from the facts, and no lift said to go the wrong way (#144).
        val accepted = WorkoutInsightWriter.accept(answer, lines)?.takeIf { directionsAgree(it, facts, person) }
        Log.d(TAG, "Week of ${facts.weekStart}: ${if (accepted != null) "used" else "rejected"} in ${(System.nanoTime() - startedAt) / 1_000_000} ms: ${answer.take(400)}")
        return accepted
    }

    companion object {
        private const val TAG = "WeeklyRecapWriter"

        fun prompt(factLines: String) = ModelRequest(
            system = SYSTEM,
            prompt = "## Example\n$EXAMPLE\n\n## Facts\n<facts>\n$factLines\n</facts>",
            temperature = 0.2f,
            maxOutputTokens = 120,
        )

        /** One fact per line, numbers written once and exactly. */
        fun factLines(facts: WeekFacts, person: PersonFacts = PersonFacts()): String = buildList {
            person.nameLine()?.let(::add)
            person.plannedDaysPerWeek?.let { add(planLine(facts.workouts, it)) }
            val hours = facts.timeTrained.toHours()
            val minutes = facts.timeTrained.toMinutes() % 60
            add("Week: ${facts.workouts} workouts, ${if (hours > 0) "$hours h $minutes min" else "$minutes min"} trained, ${facts.prs} personal records")
            add("Volume: ${facts.volumeKg.toLong()} kg" + facts.volumeChangePercent?.let { ", ${it.change()} on the week before" }.orEmpty())
            facts.bestSet?.let { set ->
                val what = when {
                    set.weightKg != null && set.reps != null -> "${set.weightKg.kg()} kg x ${set.reps}"
                    set.reps != null -> "${set.reps} reps"
                    else -> "${set.seconds ?: 0} s"
                }
                add("Best set: ${set.exerciseName} $what" + if (set.isPr) " (a record)" else "")
            }
            facts.bodyPartChange?.let { add("${it.part.name} volume: ${it.percent.change()} on the week before") }
            facts.plateau?.let { add("Stalled: ${it.exerciseName}, flat for ${it.weeks} weeks (its plan: lighter weight, more reps)") }
        }.joinToString("\n")

        /** The week against the plan, worked out here: a small model can't be trusted to compare. */
        fun planLine(workouts: Int, planned: Int): String = when {
            workouts == planned -> "Plan: all $planned planned training days done"
            workouts < planned -> "Plan: $workouts of $planned planned training days done (${planned - workouts} missed)"
            else -> "Plan: $workouts workouts, ${workouts - planned} more than the $planned planned"
        }

        /** "up 8%", "down 5%", "unchanged": the direction in words, so the model isn't left to read a sign. */
        private fun Int.change() = when {
            this > 0 -> "up $this%"
            this < 0 -> "down ${-this}%"
            else -> "unchanged"
        }

        /**
         * The recap says nothing went the wrong way: no loss words about the week's best lift, no
         * gain words about a stalled one, and no loss words at all unless a fact went down (seen:
         * "deadlift improved significantly … regain lost ground", #144).
         */
        fun directionsAgree(recap: String, facts: WeekFacts, person: PersonFacts = PersonFacts()): Boolean {
            // By clause: "volume was down 5%, but your deadlift hit 140 kg" is two facts, both right.
            val clauses = recap.lowercase(Locale.ROOT).split(Regex("(?<=[.!?;,:])\\s+|\\s+(?:but|while|although|though)\\s+"))
            val bestLost = facts.bestSet?.let { clauses.about(it.exerciseName).say(LOSS) } == true
            val stalledUp = facts.plateau?.let { clauses.about(it.exerciseName).say(GAIN) } == true
            return !bestLost && !stalledUp && (anyDown(facts, person) || !clauses.say(LOSS))
        }

        /** Something the week's facts say went down: volume, a body part's volume, or days missed. */
        private fun anyDown(facts: WeekFacts, person: PersonFacts): Boolean =
            (facts.volumeChangePercent ?: 0) < 0 || (facts.bodyPartChange?.percent ?: 0) < 0 ||
                person.plannedDaysPerWeek?.let { facts.workouts < it } == true

        /** The clauses that name [exercise] ("Deadlift (Barbell)" → "deadlift"). */
        private fun List<String>.about(exercise: String): List<String> =
            exercise.substringBefore(" (").lowercase(Locale.ROOT).let { lift -> filter { lift in it } }

        private fun List<String>.say(words: List<String>): Boolean =
            any { clause -> words.any { Regex("\\b$it").containsMatchIn(clause) } }

        private val LOSS = listOf("lost", "lose", "losing", "regain", "drop", "dropped", "down", "declin", "fell", "fall", "decreas", "worse", "slipp")
        private val GAIN = listOf("improv", "gain", "increas", "rose", "stronger", "better", "progress", "climb")
        private fun Double.kg() = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.ROOT, this)

        private const val SYSTEM =
            "You write a short recap of someone's training week for them. Use only the facts given, with numbers exactly as written. " +
                "2 or 3 short sentences, under 50 words, plain text, encouraging. If a lift is stalled, suggest its plan. " +
                "Write to them as \"you\". If a plan is given, say how the week went against it, as the fact states. " +
                "If a name is given, you may address them by it once (\"Sam, you...\"); never write about them by name. " +
                "No greetings, lists or emoji."

        private val EXAMPLE = """
            <facts>
            Week: 4 workouts, 4 h 34 min trained, 3 personal records
            Volume: 38200 kg, up 8% on the week before
            Best set: Back Squat 100 kg x 5 (a record)
            Legs volume: up 18% on the week before
            Stalled: Overhead Press, flat for 4 weeks (its plan: lighter weight, more reps)
            </facts>
            A strong week: 4 sessions and 3 records, led by a 100 kg squat. Leg volume was up 18% on the week before. Overhead press is still flat, so try its lighter, higher-rep plan this week.
        """.trimIndent()
    }
}
