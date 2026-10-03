package dev.saketanand.setwise

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.saketanand.setwise.di.ApplicationScope
import dev.saketanand.setwise.domain.ai.AiCheck
import dev.saketanand.setwise.ui.SetwiseAppRoot
import dev.saketanand.setwise.ui.navigation.AppLink
import dev.saketanand.setwise.ui.navigation.AppLinks
import dev.saketanand.setwise.ui.designsystem.theme.SetwiseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModel()

    /** Screens to open, from notifications (see AppLinks). Read by SetwiseAppRoot. */
    private val appLinks = Channel<AppLink>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate. On Android 12+ the splash icon animates, so on a fresh
        // launch hold the splash until the bars have finished rising (see avd_splash_mark.xml).
        // Skipped when recreated (e.g. rotation), where it would only delay the first frame.
        val holdSplash = savedInstanceState == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val splashShownAt = SystemClock.uptimeMillis()
        // Also held until the first screen is known (onboarding or Home), so neither flashes.
        installSplashScreen().setKeepOnScreenCondition {
            (holdSplash && SystemClock.uptimeMillis() - splashShownAt < SPLASH_ANIMATION_MS) ||
                viewModel.startDestination.value == null
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Fresh launch only: after a rotation or a restore the link was already handled (the
        // activity is recreated with the same intent).
        if (savedInstanceState == null) AppLinks.from(intent)?.let(appLinks::trySend)
        // Debug builds only: `--ez ai_check true` logs the on-device model's estimates (AiCheck).
        if (BuildConfig.DEBUG && savedInstanceState == null && intent.getBooleanExtra(AiCheck.EXTRA, false)) {
            get<CoroutineScope>(ApplicationScope).launch { get<AiCheck>().run() }
        }
        setContent {
            SetwiseTheme {
                val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
                val links = remember { appLinks.receiveAsFlow() }
                startDestination?.let { start ->
                    SetwiseAppRoot(startDestination = start, appLinks = links)
                }
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
