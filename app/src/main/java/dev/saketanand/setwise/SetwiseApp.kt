package dev.saketanand.setwise

import android.app.Application
import dev.saketanand.setwise.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class SetwiseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@SetwiseApp)
            modules(appModule)
        }
    }
}
