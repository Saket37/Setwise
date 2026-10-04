package dev.saketanand.setwise.domain.ai

import android.util.Log
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseNames
import dev.saketanand.setwise.domain.model.HistoryAnswer
import dev.saketanand.setwise.domain.model.HistoryAnswerer
import dev.saketanand.setwise.domain.model.HistoryQuestion
import dev.saketanand.setwise.domain.model.HistoryQuestionReader
import dev.saketanand.setwise.domain.model.HistoryQuestionReader.Kind
import dev.saketanand.setwise.domain.model.LoggedSetRecord
import dev.saketanand.setwise.domain.model.Period
import dev.saketanand.setwise.domain.model.PeriodName
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** What asking about the history gives. */
sealed interface HistoryReply {
    /** [byModel]: the on-device model picked the lookup (the code couldn't tell which). */
    data class Answered(val answer: HistoryAnswer, val byModel: Boolean) : HistoryReply

    /** It needs an exercise and none matched [words]. */
    data class UnknownExercise(val words: String) : HistoryReply

    /** Not one of the questions it can look up. */
    data object NotUnderstood : HistoryReply
}

/**
 * "Ask about your training": a question becomes one of the fixed lookups
 * ([HistoryQuestion]), which code runs over the training log and words from the result, so
 * every number is from the log. Code reads the question ([HistoryQuestionReader]); only when it
 * can't tell which lookup is meant does the on-device model pick one (structured output, from
 * the fixed list). The exercise is matched like the quick log's: exercises done first, then the
 * library.
 */
class HistoryAssistant(
    private val model: OnDeviceModel,
    private val exercises: ExerciseAssistant,
) {

    suspend fun ask(question: String, log: List<LoggedSetRecord>, library: List<Exercise>, today: LocalDate, zone: ZoneId): HistoryReply {
        var read = HistoryQuestionReader.read(question, today)
        var byModel = false
        if (read.kind == null) {
            val picked = modelPick(question) ?: return HistoryReply.NotUnderstood
            read = read.copy(kind = picked.kind, period = read.period ?: picked.period, kg = read.kg ?: picked.kg)
            byModel = true
        }
        val kind = read.kind ?: return HistoryReply.NotUnderstood
        val period = Period.of(read.period ?: PeriodName.AllTime, today)
        val exercise = read.exerciseWords.takeIf { it.isNotBlank() }?.let { exerciseFor(it, log, library) }
        val lookup = when (kind) {
            Kind.BestSet -> HistoryQuestion.BestSet(exercise ?: return unknown(read.exerciseWords), period)
            Kind.LastLifted -> HistoryQuestion.LastLifted(exercise ?: return unknown(read.exerciseWords), read.kg)
            Kind.Volume -> HistoryQuestion.Volume(period, read.muscles)
            Kind.Sessions -> {
                if (exercise == null && read.muscles == null && read.exerciseWords.isNotBlank()) return unknown(read.exerciseWords)
                HistoryQuestion.Sessions(period, exercise, read.muscles)
            }
            Kind.PrWorkouts -> HistoryQuestion.PrWorkouts(period)
        }
        return HistoryReply.Answered(HistoryAnswerer.answer(lookup, log, zone), byModel)
    }

    private fun unknown(words: String) = if (words.isBlank()) HistoryReply.NotUnderstood else HistoryReply.UnknownExercise(words)

    /** Exercises they've done first ("bench" is their bench press), then the whole library. */
    private suspend fun exerciseFor(words: String, log: List<LoggedSetRecord>, library: List<Exercise>): Exercise? {
        val doneIds = log.map { it.exerciseId }.toSet()
        val done = library.filter { it.id in doneIds }
        val heard = ExerciseNames.soundAlikeFixed(words, library).ifEmpty { words }
        return exercises.clearMatch(heard, done) ?: exercises.findMatch(heard, library)
    }

    private data class Picked(val kind: Kind, val period: PeriodName?, val kg: Double?)

    private suspend fun modelPick(question: String): Picked? {
        if (model.availability() != ModelAvailability.Ready) return null
        val answer = try {
            model.generate(prompt(question), ModelHistoryPick.OUTPUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The model couldn't read '$question'", e)
            null
        }
        Log.d(TAG, "'$question': $answer")
        answer ?: return null
        val kind = KINDS[answer.lookup] ?: return null
        // A weight the question doesn't have is made up.
        val kg = answer.kg.takeIf { it > 0 && Regex("\\d+(?:\\.\\d+)?").findAll(question).any { m -> m.value.toDouble() == it } }
        return Picked(kind, PERIODS[answer.period], kg)
    }

    companion object {
        private const val TAG = "HistoryAssistant"

        private val KINDS = mapOf(
            "best set" to Kind.BestSet,
            "last time lifted" to Kind.LastLifted,
            "volume" to Kind.Volume,
            "number of workouts" to Kind.Sessions,
            "workouts with records" to Kind.PrWorkouts,
        )
        private val PERIODS = mapOf(
            "this week" to PeriodName.ThisWeek,
            "last week" to PeriodName.LastWeek,
            "this month" to PeriodName.ThisMonth,
            "last month" to PeriodName.LastMonth,
            "this year" to PeriodName.ThisYear,
            "all time" to PeriodName.AllTime,
        )

        fun prompt(question: String) = ModelRequest(
            system = "You sort a gym-goer's question about their own training into one lookup from a fixed list. " +
                "Pick \"none\" if it isn't about their logged workouts.",
            prompt = "## Examples\n$EXAMPLES\n\n## Question\n<question>$question</question>",
            temperature = 0.1f,
            maxOutputTokens = 60,
        )

        private val EXAMPLES = """
            <question>what's the heaviest I've deadlifted</question>
            {"lookup": "best set", "period": "all time", "kg": 0}
            <question>did I do any leg work in the past 7 days</question>
            {"lookup": "number of workouts", "period": "this week", "kg": 0}
            <question>should I eat more protein</question>
            {"lookup": "none", "period": "all time", "kg": 0}
        """.trimIndent()
    }
}

/** The model's pick: structured output, every field from a fixed list. */
@Generable("Which lookup answers a question about someone's logged workouts")
@Serializable
data class ModelHistoryPick(
    @Guide(description = "The lookup", enumValues = ["best set", "last time lifted", "volume", "number of workouts", "workouts with records", "none"])
    val lookup: String,
    @Guide(description = "The time span asked about", enumValues = ["this week", "last week", "this month", "last month", "this year", "all time"])
    val period: String,
    @Guide(description = "A weight in kg the question names, 0 if none", minimum = 0.0, maximum = 500.0)
    val kg: Double,
) {
    companion object {
        val OUTPUT = ModelOutput(
            ModelHistoryPick::class,
            serializer(),
            """{"lookup": "<one of the lookups>", "period": "<one of the periods>", "kg": <number or 0>}""",
        )
    }
}
