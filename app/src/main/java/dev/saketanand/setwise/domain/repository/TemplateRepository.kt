package dev.saketanand.setwise.domain.repository

import dev.saketanand.setwise.domain.model.Template
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {

    /** All templates with their exercises and when they were last used. */
    fun observeTemplates(): Flow<List<Template>>
}
