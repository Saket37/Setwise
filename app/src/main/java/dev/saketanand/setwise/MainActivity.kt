package dev.saketanand.setwise

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.saketanand.setwise.ui.SetwiseAppRoot
import dev.saketanand.setwise.ui.designsystem.theme.SetwiseTheme

class MainActivity : ComponentActivity() {
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
        setContent {
            SetwiseTheme {
                SetwiseAppRoot()
            }
        }
    }
}

/** Matches the total length of avd_splash_mark.xml (last bar: 340ms offset + 380ms). */
private const val SPLASH_ANIMATION_MS = 720L
