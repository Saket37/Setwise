package dev.saketanand.setwise

import android.app.Application
import dev.saketanand.setwise.data.seed.ExerciseSeeder
import dev.saketanand.setwise.di.appModule
import dev.saketanand.setwise.di.viewModelModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SetwiseApp : Application() {

    /** App-wide scope for startup work. SupervisorJob: one failed job doesn't cancel the rest. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@SetwiseApp)
            modules(appModule, viewModelModule)
        }

        // Load the built-in exercise library on first launch (no-op afterwards).
        val exerciseSeeder: ExerciseSeeder = get()
        appScope.launch { exerciseSeeder.seedIfNeeded() }
    }
}
