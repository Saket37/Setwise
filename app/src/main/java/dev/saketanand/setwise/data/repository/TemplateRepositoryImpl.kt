package dev.saketanand.setwise.data.repository

import dev.saketanand.setwise.data.local.dao.TemplateDao
import dev.saketanand.setwise.data.mapper.toDomain
import dev.saketanand.setwise.domain.model.Template
import dev.saketanand.setwise.domain.repository.TemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class TemplateRepositoryImpl(
    private val templateDao: TemplateDao,
) : TemplateRepository {

    override fun observeTemplates(): Flow<List<Template>> =
        // Re-emits when templates change or when a workout finishes (last-used dates).
        combine(
            templateDao.observeTemplatesWithExercises(),
            templateDao.observeLastUsed(),
        ) { templates, lastUsed ->
            val lastUsedById = lastUsed.associate { it.templateId to it.lastStartedAt }
            templates.map { it.toDomain(lastUsedAtMillis = lastUsedById[it.template.id]) }
        }
}
