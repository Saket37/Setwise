package dev.saketanand.setwise.di

import dev.saketanand.setwise.MainViewModel
import dev.saketanand.setwise.ui.exercises.CreateExerciseViewModel
import dev.saketanand.setwise.ui.exercises.ExerciseDetailViewModel
import dev.saketanand.setwise.ui.exercises.ExercisePickerViewModel
import dev.saketanand.setwise.ui.history.HistoryViewModel
import dev.saketanand.setwise.ui.home.HomeViewModel
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
    viewModelOf(::SettingsViewModel)

    // Route argument passed by SetwiseNavHost: koinViewModel { parametersOf(route.workoutId) }.
    viewModel { params ->
        ActiveWorkoutViewModel(
            workoutId = params.get(),
            workoutRepository = get(),
            dateProvider = get(),
            savedStateHandle = get(),
            writeScope = get(ApplicationScope),
            restTimer = get(),
            restNotifications = get(),
        )
    }
    viewModelOf(::CardioEntryViewModel)
    // Route argument passed by SetwiseNavHost.
    viewModel { params ->
        WorkoutSummaryViewModel(
            workoutId = params.get(),
            workoutRepository = get(),
            templateRepository = get(),
            dateProvider = get(),
        )
    }

    viewModelOf(::ExercisePickerViewModel)
    viewModelOf(::CreateExerciseViewModel)
    viewModel { params ->
        ExerciseDetailViewModel(
            exerciseId = params.get(),
            exerciseRepository = get(),
            workoutRepository = get(),
            dateProvider = get(),
        )
    }

    viewModelOf(::TemplateEditorViewModel)
    viewModelOf(::TemplateFromGoalViewModel)
}
