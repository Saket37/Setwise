package dev.saketanand.setwise.ui.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import dev.saketanand.setwise.R
import dev.saketanand.setwise.ui.LocalWallClock
import dev.saketanand.setwise.ui.ObserveAsEvents
import dev.saketanand.setwise.ui.RecognizeSpeech
import dev.saketanand.setwise.ui.currentLocale
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButton
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonDefaults
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonSize
import dev.saketanand.setwise.ui.designsystem.components.SetwiseButtonStyle
import dev.saketanand.setwise.ui.designsystem.components.SetwiseEmptyState
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTimePickerDialog
import dev.saketanand.setwise.ui.designsystem.components.SetwiseTopAppBar
import dev.saketanand.setwise.ui.designsystem.preview.PREVIEW_NOW_MILLIS
import dev.saketanand.setwise.ui.designsystem.preview.PreviewScreens
import dev.saketanand.setwise.ui.designsystem.preview.SetwiseScreenPreview
import dev.saketanand.setwise.ui.navigation.Route
import dev.saketanand.setwise.ui.rememberElapsedTime
import dev.saketanand.setwise.util.toClockLabel
import dev.saketanand.setwise.util.toShortDayLabel
import dev.saketanand.setwise.util.toShortTimeLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Destination: [Route.ActiveWorkout].
 * @param pickedExerciseIds Result from the exercise picker; null when there is none.
 * @param onPickedExercisesConsumed Call after adding the picked exercises, so they are not added twice.
 * @param onAddExercises Opens the exercise picker.
 * @param onOpenExercise Exercise detail / progression.
 * @param onFinished After Finish; opens the summary.
 * @param onMinimize Back to the tabs; the workout keeps running.
 */
@Composable
fun ActiveWorkoutScreenRoot(
    pickedExerciseIds: ImmutableList<Long>?,
    onPickedExercisesConsumed: () -> Unit,
    onAddExercises: () -> Unit,
    onOpenCardioEntry: (workoutExerciseId: Long) -> Unit,
    onOpenExercise: (exerciseId: Long) -> Unit,
    onFinished: (workoutId: Long) -> Unit,
    onMinimize: () -> Unit,
    viewModel: ActiveWorkoutViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The picker's result arrives once; hand it to the ViewModel and clear it. The latest
    // callback, not the one from when the effect started (#35).
    val onPickedConsumed by rememberUpdatedState(onPickedExercisesConsumed)
    LaunchedEffect(pickedExerciseIds) {
        if (pickedExerciseIds != null) {
            viewModel.onAction(ActiveWorkoutAction.OnExercisesPicked(pickedExerciseIds))
            onPickedConsumed()
        }
    }

    // Ask for notifications the first time a rest starts (Android 13+), when it's clear why:
    // the countdown and "rest over" alert show there. Without it the timer still works in the app.
    // Allowed → show the countdown that was dropped while the prompt was up.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onAction(ActiveWorkoutAction.OnNotificationsAllowed)
    }
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    val isResting = uiState.rest != null
    LaunchedEffect(isResting) {
        if (isResting && !askedForNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // dropUnlessResumed: no double navigation from a double tap or two events in a row.
    val minimize = dropUnlessResumed(block = onMinimize)
    // Editing a finished workout: the system back saves too (the edits are already written).
    BackHandler(enabled = uiState.isEditingFinished) { viewModel.onAction(ActiveWorkoutAction.OnFinishClick) }
    val addExercises = dropUnlessResumed(block = onAddExercises)

    // The quick-log line lives here (typing stays in sync); changing it drops a card about the old one.
    val quickLogField = rememberTextFieldState()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(quickLogField) {
        snapshotFlow { quickLogField.text.toString() }.collect { viewModel.onAction(ActiveWorkoutAction.OnQuickLogEdited(it)) }
    }
    // Speaking: what was heard goes in the bar and is read straight away.
    // Speaking: on-device (with the microphone permission) where the phone has it, else its
    // own recognizer's dialog, which needs no permission (also when the permission is declined).
    val canSpeak = remember(context) { RecognizeSpeech.isAvailable(context) }
    val speakPrompt = stringResource(R.string.quick_log_speak_prompt)
    val speak = rememberLauncherForActivityResult(RecognizeSpeech()) { heard ->
        if (heard != null) {
            quickLogField.setTextAndPlaceCursorAtEnd(heard)
            viewModel.onAction(ActiveWorkoutAction.OnQuickLogSubmit(heard))
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val saveFailed = stringResource(R.string.save_failed)
    val setRemoved = stringResource(R.string.set_removed)
    val undo = stringResource(R.string.undo)

    // Events navigate directly (not through dropUnlessResumed): they're delivered from STARTED,
    // where dropUnlessResumed would silently ignore them, and the ViewModel sends each only once.
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ActiveWorkoutEvent.Finished -> onFinished(event.workoutId)
            ActiveWorkoutEvent.Closed -> onMinimize()
            ActiveWorkoutEvent.SaveFailed -> snackbarScope.launch { snackbarHostState.showSnackbar(saveFailed) }
            is ActiveWorkoutEvent.SetRemoved -> snackbarScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss() // one at a time: the newest removal
                val result = snackbarHostState.showSnackbar(
                    message = setRemoved.format(Locale.ROOT, event.set.setNumber), // Latin digits, like the set rows
                    actionLabel = undo,
                    duration = SnackbarDuration.Long, // ~10 s: time to notice what was removed
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.onAction(ActiveWorkoutAction.OnUndoRemoveSet(event.set))
            }
            is ActiveWorkoutEvent.QuickLogHeard -> quickLogField.setTextAndPlaceCursorAtEnd(event.text)
            ActiveWorkoutEvent.QuickLogAdded -> {
                quickLogField.clearText()
                focusManager.clearFocus()
            }
        }
    }

    val usePhoneRecognizer = dropUnlessResumed {
        viewModel.onAction(ActiveWorkoutAction.OnPhoneSpeechUsed)
        speak.launch(speakPrompt)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.onAction(ActiveWorkoutAction.OnStartListening)
        } else if (canSpeak) {
            usePhoneRecognizer()
        }
    }
    val onSpeak: (() -> Unit)? = when {
        uiState.onDeviceSpeech -> {
            {
                when {
                    uiState.quickLog.isListening -> viewModel.onAction(ActiveWorkoutAction.OnStopListening)
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                        viewModel.onAction(ActiveWorkoutAction.OnStartListening)
                    else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        canSpeak -> usePhoneRecognizer
        else -> null
    }

    ActiveWorkoutScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        quickLogField = quickLogField,
        onSpeak = onSpeak,
        onAction = { action ->
            when (action) {
                // Editing a finished workout: leaving saves the edits (they're already written).
                ActiveWorkoutAction.OnMinimizeClick -> if (uiState.isEditingFinished) viewModel.onAction(ActiveWorkoutAction.OnFinishClick) else minimize()
                ActiveWorkoutAction.OnAddExerciseClick -> addExercises()
                is ActiveWorkoutAction.OnLogCardioClick -> onOpenCardioEntry(action.workoutExerciseId)
                is ActiveWorkoutAction.OnExerciseHistoryClick -> onOpenExercise(action.exerciseId)
                else -> viewModel.onAction(action)
            }
        },
    )
}

/**
 * Artboard 4: header (minimise, name, start time, clock, Finish), one card per exercise
 * (the open one with its set table), "Add exercise" and "Discard workout".
 */
@Composable
fun ActiveWorkoutScreen(
    uiState: ActiveWorkoutUiState,
    onAction: (ActiveWorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
    quickLogField: TextFieldState = rememberTextFieldState(),
    /** The quick-log mic; null hides it. */
    onSpeak: (() -> Unit)? = null,
    /** "Removed set 2 · Undo", save errors: shown above the rest bar. */
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var isQuickLogFocused by remember { mutableStateOf(false) }
    val quickLogFocus = remember { FocusRequester() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Keep the focused set row above the keyboard. The app Scaffold already pads for the
            // navigation bar, so only the part of the keyboard above it is added here.
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
    ) {
        ActiveWorkoutTopBar(uiState, onAction)

        if (!uiState.isLoading) {
            ExerciseCards(uiState = uiState, onAction = onAction, modifier = Modifier.weight(1f))
        }

        SnackbarHost(snackbarHostState)

        // Keeps showing the last rest while the bar slides away.
        var lastRest by remember { mutableStateOf(uiState.rest) }
        if (uiState.rest != null) lastRest = uiState.rest
        AnimatedVisibility(
            visible = uiState.rest != null,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            lastRest?.let { RestTimerBar(rest = it, onAction = onAction) }
        }

        if (!uiState.isLoading) {
            QuickLogSection(
                field = quickLogField,
                quickLog = uiState.quickLog,
                isFieldFocused = isQuickLogFocused,
                onFieldFocusChange = { isQuickLogFocused = it },
                focusRequester = quickLogFocus,
                onAction = { action ->
                    // "Edit": back into the line.
                    if (action == ActiveWorkoutAction.OnQuickLogEdit) quickLogFocus.requestFocus()
                    onAction(action)
                },
                onSpeak = onSpeak,
            )
        }
    }

    uiState.dialog?.let { ActiveWorkoutDialogs(dialog = it, workoutName = uiState.name, onAction = onAction) }

    if (uiState.isStartTimePickerVisible && uiState.startTime != null) {
        val clock = LocalWallClock.current
        SetwiseTimePickerDialog(
            title = stringResource(R.string.pick_start_time),
            initial = uiState.startTime,
            onConfirm = { onAction(ActiveWorkoutAction.OnStartTimeChange(it)) },
            onDismiss = { onAction(ActiveWorkoutAction.OnStartTimePickerDismiss) },
            isAllowed = { time ->
                isPickableStartTime(time, Instant.ofEpochMilli(uiState.startedAtMillis), Instant.ofEpochMilli(clock()), ZoneId.systemDefault())
            },
            notAllowedMessage = stringResource(R.string.start_time_in_future),
        )
    }
}

/** The exercise cards, then "Add exercise" and "Discard workout". */
@Composable
private fun ExerciseCards(
    uiState: ActiveWorkoutUiState,
    onAction: (ActiveWorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.exercises.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                SetwiseEmptyState(
                    title = stringResource(R.string.no_exercises_yet),
                    message = stringResource(R.string.no_exercises_yet_message),
                    icon = R.drawable.ic_nav_workout,
                )
            }
        }
        items(
            items = uiState.exercises,
            key = { it.id },
            // Open and collapsed cards have different layouts, so Compose shouldn't reuse one for the other.
            contentType = { if (it.id == uiState.expandedExerciseId) "open" else "collapsed" },
        ) { exercise ->
            val cardModifier = Modifier.animateItem()
            if (exercise.id == uiState.expandedExerciseId) {
                ExpandedExerciseCard(exercise = exercise, onAction = onAction, modifier = cardModifier, editableWhenDone = uiState.isEditingFinished)
            } else {
                CollapsedExerciseCard(exercise = exercise, onAction = onAction, modifier = cardModifier)
            }
        }
        item(key = "addExercise", contentType = "button") {
            SetwiseButton(
                text = stringResource(R.string.add_exercise),
                onClick = { onAction(ActiveWorkoutAction.OnAddExerciseClick) },
                style = if (uiState.exercises.isEmpty()) SetwiseButtonStyle.Filled else SetwiseButtonStyle.Outlined,
                startIcon = R.drawable.ic_add,
                textStyle = if (uiState.exercises.isEmpty()) null else MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
            )
        }
        // A finished workout is deleted from its summary.
        if (!uiState.isEditingFinished) item(key = "discard", contentType = "button") {
            SetwiseButton(
                text = stringResource(R.string.discard_workout),
                onClick = { onAction(ActiveWorkoutAction.OnDiscardWorkoutClick) },
                style = SetwiseButtonStyle.Text,
                size = SetwiseButtonSize.Medium,
                colors = SetwiseButtonDefaults.destructiveColors(SetwiseButtonStyle.Text),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
            )
        }
    }
}

/** Name (tap: rename), "Started 6:42 PM ✎" (editing a finished workout: "Editing sets"), the clock, Finish / Save. */
@Composable
private fun ActiveWorkoutTopBar(uiState: ActiveWorkoutUiState, onAction: (ActiveWorkoutAction) -> Unit) {
    SetwiseTopAppBar(
        title = uiState.name,
        onBack = { onAction(ActiveWorkoutAction.OnMinimizeClick) },
        onTitleClick = { onAction(ActiveWorkoutAction.OnRenameClick) },
        titleClickLabel = stringResource(R.string.rename_workout),
        navigationIcon = if (uiState.isEditingFinished) R.drawable.ic_arrow_back else R.drawable.ic_chevron_down,
        navigationContentDescription = stringResource(if (uiState.isEditingFinished) R.string.back else R.string.minimise_workout),
        subtitle = {
            when {
                // Its times are edited on the summary.
                uiState.isEditingFinished -> Text(
                    stringResource(R.string.editing_sets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> uiState.startTime?.let {
                    StartTimeButton(it, pastDay = uiState.pastDay, onClick = { onAction(ActiveWorkoutAction.OnStartTimeClick) })
                }
            }
        },
    ) {
        // No running clock for a workout logged afterwards for a past day, or one being edited.
        if (!uiState.isLoading && uiState.pastDay == null && !uiState.isEditingFinished) {
            WorkoutClock(startedAtMillis = uiState.startedAtMillis, modifier = Modifier.padding(end = 6.dp))
        }
        SetwiseButton(
            text = stringResource(if (uiState.isEditingFinished) R.string.save else R.string.finish),
            onClick = { onAction(ActiveWorkoutAction.OnFinishClick) },
            size = SetwiseButtonSize.Medium,
            textStyle = MaterialTheme.typography.titleSmall,
            enabled = !uiState.isLoading && !uiState.isFinishing,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

/** "Started 6:42 PM ✎": opens the time picker to back-date the start. */
@Composable
private fun StartTimeButton(startTime: LocalTime, pastDay: LocalDate?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 24.dp)
            .clickable(onClickLabel = stringResource(R.string.change_start_time), role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            if (pastDay != null) {
                stringResource(R.string.started_on, pastDay.toShortDayLabel(currentLocale()), startTime.toShortTimeLabel())
            } else {
                stringResource(R.string.started_at, startTime.toShortTimeLabel())
            }
        )
        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(13.dp))
    }
}

/** "38:12", ticking. Its own composable so each tick redraws only this text. */
@Composable
private fun WorkoutClock(startedAtMillis: Long, modifier: Modifier = Modifier) {
    val elapsed = rememberElapsedTime(startedAtMillis)
    Text(
        text = elapsed.toClockLabel(),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

// Previews: one per scenario

@PreviewScreens
@Composable
private fun ActiveWorkoutScreenPreview() = SetwiseScreenPreview {
    ActiveWorkoutScreen(uiState = SampleActiveWorkoutState, onAction = {})
}

@PreviewScreens
@Composable
private fun ActiveWorkoutEmptyPreview() = SetwiseScreenPreview {
    ActiveWorkoutScreen(
        uiState = SampleActiveWorkoutState.copy(name = "Workout", exercises = emptyList(), expandedExerciseId = null),
        onAction = {},
    )
}

@PreviewScreens
@Composable
private fun ActiveWorkoutFinishDialogPreview() = SetwiseScreenPreview {
    ActiveWorkoutScreen(
        uiState = SampleActiveWorkoutState.copy(dialog = ActiveWorkoutDialog.FinishWithIncompleteSets(incompleteSets = 5)),
        onAction = {},
    )
}

/** A workout mid-way: previews and UI tests. */
internal val SampleActiveWorkoutState = ActiveWorkoutUiState(
    isLoading = false,
    name = "Push Day",
    startedAtMillis = PREVIEW_NOW_MILLIS - 38 * 60_000,
    startTime = LocalTime.of(18, 42),
    expandedExerciseId = 1,
    exercises = listOf(
        WorkoutExerciseUi(
            id = 1, exerciseId = 10, name = "Bench Press (Barbell)", kind = SetKind.WeightReps, restSec = 120,
            lastTime = "60 × 8 · 60 × 8 · 60 × 7 · 57.5 × 9",
            sets = listOf(
                SetUi(1, 1, "60 × 8", "60", "8", "60", "8", isCompleted = true, isPr = false),
                SetUi(2, 2, "60 × 8", "62.5", "8", "60", "8", isCompleted = true, isPr = true),
                SetUi(3, 3, "60 × 7", "", "", "62.5", "8", isCompleted = false, isPr = false),
                SetUi(4, 4, "57.5 × 9", "", "", "57.5", "9", isCompleted = false, isPr = false),
            ),
        ),
        WorkoutExerciseUi(
            id = 2, exerciseId = 11, name = "Overhead Press", kind = SetKind.WeightReps, restSec = 90,
            lastTime = "40 × 6 · 40 × 6 · 37.5 × 8",
            sets = List(3) { SetUi(10L + it, it + 1, null, "", "", "40", "6", isCompleted = false, isPr = false) },
        ),
        WorkoutExerciseUi(
            id = 3, exerciseId = 12, name = "Treadmill", kind = SetKind.Cardio, restSec = 0, lastTime = null,
            sets = listOf(SetUi(20, 1, null, "", "", "", "", isCompleted = false, isPr = false)),
        ),
    ),
)
