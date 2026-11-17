package dev.saketanand.setwise.domain.ai

import android.util.Log
import dev.saketanand.setwise.domain.model.BodyTip
import dev.saketanand.setwise.domain.model.BodyTips
import kotlin.coroutines.cancellation.CancellationException

/**
 * A body report's suggestions in words, by the on-device model: one short sentence per tip
 * worked out in code ([BodyTips]). The model only phrases them, so the text is kept only if every
 * number in it is one of the facts' and it's short plain prose ([WorkoutInsightWriter.accept]).
 * Null when the model isn't there, fails or the text doesn't pass: the screen then words the
 * tips itself.
 */
class BodyTipsWriter(private val model: OnDeviceModel) {

    /** One sentence per tip, in order; null to use the screen's own wording. */
    suspend fun write(tips: List<BodyTip>): List<String>? {
        if (tips.isEmpty() || model.availability() != ModelAvailability.Ready) return null
        val facts = BodyTips.factLines(tips)

        // Any failure of the model keeps the screen's wording; OnDeviceModel doesn't narrow what it throws.
        @Suppress("TooGenericExceptionCaught")
        val answer = try {
            model.generate(prompt(facts))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The model couldn't write body tips", e)
            return null
        }
        val sentences = WorkoutInsightWriter.accept(answer, facts)?.split(Regex("(?<=[.!?])\\s+"))?.filter { it.isNotBlank() }
        Log.d(TAG, "Body tips ${if (sentences != null) "used" else "rejected"}: ${answer.take(LOGGED_CHARS)}")
        // One sentence per fact, or the screen's own wording.
        return sentences?.takeIf { it.size == tips.size }
    }

    companion object {
        private const val TAG = "BodyTipsWriter"
        private const val LOGGED_CHARS = 400

        fun prompt(factLines: String): ModelRequest = ModelRequest(
            system = "You turn facts from a body composition report into short suggestions. Use only the facts given, with numbers exactly as written. " +
                "Write exactly one short sentence per fact, in the same order, under 30 words each, plain text, speaking to the person (you, your). " +
                "Encouraging, not alarming. No greetings, lists, emoji, medical advice or diet plans.",
            prompt = "## Example\n$EXAMPLE\n\n## Facts\n<facts>\n$factLines\n</facts>",
            temperature = 0.2f,
            maxOutputTokens = 160,
        )

        private val EXAMPLE = """
            <facts>
            Lean muscle: right leg rated Under, left leg not. Idea: single-leg work like split squats or step-ups
            Since the last report on 7 Sep: fat mass -0.8 kg, muscle +0.3 kg. Idea: keep the current training and eating
            </facts>
            Your right leg is rated Under while your left isn't, so single-leg work like split squats or step-ups will help even them out. Since 7 Sep you've lost 0.8 kg of fat and gained 0.3 kg of muscle, so keep up the current training and eating.
        """.trimIndent()
    }
}
