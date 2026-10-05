package dev.saketanand.setwise.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dev.saketanand.setwise.data.dev.DevDataSeeder
import dev.saketanand.setwise.data.files.ContentResolverFileTextReader
import dev.saketanand.setwise.data.local.SetwiseDatabase
import dev.saketanand.setwise.data.prefs.DataStoreBodyRepository
import dev.saketanand.setwise.data.prefs.DataStoreUserSettingsRepository
import dev.saketanand.setwise.data.repository.DayMarkRepositoryImpl
import dev.saketanand.setwise.data.repository.ExerciseRepositoryImpl
import dev.saketanand.setwise.data.repository.TemplateRepositoryImpl
import dev.saketanand.setwise.data.repository.WorkoutRepositoryImpl
import dev.saketanand.setwise.data.seed.ExerciseSeeder
import dev.saketanand.setwise.data.seed.SeedPreferences
import dev.saketanand.setwise.domain.CalorieSync
import dev.saketanand.setwise.domain.ai.AiCheck
import dev.saketanand.setwise.domain.ai.BodyReportReader
import dev.saketanand.setwise.domain.ai.CalorieEstimator
import dev.saketanand.setwise.domain.ai.ExerciseAssistant
import dev.saketanand.setwise.domain.ai.FileTextReader
import dev.saketanand.setwise.domain.ai.GoalPlanAssistant
import dev.saketanand.setwise.domain.ai.HistoryAssistant
import dev.saketanand.setwise.domain.ai.ImportReader
import dev.saketanand.setwise.domain.ai.ModelDownloader
import dev.saketanand.setwise.domain.ai.OnDeviceModel
import dev.saketanand.setwise.domain.ai.PlateauNoteWriter
import dev.saketanand.setwise.domain.ai.QuickLogInterpreter
import dev.saketanand.setwise.domain.ai.SpeechInput
import dev.saketanand.setwise.domain.ai.TextReader
import dev.saketanand.setwise.domain.ai.WeeklyRecapWriter
import dev.saketanand.setwise.domain.ai.WorkoutImporter
import dev.saketanand.setwise.domain.ai.WorkoutInsightWriter
import dev.saketanand.setwise.domain.repository.BodyRepository
import dev.saketanand.setwise.domain.repository.DayMarkRepository
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.TemplateRepository
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.llm.GeminiNanoModel
import dev.saketanand.setwise.llm.GenAiSpeechInput
import dev.saketanand.setwise.llm.MlKitTextReader
import dev.saketanand.setwise.timer.AndroidNotificationPermission
import dev.saketanand.setwise.timer.DefaultRestTimer
import dev.saketanand.setwise.timer.NotificationPermission
import dev.saketanand.setwise.timer.RestAlert
import dev.saketanand.setwise.timer.RestNotificationRefresher
import dev.saketanand.setwise.timer.RestTimer
import dev.saketanand.setwise.timer.RestTimerCoordinator
import dev.saketanand.setwise.util.DateProvider
import dev.saketanand.setwise.util.ElapsedClock
import dev.saketanand.setwise.util.SystemDateProvider
import dev.saketanand.setwise.util.SystemElapsedClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val appModule = module {
    // The app's clock ("now", today + midnight rollover); swapped for a fixed date in tests.
    singleOf(::SystemDateProvider) bind DateProvider::class

    includes(dispatchersModule)

    // Outlives screens; SupervisorJob so one failing job doesn't cancel the others.
    single(ApplicationScope) { CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(DefaultDispatcher)) }

    // Rest timer: one for the whole app, on the main thread (its callers are the screen and the
    // notification's buttons). Counts on the boot clock, which doesn't jump with time changes.
    single<ElapsedClock> { SystemElapsedClock }
    single<RestTimer> { DefaultRestTimer(CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(MainDispatcher)), get()) }
    single { RestAlert(androidContext()) }
    // On-device model (Gemini Nano). Features take OnDeviceModel, so tests use a fake.
    single<OnDeviceModel> { GeminiNanoModel() }
    single<SpeechInput> { GenAiSpeechInput() }
    single<TextReader> { MlKitTextReader(androidContext()) }
    singleOf(::BodyReportReader)
    singleOf(::WorkoutImporter)
    single<FileTextReader> { ContentResolverFileTextReader(androidContext().contentResolver, get(IoDispatcher)) }
    singleOf(::ImportReader)
    singleOf(::GoalPlanAssistant)
    singleOf(::CalorieEstimator)
    singleOf(::WorkoutInsightWriter)
    singleOf(::ExerciseAssistant)
    singleOf(::QuickLogInterpreter)
    singleOf(::PlateauNoteWriter)
    singleOf(::HistoryAssistant)
    singleOf(::WeeklyRecapWriter)
    single { ModelDownloader(get(), get(ApplicationScope)) }
    factoryOf(::AiCheck) // debug launch extra only (MainActivity)
    singleOf(::CalorieSync)
    single<NotificationPermission> { AndroidNotificationPermission(androidContext()) }
    single { RestTimerCoordinator(androidContext(), get(), get(), get(), get(), get(), CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(MainDispatcher))) } bind
        RestNotificationRefresher::class

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
    single { get<SetwiseDatabase>().dayMarkDao() }

    // App preferences (seed version now; settings like body weight later). One instance per file.
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile("setwise_prefs") }
    }
    singleOf(::SeedPreferences)
    // Body weight, training days, check-in switch, onboarding done (Settings / onboarding).
    singleOf(::DataStoreUserSettingsRepository) bind UserSettingsRepository::class
    singleOf(::DataStoreBodyRepository) bind BodyRepository::class

    single { ExerciseSeeder(androidContext(), get(), get(), get(), get(IoDispatcher)) }

    // Repositories: bound to their domain interface, so callers depend on ExerciseRepository.
    singleOf(::ExerciseRepositoryImpl) bind ExerciseRepository::class
    singleOf(::WorkoutRepositoryImpl) bind WorkoutRepository::class
    singleOf(::TemplateRepositoryImpl) bind TemplateRepository::class
    singleOf(::DayMarkRepositoryImpl) bind DayMarkRepository::class

    // Debug-only fake data (only invoked when BuildConfig.DEBUG; see SetwiseApp).
    singleOf(::DevDataSeeder)
}
