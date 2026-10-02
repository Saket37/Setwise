package dev.saketanand.setwise.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeUiStateTest {

    private val loaded = HomeUiState(isLoading = false)

    private val template = TemplateUi(
        id = 1, name = "Push Day", category = "PUSH",
        exercisePreview = listOf("Bench"), moreExerciseCount = 0,
        exerciseCount = 1, setCount = 3, estimatedMinutes = 20, lastUsedDaysAgo = null,
    )

    @Test
    fun `loading shows loading, even with no data`() {
        assertEquals(HomeContent.Loading, HomeUiState(isLoading = true).content)
    }

    @Test
    fun `nothing at all is first run`() {
        assertEquals(HomeContent.FirstRun, loaded.content)
    }

    @Test
    fun `a template alone is enough for the dashboard`() {
        assertEquals(HomeContent.Dashboard, loaded.copy(templates = listOf(template)).content)
    }

    @Test
    fun `a workout in progress is enough for the dashboard`() {
        val active = ActiveWorkoutUi(workoutId = 1, name = "Push Day", startedAtMillis = 0, completedSets = 0)
        assertEquals(HomeContent.Dashboard, loaded.copy(activeWorkout = active).content)
    }

    @Test
    fun `trained but no templates shows plan your routine`() {
        val last = LastWorkoutUi(workoutId = 1, name = "Push Day", daysAgo = 2)
        assertEquals(true, loaded.copy(lastWorkout = last).showPlanYourRoutine)
    }

    @Test
    fun `plan your routine is hidden once a template exists, and on first run`() {
        val last = LastWorkoutUi(workoutId = 1, name = "Push Day", daysAgo = 2)
        assertEquals(false, loaded.copy(lastWorkout = last, templates = listOf(template)).showPlanYourRoutine)
        assertEquals(false, loaded.showPlanYourRoutine) // first run has its own section
        assertEquals(false, HomeUiState(isLoading = true).showPlanYourRoutine)
    }

    @Test
    fun `a finished workout is enough for the dashboard`() {
        val last = LastWorkoutUi(workoutId = 1, name = "Push Day", daysAgo = 2)
        assertEquals(HomeContent.Dashboard, loaded.copy(lastWorkout = last).content)
    }
}
