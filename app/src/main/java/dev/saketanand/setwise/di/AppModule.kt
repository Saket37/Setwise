package dev.saketanand.setwise.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dev.saketanand.setwise.data.local.SetwiseDatabase
import dev.saketanand.setwise.data.repository.ExerciseRepositoryImpl
import dev.saketanand.setwise.data.seed.ExerciseSeeder
import dev.saketanand.setwise.data.seed.SeedPreferences
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    // Shared JSON parser. ignoreUnknownKeys: new fields in exercises.json won't crash older builds.
    single { Json { ignoreUnknownKeys = true } }

    // The one database instance for the app. Never rename the file: a new name = a new, empty DB.
    single {
        Room.databaseBuilder(androidContext(), SetwiseDatabase::class.java, "setwise_database")
            .build()
    }
    single { get<SetwiseDatabase>().exerciseDao() }

    // App preferences (seed version now; settings like body weight later). One instance per file.
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile("setwise_prefs") }
    }
    singleOf(::SeedPreferences)

    singleOf(::ExerciseSeeder)

    // Repositories: bound to their domain interface, so callers depend on ExerciseRepository.
    singleOf(::ExerciseRepositoryImpl) bind ExerciseRepository::class
}
