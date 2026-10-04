package dev.saketanand.setwise.domain.ai

import android.content.Context
import android.util.Log
import dev.saketanand.setwise.domain.model.BestSetFact
import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.ExerciseChange
import dev.saketanand.setwise.domain.model.ExerciseSession
import dev.saketanand.setwise.domain.model.GoalPlanner
import dev.saketanand.setwise.domain.model.GoalReader
import dev.saketanand.setwise.domain.model.Intensity
import dev.saketanand.setwise.domain.model.LoggedSet
import dev.saketanand.setwise.domain.model.Measure
import dev.saketanand.setwise.domain.model.Plateau
import dev.saketanand.setwise.domain.model.PrKind
import dev.saketanand.setwise.domain.model.PreviousWorkout
import dev.saketanand.setwise.domain.model.Progression
import dev.saketanand.setwise.domain.model.RecordFact
import dev.saketanand.setwise.domain.model.SetFact
import dev.saketanand.setwise.domain.model.SharedSet
import dev.saketanand.setwise.domain.model.WeekFacts
import dev.saketanand.setwise.domain.model.WorkoutFacts
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import org.json.JSONArray

/**
 * Debug-only check of the on-device model on a real phone: runs the calorie estimate and the
 * summary insight on the last few finished workouts and logs, per workout, the formula's
 * number, the model's answer, whether it was used and how long it took, the insight's facts
 * and text, "New exercise" help on sample names, quick-log lines, then plateau notes (tags
 * SetwiseAiCheck, CalorieEstimator, WorkoutInsightWriter, ExerciseAssistant, QuickLogInterpreter,
 * PlateauNoteWriter, GeminiNanoModel). Saves nothing. One run at a time. Started by
 * MainActivity from a debug launch extra (add `--es ai_check_only quick_log`, `plateau` or
 * `client` for one part alone, which finishes before the screen times out):
 *
 *     adb shell am start -n dev.saketanand.setwise/.MainActivity --ez ai_check true
 */
// One function per feature it checks, by design.
@Suppress("TooManyFunctions")
class AiCheck(
    private val context: Context,
    private val model: OnDeviceModel,
    private val speechInput: SpeechInput,
    private val estimator: CalorieEstimator,
    private val insightWriter: WorkoutInsightWriter,
    private val weeklyRecapWriter: WeeklyRecapWriter,
    private val goalPlanAssistant: GoalPlanAssistant,
    private val exerciseAssistant: ExerciseAssistant,
    private val quickLogInterpreter: QuickLogInterpreter,
    private val plateauNotes: PlateauNoteWriter,
    private val historyAssistant: HistoryAssistant,
    private val importReader: ImportReader,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val userSettingsRepository: UserSettingsRepository,
) {
    /** @param only [ONLY_QUICK_LOG] or [ONLY_PLATEAU] for that part alone; null: everything. */
    suspend fun run(only: String? = null, workouts: Int = DEFAULT_WORKOUTS) {
        if (!running.compareAndSet(false, true)) {
            Log.i(TAG, "Already running")
            return
        }
        try {
            when (only) {
                ONLY_QUICK_LOG -> checkQuickLog()
                ONLY_PLATEAU -> checkPlateauNotes()
                ONLY_CLIENT -> checkClientReopen()
                ONLY_SPEECH -> checkSpeech()
                ONLY_ASK -> checkAsk()
                ONLY_IMPORT -> checkImport()
                ONLY_PERSONAL -> checkPersonal()
                ONLY_GOAL -> checkGoal()
                else -> checkAll(workouts)
            }
            Log.i(TAG, "Done")
        } finally {
            running.set(false)
        }
    }

    private suspend fun checkAll(workouts: Int) {
        val availability = model.availability()
        val weight = userSettingsRepository.settings.first().bodyWeightKg
        Log.i(TAG, "Start: model $availability, body weight ${weight ?: "not set"}")
        if (weight == null) {
            Log.i(TAG, "No body weight: no estimates. Set it in Settings.")
        } else {
            checkWorkouts(workouts, weight)
        }
        checkExerciseNames()
        checkQuickLog()
        checkPlateauNotes()
    }

    /** Plans for goals that say more than code reads: code's picks, then the model's. */
    private suspend fun checkGoal() {
        val library = exerciseRepository.observeExercises("", null).first()
        SAMPLE_GOALS.forEach { text ->
            val goal = GoalReader.read(text)
            val startedAt = System.nanoTime()
            val byCode = GoalPlanner.plan(goal, library)
            val plan = goalPlanAssistant.plan(text, goal, library, emptySet())
            Log.i(TAG, "Goal (${(System.nanoTime() - startedAt) / 1_000_000} ms, byModel=${plan.byModel}): $text")
            plan.templates.zip(byCode).forEach { (chosen, code) ->
                val changes = chosen.exercises.zip(code.exercises).filter { (a, b) -> a.exercise != b.exercise }
                    .joinToString("; ") { (a, b) -> "${b.exercise.name} -> ${a.exercise.name}" }
                Log.i(TAG, "  ${chosen.name}: " + chosen.exercises.joinToString(" | ") { it.exercise.name } + if (changes.isNotEmpty()) "  [model: $changes]" else "")
            }
        }
    }

    /** The workout insight and weekly recap with the profile's name and planned days (or sample ones). */
    private suspend fun checkPersonal() {
        val person = PersonFacts.from(userSettingsRepository.settings.first())
            .let { if (it.firstName == null) it.copy(firstName = "Alex") else it }
            .let { if (it.plannedDaysPerWeek == null) it.copy(plannedDaysPerWeek = 4) else it }
        val workout = WorkoutFacts(
            workoutName = "Push Day", minutes = 64, intensity = Intensity.Moderate, medianRestSec = 95,
            changes = listOf(ExerciseChange("Bench Press (Barbell)", Measure.Weight, SetFact(62.5, 8, null), SetFact(60.0, 8, null))),
            records = listOf(RecordFact("Bench Press (Barbell)", PrKind.Weight)),
            volumeKg = 8_420.0, previous = PreviousWorkout("Push Day", 7_900.0),
        )
        val week = WeekFacts(
            LocalDate.of(2026, 9, 28), 3, java.time.Duration.ofMinutes(190), 1, 21_400.0, 6,
            BestSetFact("Back Squat", 100.0, 5, null, isPr = true), null, null,
        )
        listOf(person, person.copy(firstName = "Priya")).forEachIndexed { round, person ->
            Log.i(TAG, "Personal $round insight facts:\n${WorkoutInsightWriter.factLines(workout, person)}")
            Log.i(TAG, "Personal $round insight: ${insightWriter.write(workout, person) ?: "(rejected or no model)"}")
            Log.i(TAG, "Personal $round recap facts:\n${WeeklyRecapWriter.factLines(week, person)}")
            Log.i(TAG, "Personal $round recap: ${weeklyRecapWriter.write(week, person) ?: "(rejected or no model)"}")
        }
    }

    /** Free-form workout logs: what the model makes of them, after the number guard. */
    private suspend fun checkImport() {
        SAMPLE_LOGS.forEach { log ->
            val startedAt = System.nanoTime()
            val read = importReader.fromText(log)
            Log.i(TAG, "Log (${(System.nanoTime() - startedAt) / 1_000_000} ms, ${read.source}): ${log.lines().first()}")
            read.workouts.forEach { workout ->
                val exercises = workout.exercises.joinToString(" | ") { exercise -> exercise.name + " " + exercise.sets.joinToString(", ", transform = ::setLabel) }
                Log.i(TAG, "  ${workout.name} @ ${workout.startedAt}: $exercises")
            }
            if (read.workouts.isEmpty()) Log.i(TAG, "  nothing kept")
        }
    }

    /** History questions over the real log: the reply, and whether the model picked the lookup. */
    private suspend fun checkAsk() {
        val log = workoutRepository.getTrainingLog()
        val library = exerciseRepository.observeExercises("", null).first()
        val zone = java.time.ZoneId.systemDefault()
        SAMPLE_QUESTIONS.forEach { question ->
            val startedAt = System.nanoTime()
            val reply = historyAssistant.ask(question, log, library, java.time.LocalDate.now(zone), zone)
            Log.i(TAG, "'$question' (${(System.nanoTime() - startedAt) / 1_000_000} ms) → $reply")
        }
    }

    /**
     * Spoken quick-log lines (debug assets: text-to-speech clips, US and Indian English): what
     * on-device speech recognition heard and what the quick log
     * understands from each. Downloads the speech model if it's missing.
     */
    private suspend fun checkSpeech() {
        if (!ensureReady("Speech recognition", { speechInput.availability() }, { speechInput.download() })) return
        val recent = exerciseRepository.observeRecentExercises(50).first().map { it.exercise }
        val library = exerciseRepository.observeExercises("", null).first()
        val empty = WorkoutSession(0, "", null, Instant.EPOCH, null, emptyList())
        val clips = JSONArray(context.assets.open("speech/index.json").bufferedReader().use { it.readText() })
        for (i in 0 until clips.length()) {
            val clip = clips.getJSONObject(i)
            val file = File(context.cacheDir, clip.getString("file"))
            context.assets.open("speech/${clip.getString("file")}").use { input -> file.outputStream().use { input.copyTo(it) } }
            val startedAt = System.nanoTime()
            val heard = try {
                withTimeout(SPEECH_TIMEOUT_MS) {
                    speechInput.listen(file).toList().filterIsInstance<Heard.Final>().joinToString(" ") { it.text.trim() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "(failed: ${e.message})"
            } finally {
                file.delete()
            }
            val ms = (System.nanoTime() - startedAt) / 1_000_000
            Log.i(TAG, "Said (${clip.getString("voice")}): '${clip.getString("said")}'")
            Log.i(TAG, "  heard in $ms ms: '$heard' → ${understood(heard, empty, recent, library)}")
        }
    }

    /** Ready, after downloading if it can be; logs what happened. */
    private suspend fun ensureReady(
        feature: String,
        availability: suspend () -> ModelAvailability,
        download: () -> Flow<ModelDownload>,
    ): Boolean {
        val status = availability()
        Log.i(TAG, "$feature: $status")
        if (status == ModelAvailability.Downloadable || status == ModelAvailability.Downloading) {
            val last = download().onEach { if (it !is ModelDownload.Progress) Log.i(TAG, "$feature download: $it") }.lastOrNull()
            Log.i(TAG, "$feature after download: ${availability()} ($last)")
        }
        return availability() == ModelAvailability.Ready
    }

    private suspend fun understood(line: String, session: WorkoutSession, recent: List<Exercise>, library: List<Exercise>): String =
        when (val result = quickLogInterpreter.interpret(line, session, null, recent, library)) {
            is QuickLogResult.Sets -> "${result.target.exercise.name}: " +
                result.sets.joinToString { listOfNotNull(it.weightKg?.let { kg -> "$kg kg" }, it.reps?.let { r -> "$r" }, it.seconds?.let { s -> "${s}s" }).joinToString(" × ") } +
                " (${result.source})"
            is QuickLogResult.Cardio -> "${result.target.exercise.name}: ${result.values}"
            is QuickLogResult.NotUnderstood -> "not understood: ${result.reason}"
        }

    /**
     * The model's client closed (as when the app goes to the background) and reopened between
     * two answers: both should come back.
     */
    private suspend fun checkClientReopen() {
        val request = ModelRequest(system = "Answer with one word.", prompt = "Say ok.", temperature = 0f, maxOutputTokens = 8)
        suspend fun ask() = try {
            "'${model.generate(request).trim()}'"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "failed: ${e.message}"
        }
        Log.i(TAG, "Before closing: ${ask()}")
        model.onAppInBackground(true)
        model.onAppInBackground(false)
        Log.i(TAG, "After reopening: ${ask()}")
    }

    /**
     * Plateau notes: for each recent exercise that has stalled, then for sample plateaus (a
     * lift, bodyweight reps, a hold), the facts and the note, or why there's none.
     */
    private suspend fun checkPlateauNotes() {
        val now = Instant.now()
        val real = exerciseRepository.observeRecentExercises(50).first().mapNotNull { recent ->
            val sessions = workoutRepository.observeExerciseSessions(recent.exercise.id).first()
            Progression.plateau(recent.exercise, sessions, now)?.let { Triple(recent.exercise.name, it, sessions.first()) }
        }
        Log.i(TAG, "Plateaus in your logs: ${real.size}")
        (real + SAMPLE_PLATEAUS).forEach { (name, plateau, last) ->
            Log.i(TAG, "Plateau facts:\n${PlateauNoteWriter.factLines(name, plateau, last)}")
            Log.i(TAG, "Plateau note: ${plateauNotes.write(name, plateau, last) ?: "(none: the template is shown)"}")
        }
    }

    private suspend fun checkWorkouts(workouts: Int, weight: Double) {
        val history = workoutRepository.observeHistory().first()
        val recent = history.take(workouts)
        if (recent.isEmpty()) Log.i(TAG, "No finished workouts yet.")
        recent.forEach { item ->
            val session = workoutRepository.observeSession(item.id).first() ?: return@forEach
            val result = estimator.estimate(session, weight, useModel = true)
            Log.i(
                TAG,
                "${item.name} (workout ${item.id}, ${session.exercises.size} exercises): " +
                    "${result?.estimate?.kcal} kcal ${result?.estimate?.intensity?.storedName} from ${result?.source}; " +
                    "saved now: ${session.calories} kcal",
            )
            val facts = WorkoutFacts.of(session, history)
            Log.i(TAG, "Facts:\n${WorkoutInsightWriter.factLines(facts)}")
            Log.i(TAG, "Insight: ${insightWriter.write(facts) ?: "(none: the template is shown)"}")
        }
    }

    /** "New exercise" help on names people type: the library match and the suggested details. */
    private suspend fun checkExerciseNames() {
        val library = exerciseRepository.observeExercises("", null).first()
        SAMPLE_NAMES.forEach { name ->
            val match = exerciseAssistant.findMatch(name, library)
            val details = exerciseAssistant.suggestDetails(name)
            Log.i(TAG, "'$name' → match: ${match?.name ?: "none"}; details: ${details?.guess} (${details?.source})")
        }
    }

    /** Quick-log lines, outside a workout (exercises done before, then the library): what each is understood as, and by what. */
    private suspend fun checkQuickLog() {
        val recent = exerciseRepository.observeRecentExercises(50).first().map { it.exercise }
        val library = exerciseRepository.observeExercises("", null).first()
        val empty = WorkoutSession(0, "", null, Instant.EPOCH, null, emptyList())
        SAMPLE_LINES.forEach { line -> Log.i(TAG, "'$line' → ${understood(line, empty, recent, library)}") }
    }

    private fun setLabel(set: SharedSet) =
        listOfNotNull(set.weightKg?.let { "$it kg" }, set.reps?.let { "×$it" }, set.seconds?.let { "${it}s" }).joinToString(" ")

    companion object {
        const val TAG = "SetwiseAiCheck"

        /** The first five the parser reads alone; the rest have words only the model can place. */
        private val SAMPLE_LINES = listOf(
            "bench three sets of eight at sixty",
            "plank 3x45s",
            "ohp 3 sets of 6 at 40, last one 37.5 for 8",
            "lat pulldown 3x12 at 50 but the last set only 9 reps",
            "squat 100 for 5 then 2 more sets of 5 at 105",
            "bench 60 for 8, 8, 7",
            "deadlift 3x5 at 120 then dropped to 100 for 8",
            "incline db press 22 for 10, 9 and 8",
            "pull ups 10, 8, 6",
        )

        private val SAMPLE_NAMES = listOf(
            "flat db press", "bb rdl", "lat pull down", "incline db fly", "ohp", "skull crushers",
            "zercher squat", "jefferson curl", "farmer carry", "dead hang", "incline walk",
            "landmine press", "bulgarian split squat", "hip thrust machine", "copenhagen plank",
        )
        const val EXTRA = "ai_check"
        const val EXTRA_ONLY = "ai_check_only"
        const val ONLY_QUICK_LOG = "quick_log"
        const val ONLY_PLATEAU = "plateau"
        const val ONLY_CLIENT = "client"
        const val ONLY_SPEECH = "speech"
        const val ONLY_ASK = "ask"
        const val ONLY_PERSONAL = "personal"
        const val ONLY_GOAL = "goal"

        /** Goals that say more than code reads: the model's picks should follow them. */
        private val SAMPLE_GOALS = listOf(
            "Build muscle 4 days a week, an hour, my knees don't like deep squats",
            "Get stronger at bench, 3 days, 45 min, I hate burpees and love pull-ups",
            "3 days a week at the gym, 45 minutes, sore lower back so go easy on deadlifts",
        )
        const val ONLY_IMPORT = "import"

        /** Prose no code reader takes apart: what's left for the model. */
        private val SAMPLE_LOGS = listOf(
            "Leg day on 29/09/2026 at 18:30. Squatted 100 for three sets of 5, then planks of 60 and 45 seconds.",
            "2 Oct 2026, push: benched 60 for 8, then 62.5 for 6 twice. Lateral raises with 10 for 15, three times.",
            "Saturday 3rd October 2026 morning I did pullups (10, 8 and 6) and barbell rows, 3 sets of 10 with 60.",
        )

        /** The first five the code reads; the rest need the model to pick a lookup (or "none"). */
        private val SAMPLE_QUESTIONS = listOf(
            "When did I last squat 100 kg?",
            "What's my best bench press?",
            "How much volume did I do last month?",
            "How many times did I train legs this month?",
            "Show workouts where I hit a PR",
            "what's the heaviest I've deadlifted",
            "did I do any chest work this week",
            "how strong is my overhead press these days",
            "total kilos moved in September",
            "should I eat more protein",
        )

        private val SAMPLE_PLATEAUS: List<Triple<String, Plateau, ExerciseSession>> = run {
            val since = Instant.parse("2026-09-07T12:00:00Z")
            val last = Instant.parse("2026-10-02T12:00:00Z")
            fun sets(vararg sets: LoggedSet) = sets.toList()
            listOf(
                Triple(
                    "Overhead Press (Barbell)",
                    Plateau(since, weeks = 4, sessions = 8, best = 48.2, measure = Measure.Weight),
                    ExerciseSession(0, last, sets(LoggedSet(40.0, 6, null, null), LoggedSet(40.0, 6, null, null), LoggedSet(40.0, 5, null, null))),
                ),
                Triple(
                    "Back Squat (Barbell)",
                    Plateau(since, weeks = 6, sessions = 7, best = 122.0, measure = Measure.Weight),
                    ExerciseSession(0, last, sets(LoggedSet(105.0, 5, null, null), LoggedSet(105.0, 5, null, null), LoggedSet(105.0, 4, null, null))),
                ),
                Triple(
                    "Pull-up",
                    Plateau(since, weeks = 3, sessions = 6, best = 10.0, measure = Measure.Reps),
                    ExerciseSession(0, last, sets(LoggedSet(null, 10, null, null), LoggedSet(null, 8, null, null), LoggedSet(null, 7, null, null))),
                ),
                Triple(
                    "Plank",
                    Plateau(since, weeks = 5, sessions = 10, best = 60.0, measure = Measure.Seconds),
                    ExerciseSession(0, last, sets(LoggedSet(null, null, 60, null), LoggedSet(null, null, 50, null))),
                ),
            )
        }
        private val running = AtomicBoolean(false)
        private const val DEFAULT_WORKOUTS = 5
        private const val SPEECH_TIMEOUT_MS = 30_000L
    }
}
