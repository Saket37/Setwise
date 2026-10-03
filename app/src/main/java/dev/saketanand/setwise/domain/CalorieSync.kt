package dev.saketanand.setwise.domain

import android.util.Log
import dev.saketanand.setwise.domain.ai.CalorieEstimator
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dev.saketanand.setwise.util.DateProvider
import java.time.Duration

/**
 * Keeps calorie estimates filled in, app-wide: whenever a finished workout has none (just
 * finished, its times corrected, or a body weight was only now entered) and the body weight is
 * known, works it out ([CalorieEstimator]: the on-device model when it's there, else the
 * formula) and saves it. Started once, in SetwiseApp.
 */
class CalorieSync(
    private val workoutRepository: WorkoutRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val estimator: CalorieEstimator,
    private val dateProvider: DateProvider,
) {
    fun start(scope: CoroutineScope) {
        scope.launch { run() }
    }

    /** Runs until cancelled. Separate from [start] for tests. */
    suspend fun run() {
        val bodyWeight: Flow<Double?> = userSettingsRepository.settings.map { it.bodyWeightKg }.distinctUntilChanged()
        combine(bodyWeight, workoutRepository.observeWorkoutsWithoutCalories()) { weight, ids -> weight to ids }
            .catch { e -> Log.e(TAG, "Watching for workouts without calories failed", e) }
            // One pass at a time, never cancelled midway (a model answer can take seconds):
            // each saved estimate re-emits the list, and conflate keeps just the newest for the
            // next pass, which skips what's already done.
            .conflate()
            .collect { (weight, ids) ->
                weight ?: return@collect // the summary's tile asks for it
                // AICore limits each app's use: once the model fails in a pass, the rest of it
                // uses the formula (it's asked again for the next workout finished).
                var modelOk = true
                ids.forEach { id -> if (estimate(id, weight, modelOk) == false) modelOk = false }
            }
    }

    /**
     * Estimates and saves one workout. The model is only asked about recent workouts (the one
     * just finished, a correction): a backlog (e.g. a body weight entered for the first time)
     * gets the formula instead of a burst of model calls. Returns false if the model failed.
     */
    private suspend fun estimate(workoutId: Long, weightKg: Double, modelOk: Boolean): Boolean {
        try {
            val session = workoutRepository.observeSession(workoutId).first() ?: return true
            // The list can be a step behind: skip one estimated since.
            if (session.calories != null) return true
            val recent = session.endedAt?.let { Duration.between(it, dateProvider.now()) <= MODEL_WINDOW } ?: false
            val result = estimator.estimate(session, weightKg, useModel = modelOk && recent) ?: return true
            workoutRepository.setCalories(workoutId, result.estimate, result.source)
            return !result.modelFailed
        } catch (e: CancellationException) {
            throw e // the app is going away: not a failure
        } catch (e: Exception) {
            Log.e(TAG, "Estimating calories of workout $workoutId failed", e)
            return true
        }
    }

    private companion object {
        const val TAG = "CalorieSync"

        /** Workouts finished this recently are worth asking the model about. */
        val MODEL_WINDOW: Duration = Duration.ofHours(48)
    }
}
