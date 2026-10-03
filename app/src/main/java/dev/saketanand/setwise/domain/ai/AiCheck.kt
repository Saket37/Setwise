package dev.saketanand.setwise.domain.ai

import android.util.Log
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.first

/**
 * Debug-only check of the on-device model on a real phone: runs the calorie estimate on the
 * last few finished workouts and logs, per workout, the formula's number, the model's answer,
 * whether it was used and how long it took (tags SetwiseAiCheck, CalorieEstimator,
 * GeminiNanoModel). Saves nothing. Started by MainActivity from a debug launch extra:
 *
 *     adb shell am start -n dev.saketanand.setwise/.MainActivity --ez ai_check true
 */
class AiCheck(
    private val model: OnDeviceModel,
    private val estimator: CalorieEstimator,
    private val workoutRepository: WorkoutRepository,
    private val userSettingsRepository: UserSettingsRepository,
) {
    suspend fun run(workouts: Int = DEFAULT_WORKOUTS) {
        val availability = model.availability()
        val weight = userSettingsRepository.settings.first().bodyWeightKg
        Log.i(TAG, "Start: model $availability, body weight ${weight ?: "not set"}")
        if (weight == null) {
            Log.i(TAG, "No body weight: no estimates. Set it in Settings.")
            return
        }
        val recent = workoutRepository.observeHistory().first().take(workouts)
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
        }
        Log.i(TAG, "Done")
    }

    companion object {
        const val TAG = "SetwiseAiCheck"
        const val EXTRA = "ai_check"
        private const val DEFAULT_WORKOUTS = 5
    }
}
