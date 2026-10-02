package dev.saketanand.setwise.domain.repository

import dev.saketanand.setwise.domain.model.Exercise
import kotlinx.coroutines.flow.Flow

/** The exercise library as the rest of the app sees it: domain models only, no Room. */
interface ExerciseRepository {

    /**
     * @param query text the name must contain; "" for no search.
     * @param muscleGroup selected filter; null for all groups.
     */
    fun observeExercises(query: String, muscleGroup: String?): Flow<List<Exercise>>

    fun observeMuscleGroups(): Flow<List<String>>
}
