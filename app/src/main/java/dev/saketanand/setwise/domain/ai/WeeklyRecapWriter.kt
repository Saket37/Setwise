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
        val accepted = WorkoutInsightWriter.accept(answer, lines)
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
            add("Volume: ${facts.volumeKg.toLong()} kg" + facts.volumeChangePercent?.let { ", ${it.signed()}% on the week before" }.orEmpty())
            facts.bestSet?.let { set ->
                val what = when {
                    set.weightKg != null && set.reps != null -> "${set.weightKg.kg()} kg x ${set.reps}"
                    set.reps != null -> "${set.reps} reps"
                    else -> "${set.seconds ?: 0} s"
                }
                add("Best set: ${set.exerciseName} $what" + if (set.isPr) " (a record)" else "")
            }
            facts.bodyPartChange?.let { add("${it.part.name} volume: ${it.percent.signed()}% on the week before") }
            facts.plateau?.let { add("Stalled: ${it.exerciseName}, flat for ${it.weeks} weeks (its plan: lighter weight, more reps)") }
        }.joinToString("\n")

        /** The week against the plan, worked out here: a small model can't be trusted to compare. */
        fun planLine(workouts: Int, planned: Int): String = when {
            workouts == planned -> "Plan: all $planned planned training days done"
            workouts < planned -> "Plan: $workouts of $planned planned training days done (${planned - workouts} missed)"
            else -> "Plan: $workouts workouts, ${workouts - planned} more than the $planned planned"
        }

        private fun Int.signed() = if (this > 0) "+$this" else "$this"
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
            Volume: 38200 kg, +8% on the week before
            Best set: Back Squat 100 kg x 5 (a record)
            Legs volume: +18% on the week before
            Stalled: Overhead Press, flat for 4 weeks (its plan: lighter weight, more reps)
            </facts>
            A strong week: 4 sessions and 3 records, led by a 100 kg squat. Leg volume was up 18% on the week before. Overhead press is still flat, so try its lighter, higher-rep plan this week.
        """.trimIndent()
    }
}
