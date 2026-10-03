package dev.saketanand.setwise.domain.ai

import android.util.Log
import dev.saketanand.setwise.domain.model.WorkoutFacts
import dev.saketanand.setwise.domain.model.WorkoutSession
import dev.saketanand.setwise.domain.repository.ExerciseRepository
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first

/**
 * Debug-only check of the on-device model on a real phone: runs the calorie estimate and the
 * summary insight on the last few finished workouts and logs, per workout, the formula's
 * number, the model's answer, whether it was used and how long it took, the insight's facts
 * and text, "New exercise" help on sample names, then quick-log lines (tags SetwiseAiCheck,
 * CalorieEstimator, WorkoutInsightWriter, ExerciseAssistant, QuickLogInterpreter, GeminiNanoModel).
 * Saves nothing. One run at a time. Started by MainActivity from a debug launch extra (add
 * `--ez ai_check_quick_log true` for the quick-log lines alone):
 *
 *     adb shell am start -n dev.saketanand.setwise/.MainActivity --ez ai_check true
 */
class AiCheck(
    private val model: OnDeviceModel,
    private val estimator: CalorieEstimator,
    private val insightWriter: WorkoutInsightWriter,
    private val exerciseAssistant: ExerciseAssistant,
    private val quickLogInterpreter: QuickLogInterpreter,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val userSettingsRepository: UserSettingsRepository,
) {
    suspend fun run(quickLogOnly: Boolean = false, workouts: Int = DEFAULT_WORKOUTS) {
        if (!running.compareAndSet(false, true)) {
            Log.i(TAG, "Already running")
            return
        }
        try {
            if (quickLogOnly) checkQuickLog() else checkAll(workouts)
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
        SAMPLE_LINES.forEach { line ->
            val understood = when (val result = quickLogInterpreter.interpret(line, empty, null, recent, library)) {
                is QuickLogResult.Sets -> "${result.target.exercise.name}: " +
                    result.sets.joinToString { listOfNotNull(it.weightKg?.let { kg -> "$kg kg" }, it.reps?.let { r -> "$r" }, it.seconds?.let { s -> "${s}s" }).joinToString(" × ") } +
                    " (${result.source})"
                is QuickLogResult.Cardio -> "${result.target.exercise.name}: ${result.values}"
                is QuickLogResult.NotUnderstood -> "not understood: ${result.reason}"
            }
            Log.i(TAG, "'$line' → $understood")
        }
    }

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
        const val EXTRA_QUICK_LOG_ONLY = "ai_check_quick_log"
        private val running = AtomicBoolean(false)
        private const val DEFAULT_WORKOUTS = 5
    }
}
