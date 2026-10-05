package dev.saketanand.setwise

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.saketanand.setwise.data.dev.DevDataSeeder
import dev.saketanand.setwise.data.prefs.LegacyBodyMeasurements
import dev.saketanand.setwise.data.seed.ExerciseSeeder
import dev.saketanand.setwise.di.IoDispatcher
import dev.saketanand.setwise.di.appModule
import dev.saketanand.setwise.di.viewModelModule
import dev.saketanand.setwise.domain.CalorieSync
import dev.saketanand.setwise.domain.ai.OnDeviceModel
import dev.saketanand.setwise.timer.RestNotifications
import dev.saketanand.setwise.timer.RestTimerCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SetwiseApp : Application() {

    /** App-wide scope for startup work (disk, the seed). SupervisorJob: one failed job doesn't cancel the rest. */
    private val appScope by lazy { CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(IoDispatcher)) }

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@SetwiseApp)
            modules(appModule, viewModelModule)
        }

        // Rest timer: notification channels, and the link to its foreground service and alerts.
        RestNotifications.createChannels(this)
        get<RestTimerCoordinator>().start()

        // The on-device model frees its client while the app is in the background.
        val model: OnDeviceModel = get()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = model.onAppInBackground(false)
                override fun onStop(owner: LifecycleOwner) = model.onAppInBackground(true)
            },
        )

        // Calorie estimates for finished workouts, as soon as a body weight is known.
        get<CalorieSync>().start(appScope)

        // Load the built-in exercise library on first launch (no-op afterwards).
        val exerciseSeeder: ExerciseSeeder = get()
        appScope.launch {
            // Older builds kept body measurements in DataStore: into the database, once.
            get<LegacyBodyMeasurements>().moveToDatabase()
            exerciseSeeder.seedIfNeeded()
            // Debug builds only: fake templates + history, after the exercises exist.
            if (BuildConfig.DEBUG && DevDataSeeder.ENABLED) {
                get<DevDataSeeder>().seedIfEmpty()
            }
        }
    }
}
