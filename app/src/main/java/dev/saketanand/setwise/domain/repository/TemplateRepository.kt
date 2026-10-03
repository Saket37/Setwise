package dev.saketanand.setwise.domain.repository

import dev.saketanand.setwise.domain.model.Template
import dev.saketanand.setwise.domain.model.TemplateDraft
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {

    /** All templates with their exercises and when they were last used. */
    fun observeTemplates(): Flow<List<Template>>

    /** One template with its exercises; null if it doesn't exist (e.g. deleted). */
    suspend fun getTemplate(templateId: Long): Template?

    /**
     * Creates ([TemplateDraft.id] = 0) or replaces a template's name, category and exercises, in
     * one transaction. Returns its id.
     */
    suspend fun saveTemplate(draft: TemplateDraft, now: Instant): Long

    /** Deletes the template; workouts started from it keep their data. */
    suspend fun deleteTemplate(templateId: Long)

    /**
     * A new template from a finished workout: same name, its exercises in order, each with as
     * many sets as were done. Returns the template id.
     */
    suspend fun createFromWorkout(workoutId: Long, createdAt: Instant): Long
}
