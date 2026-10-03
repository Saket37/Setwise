package dev.saketanand.setwise.di

import dev.saketanand.setwise.ui.exercises.CreateExerciseViewModel
import dev.saketanand.setwise.ui.exercises.ExerciseDetailViewModel
import dev.saketanand.setwise.ui.exercises.ExercisePickerViewModel
import dev.saketanand.setwise.ui.history.HistoryViewModel
import dev.saketanand.setwise.ui.home.HomeViewModel
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
        )
    }
    viewModelOf(::CardioEntryViewModel)
    viewModelOf(::WorkoutSummaryViewModel)

    viewModelOf(::ExercisePickerViewModel)
    viewModelOf(::CreateExerciseViewModel)
    viewModelOf(::ExerciseDetailViewModel)

    viewModelOf(::TemplateEditorViewModel)
    viewModelOf(::TemplateFromGoalViewModel)
}
