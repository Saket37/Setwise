package dev.saketanand.setwise.ui.navigation

import android.content.Context
import android.content.Intent
import dev.saketanand.setwise.MainActivity
import android.net.Uri

/** A screen something outside the app's UI (a notification) asks to open. */
sealed interface AppLink {
    /** The active workout, e.g. from the rest timer's notifications. */
    data class Workout(val workoutId: Long) : AppLink

    /** Text shared to Setwise (e.g. a workout from Strong, or a CSV file's content): the import screen, with it. */
    data class SharedText(val text: String) : AppLink

    /** Screenshots shared to Setwise: the import screen reads them. */
    data class SharedImages(val uris: List<String>) : AppLink
}

/**
 * Builds and reads [AppLink] intents. They are explicit intents to MainActivity with a private
 * extra, not exported URIs, so other apps can't open screens with them.
 */
object AppLinks {

    private const val EXTRA_WORKOUT_ID = "dev.saketanand.setwise.extra.OPEN_WORKOUT_ID"

    /** SINGLE_TOP: if the app is open, MainActivity gets it in onNewIntent instead of a second copy. */
    fun intent(context: Context, link: AppLink): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply {
                when (link) {
                    is AppLink.Workout -> putExtra(EXTRA_WORKOUT_ID, link.workoutId)
                    is AppLink.SharedText -> setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link.text)
                    is AppLink.SharedImages -> Unit // only ever received
                }
            }

    /**
     * @param readText reads a shared file's text (a CSV export); the activity has the permission
     *   to read it only now, while handling the intent.
     */
    fun from(intent: Intent?, readText: (Uri) -> String? = { null }): AppLink? {
        val isSend = intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE
        if (isSend && intent?.type?.startsWith("image/") == true) {
            val uris = streams(intent).map { it.toString() }
            return uris.takeIf { it.isNotEmpty() }?.let(AppLink::SharedImages)
        }
        if (isSend) {
            intent?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.take(MAX_SHARED_TEXT)?.let { return AppLink.SharedText(it) }
            streams(intent!!).firstOrNull()?.let(readText)?.takeIf { it.isNotBlank() }?.take(MAX_SHARED_TEXT)?.let { return AppLink.SharedText(it) }
            return null
        }
        return intent?.getLongExtra(EXTRA_WORKOUT_ID, NONE)?.takeIf { it != NONE }?.let(AppLink::Workout)
    }

    @Suppress("DEPRECATION")
    private fun streams(intent: Intent): List<Uri> =
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            ?: listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM))

    /** Plenty for a few hundred workouts as CSV; keeps the navigation argument manageable. */
    private const val MAX_SHARED_TEXT = 400_000

    private const val NONE = -1L
}
