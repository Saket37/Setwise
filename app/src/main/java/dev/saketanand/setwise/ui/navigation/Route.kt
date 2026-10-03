package dev.saketanand.setwise.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations. Arguments are constructor properties. SetwiseNavHost reads
 * them with `entry.toRoute<Route.X>()` and passes them to the ViewModel through Koin
 * (`koinViewModel { parametersOf(route.id) }`), so ViewModels stay unit-testable (toRoute on a
 * SavedStateHandle needs an Android Bundle).
 */
@Serializable
sealed interface Route {

    // Top-level tabs (bottom navigation bar)

    /** Workout tab: start workout sheet, templates, weekly summary card. */
    @Serializable data object Home : Route

    /** History tab: past workouts by date + "Ask your history". */
    @Serializable data object History : Route

    /** Settings tab: profile, rest timer, on-device AI, connected apps, data. */
    @Serializable data object Settings : Route

    // Workout flow

    /** Live workout with sets, rest timer and quick-log. */
    @Serializable data class ActiveWorkout(val workoutId: Long) : Route

    /** Treadmill / cardio inputs for one exercise in the active workout. */
    @Serializable data class CardioEntry(val workoutExerciseId: Long) : Route

    /** Shown after Finish, and when opening a workout from History. */
    @Serializable data class WorkoutSummary(val workoutId: Long) : Route

    // Exercises

    /**
     * Search + pick exercises. Opened from Active workout and Template editor; the picked ids are
     * returned to the caller via the previous back-stack entry (see SetwiseNavHost).
     */
    @Serializable data object ExercisePicker : Route

    /** Create a custom exercise (with LLM matching / classification). */
    @Serializable data class CreateExercise(val initialName: String = "") : Route

    /** Exercise history, progression hint and plateau note. */
    @Serializable data class ExerciseDetail(val exerciseId: Long) : Route

    // Templates

    /** Create (templateId = NEW_TEMPLATE_ID) or edit a template. */
    @Serializable data class TemplateEditor(val templateId: Long = NEW_TEMPLATE_ID) : Route

    /** Generate a draft template from a goal with the LLM. */
    @Serializable data object TemplateFromGoal : Route

    companion object {
        const val NEW_TEMPLATE_ID = 0L
    }
}
