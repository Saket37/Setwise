package dev.saketanand.setwise.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dev.saketanand.setwise.ui.body.BodyReportViewModel
import dev.saketanand.setwise.ui.exercises.CreateExerciseViewModel
import dev.saketanand.setwise.ui.exercises.ExerciseDetailViewModel
import dev.saketanand.setwise.ui.importing.ImportViewModel
import dev.saketanand.setwise.ui.importing.SharedImport
import dev.saketanand.setwise.ui.summary.WorkoutSummaryViewModel
import dev.saketanand.setwise.ui.templates.TemplateEditorViewModel
import dev.saketanand.setwise.ui.workout.ActiveWorkoutViewModel
import dev.saketanand.setwise.ui.workout.CardioEntryViewModel
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

/** A missing or mistyped binding fails here, not as a crash when a screen first asks for it. */
@OptIn(KoinExperimentalAPI::class)
class KoinModulesTest {

    @Test
    fun `every definition's dependencies are declared`() {
        module { includes(appModule, viewModelModule) }.verify(
            // Given by Android / by Koin for each ViewModel at runtime.
            extraTypes = listOf(Context::class, SavedStateHandle::class),
            // What each screen passes its ViewModel (koinViewModel { parametersOf(…) }).
            injections = injectedParameters(
                definition<ImportViewModel>(SharedImport::class),
                definition<ActiveWorkoutViewModel>(Long::class),
                definition<CardioEntryViewModel>(Long::class),
                definition<WorkoutSummaryViewModel>(Long::class),
                definition<CreateExerciseViewModel>(String::class),
                definition<ExerciseDetailViewModel>(Long::class),
                definition<TemplateEditorViewModel>(Long::class),
                definition<BodyReportViewModel>(Long::class),
            ),
        )
    }
}
