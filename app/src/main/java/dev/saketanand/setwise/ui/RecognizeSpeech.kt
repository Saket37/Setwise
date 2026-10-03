package dev.saketanand.setwise.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContract
import java.util.Locale

/**
 * Speech to text through the phone's own recognizer (its dialog; no microphone permission here):
 * the input is the hint it shows, the output what it heard, or null if nothing or cancelled.
 * English, in the phone's region: the quick-log reader understands English.
 */
class RecognizeSpeech : ActivityResultContract<String, String?>() {

    override fun createIntent(context: Context, input: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.Builder().setLanguage("en").setRegion(Locale.getDefault().country).build().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, input)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        intent?.takeIf { resultCode == Activity.RESULT_OK }
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    companion object {
        /** Whether the phone has a recognizer (the manifest's <queries> lets this see it). */
        fun isAvailable(context: Context): Boolean =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null
    }
}
