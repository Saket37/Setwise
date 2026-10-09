package dev.saketanand.setwise.di

import android.util.Log
import dev.saketanand.setwise.MainViewModel
import dev.saketanand.setwise.ui.body.BodyProgressViewModel
import dev.saketanand.setwise.ui.body.BodyReportViewModel
import dev.saketanand.setwise.ui.body.BodyViewModel
import dev.saketanand.setwise.ui.exercises.CreateExerciseViewModel
import dev.saketanand.setwise.ui.exercises.ExerciseDetailViewModel
import dev.saketanand.setwise.ui.exercises.ExercisePickerViewModel
import dev.saketanand.setwise.ui.history.HistoryViewModel
import dev.saketanand.setwise.ui.home.HomeViewModel
import dev.saketanand.setwise.ui.importing.ImportViewModel
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.ui.onboarding.OnboardingViewModel
import dev.saketanand.setwise.ui.settings.SettingsViewModel
import dev.saketanand.setwise.ui.summary.WorkoutSummaryViewModel
import dev.saketanand.setwise.ui.templates.TemplateEditorViewModel
import dev.saketanand.setwise.ui.templates.TemplateFromGoalViewModel
import dev.saketanand.setwise.ui.workout.ActiveWorkoutViewModel
import dev.saketanand.setwise.ui.workout.CardioEntryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * One ViewModel per screen. viewModelOf resolves constructor parameters from Koin
 * (repositories etc.) and supplies SavedStateHandle with the navigation arguments.
 */
val viewModelModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::BodyViewModel)
    viewModelOf(::BodyProgressViewModel)
    // The route, read by type (as the active workout's); without one the screen finds nothing and closes.
    viewModel { params ->
        BodyReportViewModel(params.getOrNull<Route.BodyReport>()?.measurementId ?: Route.NO_WORKOUT_ID, get(), get())
    }
    viewModel { params ->
        ImportViewModel(
            shared = params.get(),
            reader = get(),
            importer = get(),
            exerciseRepository = get(),
            dateProvider = get(),
        )
    }
    viewModelOf(::SettingsViewModel)

    // SetwiseNavHost passes the whole route (koinViewModel { parametersOf(route) }): read by type,
    // so its values can't be mixed up. Without one there's no workout to show: the screen closes
    // (no workout has NO_WORKOUT_ID) instead of the app crashing.
    viewModel { params ->
        val route = params.getOrNull<Route.ActiveWorkout>()
            ?: Route.ActiveWorkout(Route.NO_WORKOUT_ID).also { Log.e("ViewModelModule", "The active workout opened without its route") }
        ActiveWorkoutViewModel(
            workoutId = route.workoutId,
            isEditingFinished = route.editingFinished,
            workoutRepository = get(),
            dateProvider = get(),
            savedStateHandle = get(),
            writeScope = get(ApplicationScope),
            restTimer = get(),
            restNotifications = get(),
            quickLogInterpreter = get(),
            exerciseRepository = get(),
            speechInput = get(),
            userSettings = get(),
        )
    }
    viewModel { params ->
        CardioEntryViewModel(
            workoutExerciseId = params.get(),
            workoutRepository = get(),
            dateProvider = get(),
            savedStateHandle = get(),
            userSettingsRepository = get(),
            bodyRepository = get(),
        )
    }
    // Route argument passed by SetwiseNavHost.
    viewModel { params ->
        WorkoutSummaryViewModel(
            workoutId = params.get(),
            workoutRepository = get(),
            templateRepository = get(),
            userSettingsRepository = get(),
            insightWriter = get(),
            dateProvider = get(),
        )
    }

    viewModelOf(::ExercisePickerViewModel)
    viewModel { params ->
        CreateExerciseViewModel(
            initialName = params[0],
            exerciseRepository = get(),
            assistant = get(),
            savedStateHandle = get(),
            editor = get(),
            exerciseId = params[1],
        )
    }
    viewModel { params ->
        ExerciseDetailViewModel(
            exerciseId = params.get(),
            exerciseRepository = get(),
            workoutRepository = get(),
            dateProvider = get(),
            plateauNotes = get(),
            editor = get(),
        )
    }

    viewModel { params ->
        TemplateEditorViewModel(
            templateId = params.get(),
            templateRepository = get(),
            exerciseRepository = get(),
            dateProvider = get(),
            savedStateHandle = get(),
        )
    }
    viewModelOf(::TemplateFromGoalViewModel)
}
