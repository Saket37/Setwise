package dev.saketanand.setwise.domain.ai

import android.util.Log
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import dev.saketanand.setwise.domain.model.CsvWorkoutParser
import dev.saketanand.setwise.domain.model.LogTextParser
import dev.saketanand.setwise.domain.model.SharedExercise
import dev.saketanand.setwise.domain.model.SharedSet
import dev.saketanand.setwise.domain.model.SharedWorkout
import dev.saketanand.setwise.domain.model.StrongShareParser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** Reads a text file the user chose or shared (a CSV export). */
interface FileTextReader {
    /** The text at [uri] (a content:// or file:// link), at most [maxChars]; throws if it can't be read. */
    suspend fun read(uri: String, maxChars: Int): String
}

/**
 * Turns what the user brings into workouts to import: a CSV export, Strong's shared text,
 * screenshots, or any other text. Code first ([CsvWorkoutParser], [StrongShareParser],
 * [LogTextParser] for hand-written logs; a screenshot's text goes through them too); only text
 * none of them reads goes to Gemini Nano, one block (a workout) at a time: on-device it only
 * manages one workout's sets per answer. A workout the model gives is kept only if every weight,
 * rep count and time in it is a number in the text, and its date's day is too.
 */
class ImportReader(
    private val textReader: TextReader,
    private val fileReader: FileTextReader,
    private val model: OnDeviceModel,
) {

    enum class Source { Csv, StrongShare, Log, Model }

    /** [source]: what read them; null if nothing did. [unreadLines]: lines of a log it couldn't read. */
    data class Read(val workouts: List<SharedWorkout>, val source: Source?, val unreadLines: Int = 0)

    suspend fun fromText(text: String): Read {
        CsvWorkoutParser.parse(text)?.takeIf { it.isNotEmpty() }?.let { return Read(it, Source.Csv) }
        StrongShareParser.parse(text).takeIf { it.isNotEmpty() }?.let { return Read(it, Source.StrongShare) }
        LogTextParser.parse(text).takeIf { it.workouts.isNotEmpty() }?.let { return Read(it.workouts, Source.Log, it.unreadLines) }
        val byModel = modelWorkouts(text)
        return Read(byModel, Source.Model.takeIf { byModel.isNotEmpty() })
    }

    /** A chosen or shared file (a CSV export), read as [fromText]; throws if it can't be read. */
    suspend fun fromFile(uri: String): Read = fromText(fileReader.read(uri, MAX_FILE_CHARS))

    /**
     * Screenshots: their text read on the phone. A screenshot of Strong-style text with no workout
     * header of its own continues the workout of a neighbouring one (whichever order they were
     * picked in); otherwise all of them together are read as [fromText].
     */
    suspend fun fromImages(uris: List<String>): Read {
        val texts = uris.map { uri -> textReader.read(uri).sortedWith(compareBy({ it.top }, { it.left })).joinToString("\n") { it.text } }
        val headed = texts.map { StrongShareParser.parse(it) }
        if (headed.any { it.isNotEmpty() }) {
            val workouts = headed.flatten().toMutableList()
            texts.forEachIndexed { i, text ->
                if (headed[i].isNotEmpty()) return@forEachIndexed
                val exercises = StrongShareParser.parse("$CONTINUED\n$text").firstOrNull()?.exercises ?: return@forEachIndexed
                // The workout of the nearest screenshot before it, else after it.
                val owner = (i - 1 downTo 0).firstOrNull { headed[it].isNotEmpty() }?.let { headed[it].last() }
                    ?: (i + 1 until texts.size).first { headed[it].isNotEmpty() }.let { headed[it].first() }
                val index = workouts.indexOf(owner)
                workouts[index] = workouts[index].copy(exercises = workouts[index].exercises + exercises)
            }
            return Read(workouts, Source.StrongShare)
        }
        return fromText(texts.joinToString("\n\n"))
    }

    private suspend fun modelWorkouts(text: String): List<SharedWorkout> {
        if (text.isBlank() || model.availability() != ModelAvailability.Ready) return emptyList()
        return chunks(text).mapNotNull { chunk ->
            // Any failure of the model skips this chunk; OnDeviceModel doesn't narrow what it throws.
            @Suppress("TooGenericExceptionCaught")
            val answer = try {
                model.generate(prompt(chunk), ModelImportedWorkout.OUTPUT)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "The model couldn't read a chunk", e)
                null
            }
            Log.d(TAG, "Model: $answer")
            answer?.let { accept(it, chunk) }
        }
    }

    companion object {
        private const val TAG = "ImportReader"
        private const val MAX_CHUNK = 1_200

        /** Years of one-row-per-set exports fit; a bigger file is read up to this. */
        private const val MAX_FILE_CHARS = 8_000_000

        /** A stand-in header, to read a screenshot that continues another's workout. */
        private const val CONTINUED = "Continued\n1 January 2000 at 12:00"

        /** The text's blocks (split at blank lines), each cut to what the model can take. */
        fun chunks(text: String): List<String> =
            text.split(Regex("\\n\\s*\\n")).map { it.trim().take(MAX_CHUNK) }.filter { it.isNotEmpty() }

        /** The model's workout if every number in it is in [text] and its date reads; else null. */
        fun accept(workout: ModelImportedWorkout, text: String): SharedWorkout? {
            val numbers = Regex("\\d+(?:[.,]\\d+)?").findAll(text).map { it.value.replace(',', '.').toDouble() }.toSet()
            fun inText(value: Double) = value in numbers
            val day = runCatching { LocalDate.parse(workout.date) }.getOrNull() ?: return null
            if (!inText(day.dayOfMonth.toDouble())) return null // a made-up date
            val time = runCatching { LocalTime.parse(workout.time) }.getOrNull() ?: LocalTime.NOON
            val sets = workout.sets.mapNotNull { row ->
                val set = SharedSet(row.weightKg.takeIf { it > 0 }, row.reps.takeIf { it > 0 }, row.seconds.takeIf { it > 0 })
                (row.exercise.trim() to set).takeIf { row.exercise.isNotBlank() && (set.reps != null || set.seconds != null) }
            }
            val values = sets.flatMap { (_, set) -> listOfNotNull(set.weightKg, set.reps?.toDouble(), set.seconds?.toDouble()) }
            if (sets.isEmpty() || values.any { !inText(it) }) return null
            // Rows to exercises, in first-seen order. Code first: where code reads an exercise's
            // lines itself, its sets count, so a real number the model put in the wrong field
            // ("3 sets of 10 with 60" as 60 kg × 3) doesn't (#43).
            val exercises = sets.groupBy({ it.first }, { it.second }).map { (name, exerciseSets) ->
                SharedExercise(name, setsReadInCode(name, text) ?: exerciseSets)
            }
            return SharedWorkout(workout.name.trim().ifEmpty { "Workout" }, LocalDateTime.of(day, time), exercises)
        }

        /**
         * [exercise]'s sets as code reads them from [text]'s lines, if every line naming it (but
         * not a date line) is one [LogTextParser] reads as that exercise; else null.
         */
        fun setsReadInCode(exercise: String, text: String): List<SharedSet>? {
            val mentions = text.lines().map { it.trim() }
                .filter { it.contains(exercise, ignoreCase = true) && LogTextParser.dateTime(it) == null }
            val read = mentions.mapNotNull { line -> LogTextParser.exercise(line)?.takeIf { it.name.equals(exercise, ignoreCase = true) } }
            return read.takeIf { it.isNotEmpty() && it.size == mentions.size }?.flatMap { it.sets }
        }

        fun prompt(text: String) = ModelRequest(
            system = "You turn one workout from someone's log into its date, time and every set in order, each with its exercise. " +
                "Use only numbers from the text. Use 0 for what a set doesn't have.",
            prompt = "## Example\n$EXAMPLE\n\n## Log\n<log>\n$text\n</log>",
            temperature = 0.1f,
            maxOutputTokens = 512,
        )

        private val EXAMPLE = """
            <log>
            Leg day - Tue 29/09/2026 18:30
            Squat: 100kg 5,5,5
            Plank 60s, 45s
            </log>
            {"name": "Leg day", "date": "2026-09-29", "time": "18:30", "sets": [{"exercise": "Squat", "weightKg": 100, "reps": 5, "seconds": 0}, {"exercise": "Squat", "weightKg": 100, "reps": 5, "seconds": 0}, {"exercise": "Squat", "weightKg": 100, "reps": 5, "seconds": 0}, {"exercise": "Plank", "weightKg": 0, "reps": 0, "seconds": 60}, {"exercise": "Plank", "weightKg": 0, "reps": 0, "seconds": 45}]}
        """.trimIndent()
    }
}

/** One workout of a log, as the model reads it: structured output. */
@Generable("One workout")
@Serializable
data class ModelImportedWorkout(
    @Guide(description = "Its name, or Workout")
    val name: String,
    @Guide(description = "Its date as yyyy-mm-dd")
    val date: String,
    @Guide(description = "Its start time as hh:mm (24-hour), or 12:00")
    val time: String,
    @Guide(description = "Every set in order, each with its exercise", maxItems = 40)
    val sets: List<ModelImportedSet>,
) {
    companion object {
        val OUTPUT = ModelOutput(
            ModelImportedWorkout::class,
            serializer(),
            """{"name": "<name>", "date": "<yyyy-mm-dd>", "time": "<hh:mm>", "sets": [""" +
                """{"exercise": "<exercise>", "weightKg": <kg or 0>, "reps": <reps or 0>, "seconds": <seconds or 0>}]}""",
        )
    }
}

@Generable("One set of an exercise")
@Serializable
data class ModelImportedSet(
    @Guide(description = "The exercise's name as written")
    val exercise: String,
    @Guide(description = "Weight in kg, 0 if none", minimum = 0.0, maximum = 500.0)
    val weightKg: Double,
    @Guide(description = "Reps, 0 for a timed hold", minimum = 0.0, maximum = 100.0)
    val reps: Int,
    @Guide(description = "Seconds, 0 unless it's timed", minimum = 0.0, maximum = 3600.0)
    val seconds: Int,
)
