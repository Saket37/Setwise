package dev.saketanand.setwise.domain.ai

import android.util.Log
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import dev.saketanand.setwise.domain.model.BodyReportParser
import dev.saketanand.setwise.domain.model.BodyRules
import dev.saketanand.setwise.domain.model.OcrLine
import dev.saketanand.setwise.domain.model.ReportValues
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/** Reads the text in a photo, on the phone. */
interface TextReader {
    /** The lines in the image at [uri] (a content:// or file:// link); throws if it can't be read. */
    suspend fun read(uri: String): List<OcrLine>
}

/**
 * Reads a body composition report from a photo: the text on-device ([TextReader]), then the
 * values by their labels in code ([BodyReportParser]). Only when code finds fewer than two
 * values does Gemini Nano look at the text (structured output); each value it gives must be one
 * of the text's numbers and believable, and code's values always win. The user checks them all
 * before anything is saved.
 */
class BodyReportReader(
    private val textReader: TextReader,
    private val model: OnDeviceModel,
) {

    sealed interface Result {
        data class Read(val values: ReportValues, val byModel: Boolean) : Result

        /** No text, or nothing that looks like a report. */
        data object NothingFound : Result
    }

    suspend fun read(uri: String): Result {
        val lines = textReader.read(uri)
        if (lines.isEmpty()) return Result.NothingFound
        if (dev.saketanand.setwise.BuildConfig.DEBUG) lines.forEach { Log.d(TAG, "OCR [${it.left},${it.top},${it.right},${it.bottom}] ${it.text}") }
        val parsed = BodyReportParser.parse(lines)
        if (parsed.found >= MIN_FOUND_BY_CODE) return Result.Read(parsed, byModel = false)
        val fromModel = modelValues(lines.joinToString("\n") { it.text })
        val values = parsed.orElse(fromModel ?: ReportValues())
        return if (values.found == 0) Result.NothingFound else Result.Read(values, byModel = fromModel != null && values != parsed)
    }

    private suspend fun modelValues(text: String): ReportValues? {
        if (model.availability() != ModelAvailability.Ready) return null
        val answer = try {
            model.generate(prompt(text.take(MAX_TEXT)), ModelBodyReport.OUTPUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The model couldn't read the report", e)
            null
        } ?: return null
        Log.d(TAG, "Model: $answer")
        return accept(answer, text)
    }

    companion object {
        private const val TAG = "BodyReportReader"
        private const val MIN_FOUND_BY_CODE = 2
        private const val MAX_TEXT = 2_000

        /** The model's values that are numbers in [text] and believable; the rest dropped. */
        fun accept(answer: ModelBodyReport, text: String): ReportValues {
            val numbers = Regex("\\d+(?:[.,]\\d+)?").findAll(text).map { it.value.replace(',', '.').toDouble() }.toList()
            fun inText(value: Double) = numbers.any { abs(it - value) < 0.05 }
            fun Double.kept(range: ClosedFloatingPointRange<Double>) = takeIf { it > 0 && it in range && inText(it) }
            return ReportValues(
                weightKg = answer.weightKg.kept(BodyRules.WEIGHT_KG),
                bodyFatPercent = answer.bodyFatPercent.kept(BodyRules.BODY_FAT_PERCENT),
                muscleMassKg = answer.muscleMassKg.kept(BodyRules.MUSCLE_KG),
                bmrKcal = answer.bmrKcal.takeIf { it in BodyRules.BMR_KCAL && inText(it.toDouble()) },
                visceralFat = answer.visceralFat.kept(BodyRules.VISCERAL),
            )
        }

        fun prompt(text: String) = ModelRequest(
            system = "You read the values from a body composition report's text. Use only numbers from the text, " +
                "in kg, % and kcal. Use 0 for any value the report doesn't have.",
            prompt = "## Report text\n<report>\n$text\n</report>",
            temperature = 0.1f,
            maxOutputTokens = 80,
        )
    }
}

/** The model's reading of a report: structured output, 0 for what isn't there. */
@Generable("Values from a body composition report")
@Serializable
data class ModelBodyReport(
    @Guide(description = "Body weight in kg, 0 if not there", minimum = 0.0, maximum = 400.0)
    val weightKg: Double,
    @Guide(description = "Body fat percentage, 0 if not there", minimum = 0.0, maximum = 70.0)
    val bodyFatPercent: Double,
    @Guide(description = "Skeletal muscle mass in kg, 0 if not there", minimum = 0.0, maximum = 150.0)
    val muscleMassKg: Double,
    @Guide(description = "Basal metabolic rate in kcal, 0 if not there", minimum = 0.0, maximum = 5000.0)
    val bmrKcal: Int,
    @Guide(description = "Visceral fat level, 0 if not there", minimum = 0.0, maximum = 60.0)
    val visceralFat: Double,
) {
    companion object {
        val OUTPUT = ModelOutput(
            ModelBodyReport::class,
            serializer(),
            """{"weightKg": <kg or 0>, "bodyFatPercent": <% or 0>, "muscleMassKg": <kg or 0>, "bmrKcal": <kcal or 0>, "visceralFat": <level or 0>}""",
        )
    }
}
