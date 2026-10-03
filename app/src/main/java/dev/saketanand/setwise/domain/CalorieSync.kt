package dev.saketanand.setwise.domain

import android.util.Log
import dev.saketanand.setwise.domain.model.CalorieFormula
import dev.saketanand.setwise.domain.repository.UserSettingsRepository
import dev.saketanand.setwise.domain.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps calorie estimates filled in, app-wide: whenever a finished workout has none (just
 * finished, its times corrected, or a body weight was only now entered) and the body weight is
 * known, works it out by [CalorieFormula] and saves it. Started once, in SetwiseApp.
 * (Milestone 10 adds the on-device model's estimate on top, with this as the fallback.)
 */
class CalorieSync(
    private val workoutRepository: WorkoutRepository,
    private val userSettingsRepository: UserSettingsRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch { run() }
    }

    /** Runs until cancelled. Separate from [start] for tests. */
    suspend fun run() {
        val bodyWeight: Flow<Double?> = userSettingsRepository.settings.map { it.bodyWeightKg }.distinctUntilChanged()
        combine(bodyWeight, workoutRepository.observeWorkoutsWithoutCalories()) { weight, ids -> weight to ids }
            .catch { e -> Log.e(TAG, "Watching for workouts without calories failed", e) }
            .collectLatest { (weight, ids) ->
                weight ?: return@collectLatest // the summary's tile asks for it
                ids.forEach { id -> estimate(id, weight) }
            }
    }

    private suspend fun estimate(workoutId: Long, weightKg: Double) {
        runCatching {
            val session = workoutRepository.observeSession(workoutId).first() ?: return
            val estimate = CalorieFormula.estimate(session, weightKg) ?: return
            workoutRepository.setCalories(workoutId, estimate, CalorieFormula.SOURCE)
        }.onFailure { e -> Log.e(TAG, "Estimating calories of workout $workoutId failed", e) }
    }

    private companion object {
        const val TAG = "CalorieSync"
    }
}
