package dev.saketanand.setwise.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.saketanand.setwise.domain.ai.PersonFacts
import dev.saketanand.setwise.domain.ai.WeeklyRecapWriter
import dev.saketanand.setwise.domain.model.DayCheckIn
import dev.saketanand.setwise.domain.model.DayStatus
import dev.saketanand.setwise.domain.model.WeeklyRecap
import dev.saketanand.setwise.domain.model.WeeklySummaryRules
import dev.saketanand.setwise.domain.repository.DayMarkRepository
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.TemplateRepository
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import dev.saketanand.setwise.util.DateProvider
import dev.saketanand.setwise.util.weekRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Screen: [HomeScreenRoot]. */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class HomeViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val templateRepository: TemplateRepository,
    private val dayMarkRepository: DayMarkRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val dateProvider: DateProvider,
    private val weeklyRecapWriter: WeeklyRecapWriter,
) : ViewModel() {

    private val zone: ZoneId get() = dateProvider.zone

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** One-off events (navigate after a workout is created, show an error). Each is handled once. */
    // BUFFERED: if the screen isn't collecting at that moment (e.g. app just backgrounded),
    // send() doesn't wait; the event is delivered when the screen is visible again.
    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = eventChannel.receiveAsFlow()

    init {
        // Everything the Workout tab shows, live: any change in the database re-emits, and so
        // does the date at midnight (header date, "2 days ago", and this week's window).
        // isLoading turns false with the first emission, so the first-run layout can't flash
        // before the data is known.
        dateProvider.today()
            .flatMapLatest { today ->
                val (weekStart, weekEnd) = weekRange(today, zone)
                combine(
                    workoutRepository.observeLastFinishedWorkout(),
                    workoutRepository.observeActiveWorkout(),
                    workoutRepository.observeStats(weekStart, weekEnd),
                    templateRepository.observeTemplates(),
                    exerciseRepository.observeExerciseCount(),
                ) { lastWorkout, activeWorkout, weekStats, templates, exerciseCount ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            today = today,
                            lastWorkout = lastWorkout?.toLastWorkoutUi(today, zone),
                            activeWorkout = activeWorkout?.toUi(),
                            weekStats = weekStats.toUi(),
                            templates = templates.map { template -> template.toUi(today, zone) },
                            exerciseCount = exerciseCount,
                        )
                    }
                }
            }
            // A failing query must not crash the app (launchIn would rethrow); log and show
            // what we have.
            .catch { e ->
                Log.e(TAG, "Loading the Workout tab failed", e)
                _state.update { it.copy(isLoading = false) }
            }
            .launchIn(viewModelScope)

        observeCheckIn()
        observeWeeklySummary()
    }

    /**
     * Last week's summary card (artboard 13), all this week until closed: its facts from the
     * training log, and the recap written on-device once and kept (else the card's template).
     */
    private fun observeWeeklySummary() {
        combine(
            dateProvider.today(),
            workoutRepository.observeHistory(),
            userSettingsRepository.settings.map { it.weeklySummaryDismissedWeek }.distinctUntilChanged(),
        ) { today, history, dismissed -> Triple(today, history, dismissed) }
            .collectLatestIn { (today, history, dismissed) ->
                val week = WeeklySummaryRules.lastWeekStart(today)
                val facts = if (dismissed == week) {
                    null
                } else {
                    val library = exerciseRepository.observeExercises("", null).first()
                    WeeklySummaryRules.facts(week, workoutRepository.getTrainingLog(), history, library, zone)
                }
                if (facts == null) {
                    _state.update { it.copy(weeklySummary = null) }
                    return@collectLatestIn
                }
                val kept = userSettingsRepository.settings.first().weeklyRecap?.takeIf { it.weekStart == week }?.text
                val writing = kept == null && weeklyRecapWriter.canWrite()
                _state.update { it.copy(weeklySummary = WeeklySummaryUi(facts, kept, isGeneratingRecap = writing)) }
                if (writing) {
                    val recap = weeklyRecapWriter.write(facts, PersonFacts.from(userSettingsRepository.settings.first()))
                    recap?.let { userSettingsRepository.setWeeklyRecap(WeeklyRecap(week, it)) }
                    _state.update { it.copy(weeklySummary = WeeklySummaryUi(facts, recap, isGeneratingRecap = false)) }
                }
            }
    }

    /** collectLatest in viewModelScope; a failure is logged, not thrown. */
    private fun <T> Flow<T>.collectLatestIn(block: suspend (T) -> Unit) {
        viewModelScope.launch {
            try {
                collectLatest(block)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "The weekly summary failed", e)
            }
        }
    }

    /**
     * The day check-in ([DayCheckIn]): opens the sheet when there are unlogged past days to ask
     * about (at most once a day, never during a workout), and keeps the answers in it live.
     */
    private fun observeCheckIn() {
        combine(
            dateProvider.today(),
            workoutRepository.observeHistory(),
            workoutRepository.observeActiveWorkout(),
            dayMarkRepository.observeMarks(),
            userSettingsRepository.settings,
        ) { today, history, activeWorkout, marks, settings ->
            val open = _state.value.checkIn
            if (open != null) {
                // Already showing: the days stay put, their answers follow the database.
                _state.update { it.copy(checkIn = CheckInUi(open.days.map { day -> day.copy(status = marks[day.date]) })) }
                return@combine
            }
            val workoutDays = history.map { it.startedAt.atZone(zone).toLocalDate() }
            val days = DayCheckIn.daysToAsk(
                today = today,
                trainedDays = workoutDays.toSet(),
                marks = marks,
                trainingDays = settings.trainingDays,
                firstWorkoutDate = workoutDays.minOrNull(),
            )
            if (DayCheckIn.shouldAsk(settings, today, settings.checkInLastAskedOn, activeWorkout != null, days)) {
                // Counts as asked once shown, whatever the answer: tomorrow at the earliest again.
                userSettingsRepository.setCheckInLastAskedOn(today)
                _state.update { it.copy(checkIn = CheckInUi(days.map { day -> CheckInDayUi(day, status = null) })) }
            }
        }
            .catch { e -> Log.e(TAG, "The day check-in failed", e) }
            .launchIn(viewModelScope)
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.OnStartWorkoutClick ->
                _state.update { it.copy(isStartSheetVisible = true) }

            HomeAction.OnStartSheetDismiss ->
                _state.update { it.copy(isStartSheetVisible = false, customStartTime = null) }

            is HomeAction.OnStartTimeChange ->
                _state.update { it.copy(customStartTime = action.startTime) }

            HomeAction.OnStartEmptyWorkout -> requestStart(templateId = null)

            is HomeAction.OnStartFromTemplate -> requestStart(templateId = action.templateId)

            HomeAction.OnConfirmDiscardAndStart -> {
                val dialog = _state.value.discardDialog ?: return
                _state.update { it.copy(discardDialog = null) }
                startWorkout(templateId = dialog.templateIdToStart, discardRunningWorkoutId = dialog.runningWorkoutId)
            }

            // Cancelled: the start time picked in the sheet doesn't carry over to the next start.
            HomeAction.OnDismissDiscardDialog -> _state.update { it.copy(discardDialog = null, customStartTime = null) }

            is HomeAction.OnSaveLastWorkoutAsTemplate -> saveWorkoutAsTemplate(action.workoutId)

            HomeAction.OnWeeklySummaryDismiss -> {
                val week = _state.value.weeklySummary?.facts?.weekStart ?: return
                _state.update { it.copy(weeklySummary = null) }
                viewModelScope.launch { userSettingsRepository.setWeeklySummaryDismissed(week) }
            }

            is HomeAction.OnCheckInMark -> mark(listOf(action.date), action.status)

            HomeAction.OnCheckInMarkAllRest -> {
                val unanswered = _state.value.checkIn?.days.orEmpty().filter { it.status == null }.map { it.date }
                _state.update { it.copy(checkIn = null) }
                mark(unanswered, DayStatus.Rest)
            }

            is HomeAction.OnCheckInLogWorkout -> {
                _state.update { it.copy(checkIn = null) }
                // No discard dialog: the check-in never shows while a workout is running.
                if (_state.value.activeWorkout == null) {
                    startWorkout(templateId = null, startedAt = DayCheckIn.backfillStartedAt(action.date, zone))
                }
            }

            HomeAction.OnCheckInDismiss -> _state.update { it.copy(checkIn = null) }

            // Navigation actions: HomeScreenRoot sends these straight to its nav callbacks.
            is HomeAction.OnResumeWorkout,
            is HomeAction.OnTemplateClick,
            HomeAction.OnCreateTemplateClick,
            HomeAction.OnCreateTemplateFromGoalClick,
            is HomeAction.OnPlateauExerciseClick -> Unit
        }
    }

    /**
     * Starts a workout, unless one is already running: then asks first (discard dialog), because
     * only one workout can be in progress at a time.
     */
    private fun requestStart(templateId: Long?) {
        // The sheet has already slid away (StartWorkoutSheet); take it out of the state too, so
        // it doesn't stay composed (invisible) behind the discard dialog. The start time stays.
        _state.update { it.copy(isStartSheetVisible = false) }
        val running = _state.value.activeWorkout
        if (running == null) {
            startWorkout(templateId)
        } else {
            _state.update {
                it.copy(
                    discardDialog = DiscardDialogUi(
                        runningWorkoutId = running.workoutId,
                        runningWorkoutName = running.name,
                        runningCompletedSets = running.completedSets,
                        templateIdToStart = templateId,
                    )
                )
            }
        }
    }

    /**
     * Creates the workout row, then tells the screen to open it via [HomeEvent.WorkoutStarted].
     * @param templateId null for an empty workout.
     * @param discardRunningWorkoutId running workout to delete in the same transaction.
     */
    private fun startWorkout(templateId: Long?, discardRunningWorkoutId: Long? = null, startedAt: Instant? = null) {
        if (_state.value.isStartingWorkout) return // ignore double taps
        _state.update { it.copy(isStartingWorkout = true) }
        viewModelScope.launch {
            // Given (a past day from the check-in); else "Change" in the sheet back-dates the
            // start to that time today; otherwise now.
            val start = startedAt
                ?: _state.value.customStartTime?.atDate(_state.value.today)?.atZone(zone)?.toInstant()
                ?: dateProvider.now()
            runCatching { workoutRepository.startWorkout(templateId, start, discardRunningWorkoutId) }
                .onSuccess { id -> eventChannel.send(HomeEvent.WorkoutStarted(id)) }
                .onFailure { eventChannel.send(HomeEvent.StartWorkoutFailed) }
            _state.update {
                it.copy(isStartingWorkout = false, isStartSheetVisible = false, customStartTime = null)
            }
        }
    }

    private fun mark(days: List<LocalDate>, status: DayStatus?) {
        if (days.isEmpty()) return
        viewModelScope.launch {
            runCatching { dayMarkRepository.mark(days, status, dateProvider.now()) }
                .onFailure { e -> Log.e(TAG, "Marking $days as $status failed", e) }
        }
    }

    /** Copies a finished workout's exercises (and set counts) into a new template. */
    private fun saveWorkoutAsTemplate(workoutId: Long) {
        if (isSavingTemplate) return // ignore double taps
        isSavingTemplate = true
        viewModelScope.launch {
            runCatching { templateRepository.createFromWorkout(workoutId, dateProvider.now()) }
                .onSuccess { id -> eventChannel.send(HomeEvent.TemplateCreated(id)) }
                .onFailure { e ->
                    Log.e(TAG, "Saving workout $workoutId as a template failed", e)
                    eventChannel.send(HomeEvent.SaveTemplateFailed)
                }
            isSavingTemplate = false
        }
    }

    private var isSavingTemplate = false

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
