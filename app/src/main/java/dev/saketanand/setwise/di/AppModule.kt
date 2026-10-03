package dev.saketanand.setwise.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dev.saketanand.setwise.data.local.SetwiseDatabase
import dev.saketanand.setwise.data.dev.DevDataSeeder
import dev.saketanand.setwise.data.repository.ExerciseRepositoryImpl
import dev.saketanand.setwise.data.repository.TemplateRepositoryImpl
import dev.saketanand.setwise.data.repository.WorkoutRepositoryImpl
import dev.saketanand.setwise.data.seed.ExerciseSeeder
import dev.saketanand.setwise.data.seed.SeedPreferences
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.TemplateRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import dev.saketanand.setwise.util.SystemDateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    // The app's clock ("now", today + midnight rollover); swapped for a fixed date in tests.
    singleOf(::SystemDateProvider) bind DateProvider::class

    // Outlives screens; SupervisorJob so one failing job doesn't cancel the others.
    single(ApplicationScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // Shared JSON parser. ignoreUnknownKeys: new fields in exercises.json won't crash older builds.
    single { Json { ignoreUnknownKeys = true } }

    // The one database instance for the app. Never rename the file: a new name = a new, empty DB.
    single {
        Room.databaseBuilder(androidContext(), SetwiseDatabase::class.java, "setwise_database")
            .build()
    }
    single { get<SetwiseDatabase>().exerciseDao() }
    single { get<SetwiseDatabase>().workoutDao() }
    single { get<SetwiseDatabase>().templateDao() }

    // App preferences (seed version now; settings like body weight later). One instance per file.
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile("setwise_prefs") }
    }
    singleOf(::SeedPreferences)

    singleOf(::ExerciseSeeder)

    // Repositories: bound to their domain interface, so callers depend on ExerciseRepository.
    singleOf(::ExerciseRepositoryImpl) bind ExerciseRepository::class
    singleOf(::WorkoutRepositoryImpl) bind WorkoutRepository::class
    singleOf(::TemplateRepositoryImpl) bind TemplateRepository::class

    // Debug-only fake data (only invoked when BuildConfig.DEBUG; see SetwiseApp).
    singleOf(::DevDataSeeder)
}
