package dev.saketanand.setwise.domain.repository

import dev.saketanand.setwise.domain.model.Exercise
import dev.saketanand.setwise.domain.model.RecentExercise
import kotlinx.coroutines.flow.Flow

/** The exercise library as the rest of the app sees it: domain models only, no Room. */
interface ExerciseRepository {

    /**
     * @param query text the name must contain; "" for no search.
     * @param muscleGroup selected filter; null for all groups.
     */
    fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>>

    /** Muscle groups for filter chips, biggest group first. */
    fun observeMuscleGroups(): Flow<List<String>>

    /** Up to [limit] exercises from finished workouts, most recent first, with their last top set. */
    fun observeRecentExercises(limit: Int): Flow<List<RecentExercise>>

    /** Live size of the exercise library ("128 exercises ready"). */
    fun observeExerciseCount(): Flow<Int>

    /** The exercises with these ids, in the same order; unknown ids are left out. */
    suspend fun getExercises(ids: List<Long>): List<Exercise>

    /** Null if there's no exercise with this id (e.g. deleted). */
    fun observeExercise(id: Long): Flow<Exercise?>
}
