package dev.saketanand.setwise.domain.repository

import dev.saketanand.setwise.domain.model.Template
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {

    /** All templates with their exercises and when they were last used. */
    fun observeTemplates(): Flow<List<Template>>

    /**
     * A new template from a finished workout: same name, its exercises in order, each with as
     * many sets as were done. Returns the template id.
     */
    suspend fun createFromWorkout(workoutId: Long, createdAt: Instant): Long
}
