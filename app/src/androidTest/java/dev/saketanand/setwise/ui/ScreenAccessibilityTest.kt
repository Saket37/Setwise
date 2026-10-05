package dev.saketanand.setwise.ui

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils.matchesChecks
import com.google.android.apps.common.testing.accessibility.framework.checks.TouchTargetSizeCheck
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import dev.saketanand.setwise.domain.model.DayState
import dev.saketanand.setwise.ui.designsystem.theme.SetwiseTheme
import dev.saketanand.setwise.ui.exercises.ExerciseDetailScreen
import dev.saketanand.setwise.ui.exercises.SampleExerciseDetailState
import dev.saketanand.setwise.ui.history.DayUi
import dev.saketanand.setwise.ui.history.HistoryMonthUi
import dev.saketanand.setwise.ui.history.HistoryScreen
import dev.saketanand.setwise.ui.history.HistoryUiState
import dev.saketanand.setwise.ui.history.HistoryWorkoutUi
import dev.saketanand.setwise.ui.home.HomeScreen
import dev.saketanand.setwise.ui.home.HomeUiState
import dev.saketanand.setwise.ui.home.SampleHomeState
import dev.saketanand.setwise.ui.importing.ImportScreen
import dev.saketanand.setwise.ui.importing.ImportUiState
import dev.saketanand.setwise.ui.settings.SampleSettingsState
import dev.saketanand.setwise.ui.settings.SettingsScreen
import dev.saketanand.setwise.ui.summary.SampleWorkoutSummaryState
import dev.saketanand.setwise.ui.summary.WorkoutSummaryScreen
import dev.saketanand.setwise.ui.templates.SampleTemplateFromGoalState
import dev.saketanand.setwise.ui.templates.TemplateFromGoalScreen
import dev.saketanand.setwise.ui.workout.ActiveWorkoutScreen
import dev.saketanand.setwise.ui.workout.SampleActiveWorkoutState
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration.Companion.minutes
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Google's Accessibility Test Framework over each main screen with typical content, in light and
 * dark theme: touch targets, text contrast, labels for controls, duplicate labels. A finding fails
 * the test with what and where. The checks need API 34+ and a device or emulator (not Robolectric).
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class ScreenAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun home() = check { HomeScreen(uiState = SampleHomeState, onAction = {}) }

    @Test
    fun homeFirstRun() = check { HomeScreen(uiState = HomeUiState(isLoading = false), onAction = {}) }

    @Test
    fun activeWorkout() = check(knownSmallTouchTargets = true) {
        ActiveWorkoutScreen(uiState = SampleActiveWorkoutState, onAction = {})
    }

    @Test
    fun workoutSummary() = check(knownSmallTouchTargets = true) {
        WorkoutSummaryScreen(uiState = SampleWorkoutSummaryState, onAction = {})
    }

    @Test
    fun history() = check { HistoryScreen(uiState = historyState, onAction = {}) }

    @Test
    fun exerciseDetail() = check { ExerciseDetailScreen(uiState = SampleExerciseDetailState, onBack = {}, onSessionClick = {}) }

    @Test
    fun settings() = check { SettingsScreen(uiState = SampleSettingsState, onAction = {}) }

    @Test
    fun buildFromAGoal() = check {
        TemplateFromGoalScreen(
            uiState = SampleTemplateFromGoalState,
            goal = rememberTextFieldState("Get stronger at squat and bench, 45 minutes"),
            onAction = {},
            onBack = {},
        )
    }

    @Test
    fun import() = check {
        ImportScreen(
            uiState = ImportUiState(),
            initialText = "",
            onRead = {},
            onChooseFile = {},
            onChooseScreenshots = {},
            onImport = {},
            onBack = {},
            onOpenHistory = {},
        )
    }

    /**
     * [screen] in light, then dark theme, checked as it first appears.
     * @param knownSmallTouchTargets the screen's touch targets under 48dp are tracked in #34:
     *   skip only that check until it's fixed (every other check still runs).
     */
    private fun check(knownSmallTouchTargets: Boolean = false, screen: @Composable () -> Unit) {
        val validator = AccessibilityValidator().setRunChecksFromRootView(true)
        if (knownSmallTouchTargets) {
            validator.setSuppressingResultMatcher(matchesChecks(equalTo(TouchTargetSizeCheck::class.java)))
        }
        compose.enableAccessibilityChecks(validator)
        var dark by mutableStateOf(false)
        compose.setContent { SetwiseTheme(darkTheme = dark) { screen() } }
        compose.onRoot().tryPerformAccessibilityChecks()
        dark = true
        compose.waitForIdle()
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    private val historyState: HistoryUiState
        get() {
            val today = LocalDate.of(2026, 10, 4)
            val workouts = listOf(
                HistoryWorkoutUi(1, "Push Day", today.minusDays(1), 64.minutes, 6_240.0, 0.0, 410, 2),
                HistoryWorkoutUi(2, "Pull Day", today.minusDays(3), 58.minutes, 5_120.0, 0.0, 380, 0),
                HistoryWorkoutUi(3, "Run", today.minusDays(4), 31.minutes, 0.0, 5.2, 330, 0),
            )
            return HistoryUiState(
                isLoading = false,
                days = (0L..13L).map { back ->
                    val date = today.minusDays(back)
                    val state = if (workouts.any { it.date == date }) DayState.Trained else DayState.Rest
                    DayUi(date = date, isToday = back == 0L, state = state, isSelected = false)
                },
                months = listOf(HistoryMonthUi(YearMonth.from(today), workouts)),
                today = today,
            )
        }
}
