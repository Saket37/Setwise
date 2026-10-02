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
