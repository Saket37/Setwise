package dev.saketanand.setwise.ui.home

import androidx.compose.runtime.Immutable
import dev.saketanand.setwise.domain.model.DayStatus
import dev.saketanand.setwise.domain.model.WeekFacts
import java.time.LocalDate
import java.time.LocalTime
import kotlin.time.Duration

/**
 * Everything the Workout tab (Home) shows. Design artboards:
 * 1 Workout tab · 1b first run (empty) · 2 start workout sheet · 13 weekly summary card.
 */
// @Immutable: these UI models are never mutated after creation (lists included), so Compose can
// compare them with equals() and skip redrawing parts whose data didn't change.
@Immutable
data class HomeUiState(
    /** True until the first data arrives; avoids flashing the empty state on launch. */
    val isLoading: Boolean = true,

    // Header
    /** Shown top-right, e.g. "Fri, 2 Oct". Format in the UI. */
    val today: LocalDate = LocalDate.now(),
    /** "Last session: Pull Day · 2 days ago". Null if no workout has been finished yet. */
    val lastWorkout: LastWorkoutUi? = null,

    // A workout that was started but not finished (app closed, minimised…)
    /** Shows a "Resume workout" card above everything else. */
    val activeWorkout: ActiveWorkoutUi? = null,

    // This week strip
    val weekStats: WeekStatsUi = WeekStatsUi(),

    // Weekly summary card (artboard 13), shown at the start of a new week
    val weeklySummary: WeeklySummaryUi? = null,

    // Templates
    val templates: List<TemplateUi> = emptyList(),

    // Start workout sheet (artboard 2)
    val isStartSheetVisible: Boolean = false,
    /** Null means "Starts now". Set when the user taps "Change" to back-date the start. */
    val customStartTime: LocalTime? = null,
    /** A workout row is being created; disable the start buttons to avoid double taps. */
    val isStartingWorkout: Boolean = false,

    /** Size of the exercise library, for "128 exercises ready". 0 until loaded. */
    val exerciseCount: Int = 0,

    /** Set when the user starts a workout while one is running: shows the discard dialog. */
    val discardDialog: DiscardDialogUi? = null,

    /** "Did you train?": past days without a workout, asked about once a day. Null = hidden. */
    val checkIn: CheckInUi? = null,
) {
    /**
     * Which layout HomeScreen draws. Derived from the fields above, so it can never disagree
     * with them. Use it as `when (uiState.content) { … }`.
     */
    val content: HomeContent
        get() = when {
            isLoading -> HomeContent.Loading
            // Nothing to show: no finished workout, no template, nothing in progress.
            lastWorkout == null && templates.isEmpty() && activeWorkout == null -> HomeContent.FirstRun
            else -> HomeContent.Dashboard
        }

    /**
     * Dashboard only: the user has trained (or has a workout running) but hasn't saved any
     * template yet. Show the "Plan your routine" section (create a template / build from a goal)
     * in place of the template cards. On FirstRun that section is already part of the layout.
     */
    val showPlanYourRoutine: Boolean
        get() = content == HomeContent.Dashboard && templates.isEmpty()
}

/** The three layouts of the Workout tab. */
enum class HomeContent {
    /** Data not loaded yet; show nothing (or a placeholder) instead of flashing the wrong layout. */
    Loading,

    /** Artboard 1b: welcome screen with "Start an empty workout" and template options. */
    FirstRun,

    /** Artboard 1 (+13): greeting, week stats, resume card, weekly summary, templates. */
    Dashboard,
}

/** Most recent finished workout, for the header subtitle. */
@Immutable
data class LastWorkoutUi(
    val workoutId: Long,
    val name: String,
    /** 0 = today, 1 = yesterday, … */
    val daysAgo: Int,
)

/** An unfinished workout the user can resume. */
@Immutable
data class ActiveWorkoutUi(
    val workoutId: Long,
    val name: String,
    /** For the live "running for 12:34" text. */
    val startedAtMillis: Long,
    val completedSets: Int,
)

/** The three tiles: workouts · time trained · new PRs (current week, Monday–Sunday). */
@Immutable
data class WeekStatsUi(
    val workouts: Int = 0,
    val timeTrained: Duration = Duration.ZERO,
    val newPrs: Int = 0,
)

/** One template card on Home, and one row in the start sheet. */
@Immutable
data class TemplateUi(
    val id: Long,
    val name: String,
    /** Small tag on the card, e.g. "PUSH". Null if the template has no category. */
    val category: String?,
    /** First few exercise names for the card subtitle: "Bench · Incline DB · OHP". */
    val exercisePreview: List<String>,
    /** Exercises beyond the preview, shown as "+2". */
    val moreExerciseCount: Int,
    // Start sheet details: "6 exercises · 20 sets · ~65 min"
    val exerciseCount: Int,
    val setCount: Int,
    /** Rough length from sets × rest; null for cardio-only or when unknown. */
    val estimatedMinutes: Int?,
    /** "4 days ago"; null if never used. */
    val lastUsedDaysAgo: Int?,
)

/** Recap of the previous week (artboard 13): its facts (worked out in code) and the recap in words. */
@Immutable
data class WeeklySummaryUi(
    val facts: WeekFacts,
    /** Written on-device; null: the template from the same facts. */
    val recap: String?,
    /** The model is writing the recap: a placeholder line. */
    val isGeneratingRecap: Boolean,
)

/**
 * "Discard Pull Day?" dialog. Captures the running workout's id when the dialog opens, so
 * confirming deletes exactly the workout the user was asked about.
 */
@Immutable
data class DiscardDialogUi(
    val runningWorkoutId: Long,
    val runningWorkoutName: String,
    val runningCompletedSets: Int,
    /** What to start after discarding; null = empty workout. */
    val templateIdToStart: Long?,
)

/** The day check-in sheet: the days it asks about, newest first, with what the user answered. */
@Immutable
data class CheckInUi(val days: List<CheckInDayUi>) {
    val isAnyAnswered: Boolean get() = days.any { it.status != null }
}

@Immutable
data class CheckInDayUi(
    val date: LocalDate,
    /** Rest / Missed once answered; null = not yet. */
    val status: DayStatus?,
)
