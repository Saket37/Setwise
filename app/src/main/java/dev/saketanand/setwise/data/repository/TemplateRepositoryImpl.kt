package dev.saketanand.setwise.data.repository

import androidx.room.withTransaction
import dev.saketanand.setwise.data.local.SetwiseDatabase
import dev.saketanand.setwise.data.local.dao.TemplateDao
import dev.saketanand.setwise.data.local.dao.WorkoutDao
import dev.saketanand.setwise.data.local.entity.TemplateEntity
import dev.saketanand.setwise.data.local.entity.TemplateExerciseEntity
import dev.saketanand.setwise.data.mapper.toDomain
import dev.saketanand.setwise.domain.model.Template
import dev.saketanand.setwise.domain.repository.TemplateRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class TemplateRepositoryImpl(
    private val database: SetwiseDatabase,
    private val templateDao: TemplateDao,
    private val workoutDao: WorkoutDao,
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

    override suspend fun createFromWorkout(workoutId: Long, createdAt: Instant): Long =
        database.withTransaction {
            val workout = requireNotNull(workoutDao.getWorkoutWithExercises(workoutId)) { "No workout $workoutId" }
            val templateId = templateDao.insertTemplate(
                TemplateEntity(name = workout.workout.name, createdAt = createdAt.toEpochMilli())
            )
            templateDao.insertTemplateExercises(
                workout.items
                    .filter { it.sets.isNotEmpty() }
                    .sortedWith(compareBy({ it.item.position }, { it.item.id }))
                    .mapIndexed { index, row ->
                        TemplateExerciseEntity(
                            templateId = templateId,
                            exerciseId = row.exercise.id,
                            position = index,
                            targetSets = row.sets.size,
                        )
                    }
            )
            templateId
        }
}
