package dev.saketanand.setwise

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.saketanand.setwise.ui.SetwiseAppRoot
import dev.saketanand.setwise.ui.navigation.AppLink
import dev.saketanand.setwise.ui.navigation.AppLinks
import dev.saketanand.setwise.ui.designsystem.theme.SetwiseTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {

    /** Screens to open, from notifications (see AppLinks). Read by SetwiseAppRoot. */
    private val appLinks = Channel<AppLink>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate. On Android 12+ the splash icon animates, so on a fresh
        // launch hold the splash until the bars have finished rising (see avd_splash_mark.xml).
        // Skipped when recreated (e.g. rotation), where it would only delay the first frame.
        val holdSplash = savedInstanceState == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val splashShownAt = SystemClock.uptimeMillis()
        installSplashScreen().setKeepOnScreenCondition {
            holdSplash && SystemClock.uptimeMillis() - splashShownAt < SPLASH_ANIMATION_MS
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Fresh launch only: after a rotation or a restore the link was already handled (the
        // activity is recreated with the same intent).
        if (savedInstanceState == null) AppLinks.from(intent)?.let(appLinks::trySend)
        setContent {
            SetwiseTheme {
                SetwiseAppRoot(appLinks = appLinks.receiveAsFlow())
            }
        }
    }

    /** The app was already open (launchMode singleTop) and a notification was tapped. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AppLinks.from(intent)?.let(appLinks::trySend)
    }
}

/** Matches the total length of avd_splash_mark.xml (last bar: 340ms offset + 380ms). */
private const val SPLASH_ANIMATION_MS = 720L
