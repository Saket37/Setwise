package dev.saketanand.setwise.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.saketanand.setwise.MainActivity

/** A screen something outside the app's UI (a notification) asks to open. */
sealed interface AppLink {
    /** The active workout, e.g. from the rest timer's notifications. */
    data class Workout(val workoutId: Long) : AppLink

    /** Text shared to Setwise (e.g. a workout from Strong): the import screen, with it. */
    data class SharedText(val text: String) : AppLink

    /** A file shared to Setwise (a CSV export): the import screen reads it. */
    data class SharedFile(val uri: String) : AppLink

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
                    is AppLink.SharedFile, is AppLink.SharedImages -> Unit // only ever received
                }
            }

    /**
     * The link in [intent], if any. A shared file or screenshots come as links the import screen
     * reads: the share's read permission lasts while this activity does.
     */
    fun from(intent: Intent?): AppLink? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
            return intent.getLongExtra(EXTRA_WORKOUT_ID, NONE).takeIf { it != NONE }?.let(AppLink::Workout)
        }
        val uris = streams(intent).map { it.toString() }
        return when {
            intent.type?.startsWith("image/") == true -> uris.takeIf { it.isNotEmpty() }?.let(AppLink::SharedImages)
            else -> intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.take(MAX_SHARED_TEXT)?.let(AppLink::SharedText)
                ?: uris.firstOrNull()?.let(AppLink::SharedFile)
        }
    }

    @Suppress("DEPRECATION")
    private fun streams(intent: Intent): List<Uri> =
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            ?: listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM))

    /** Plenty for dozens of shared workouts; keeps the navigation argument small. */
    private const val MAX_SHARED_TEXT = 100_000

    private const val NONE = -1L
}
