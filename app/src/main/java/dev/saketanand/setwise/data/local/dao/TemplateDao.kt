package dev.saketanand.setwise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.saketanand.setwise.data.local.entity.TemplateEntity
import dev.saketanand.setwise.data.local.entity.TemplateExerciseEntity
import dev.saketanand.setwise.data.local.relation.TemplateLastUsedRow
import dev.saketanand.setwise.data.local.relation.TemplateWithExercises
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Insert
    suspend fun insertTemplate(template: TemplateEntity): Long

    @Insert
    suspend fun insertTemplateExercises(items: List<TemplateExerciseEntity>)

    /**
     * All templates with their exercises. @Transaction: Room runs the template query and the
     * relation queries together, so the lists always match the templates.
     */
    @Transaction
    @Query("SELECT * FROM templates ORDER BY createdAt")
    fun observeTemplatesWithExercises(): Flow<List<TemplateWithExercises>>

    /** Same as above, once (used when starting a workout from a template). */
    @Transaction
    @Query("SELECT * FROM templates WHERE id = :templateId")
    suspend fun getTemplateWithExercises(templateId: Long): TemplateWithExercises?

    @Query("UPDATE templates SET name = :name, category = :category WHERE id = :templateId")
    suspend fun updateTemplate(templateId: Long, name: String, category: String?): Int

    @Query("DELETE FROM template_exercises WHERE templateId = :templateId")
    suspend fun deleteTemplateExercises(templateId: Long)

    @Query("DELETE FROM templates WHERE id = :templateId")
    suspend fun deleteTemplate(templateId: Long)

    /** Workouts started from a deleted template become plain workouts (e.g. can be saved as a template). */
    @Query("UPDATE workouts SET templateId = NULL WHERE templateId = :templateId")
    suspend fun detachWorkouts(templateId: Long)

    /** Last time each template was used for a finished workout ("4 days ago"). */
    @Query(
        """
        SELECT templateId, MAX(startedAt) AS lastStartedAt
        FROM workouts
        WHERE templateId IS NOT NULL AND endedAt IS NOT NULL
        GROUP BY templateId
        """
    )
    fun observeLastUsed(): Flow<List<TemplateLastUsedRow>>
}
